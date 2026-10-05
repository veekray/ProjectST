package ru.projectst.rpgcore.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import ru.projectst.rpgcore.damage.DamageEngine;
import ru.projectst.rpgcore.damage.DamageRequest;
import ru.projectst.rpgcore.damage.DamageResult;
import ru.projectst.rpgcore.damage.DamageSchool;
import ru.projectst.rpgcore.damage.DefenderState;
import ru.projectst.rpgcore.skill.Action;
import ru.projectst.rpgcore.skill.CastContext;
import ru.projectst.rpgcore.skill.Position;
import ru.projectst.rpgcore.skill.SkillWorld;
import ru.projectst.rpgcore.skill.TargetSpec;
import ru.projectst.rpgcore.stat.StatService;
import ru.projectst.rpgcore.stat.StatSnapshot;
import ru.projectst.rpgcore.status.StatusService;

/**
 * Реализация мира поверх Bukkit.
 *
 * <p>Здесь собрано всё, что исполнитель навыков делать не умеет и не должен:
 * поиск сущностей, применение урона к здоровью, частицы, звук, планировщик.
 *
 * <p><b>Правило единственного вызова.</b> {@code LivingEntity#damage}
 * вызывается в этом классе один раз, в {@link #dealDamage}. Любой другой путь
 * нанесения урона — ошибка ревью. Без этого правила «единый конвейер» остаётся
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

    // ------------------------------------------------------------------ цели

    @Override
    public List<UUID> resolveTargets(CastContext context, TargetSpec.Type type,
                                     double radius, double angle) {
        if (type == TargetSpec.Type.SELF) {
            return List.of(context.caster());
        }
        if (type == TargetSpec.Type.TRIGGER) {
            return context.triggerOpt().map(List::of).orElse(List.of());
        }

        Location centre;
        if (type.needsOrigin()) {
            // Точки нет — пустой список. Молчаливый откат к позиции кастера
            // означал бы, что взрыв в точке попадания иногда гремит под ногами.
            Optional<Location> origin = toLocation(context.origin());
            if (origin.isEmpty()) {
                return List.of();
            }
            centre = origin.get();
        } else {
            Entity source = Bukkit.getEntity(context.caster());
            if (source == null) {
                return List.of();
            }
            centre = source.getLocation();
        }

        Entity caster = Bukkit.getEntity(context.caster());
        List<UUID> out = new ArrayList<>();
        for (Entity nearby : centre.getWorld().getNearbyEntities(centre, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living) || living.isDead()) {
                continue;
            }
            boolean isCaster = nearby.getUniqueId().equals(context.caster());
            if (isCaster && type != TargetSpec.Type.ALLIES_IN_RADIUS) {
                continue;
            }
            // getNearbyEntities даёт параллелепипед, а радиус — это сфера.
            if (centre.distance(nearby.getLocation()) > radius) {
                continue;
            }
            if (type == TargetSpec.Type.ALLIES_IN_RADIUS && !(nearby instanceof Player)) {
                continue;
            }
            if (type == TargetSpec.Type.ENEMIES_IN_CONE
                    && (caster == null || !insideCone(caster, nearby, angle))) {
                continue;
            }
            out.add(nearby.getUniqueId());
        }
        return out;
    }

    private static boolean insideCone(Entity source, Entity target, double angleDegrees) {
        Vector look = source.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-6) {
            return true;
        }
        look.normalize();
        Vector to = target.getLocation().toVector()
                .subtract(source.getLocation().toVector()).setY(0);
        if (to.lengthSquared() < 1.0E-6) {
            return true; // вплотную: угол не определён, считаем попаданием
        }
        double cos = look.dot(to.normalize());
        return Math.toDegrees(Math.acos(Math.clamp(cos, -1, 1))) <= angleDegrees / 2;
    }

    @Override
    public Optional<Position> positionOf(UUID entity) {
        Entity e = Bukkit.getEntity(entity);
        return e == null ? Optional.empty() : Optional.of(toPosition(e.getLocation()));
    }

    @Override
    public boolean isPlayer(UUID entity) {
        return Bukkit.getEntity(entity) instanceof Player;
    }

    // ------------------------------------------------------------------ бой

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
                new DamageRequest(amount, school, skillId, Set.of()),
                attackerStats, defenderStats, state);

        if (result.blocked()) {
            return;
        }
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
        target.setHealth(Math.clamp(target.getHealth() + amount, 0, max));
    }

    @Override
    public void potion(UUID targetId, String effect, int durationTicks, int amplifier) {
        if (!(Bukkit.getEntity(targetId) instanceof LivingEntity target)) {
            return;
        }
        // PotionEffectType в 1.21 — уже реестр, а не перечисление, в отличие
        // от Particle и Sound. Поэтому поиск другой.
        PotionEffectType type = Registry.EFFECT.get(
                NamespacedKey.minecraft(effect.toLowerCase(Locale.ROOT)));
        if (type == null) {
            plugin.getLogger().warning("неизвестный эффект зелья: " + effect);
            return;
        }
        target.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, false, true));
    }

    // ------------------------------------------------------------------ движение

    @Override
    public void push(UUID targetId, Position from, double strength, double lift) {
        Entity target = Bukkit.getEntity(targetId);
        Optional<Location> origin = toLocation(from);
        if (target == null || origin.isEmpty()) {
            return;
        }
        Vector away = target.getLocation().toVector().subtract(origin.get().toVector());
        if (away.lengthSquared() < 1.0E-6) {
            away = target.getLocation().getDirection();
        }
        target.setVelocity(away.normalize().multiply(strength).setY(lift));
    }

    @Override
    public void pullTowards(UUID targetId, Position to, double strength) {
        Entity target = Bukkit.getEntity(targetId);
        Optional<Location> destination = toLocation(to);
        if (target == null || destination.isEmpty()) {
            return;
        }
        Vector towards = destination.get().toVector().subtract(target.getLocation().toVector());
        if (towards.lengthSquared() < 1.0E-6) {
            return; // уже на месте
        }
        target.setVelocity(towards.normalize().multiply(strength));
    }

    @Override
    public void teleport(UUID targetId, Position to) {
        Entity target = Bukkit.getEntity(targetId);
        Optional<Location> destination = toLocation(to);
        if (target == null || destination.isEmpty()) {
            return;
        }
        Location safe = destination.get().clone();
        safe.setYaw(target.getLocation().getYaw());
        safe.setPitch(target.getLocation().getPitch());
        target.teleport(safe);
    }

    @Override
    public Optional<Position> forwardOf(UUID entityId, double distance) {
        Entity entity = Bukkit.getEntity(entityId);
        if (entity == null) {
            return Optional.empty();
        }
        // Луч, а не слепое смещение: иначе рывок забрасывал бы в стену.
        Location eye = entity instanceof LivingEntity living
                ? living.getEyeLocation() : entity.getLocation();
        RayTraceResult blocks = entity.getWorld().rayTraceBlocks(
                eye, eye.getDirection(), distance);
        Location target = blocks == null
                ? eye.clone().add(eye.getDirection().multiply(distance))
                : blocks.getHitPosition().toLocation(entity.getWorld())
                        .subtract(eye.getDirection().multiply(0.5));
        return Optional.of(toPosition(target));
    }

    @Override
    public RayHit castRay(UUID casterId, double range, boolean stopAtEntity) {
        Entity caster = Bukkit.getEntity(casterId);
        if (caster == null) {
            return null;
        }
        Location eye = caster instanceof LivingEntity living
                ? living.getEyeLocation() : caster.getLocation();
        Vector direction = eye.getDirection();

        RayTraceResult hit = caster.getWorld().rayTrace(eye, direction, range,
                org.bukkit.FluidCollisionMode.NEVER, true, 0.6,
                entity -> stopAtEntity && entity instanceof LivingEntity
                        && !entity.getUniqueId().equals(casterId) && !entity.isDead());

        if (hit == null) {
            return new RayHit(toPosition(eye.clone().add(direction.multiply(range))), null);
        }
        Location point = hit.getHitPosition().toLocation(caster.getWorld());
        return new RayHit(toPosition(point),
                hit.getHitEntity() == null ? null : hit.getHitEntity().getUniqueId());
    }

    // ------------------------------------------------------------------ видимое

    @Override
    public void particles(Position at, String particle, Action.Particles.Shape shape,
                          int count, double size) {
        Optional<Location> location = toLocation(at);
        if (location.isEmpty()) {
            return;
        }
        Particle type = fromKey(particle, Particle.class);
        if (type == null) {
            plugin.getLogger().warning("неизвестная частица: " + particle);
            return;
        }
        World world = location.get().getWorld();
        Location centre = location.get();
        switch (shape) {
            case POINT -> world.spawnParticle(type, centre, count, 0.2, 0.2, 0.2, 0);
            case SPHERE -> world.spawnParticle(type, centre, count, size, size, size, 0);
            case RING -> {
                for (int i = 0; i < count; i++) {
                    double a = 2 * Math.PI * i / count;
                    world.spawnParticle(type,
                            centre.clone().add(Math.cos(a) * size, 0.2, Math.sin(a) * size),
                            1, 0, 0, 0, 0);
                }
            }
            case LINE -> world.spawnParticle(type, centre, count, 0.05, 0.05, 0.05, 0);
        }
    }

    /**
     * Рисует зону кольцом по её границе.
     *
     * <p>Не входит в порт {@link SkillWorld}: это обязанность плагина, а не
     * навыка. Автор навыка задаёт тег, радиус и частицу, а то, что зону видно,
     * обеспечивается здесь — забыть нарисовать печать невозможно.
     */
    public void drawZone(ru.projectst.rpgcore.skill.Zone zone) {
        if (zone.particle() == null) {
            return;
        }
        int points = Math.max(8, (int) Math.round(zone.radius() * 8));
        particles(zone.center(), zone.particle(), Action.Particles.Shape.RING,
                points, zone.radius());
    }

    @Override
    public void sound(Position at, String sound, double volume, double pitch) {
        Optional<Location> location = toLocation(at);
        if (location.isEmpty()) {
            return;
        }
        Sound type = fromKey(sound, Sound.class);
        if (type == null) {
            plugin.getLogger().warning("неизвестный звук: " + sound);
            return;
        }
        location.get().getWorld().playSound(location.get(), type, (float) volume, (float) pitch);
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

    // ------------------------------------------------------------------ перевод

    private static Position toPosition(Location location) {
        return new Position(location.getWorld().getUID(),
                location.getX(), location.getY(), location.getZ());
    }

    private static Optional<Location> toLocation(Position position) {
        if (position == null) {
            return Optional.empty();
        }
        World world = Bukkit.getWorld(position.worldId());
        return world == null ? Optional.empty()
                : Optional.of(new Location(world, position.x(), position.y(), position.z()));
    }

    /**
     * Ищет значение реестра по имени. Частицы, звуки и эффекты зелий в 1.21 —
     * это реестры, а не только перечисления, но имена у них те же, поэтому
     * достаточно сопоставления по строке.
     */
    private static <T extends Enum<T>> T fromKey(String key, Class<T> type) {
        String normalized = key.toUpperCase(Locale.ROOT).replace('.', '_').replace(':', '_');
        for (T candidate : type.getEnumConstants()) {
            if (candidate.name().equals(normalized)) {
                return candidate;
            }
        }
        return null;
    }
}
