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
import ru.projectst.rpgcore.net.FxMessage;
import ru.projectst.rpgcore.skill.Action;
import ru.projectst.rpgcore.skill.CastContext;
import ru.projectst.rpgcore.skill.Facing;
import ru.projectst.rpgcore.skill.FxEvent;
import ru.projectst.rpgcore.skill.Heading;
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

    /** Кому события мода, кому ванильные частицы. */
    private final FxBroadcaster fx;
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
                            StatusService statuses, MinionService minions, FxBroadcaster fx) {
        this.plugin = plugin;
        this.engine = engine;
        this.stats = stats;
        this.statuses = statuses;
        this.minions = minions;
        this.fx = fx;
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
        Heading coneAxis = type.isCone() ? coneAxis(type, caster, centre) : null;
        if (type == TargetSpec.Type.ENEMIES_IN_CONE_TO_CASTER && coneAxis == null) {
            return List.of();
        }
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
            if (coneAxis != null && !Facing.insideCone(coneAxis.x(), coneAxis.z(),
                    nearby.getLocation().getX() - centre.getX(),
                    nearby.getLocation().getZ() - centre.getZ(), angle)) {
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

    /**
     * Ось конуса выборки.
     *
     * <p>Тот же расчёт, что у картинки в исполнителе ({@code SkillRuntime#areaMarks}):
     * ось взгляда для конуса от кастера и {@link Facing#coneAxisToCaster} для
     * конуса от точки. Две разные оси у выборки и у картинки — это ровно та
     * граница, которая показывает не то, что бьёт.
     */
    private Heading coneAxis(TargetSpec.Type type, Entity caster, Location apex) {
        if (caster == null) {
            return null;
        }
        Heading look = lookOf(caster.getUniqueId()).orElse(null);
        if (type == TargetSpec.Type.ENEMIES_IN_CONE) {
            // Смотрит строго вверх или вниз: направления по земле нет, и конус
            // накрывает всё вокруг, как и раньше (null — без проверки угла).
            return look;
        }
        return Facing.coneAxisToCaster(caster.getLocation().getX() - apex.getX(),
                caster.getLocation().getZ() - apex.getZ(), look);
    }

    @Override
    public Optional<Heading> lookOf(UUID entityId) {
        Entity entity = Bukkit.getEntity(entityId);
        if (entity == null) {
            return Optional.empty();
        }
        Vector look = entity.getLocation().getDirection();
        return Optional.ofNullable(Heading.orNull(look.getX(), look.getZ()));
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
    public DamageResult dealDamage(UUID casterId, UUID targetId, double amount,
                                   DamageSchool school, String skillId) {
        if (!(Bukkit.getEntity(targetId) instanceof LivingEntity target) || target.isDead()) {
            return null;
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
            return result;
        }
        statuses.consumeShield(targetId, result.absorbed());
        drinkBlood(casterId, result.applied());

        // Единственный вызов, отнимающий здоровье. Событие Bukkit испускается
        // им же, поэтому региональные плагины могут отменить урон штатно.
        double before = target.getHealth() + target.getAbsorptionAmount();
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
        // Отменённое событие урона не бросает исключения и ничего не
        // возвращает: о нём говорит только нетронутое здоровье. Без этой
        // проверки вспышка попадания горела бы в регионе, где урон запрещён.
        if (!target.isDead() && target.getHealth() + target.getAbsorptionAmount() >= before) {
            return DamageResult.blockedBy(DamageResult.Blocker.EVENT, result.absorbed());
        }
        return result;
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
        double share = stats.share(attackerId, StatIds.LIFESTEAL);
        if (share > 0) {
            heal(attackerId, dealt * share);
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
        // Без пузырьков: это ванильные частицы, которые сыпались бы с цели
        // поверх эффектов мода весь срок зелья. Значок у игрока остаётся.
        target.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, false, false, true));
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

        /** Номер снаряда в канале мода; 0 — эффекта мода нет. */
        private int handle;

        /** Кому ушло начало полёта: им же уйдёт и конец. */
        private List<Player> watchers = List.of();

        private ProjectileFlight(ProjectileSpec spec, ProjectileHandler handler, UUID casterId,
                                 Location start, Vector direction) {
            this.spec = spec;
            this.handler = handler;
            this.casterId = casterId;
            this.at = start.clone();
            this.direction = direction.clone();
        }

        void start() {
            if (spec.fx() != null || spec.particle() != null) {
                // Полёт мод ведёт сам: одно событие на вылет и одно на конец,
                // а не позиция каждый тик. Видят те, кто рядом с точкой вылета
                // или с серединой пути. Без fx — тоже событием: мод рисует общий
                // снаряд цветом класса, ванильный след идёт только без мода.
                handle = fx.nextHandle();
                Location middle = at.clone().add(direction.clone().multiply(spec.range() / 2));
                watchers = fx.viewers(middle, FxBroadcaster.RANGE + spec.range() / 2).modded();
                fx.send(watchers, new FxMessage.Projectile(handle, spec.fx(), spec.classId(),
                        at.getX(), at.getY(), at.getZ(),
                        (float) direction.getX(), (float) direction.getY(),
                        (float) direction.getZ(), (float) spec.speed(), (float) spec.range(),
                        (float) spec.gravity()));
            }
            task = Bukkit.getScheduler().runTaskTimer(plugin, this, 0L, 1L);
        }

        private void stop(Location where, boolean hit) {
            if (task != null) {
                task.cancel();
            }
            if (handle != 0) {
                fx.send(watchers, new FxMessage.ProjectileEnd(handle,
                        where.getX(), where.getY(), where.getZ(), hit));
            }
        }

        @Override
        public void run() {
            double remaining = Math.min(spec.speed(), spec.range() - travelled);
            int segments = (int) Math.ceil(remaining / MAX_SEGMENT);
            double step = remaining / Math.max(1, segments);
            // Ванильный след снаряда — только тем, у кого мода нет;
            // кто это, решается раз за тик, а не на каждый отрезок.
            List<Player> trailViewers = fx.viewers(at).vanilla();

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
                    particlesFor(trailViewers, toPosition(at), spec.particle(),
                            Action.Particles.Shape.POINT, 1, 0, 0, null);
                }

                if (spec.stopAtBlock() && at.getBlock().getType().isSolid()) {
                    // Назад на отрезок: взрыв должен гремить перед стеной, а не
                    // внутри неё, иначе его не видно.
                    stop(before, false);
                    handler.end(toPosition(before));
                    return;
                }

                UUID victim = firstTarget();
                if (victim != null) {
                    alreadyHit.add(victim);
                    hits++;
                    if (hits >= spec.pierce()) {
                        stop(at, true);
                        handler.hit(toPosition(at), victim);
                        return;
                    }
                    handler.hit(toPosition(at), victim);
                }

                if (travelled >= spec.range()) {
                    stop(at, false);
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
        particles(at, particle, shape, count, size, 0, null);
    }

    @Override
    public void particles(Position at, String particle, Action.Particles.Shape shape,
                          int count, double size, double angle, Heading axis) {
        // Даже прямой вызов не рассылает ванильные частицы всем подряд: игрок
        // с модом получает общий эффект, остальные — частицы.
        effect(new FxEvent.Burst(null, "", at, shape, size, angle, axis, particle, count));
    }

    /**
     * Частицы адресно.
     *
     * @param viewers кому показать: только игроки без мода
     */
    private void particlesFor(List<Player> viewers, Position at, String particle,
                              Action.Particles.Shape shape, int count, double size,
                              double angle, Heading axis) {
        if (viewers == null || viewers.isEmpty()) {
            return;
        }
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
            draw(world, viewers, centre, type, shape, count, size, angle, axis);
        } catch (RuntimeException e) {
            if (badParticles.add(particle)) {
                plugin.getLogger().warning("частица " + particle
                        + " не рисуется без дополнительных данных: " + e.getMessage());
            }
        }
    }

    /** Расстояние между соседними точками границы: дыр в кольце не видно. */
    private static final double BORDER_STEP = 0.5;

    /** Больше точек на одну границу не ставим: дальше это уже нагрузка, а не чёткость. */
    private static final int BORDER_MAX_POINTS = 180;

    private void draw(World world, List<Player> viewers, Location centre, Particle type,
                      Action.Particles.Shape shape, int count, double size,
                      double angle, Heading axis) {
        switch (shape) {
            case POINT -> spawn(world, viewers, type, centre, count, 0.2);
            case SPHERE -> spawn(world, viewers, type, centre, count, size);
            case RING -> {
                for (Location point : ringPoints(centre, size, count)) {
                    spawn(world, viewers, type, point, 1, 0);
                }
            }
            case CONE -> {
                if (axis != null) {
                    for (Location point : conePoints(centre, size, angle, axis)) {
                        spawn(world, viewers, type, point, 1, 0);
                    }
                }
            }
            case LINE -> spawn(world, viewers, type, centre, count, 0.05);
        }
    }

    /** Одна рассылка частиц перечисленным игрокам. */
    private static void spawn(World world, List<Player> viewers, Particle type, Location at,
                              int count, double spread) {
        // Рассылки «всем вокруг» нет намеренно: так ванильная частица дошла бы
        // и до игрока с модом. Кому показывать, решает FxBroadcaster.
        if (viewers == null) {
            return;
        }
        for (Player viewer : viewers) {
            viewer.spawnParticle(type, at, count, spread, spread, spread, 0);
        }
    }

    /**
     * Точки кольца ровно на радиусе, прижатые к земле.
     *
     * <p>Число точек — не меньше, чем нужно, чтобы между соседними было не
     * больше полублока. Раньше их было столько, сколько написано в навыке, и
     * кольцо в семь блоков из сорока точек читалось как россыпь, а не как
     * граница. Высота — своя у каждой точки: кольцо на высоте центра уходило в
     * склон с одной стороны и висело в воздухе с другой.
     *
     * @param written сколько точек написано в навыке: меньше не будет
     */
    static List<Location> ringPoints(Location centre, double radius, int written) {
        int points = pointsFor(2 * Math.PI * radius, written);
        List<Location> out = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            out.add(onGround(centre, centre.getX() + Math.cos(a) * radius,
                    centre.getZ() + Math.sin(a) * radius));
        }
        return out;
    }

    /**
     * Точки конуса: дуга по радиусу и две кромки от вершины.
     *
     * <p>Ось и угол — те же, что у выборки целей, см. {@code coneAxis}: конус,
     * нарисованный от другой вершины, показывает не тех, кого заденет.
     */
    static List<Location> conePoints(Location apex, double radius, double angleDegrees,
                                     Heading axis) {
        double half = Math.toRadians(angleDegrees) / 2;
        double heading = Math.atan2(axis.z(), axis.x());
        int arc = pointsFor(radius * half * 2, 2);
        int edge = pointsFor(radius, 2);
        List<Location> out = new ArrayList<>(arc + 2 * edge);
        for (int i = 0; i <= arc; i++) {
            double a = heading - half + 2 * half * i / arc;
            out.add(onGround(apex, apex.getX() + Math.cos(a) * radius,
                    apex.getZ() + Math.sin(a) * radius));
        }
        for (double side : new double[] {-half, half}) {
            double a = heading + side;
            for (int i = 1; i < edge; i++) {
                double r = radius * i / edge;
                out.add(onGround(apex, apex.getX() + Math.cos(a) * r,
                        apex.getZ() + Math.sin(a) * r));
            }
        }
        return out;
    }

    private static int pointsFor(double length, int written) {
        int needed = (int) Math.ceil(length / BORDER_STEP);
        return Math.clamp(Math.max(needed, written), 1, BORDER_MAX_POINTS);
    }

    /**
     * Точка над землёй рядом с высотой центра.
     *
     * <p>Ищем верх твёрдого блока на три блока вниз и два вверх от центра:
     * дальше — уже не та земля, на которой стоит область, а обрыв или крыша, и
     * точку оставляем на высоте центра.
     */
    private static Location onGround(Location centre, double x, double z) {
        World world = centre.getWorld();
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int top = (int) Math.floor(centre.getY()) + 2;
        for (int y = top; y >= top - 5; y--) {
            if (world.getBlockAt(bx, y, bz).getType().isSolid()
                    && !world.getBlockAt(bx, y + 1, bz).getType().isSolid()) {
                return new Location(world, x, y + 1.1, z);
            }
        }
        return new Location(world, x, centre.getY() + 0.2, z);
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
        // Ванильное кольцо — только тем, у кого мода нет. Игрок с модом получил
        // зону событием, своим эффектом или общим, и второе кольцо поверх было
        // бы тем самым дублем.
        Optional<Location> centre = toLocation(zone.center());
        if (centre.isEmpty()) {
            return;
        }
        List<Player> viewers = fx.viewers(centre.get()).vanilla();
        // Число точек считает само кольцо: по полублока на точку.
        particlesFor(viewers, zone.center(), zone.particle(), Action.Particles.Shape.RING,
                8, zone.radius(), 0, null);
    }

    // ------------------------------------------------------------------ эффекты мода

    @Override
    public void effect(FxEvent event) {
        switch (event) {
            case FxEvent.Burst burst -> {
                Optional<Location> at = toLocation(burst.at());
                if (at.isEmpty()) {
                    return;
                }
                FxBroadcaster.Viewers viewers = fx.viewers(at.get());
                Heading axis = burst.axis();
                // fx: none — украшение для игроков без мода: у игрока с модом
                // в том же шаге уже есть свой эффект.
                if (!FxEvent.NONE.equals(burst.fx())) {
                    fx.send(viewers.modded(), new FxMessage.Burst(burst.fx(), burst.classId(),
                            FxMessage.Shape.valueOf(burst.shape().name()),
                            burst.at().x(), burst.at().y(), burst.at().z(),
                            (float) burst.radius(), (float) burst.angle(),
                            axis == null ? 0f : (float) axis.x(),
                            axis == null ? 0f : (float) axis.z()));
                }
                particlesFor(viewers.vanilla(), burst.at(), burst.particle(), burst.shape(),
                        burst.count(), burst.radius(), burst.angle(), axis);
            }
            case FxEvent.ZonePlaced placed -> toLocation(placed.zone().center())
                    .ifPresent(centre -> fx.zonePlaced(placed.zone(), centre));
            case FxEvent.ZoneConsumed consumed ->
                    fx.zoneConsumed(consumed.zone(), consumed.pulledTo());
            case FxEvent.Hit hit -> showHit(hit);
            case FxEvent.Trail trail -> showTrail(trail);
        }
    }

    /**
     * Попадание навыка.
     *
     * <p>Крит звучит у всех — звук не рисуется дважды, а без мода игрок иначе не
     * узнал бы о крите вовсе. Вспышку видит мод, ванильные искры — те, у кого
     * мода нет.
     */
    private void showHit(FxEvent.Hit hit) {
        Entity target = Bukkit.getEntity(hit.target());
        if (target == null) {
            return;
        }
        Location chest = target.getLocation().add(0, target.getHeight() * 0.6, 0);
        FxBroadcaster.Viewers viewers = fx.viewers(chest);
        fx.send(viewers.modded(), new FxMessage.Hit(target.getEntityId(), hit.classId(),
                hit.crit()));
        if (hit.crit()) {
            spawn(chest.getWorld(), viewers.vanilla(), Particle.CRIT, chest, 16, 0.35);
            chest.getWorld().playSound(chest, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.1f);
        }
    }

    /** Шаг следа: точка на каждые сорок сантиметров пути. */
    private static final double TRAIL_STEP = 0.4;

    /** След переноса: линия частиц на высоте груди от старта до прибытия. */
    private void showTrail(FxEvent.Trail trail) {
        Optional<Location> from = toLocation(trail.from());
        Optional<Location> to = toLocation(trail.to());
        if (from.isEmpty() || to.isEmpty() || from.get().getWorld() != to.get().getWorld()) {
            return;
        }
        Particle type = fromKey(trail.particle(), Particle.class);
        if (type == null) {
            plugin.getLogger().warning("неизвестная частица: " + trail.particle());
            return;
        }
        Location middle = from.get().clone().add(to.get()).multiply(0.5);
        FxBroadcaster.Viewers viewers = fx.viewers(middle);
        List<Player> vanilla = viewers.vanilla();
        // Без fx мод рисует общий след цветом класса: ванильный — только без мода.
        fx.send(viewers.modded(), new FxMessage.Trail(trail.fx(), trail.classId(),
                trail.from().x(), trail.from().y(), trail.from().z(),
                trail.to().x(), trail.to().y(), trail.to().z()));
        Vector path = to.get().toVector().subtract(from.get().toVector());
        int points = Math.clamp((int) Math.ceil(path.length() / TRAIL_STEP), 1, 80);
        for (int i = 0; i <= points; i++) {
            Location point = from.get().clone().add(path.clone().multiply((double) i / points))
                    .add(0, 1.0, 0);
            try {
                spawn(point.getWorld(), vanilla, type, point, 1, 0.05);
            } catch (RuntimeException e) {
                if (badParticles.add(trail.particle())) {
                    plugin.getLogger().warning("частица " + trail.particle()
                            + " не рисуется без дополнительных данных: " + e.getMessage());
                }
                return;
            }
        }
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
