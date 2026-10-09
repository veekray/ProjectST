package ru.projectst.rpgcore.platform;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import ru.projectst.rpgcore.skill.CastContext;
import ru.projectst.rpgcore.skill.MinionService;
import ru.projectst.rpgcore.skill.SkillRegistry;
import ru.projectst.rpgcore.skill.SkillRuntime;
import ru.projectst.rpgcore.skill.Zone;
import ru.projectst.rpgcore.skill.ZoneService;
import ru.projectst.rpgcore.classes.ClassService;

/**
 * Жизнь зон: отрисовка, вход и собственные тики.
 *
 * <p>Зона выполняет свой навык от лица владельца и со своей точкой действия.
 * Поэтому поле друида бьёт тех, кто в нём стоит, а не тех, кто рядом с друидом,
 * и для этого не нужно ни одного условия в самом навыке.
 *
 * <p>Вход считается один раз: служба зон помнит, кто уже внутри. Без этого
 * «при входе» срабатывало бы каждый тик, пока цель стоит в круге, — то же
 * самое, что в старом стеке получалось из аур с нулевой длительностью.
 */
public final class ZoneTicker implements Runnable {

    /** Как часто задача просыпается. Тики самих зон идут по их промежуткам. */
    public static final long PERIOD_TICKS = 5L;

    private final ZoneService zones;
    private final MinionService minions;
    private final SkillRegistry skills;
    private final SkillRuntime runtime;
    private final ClassService classes;
    private final BukkitSkillWorld world;
    private final FxBroadcaster fx;

    public ZoneTicker(ZoneService zones, MinionService minions, SkillRegistry skills,
                      SkillRuntime runtime, ClassService classes, BukkitSkillWorld world,
                      FxBroadcaster fx) {
        this.zones = zones;
        this.minions = minions;
        this.skills = skills;
        this.runtime = runtime;
        this.classes = classes;
        this.world = world;
        this.fx = fx;
    }

    @Override
    public void run() {
        zones.expireAll();
        var active = zones.all();
        // Сначала мод узнаёт о зонах, потом ванильное кольцо рисуется тем, у
        // кого мода нет: порядок не важен для глаза, но так читается, кто что
        // получает.
        fx.syncZones(active);
        for (Zone zone : active) {
            world.drawZone(zone);
            if (zone.onEnter() != null) {
                checkEntries(zone);
            }
            if (zone.onTick() != null
                    && Bukkit.getCurrentTick() - zones.lastTick(zone.id()) >= zone.tickInterval()) {
                zones.markTicked(zone.id());
                fire(zone, zone.onTick(), null);
            }
        }
    }

    /** Кто вошёл в зону с прошлого раза. */
    private void checkEntries(Zone zone) {
        var centre = world.locationOf(zone.center());
        if (centre.isEmpty()) {
            return;
        }
        double r = zone.radius();
        for (Entity nearby : centre.get().getWorld()
                .getNearbyEntities(centre.get(), r, r, r)) {
            if (!(nearby instanceof LivingEntity living) || living.isDead()) {
                continue;
            }
            UUID id = nearby.getUniqueId();
            // Владелец и его призванные свою же ловушку не взводят.
            if (id.equals(zone.owner()) || minions.isOwnMinion(zone.owner(), id)) {
                continue;
            }
            if (centre.get().distance(nearby.getLocation()) > r) {
                zones.markInside(zone.id(), id, false);
                continue;
            }
            if (zones.markInside(zone.id(), id, true)) {
                fire(zone, zone.onEnter(), id);
            }
        }
    }

    private void fire(Zone zone, String skillId, UUID trigger) {
        skills.find(skillId).ifPresent(skill -> {
            int level = Math.max(1, classes.skillLevel(zone.owner(), skillId));
            runtime.cast(new CastContext(zone.owner(), level, zone.center(), trigger), skill, 0);
        });
    }
}
