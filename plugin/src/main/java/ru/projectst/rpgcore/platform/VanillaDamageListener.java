package ru.projectst.rpgcore.platform;

import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import ru.projectst.rpgcore.damage.DamageEngine;
import ru.projectst.rpgcore.damage.DamageRequest;
import ru.projectst.rpgcore.damage.DamageResult;
import ru.projectst.rpgcore.damage.DamageSchool;
import ru.projectst.rpgcore.damage.DefenderState;
import ru.projectst.rpgcore.stat.StatService;
import ru.projectst.rpgcore.stat.StatSnapshot;
import ru.projectst.rpgcore.status.ActiveStatus;
import ru.projectst.rpgcore.status.StatusRegistry;
import ru.projectst.rpgcore.status.StatusService;

/**
 * Обычный урон через наш конвейер.
 *
 * <p><b>Зачем это есть.</b> До этого класса конвейер считал только урон навыков.
 * Удар мечом, стрела, падение и огонь шли мимо — а значит, мимо шли и
 * физический урон, и защита, и сопротивление, и крит, и щиты, и проклятие
 * колдуна, которое снимает цели двадцать процентов урона. Все эти числа
 * существовали и ничего не делали: ровно тот сорт объявления без исполнения,
 * ради отказа от которого проект и затевался.
 *
 * <p><b>Один расчёт на оба пути.</b> Здесь не свой подсчёт, а тот же
 * {@link DamageEngine}: удар мечом и удар навыком считаются одной арифметикой в
 * одном порядке. Иначе «плюс двадцать процентов физического урона» означало бы
 * разное в зависимости от того, чем бьют.
 *
 * <p><b>Свой урон сюда не заходит дважды.</b> Навык уже прошёл конвейер и
 * применяется через {@code LivingEntity#damage}, а это тот же самый событийный
 * путь. Поэтому на время применения ставится отметка, и помеченный удар
 * слушатель пропускает — без неё урон навыка считался бы дважды, и заметили бы
 * это как «скиллы бьют слабее, чем написано».
 */
public final class VanillaDamageListener implements Listener {

    /** Метка статуса: пока он есть, каждый стак гасит один удар целиком. */
    public static final String TAG_ABSORB_HIT = "absorb-hit";

    private final DamageEngine engine;
    private final StatService stats;
    private final StatusService statuses;
    private final StatusRegistry registry;
    private final BukkitSkillWorld world;

    public VanillaDamageListener(DamageEngine engine, StatService stats,
                                 StatusService statuses, StatusRegistry registry,
                                 BukkitSkillWorld world) {
        this.engine = engine;
        this.stats = stats;
        this.statuses = statuses;
        this.registry = registry;
        this.world = world;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (world.applyingSkillDamage()) {
            return; // наш же урон: он конвейер уже прошёл
        }
        UUID victimId = victim.getUniqueId();

        // 1. Заряд поглощения: гасит удар целиком и тратится.
        for (ActiveStatus status : statuses.acting(victimId)) {
            if (registry.find(status.id()).filter(def -> def.hasTag(TAG_ABSORB_HIT))
                    .isEmpty()) {
                continue;
            }
            event.setCancelled(true);
            statuses.removeStack(victimId, status.id());
            if (victim instanceof Player player) {
                int left = statuses.all(victimId).stream()
                        .filter(active -> active.id().equals(status.id()))
                        .mapToInt(ActiveStatus::stacks).findFirst().orElse(0);
                player.sendActionBar(Component.text(
                        left > 0 ? "Удар поглощён, зарядов осталось: " + left
                                : "Удар поглощён, защита спала",
                        NamedTextColor.GOLD));
            }
            return;
        }

        // 2. Остальное считает конвейер: изъятие из боя, щиты, статы.
        DefenderState state = statuses.defenderState(victimId);
        UUID attackerId = attacker(event);

        StatSnapshot attackerStats = attackerId == null
                ? StatSnapshot.EMPTY : stats.snapshot(attackerId);
        // Тот же множитель Присяги, что и у навыков: два места, считающие одно
        // и то же по-разному, однажды разошлись бы.
        DamageResult result = engine.compute(
                new DamageRequest(
                        event.getDamage() * statuses.challengeScale(attackerId, victimId),
                        schoolOf(event), "vanilla", Set.of()),
                attackerStats, stats.snapshot(victimId), state);

        if (result.blocked()) {
            event.setCancelled(true);
            return;
        }
        if (result.absorbed() > 0) {
            statuses.consumeShield(victimId, result.absorbed());
        }
        event.setDamage(result.applied());
        // Вампиризм считает тот же метод, что и для урона навыков: два места,
        // считающие одно и то же, однажды разошлись бы.
        if (attackerId != null) {
            world.drinkBlood(attackerId, result.applied());
        }
    }

    /**
     * Кто бьёт.
     *
     * <p>Стрела считается ударом стрелка: иначе лучник не получал бы ни своего
     * урона, ни своего крита, и «плюс к физическому урону» означало бы «только в
     * ближнем бою», чего нигде не написано.
     */
    private UUID attacker(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Entity shooter) {
            return shooter.getUniqueId();
        }
        return damager.getUniqueId();
    }

    /**
     * Школа урона.
     *
     * <p>Всё, что прилетело от существа или его стрелы, — физическое; стихии и
     * падение — чистый урон, который статы не смягчают. Магической школы у
     * ванильных источников нет, и выдумывать её означало бы, что огонь считается
     * заклинанием.
     */
    private DamageSchool schoolOf(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof LivingEntity || damager instanceof Arrow) {
                return DamageSchool.PHYSICAL;
            }
        }
        return DamageSchool.TRUE;
    }
}
