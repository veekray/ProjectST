package ru.projectst.rpgcore;

import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.projectst.rpgcore.cast.CastService;
import ru.projectst.rpgcore.cast.CooldownTracker;
import ru.projectst.rpgcore.cast.ManaPool;
import ru.projectst.rpgcore.damage.DamageEngine;
import ru.projectst.rpgcore.data.PlayerDataException;
import ru.projectst.rpgcore.data.PlayerDataStore;
import ru.projectst.rpgcore.loader.ContentError;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.platform.ContentService;
import ru.projectst.rpgcore.classes.ClassService;
import ru.projectst.rpgcore.platform.BukkitSkillWorld;
import ru.projectst.rpgcore.platform.ExperienceListener;
import ru.projectst.rpgcore.platform.RpgCommand;
import ru.projectst.rpgcore.platform.SkillInputListener;
import ru.projectst.rpgcore.skill.SkillRuntime;
import ru.projectst.rpgcore.skill.ZoneService;
import ru.projectst.rpgcore.stat.StatEngine;
import ru.projectst.rpgcore.stat.StatService;
import ru.projectst.rpgcore.status.StatusService;

/**
 * Точка входа. Единственное место, где собирается граф объектов.
 *
 * <p>Синглтонов и статического доступа нет: всё, что нужно модулям, передаётся
 * конструктором. Из-за этого модули проверяются юнит-тестами без запуска
 * сервера, и ради этого же Bukkit не выходит за пределы {@code platform/} и
 * этого класса — за правилом следит отдельный тест.
 */
public final class RpgCorePlugin extends JavaPlugin implements Listener {

    private ContentService content;
    private StatService stats;
    private StatusService statuses;
    private PlayerDataStore data;
    private ClassService classService;
    private ManaPool mana;
    private CooldownTracker cooldowns;
    private ZoneService zones;

    @Override
    public void onEnable() {
        saveDefaultContent();

        content = new ContentService(getDataFolder().toPath());
        ContentErrors errors = content.reload();
        reportContent(errors);

        stats = new StatService(new StatEngine(content.stats()));

        // Тик сервера как источник времени для статусов: единственное место,
        // где модуль статусов встречается с Bukkit, и то через LongSupplier.
        LongSupplier clock = Bukkit::getCurrentTick;
        statuses = new StatusService(content.statuses(), clock);

        // Крит берёт случайность отсюда; в тестах подставляется детерминированная.
        DoubleSupplier random = Math::random;
        DamageEngine damage = new DamageEngine(random);

        BukkitSkillWorld world = new BukkitSkillWorld(this, damage, stats, statuses);
        zones = new ZoneService(clock);
        SkillRuntime runtime = new SkillRuntime(world, statuses, stats,
                content.balance(), content.skills(), zones, random);

        data = new PlayerDataStore(getDataFolder().toPath().resolve("players"),
                message -> getLogger().warning(message));

        classService = new ClassService(content.playerClasses(),
                content.skills(), data, stats);

        mana = new ManaPool(stats);
        cooldowns = new CooldownTracker(clock);
        CastService casts = new CastService(classService, content.skills(), content.balance(),
                statuses, content.statuses(), stats, mana, cooldowns, runtime);

        var command = getCommand("rpg");
        if (command != null) {
            RpgCommand executor = new RpgCommand(content, stats, statuses, runtime,
                    classService, casts);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("команда rpg не объявлена в plugin.yml");
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new SkillInputListener(casts), this);
        Bukkit.getPluginManager().registerEvents(new ExperienceListener(classService), this);

        // Снятие истёкших статусов. Раз в секунду достаточно: чтение статусов
        // и так убирает истёкшие лениво, этот таймер нужен только чтобы память
        // не держала записи по ушедшим целям.
        Bukkit.getScheduler().runTaskTimer(this, () -> statuses.expireAll(), 20L, 20L);

        // Восстановление маны. Стат задан в мане за секунду, и таймер идёт
        // ровно раз в секунду, чтобы между ними не было пересчёта, который
        // однажды разошёлся бы с написанным в stats.yml.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (var online : Bukkit.getOnlinePlayers()) {
                mana.regenerate(online.getUniqueId(), 1.0);
            }
        }, 20L, 20L);

        // Отрисовка зон. Зона, которую игрок не видит, — ловушка, поэтому
        // рисовать её обязанность плагина, а не автора навыка.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            zones.expireAll();
            for (var zone : zones.all()) {
                world.drawZone(zone);
            }
        }, 10L, 10L);

        getLogger().info("RpgCore включён: статов " + content.stats().size()
                + ", статусов " + content.statuses().size()
                + ", навыков " + content.skills().size()
                + ", классов " + content.playerClasses().size());
    }

    @Override
    public void onDisable() {
        if (data != null) {
            data.shutdown();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        try {
            data.load(event.getPlayer().getUniqueId());
            // Базовые статы класса применяются при входе: уровень мог
            // измениться, пока игрока не было.
            classService.applyBaseStats(event.getPlayer().getUniqueId());
            // Полный запас при входе: ноль выглядел бы как поломка.
            mana.fill(event.getPlayer().getUniqueId());
        } catch (PlayerDataException e) {
            // Испорченный файл не затирается пустышкой: игрок получает отказ,
            // администратор — строку в логе с причиной.
            getLogger().severe("данные игрока " + event.getPlayer().getName()
                    + " не читаются: " + e.getMessage());
            event.getPlayer().kick(net.kyori.adventure.text.Component.text(
                    "Ваши данные повреждены. Сообщите администратору."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        data.unload(uuid);
        stats.forget(uuid);
        statuses.forget(uuid);
        mana.forget(uuid);
        cooldowns.forget(uuid);
        // Чужие печати после выхода их владельца не должны никого усиливать.
        zones.forgetOwner(uuid);
    }

    private void saveDefaultContent() {
        for (String name : new String[] {"stats.yml", "statuses.yml", "balance.yml",
                "skills/mage_mana_bolt.yml", "skills/mage_mana_bolt_impact.yml",
                "skills/mage_flux_loop.yml", "skills/mage_collapse.yml",
                "classes/mage.yml"}) {
            if (!getDataFolder().toPath().resolve(name).toFile().isFile()) {
                saveResource(name, false);
            }
        }
    }

    private void reportContent(ContentErrors errors) {
        if (errors.isEmpty()) {
            return;
        }
        getLogger().severe("в контенте ошибок: " + errors.count()
                + ". Затронутые разделы отключены, сервер продолжает работу.");
        for (ContentError error : errors.all()) {
            getLogger().severe("  " + error);
        }
    }
}
