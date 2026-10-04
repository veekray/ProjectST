package ru.projectst.rpgcore.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import ru.projectst.rpgcore.damage.DamageEngine;
import ru.projectst.rpgcore.damage.DamageRequest;
import ru.projectst.rpgcore.damage.DamageResult;
import ru.projectst.rpgcore.damage.DamageSchool;
import ru.projectst.rpgcore.damage.DefenderState;
import ru.projectst.rpgcore.skill.SkillWorld;
import ru.projectst.rpgcore.skill.TargetSpec;
import ru.projectst.rpgcore.stat.StatService;
import ru.projectst.rpgcore.stat.StatSnapshot;
import ru.projectst.rpgcore.status.StatusService;

/**
 * Реализация мира поверх Bukkit.
 *
 * <p>Здесь собрано всё, что исполнитель навыков делать не умеет и не должен:
 * поиск сущностей, применение урона к здоровью, планировщик.
 *
 * <p><b>Правило единственного вызова.</b> {@code LivingEntity#damage} вызывается
 * в этом классе один раз, в {@link #dealDamage}. Любой другой путь нанесения
 * урона — ошибка ревью. Без этого правила «единый конвейер» остаётся
 * декларацией: именно так в старом стеке у каждого плагина оказался свой
 * расчёт, и они перестали видеть друг друга.
 */
public final class BukkitSkillWorld implements SkillWorld {

    private final Plugin plugin;
    private final DamageEngine engine;
    private final StatService stats;
    private final StatusService statuses;

    public BukkitSkillWorld(Plugin plugin, DamageEngine engine,
                            StatService stats, StatusService statuses) {
        this.plugin = plugin;
        this.engine = engine;
        this.stats = stats;
        this.statuses = statuses;
    }

    @Override
    public List<UUID> resolveTargets(UUID caster, TargetSpec.Type type,
                                     double radius, double angle) {
        Entity source = Bukkit.getEntity(caster);
        if (source == null) {
            return List.of();
        }
        if (type == TargetSpec.Type.SELF) {
            return List.of(caster);
        }

        List<UUID> out = new ArrayList<>();
        Location origin = source.getLocation();
        for (Entity nearby : source.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living) || living.isDead()) {
                continue;
            }
            if (nearby.getUniqueId().equals(caster) && type != TargetSpec.Type.ALLIES_IN_RADIUS) {
                continue;
            }
            if (origin.distance(nearby.getLocation()) > radius) {
                continue; // getNearbyEntities даёт параллелепипед, а нам нужна сфера
            }
            if (type == TargetSpec.Type.ENEMIES_IN_CONE && !insideCone(source, nearby, angle)) {
                continue;
            }
            if (type == TargetSpec.Type.ALLIES_IN_RADIUS && !(nearby instanceof Player)) {
                continue;
            }
            out.add(nearby.getUniqueId());
        }
        return out;
    }

    /** Цель внутри конуса по направлению взгляда. */
    private static boolean insideCone(Entity source, Entity target, double angleDegrees) {
        Vector look = source.getLocation().getDirection().setY(0).normalize();
        Vector to = target.getLocation().toVector()
                .subtract(source.getLocation().toVector()).setY(0);
        if (to.lengthSquared() < 1.0E-6) {
            return true; // вплотную: считаем попаданием, иначе угол не определён
        }
        double cos = look.dot(to.normalize());
        return Math.toDegrees(Math.acos(Math.clamp(cos, -1, 1))) <= angleDegrees / 2;
    }

    @Override
    public void dealDamage(UUID casterId, UUID targetId, double amount,
                           DamageSchool school, String skillId) {
        if (!(Bukkit.getEntity(targetId) instanceof LivingEntity target) || target.isDead()) {
            return;
        }
        Entity caster = Bukkit.getEntity(casterId);

        StatSnapshot attackerStats = stats.snapshot(casterId);
        StatSnapshot defenderStats = stats.snapshot(targetId);
        DefenderState state = statuses.defenderState(targetId);

        DamageResult result = engine.compute(
                new DamageRequest(amount, school, skillId, java.util.Set.of()),
                attackerStats, defenderStats, state);

        if (result.blocked()) {
            return;
        }

        // Щиты списываются только после того, как стало ясно, что урон дошёл.
        statuses.consumeShield(targetId, result.absorbed());

        // Единственный вызов, отнимающий здоровье. Событие Bukkit испускается
        // им же, поэтому региональные плагины могут отменить урон штатно.
        if (caster instanceof LivingEntity livingCaster) {
            target.damage(result.applied(), livingCaster);
        } else {
            target.damage(result.applied());
        }
    }

    @Override
    public void heal(UUID targetId, double amount) {
        if (!(Bukkit.getEntity(targetId) instanceof LivingEntity target) || target.isDead()) {
            return;
        }
        // В 1.21.1 константа ещё GENERIC_MAX_HEALTH: переименовали её в 1.21.3.
        var maxAttribute = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = maxAttribute == null ? target.getHealth() : maxAttribute.getValue();
        target.setHealth(Math.min(max, target.getHealth() + amount));
    }

    @Override
    public void message(UUID targetId, String text) {
        if (Bukkit.getEntity(targetId) instanceof Player player) {
            player.sendMessage(text);
        }
    }

    @Override
    public void runLater(int ticks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
    }
}
