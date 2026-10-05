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
import ru.projectst.rpgcore.cast.ResourcePool;
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
import ru.projectst.rpgcore.platform.MinionListener;
import ru.projectst.rpgcore.platform.SkillInputListener;
import ru.projectst.rpgcore.platform.TriggerListener;
import ru.projectst.rpgcore.platform.VitalsSync;
import ru.projectst.rpgcore.platform.ZoneTicker;
import ru.projectst.rpgcore.skill.SkillRuntime;
import ru.projectst.rpgcore.skill.MinionService;
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
    private ResourcePool resources;
    private VitalsSync vitals;
    private CooldownTracker cooldowns;
    private ZoneService zones;
    private MinionService minions;
    private BukkitSkillWorld world;

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

        zones = new ZoneService(clock);
        minions = new MinionService(clock);
        world = new BukkitSkillWorld(this, damage, stats, statuses, minions);
        SkillRuntime runtime = new SkillRuntime(world, statuses, stats,
                content.balance(), content.skills(), zones, minions, random);

        data = new PlayerDataStore(getDataFolder().toPath().resolve("players"),
                message -> getLogger().warning(message));

        classService = new ClassService(content.playerClasses(),
                content.skills(), data, stats);

        resources = new ResourcePool(stats, classService);
        cooldowns = new CooldownTracker(clock);
        CastService casts = new CastService(classService, content.skills(), content.balance(),
                statuses, content.statuses(), stats, resources, cooldowns, runtime);
        // Возврат ресурса навыком: исполнитель не знает, мана это или
        // выносливость, и знать ему незачем.
        runtime.useResources(resources::restore);

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
        Bukkit.getPluginManager().registerEvents(new TriggerListener(casts, minions), this);
        Bukkit.getPluginManager().registerEvents(new MinionListener(minions), this);

        // Снятие истёкших статусов. Раз в секунду достаточно: чтение статусов
        // и так убирает истёкшие лениво, этот таймер нужен только чтобы память
        // не держала записи по ушедшим целям.
        Bukkit.getScheduler().runTaskTimer(this, () -> statuses.expireAll(), 20L, 20L);

        // Восстановление маны. Стат задан в мане за секунду, и таймер идёт
        // ровно раз в секунду, чтобы между ними не было пересчёта, который
        // однажды разошёлся бы с написанным в stats.yml.
        vitals = new VitalsSync(stats);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (var online : Bukkit.getOnlinePlayers()) {
                resources.regenerate(online.getUniqueId(), 1.0);
                // Здоровье сверяется здесь же: стат, которого не видно в мире,
                // хуже отсутствия стата.
                vitals.apply(online);
            }
        }, 20L, 20L);

        // Призванные: срок жизни и поиск цели. Одна задача на оба дела, потому
        // что оба — про одно и то же существо и обе должны идти с одним шагом.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            minions.expired().forEach(world::despawn);
            for (var minion : minions.all()) {
                if (minion.attacksEnemies()) {
                    world.retargetMinion(minion);
                }
            }
        }, 20L, 20L);

        // Периодические навыки. Шаг задачи — секунда, а частоту каждого навыка
        // держит его собственный промежуток через перезарядку: иначе «каждые
        // две секунды» означало бы «как часто успевает задача».
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (var online : Bukkit.getOnlinePlayers()) {
                casts.fire(online.getUniqueId(),
                        ru.projectst.rpgcore.skill.SkillTrigger.ON_INTERVAL, null);
            }
        }, 20L, 20L);

        // Зоны: отрисовка, вход и собственные тики. Рисовать зону — обязанность
        // плагина, а не автора навыка: невидимая зона это ловушка, а не механика.
        Bukkit.getScheduler().runTaskTimer(this,
                new ZoneTicker(zones, minions, content.skills(), runtime, classService, world),
                ZoneTicker.PERIOD_TICKS, ZoneTicker.PERIOD_TICKS);

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
            resources.fill(event.getPlayer().getUniqueId());
            vitals.apply(event.getPlayer());
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
        resources.forget(uuid);
        cooldowns.forget(uuid);
        // Чужие печати после выхода их владельца не должны никого усиливать.
        zones.forgetOwner(uuid);
        // Призванные уходят с владельцем: бесхозный зверь остался бы бить
        // игроков, и снять его было бы нечем.
        minions.forgetOwner(uuid).forEach(world::despawn);
    }

    private void saveDefaultContent() {
        for (String name : new String[] {
                "stats.yml", "statuses.yml", "balance.yml", "skills/druid_abyss_bloom.yml",
                "skills/druid_bark_guard.yml", "skills/druid_bark_react.yml",
                "skills/druid_beast_call.yml", "skills/druid_beast_ward_tick.yml",
                "skills/druid_bloom_tick.yml", "skills/druid_grasping_roots.yml",
                "skills/druid_life_spores.yml", "skills/druid_poison_ivy.yml",
                "skills/mage_bolt_hit.yml", "skills/mage_bolt_hit_strong.yml",
                "skills/mage_collapse.yml", "skills/mage_collapse_do.yml",
                "skills/mage_flow_loop.yml", "skills/mage_herd.yml",
                "skills/mage_mana_bolt.yml", "skills/mage_scatter.yml",
                "skills/mage_seal_core.yml", "skills/mage_seal_drop.yml",
                "skills/mage_void_step.yml", "skills/rogue_dash.yml",
                "skills/rogue_fan_of_knives.yml", "skills/rogue_ghost_step.yml",
                "skills/rogue_ghost_strike.yml", "skills/rogue_mark_of_death.yml",
                "skills/rogue_mark_stack.yml", "skills/rogue_mark_tally.yml",
                "skills/rogue_mirror_burst.yml", "skills/rogue_mirror_image.yml",
                "skills/rogue_shadow_strike.yml", "skills/warlock_agony_cocoon.yml",
                "skills/warlock_bolt_hit.yml", "skills/warlock_chains_tick.yml",
                "skills/warlock_curse_tick.yml", "skills/warlock_cursed_bolt.yml",
                "skills/warlock_dark_veil.yml", "skills/warlock_despair_chains.yml",
                "skills/warlock_reap_mark.yml", "skills/warlock_soul_gain.yml",
                "skills/warlock_transfusion.yml", "skills/warlock_veil_tick.yml",
                "classes/druid.yml", "classes/mage.yml", "classes/rogue.yml",
                "classes/warlock.yml"}) {
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
