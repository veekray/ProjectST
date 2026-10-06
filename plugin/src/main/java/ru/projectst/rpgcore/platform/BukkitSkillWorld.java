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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
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
import ru.projectst.rpgcore.damage.StatIds;
import ru.projectst.rpgcore.damage.DefenderState;
import ru.projectst.rpgcore.skill.Action;
import ru.projectst.rpgcore.skill.CastContext;
import ru.projectst.rpgcore.skill.MinionService;
import ru.projectst.rpgcore.skill.Position;
import ru.projectst.rpgcore.skill.ProjectileSpec;
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
    private final MinionService minions;
    /**
     * Идёт ли прямо сейчас применение урона от навыка.
     *
     * <p>Урон навыка применяется через {@code LivingEntity#damage}, то есть по
     * тому же событийному пути, что и удар мечом. Без этой отметки слушатель
     * обычного урона пересчитал бы его второй раз, и навыки били бы слабее, чем
     * написано в балансе — а искали бы это в балансе.
     *
     * <p>Поле обычное, не потокобезопасное, и это верно: события Bukkit идут в
     * главном потоке, и урон применяется только там.
     */
    private boolean applyingSkillDamage;

    /** Частицы, о которых уже предупредили: в лог по одному разу. */
    private final Set<String> badParticles = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public BukkitSkillWorld(Plugin plugin, DamageEngine engine, StatService stats,
                            StatusService statuses, MinionService minions) {
        this.plugin = plugin;
        this.engine = engine;
        this.stats = stats;
        this.statuses = statuses;
        this.minions = minions;
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
            if (type == TargetSpec.Type.ENEMIES_IN_CONE
                    && (caster == null || !insideCone(caster, nearby, angle))) {
                continue;
            }
            // Свой призванный не попадает под свои же площадные навыки. Одно
            // правило в одном месте: иначе каждый навык с радиусом нёс бы
            // собственный фильтр, и девятый по счёту про него забыл бы.
            if (!type.hitsAllies() && minions.isOwnMinion(context.caster(), nearby.getUniqueId())) {
                continue;
            }
            // Призванный владельца попадает в выборку союзников: лечить и
            // усиливать своего зверя можно, и это тоже сказано здесь.
            if (type == TargetSpec.Type.ALLIES_IN_RADIUS
                    && !(nearby instanceof Player)
                    && !minions.isOwnMinion(context.caster(), nearby.getUniqueId())) {
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

        // Присяга ослабляет удар до конвейера: она меняет не защиту цели, а
        // силу самого удара, и потому считается один раз, здесь.
        DamageResult result = engine.compute(
                new DamageRequest(amount * statuses.challengeScale(casterId, targetId),
                        school, skillId, Set.of()),
                attackerStats, defenderStats, state);

        if (result.blocked()) {
            return;
        }
        statuses.consumeShield(targetId, result.absorbed());
        drinkBlood(casterId, result.applied());

        // Единственный вызов, отнимающий здоровье. Событие Bukkit испускается
        // им же, поэтому региональные плагины могут отменить урон штатно.
        applyingSkillDamage = true;
        try {
            if (caster instanceof LivingEntity livingCaster) {
                target.damage(result.applied(), livingCaster);
            } else {
                target.damage(result.applied());
            }
        } finally {
            // finally обязателен: отменивший урон плагин бросит исключение, и
            // отметка осталась бы включённой навсегда — то есть весь обычный
            // урон на сервере перестал бы считаться.
            applyingSkillDamage = false;
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

    /**
     * Вампиризм: часть нанесённого урона возвращается здоровьем.
     *
     * <p>Один метод на оба пути урона — и на навыки, и на обычные удары: два
     * места, считающие одно и то же, однажды разошлись бы, и выяснилось бы это
     * не на тесте, а в бою.
     *
     * <p>Возврат идёт через то же лечение, что и любое другое, то есть считается
     * с получаемым лечением. Анти-хил обязан гасить и вампиризм, иначе он был бы
     * лазейкой мимо собственных правил.
     */
    public void drinkBlood(UUID attackerId, double dealt) {
        if (dealt <= 0) {
            return;
        }
        double percent = stats.snapshot(attackerId).getOrZero(StatIds.LIFESTEAL);
        if (percent > 0) {
            heal(attackerId, dealt * percent / 100.0);
        }
    }

    /**
     * Плата здоровьем.
     *
     * <p>Здоровье снимается напрямую, а не через урон: иначе плата прошла бы
     * конвейером, подняла событие «получил урон» и сорвала бы всё, что от него
     * зависит, — начиная с собственного разгона берсерка.
     */
    @Override
    public void sacrifice(UUID targetId, double share) {
        if (!(Bukkit.getEntity(targetId) instanceof LivingEntity target) || target.isDead()) {
            return;
        }
        var attribute = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = attribute == null ? target.getHealth() : attribute.getValue();
        target.setHealth(Math.max(1.0, target.getHealth() - max * share));
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

    @Override
    public void clearPotion(UUID targetId, String effect) {
        if (!(Bukkit.getEntity(targetId) instanceof LivingEntity target)) {
            return;
        }
        PotionEffectType type = Registry.EFFECT.get(
                NamespacedKey.minecraft(effect.toLowerCase(Locale.ROOT)));
        if (type == null) {
            plugin.getLogger().warning("неизвестный эффект зелья: " + effect);
            return;
        }
        target.removePotionEffect(type);
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

    @Override
    public void dash(UUID entityId, double strength, double lift,
                     ru.projectst.rpgcore.skill.Heading heading) {
        Entity entity = Bukkit.getEntity(entityId);
        if (entity == null) {
            return;
        }
        if (heading != null) {
            // Длина уже единичная: нормализует её сам Heading, чтобы рывок по
            // диагонали не уносил дальше, чем рывок вперёд.
            entity.setVelocity(new Vector(heading.x(), 0, heading.z())
                    .multiply(strength).setY(lift));
            return;
        }
        Vector look = entity.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-6) {
            return;
        }
        entity.setVelocity(look.normalize().multiply(strength).setY(lift));
    }

    @Override
    public Optional<Position> offsetOf(UUID entityId, double distance, boolean behind) {
        Entity entity = Bukkit.getEntity(entityId);
        if (entity == null) {
            return Optional.empty();
        }
        Vector look = entity.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-6) {
            look = new Vector(0, 0, 1);
        }
        look = look.normalize().multiply(behind ? -distance : distance);
        Location at = entity.getLocation().clone().add(look);
        // Внутрь блока не ставим: иначе удар в спину заканчивался бы застреванием
        // в стене, и виноват был бы навык, а не геометрия.
        if (at.getBlock().getType().isSolid()) {
            return Optional.of(toPosition(entity.getLocation()));
        }
        return Optional.of(toPosition(at));
    }

    /** Идёт ли сейчас применение урона от навыка: см. поле выше. */
    public boolean applyingSkillDamage() {
        return applyingSkillDamage;
    }

    // ------------------------------------------------------------------ призыв

    @Override
    public Optional<UUID> spawnMob(String type, Position at, double health) {
        Optional<Location> where = toLocation(at);
        if (where.isEmpty()) {
            return Optional.empty();
        }
        EntityType entityType = fromKey(type, EntityType.class);
        if (entityType == null || entityType.getEntityClass() == null) {
            plugin.getLogger().warning("неизвестный тип существа: " + type);
            return Optional.empty();
        }
        Entity spawned = where.get().getWorld().spawnEntity(where.get(), entityType);
        if (!(spawned instanceof LivingEntity living)) {
            spawned.remove();
            plugin.getLogger().warning("тип " + type + " не живое существо");
            return Optional.empty();
        }
        if (health > 0) {
            var attribute = living.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attribute != null) {
                attribute.setBaseValue(health);
            }
            living.setHealth(Math.min(health, living.getHealth() + health));
        }
        return Optional.of(living.getUniqueId());
    }

    @Override
    public void despawn(UUID entity) {
        Entity e = Bukkit.getEntity(entity);
        if (e != null) {
            e.remove();
        }
    }

    @Override
    public void setAttackTarget(UUID mob, UUID target) {
        if (!(Bukkit.getEntity(mob) instanceof Mob attacker)) {
            return;
        }
        if (Bukkit.getEntity(target) instanceof LivingEntity victim) {
            attacker.setTarget(victim);
        } else {
            attacker.setTarget(null);
        }
    }

    /**
     * Зашёл ли наблюдатель цели за спину.
     *
     * <p>Скалярное произведение двух горизонтальных векторов: куда цель
     * повёрнута и в какую сторону от неё стоит наблюдатель. Высота отброшена —
     * иначе удар сверху считался бы ударом сбоку, хотя со спины он или нет,
     * решает направление по земле.
     *
     * <p>Поворот берётся у тела ({@code getLocation}), а не у головы
     * ({@code getEyeLocation} с собственным yaw): голова у игрока крутится
     * мгновенно и на полный круг, и «со спины» превратилось бы в «пока он
     * отвернулся». Тело разворачивается медленно, и зайти за него — действие, а
     * не удача.
     */
    /**
     * Подсветка контуром.
     *
     * <p>Снимается отложенной задачей, а не своим счётчиком: счётчик пришлось
     * бы сверять каждый тик, а задача выполняется ровно один раз. Снимаем
     * только то, что сами и зажгли: существо могло светиться и до нас — своей
     * природой или чужим плагином, — и гасить его было бы вмешательством.
     */
    @Override
    public void glow(UUID targetId, int ticks) {
        Entity target = Bukkit.getEntity(targetId);
        if (target == null || ticks <= 0 || target.isGlowing()) {
            return;
        }
        target.setGlowing(true);
        runLater(ticks, () -> {
            Entity still = Bukkit.getEntity(targetId);
            if (still != null) {
                still.setGlowing(false);
            }
        });
    }

    /**
     * Щит в перезарядку.
     *
     * <p>Работает только по игрокам: щит есть только у них, и притворяться,
     * будто мы что-то сделали мобу, незачем.
     */
    @Override
    public void disableShield(UUID targetId, int ticks) {
        if (Bukkit.getEntity(targetId) instanceof Player player && ticks > 0) {
            player.setCooldown(org.bukkit.Material.SHIELD, ticks);
        }
    }

    /**
     * Сбить цель с толку: моб выбирает себе другую жертву рядом.
     *
     * <p>Берётся ближайшее живое существо, кроме самого моба и кроме игроков:
     * кукловод стравливает чужих между собой, а не натравливает их на своих.
     */
    @Override
    public void confuse(UUID targetId, double radius) {
        if (!(Bukkit.getEntity(targetId) instanceof Mob puppet) || radius <= 0) {
            return;
        }
        LivingEntity victim = null;
        double best = Double.MAX_VALUE;
        for (Entity nearby : puppet.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living) || living.isDead()
                    || living instanceof Player || living.equals(puppet)) {
                continue;
            }
            double distance = puppet.getLocation().distanceSquared(living.getLocation());
            if (distance < best) {
                best = distance;
                victim = living;
            }
        }
        if (victim != null) {
            puppet.setTarget(victim);
        }
    }

    @Override
    public boolean isBehind(UUID observerId, UUID subjectId, double arcDegrees) {
        Entity observer = Bukkit.getEntity(observerId);
        Entity subject = Bukkit.getEntity(subjectId);
        if (observer == null || subject == null
                || !observer.getWorld().equals(subject.getWorld())) {
            return false;
        }
        var at = subject.getLocation();
        var from = observer.getLocation();
        return ru.projectst.rpgcore.skill.Facing.isBehind(at.getYaw(),
                from.getX() - at.getX(), from.getZ() - at.getZ(), arcDegrees);
    }

    @Override
    public double maxHealthOf(UUID entity) {
        if (!(Bukkit.getEntity(entity) instanceof LivingEntity living)) {
            return 0;
        }
        var attribute = living.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attribute == null ? living.getHealth() : attribute.getValue();
    }

    @Override
    public double healthOf(UUID entity) {
        return Bukkit.getEntity(entity) instanceof LivingEntity living ? living.getHealth() : 0;
    }

    /**
     * Обмен местами.
     *
     * <p>Обе точки снимаются до первого переноса. Переносить по очереди нельзя:
     * второй уехал бы в точку, где уже стоит первый, и один из двоих оказался бы
     * в блоке.
     */
    @Override
    public void swap(UUID first, UUID second) {
        Entity one = Bukkit.getEntity(first);
        Entity two = Bukkit.getEntity(second);
        if (one == null || two == null || !one.getWorld().equals(two.getWorld())) {
            return;
        }
        Location here = one.getLocation().clone();
        Location there = two.getLocation().clone();
        // Взгляд остаётся своим: обмен местами не должен разворачивать игрока,
        // иначе после него он смотрит туда, куда смотрел противник.
        here.setYaw(two.getLocation().getYaw());
        here.setPitch(two.getLocation().getPitch());
        there.setYaw(one.getLocation().getYaw());
        there.setPitch(one.getLocation().getPitch());
        one.teleport(there);
        two.teleport(here);
    }

    @Override
    public void scatter(UUID target, double radius) {
        Entity entity = Bukkit.getEntity(target);
        if (entity == null || radius <= 0) {
            return;
        }
        Location from = entity.getLocation();
        // Случайная точка берётся заново для каждой цели: один общий сдвиг
        // переставил бы строй целиком, а он должен рассыпаться.
        double offsetX = (Math.random() * 2 - 1) * radius;
        double offsetZ = (Math.random() * 2 - 1) * radius;
        Location to = from.clone().add(offsetX, 0, offsetZ);
        to.setY(to.getWorld().getHighestBlockYAt(to) + 1.0);
        // Если наверху оказалось слишком далеко от исходной высоты, оставляем
        // цель на месте: телепорт на крышу горы вместо шага в сторону — это уже
        // другое действие, и оно удивило бы.
        if (Math.abs(to.getY() - from.getY()) > radius) {
            return;
        }
        entity.teleport(to);
    }

    @Override
    public void clearThreat(UUID caster, double radius) {
        Entity source = Bukkit.getEntity(caster);
        if (source == null || radius <= 0) {
            return;
        }
        for (Entity nearby : source.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Mob mob && caster.equals(targetIdOf(mob))) {
                mob.setTarget(null);
            }
        }
    }

    /** На кого смотрит моб; null — ни на кого. */
    private UUID targetIdOf(Mob mob) {
        LivingEntity victim = mob.getTarget();
        return victim == null ? null : victim.getUniqueId();
    }

    /**
     * Находит призванному цель, если он без дела.
     *
     * <p>Не входит в порт: это поведение существа в мире, а не примитив навыка.
     * Своих и владельца зверь не трогает, чужих игроков — трогает: иначе
     * призванные были бы бесполезны в бою игрок против игрока, о чём и просили.
     *
     * <p>Радиус поиска небольшой намеренно. Зверь, уходящий за врагом через пол
     * карты, превращается в отдельную проблему: его теряют, и он бьёт кого-то в
     * другом бою.
     */
    public void retargetMinion(ru.projectst.rpgcore.skill.Minion minion) {
        if (!(Bukkit.getEntity(minion.entityId()) instanceof Mob mob) || mob.isDead()) {
            return;
        }
        LivingEntity current = mob.getTarget();
        if (current != null && !current.isDead() && !isFriendly(minion, current)) {
            return;
        }
        double radius = 12;
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity nearby : mob.getNearbyEntities(radius, radius / 2, radius)) {
            if (!(nearby instanceof LivingEntity living) || living.isDead()
                    || isFriendly(minion, living)) {
                continue;
            }
            double distance = mob.getLocation().distanceSquared(living.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        mob.setTarget(best);
    }

    /** Владелец и его же призванные — не цели. */
    private boolean isFriendly(ru.projectst.rpgcore.skill.Minion minion, LivingEntity candidate) {
        return candidate.getUniqueId().equals(minion.owner())
                || minions.isOwnMinion(minion.owner(), candidate.getUniqueId());
    }

    // ------------------------------------------------------------------ снаряды

    /**
     * Полёт снаряда.
     *
     * <p>Шаг полёта делится на отрезки не длиннее половины блока, и каждый
     * проверяется отдельно. Иначе быстрый снаряд за тик перескакивал бы цель:
     * на скорости три блока в тик он проходил мимо всего, что уже прошло, и
     * выглядело это как «иногда не попадает», что в старом стеке лечили
     * увеличением радиуса попадания, то есть делали хуже.
     *
     * <p>Снаряд не является сущностью Minecraft. Своя сущность означала бы
     * физику, хитбокс и попадание в выборку целей других навыков — всё то, из
     * чего вырастали взаимные помехи: печати мешали снарядам, снаряды —
     * печатям.
     */
    @Override
    public void launchProjectile(UUID casterId, ProjectileSpec spec,
                                 ProjectileHandler handler) {
        Entity caster = Bukkit.getEntity(casterId);
        if (!(caster instanceof LivingEntity living)) {
            return;
        }
        Location start = living.getEyeLocation();
        if (spec.yawOffset() != 0) {
            start = start.clone();
            start.setYaw((float) (start.getYaw() + spec.yawOffset()));
        }
        Vector direction = start.getDirection().normalize();

        new ProjectileFlight(spec, handler, casterId, start, direction).start();
    }

    /** Один летящий снаряд. Живёт до попадания, блока или конца дальности. */
    private final class ProjectileFlight implements Runnable {

        private static final double MAX_SEGMENT = 0.5;

        private final ProjectileSpec spec;
        private final ProjectileHandler handler;
        private final UUID casterId;
        private final Set<UUID> alreadyHit = new java.util.HashSet<>();
        private Location at;
        private Vector direction;
        private double travelled;
        private int hits;
        private org.bukkit.scheduler.BukkitTask task;

        private ProjectileFlight(ProjectileSpec spec, ProjectileHandler handler, UUID casterId,
                                 Location start, Vector direction) {
            this.spec = spec;
            this.handler = handler;
            this.casterId = casterId;
            this.at = start.clone();
            this.direction = direction.clone();
        }

        void start() {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this, 0L, 1L);
        }

        private void stop() {
            if (task != null) {
                task.cancel();
            }
        }

        @Override
        public void run() {
            double remaining = Math.min(spec.speed(), spec.range() - travelled);
            int segments = (int) Math.ceil(remaining / MAX_SEGMENT);
            double step = remaining / Math.max(1, segments);

            for (int i = 0; i < segments; i++) {
                Location before = at.clone();
                at = at.add(direction.clone().multiply(step));
                travelled += step;

                if (spec.gravity() > 0) {
                    // Снижение задаётся в блоках за тик и делится между
                    // отрезками, иначе снаряд падал бы рывками.
                    direction = direction.clone()
                            .add(new Vector(0, -spec.gravity() / segments, 0));
                }

                if (spec.particle() != null) {
                    particles(toPosition(at), spec.particle(),
                            Action.Particles.Shape.POINT, 1, 0);
                }

                if (spec.stopAtBlock() && at.getBlock().getType().isSolid()) {
                    // Назад на отрезок: взрыв должен гремить перед стеной, а не
                    // внутри неё, иначе его не видно.
                    stop();
                    handler.end(toPosition(before));
                    return;
                }

                UUID victim = firstTarget();
                if (victim != null) {
                    alreadyHit.add(victim);
                    hits++;
                    handler.hit(toPosition(at), victim);
                    if (hits >= spec.pierce()) {
                        stop();
                        return;
                    }
                }

                if (travelled >= spec.range()) {
                    stop();
                    handler.end(toPosition(at));
                    return;
                }
            }
        }

        /** Первая подходящая цель в радиусе попадания. */
        private UUID firstTarget() {
            double r = spec.hitRadius();
            for (Entity nearby : at.getWorld().getNearbyEntities(at, r, r, r)) {
                if (!(nearby instanceof LivingEntity living) || living.isDead()) {
                    continue;
                }
                UUID id = nearby.getUniqueId();
                if (id.equals(casterId) || alreadyHit.contains(id)) {
                    continue;
                }
                boolean player = nearby instanceof Player;
                if (player && !spec.hitPlayers()) {
                    continue;
                }
                if (!player && !spec.hitMobs()) {
                    continue;
                }
                if (at.distance(nearby.getLocation().add(0, living.getHeight() / 2, 0)) > r) {
                    continue;
                }
                return id;
            }
            return null;
        }
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
        // Часть ванильных частиц требует данных: BLOCK хочет состояние блока,
        // ITEM — предмет. Без них Bukkit бросает исключение, и навык обрывается
        // на полпути, уже списав ману. Поэтому промах по частице — строка в
        // логе, а не прерванный каст.
        try {
            draw(world, centre, type, shape, count, size);
        } catch (RuntimeException e) {
            if (badParticles.add(particle)) {
                plugin.getLogger().warning("частица " + particle
                        + " не рисуется без дополнительных данных: " + e.getMessage());
            }
        }
    }

    private void draw(World world, Location centre, Particle type,
                      Action.Particles.Shape shape, int count, double size) {
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
    /** Точка модели как позиция мира: нужна тикеру зон. */
    public Optional<Location> locationOf(Position at) {
        return toLocation(at);
    }

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
