package ru.projectst.rpgcore.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import ru.projectst.rpgcore.balance.BalanceBook;
import ru.projectst.rpgcore.balance.BalanceTable;
import ru.projectst.rpgcore.stat.StatModifier;
import ru.projectst.rpgcore.stat.StatService;
import ru.projectst.rpgcore.status.StatusApplication;
import ru.projectst.rpgcore.status.StatusService;

/**
 * Исполняет навыки.
 *
 * <p>Класс намеренно скучный: вся сложность живёт в модели, здесь остаётся
 * обход шагов. Это проверка того, что DSL спроектирован правильно — если бы
 * исполнителю приходилось что-то домысливать, значит модель недоговаривает.
 *
 * <p>Ключевой инвариант: <b>цели шага вычисляются один раз</b>, и все его
 * действия получают один и тот же список.
 */
public final class SkillRuntime {

    /** Защита от взаимной рекурсии навыков: А зовёт Б, Б зовёт А. */
    private static final int MAX_DEPTH = 8;

    private final SkillWorld world;
    private final StatusService statuses;
    private final StatService stats;
    private final BalanceBook balance;
    private final SkillRegistry skills;
    private final ZoneService zones;
    private final MinionService minions;
    private final DoubleSupplier random;

    /** Надбавки, привязанные к статусам: {@code modify-stat} со {@code status}. */
    private final ru.projectst.rpgcore.status.StatusStats statusStats;

    /**
     * Куда возвращать ресурс. Исполнитель не знает, мана это или выносливость:
     * чей ресурс и как он называется, решает класс игрока.
     */
    private java.util.function.ObjDoubleConsumer<UUID> resources = (player, amount) -> { };

    /**
     * Чем сбрасывать перезарядку.
     *
     * <p>Тоже через приёмник, а не прямой ссылкой: учёт перезарядок живёт в
     * воротах каста, а ворота вызывают исполнитель. Прямая ссылка замкнула бы
     * круг, и это поймал бы тест направления зависимостей.
     */
    private java.util.function.BiConsumer<UUID, String> cooldownReset = (player, skill) -> { };

    public SkillRuntime(SkillWorld world, StatusService statuses, StatService stats,
                        BalanceBook balance, SkillRegistry skills, ZoneService zones,
                        MinionService minions, DoubleSupplier random) {
        this.world = world;
        this.statuses = statuses;
        this.stats = stats;
        this.balance = balance;
        this.skills = skills;
        this.zones = zones;
        this.minions = minions;
        this.random = random;
        this.statusStats = new ru.projectst.rpgcore.status.StatusStats(statuses, stats);
    }

    /**
     * Надбавки, привязанные к статусам.
     *
     * <p>Отдаётся наружу, потому что сверять их со статусами нужно каждый тик, а
     * показывать — в интерфейсе. Экземпляр один: два учёта одних и тех же
     * надбавок разошлись бы на первом же снятии.
     */
    public ru.projectst.rpgcore.status.StatusStats statusStats() {
        return statusStats;
    }

    /**
     * Подключает возврат ресурса.
     *
     * <p>Ставится после сборки, потому что пул ресурсов знает о классах, а
     * классы — о навыках: прямая ссылка замкнула бы круг.
     */
    public void useResources(java.util.function.ObjDoubleConsumer<UUID> sink) {
        this.resources = sink == null ? (player, amount) -> { } : sink;
    }

    /** Подключает сброс перезарядок; см. {@link #cooldownReset}. */
    public void useCooldowns(java.util.function.BiConsumer<UUID, String> sink) {
        this.cooldownReset = sink == null ? (player, skill) -> { } : sink;
    }

    public void cast(UUID caster, SkillDef skill, int level) {
        cast(CastContext.of(caster, level), skill, 0);
    }

    public void cast(CastContext context, SkillDef skill, int depth) {
        if (depth > MAX_DEPTH) {
            // Молча прекращаем: это ошибка контента, и её ловит связывание,
            // а здесь нужна лишь страховка от зацикливания на живом сервере.
            return;
        }
        BalanceTable table = balance.table(skill.id());
        for (Step step : skill.steps()) {
            int delay = (int) resolve(step.delay(), table, context, 0);
            if (delay > 0) {
                world.runLater(delay, () -> runStep(context, skill, step, table, depth));
            } else {
                runStep(context, skill, step, table, depth);
            }
        }
    }

    private void runStep(CastContext outer, SkillDef skill, Step step,
                         BalanceTable table, int depth) {
        // Условия на кастере отменяют шаг целиком.
        for (Condition condition : step.conditions()) {
            if (condition.scope() == Condition.Scope.CASTER
                    && !matches(condition, outer.caster(), outer)) {
                return;
            }
        }

        // Точка действия шага. Считается здесь и дальше не меняется, поэтому
        // «откуда считается этот шаг» читается в самом шаге, а не собирается
        // из цепочки вложенных вызовов.
        CastContext context = withStepOrigin(outer, step, table);

        // Радиус шага — единственное место, где он вообще определяется, поэтому
        // стат читается здесь и больше нигде: зоны и взрывы выбирают цели тем
        // же шагом. Угол не трогаем — «шире по кругу» и «шире по дуге» это
        // разные вещи, и общий множитель испортил бы конусы.
        double radius = resolve(step.target().radius(), table, context, 0)
                * radiusScale(context.caster());
        double angle = resolve(step.target().angle(), table, context, 0);

        // Единственное место, где определяются цели шага.
        List<UUID> targets = resolveTargets(context, step, radius, angle);

        // Условия на целях не отменяют шаг, а отсеивают не прошедших.
        List<UUID> kept = new ArrayList<>(targets.size());
        for (UUID target : targets) {
            boolean ok = true;
            for (Condition condition : step.conditions()) {
                if (condition.scope() == Condition.Scope.TARGET
                        && !matches(condition, target, context)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                kept.add(target);
            }
        }
        // Пустой список целей шаг НЕ отменяет. Действия, которым цели не нужны —
        // частицы и звук в точке действия, луч — обязаны отработать всё равно.
        // Ровно этим мучил старый стек: метаскилл без подходящих целей не
        // выполнял даже партиклы, и промах выглядел как сломанный навык.
        for (Action action : step.actions()) {
            perform(context, skill, action, kept, table, depth);
        }
    }

    /** Точка действия для шага: своя, впереди по взгляду или пришедшая извне. */
    private CastContext withStepOrigin(CastContext context, Step step, BalanceTable table) {
        return switch (step.origin().kind()) {
            case INHERIT -> context;
            case SELF -> world.positionOf(context.caster())
                    .map(context::withOrigin).orElse(context);
            case FORWARD -> world.forwardOf(context.caster(),
                            step.origin().distance().resolve(table, context.level(),
                                    context.counters()))
                    .map(context::withOrigin).orElse(context);
        };
    }

    /**
     * Цели шага.
     *
     * <p>Зонные цели собираются здесь, а не в порту мира: порт не знает про
     * зоны, и знать ему незачем. Ограничение {@code limit} применяется
     * последним — после выборки, но до условий, и это важно: в старом стеке
     * {@code limit=1} отрезал список ДО фильтра, поэтому ближайшая неподходящая
     * цель вытесняла подходящую, и навык молча не срабатывал.
     */
    private List<UUID> resolveTargets(CastContext context, Step step,
                                      double radius, double angle) {
        TargetSpec spec = step.target();
        List<UUID> found;
        if (spec.type() == TargetSpec.Type.ENEMIES_NEAR_ZONE) {
            java.util.LinkedHashSet<UUID> unique = new java.util.LinkedHashSet<>();
            for (Zone zone : zones.ofOwner(context.caster(), spec.tag())) {
                unique.addAll(world.resolveTargets(context.withOrigin(zone.center()),
                        TargetSpec.Type.ENEMIES_NEAR_ORIGIN, radius, angle));
            }
            found = new ArrayList<>(unique);
        } else if (spec.type() == TargetSpec.Type.OWN_MINIONS) {
            // Своих призванных знает реестр, а не мир: искать их по типу
            // существа в радиусе и отсеивать чужих сравнением имени хозяина
            // приходилось в старом стеке, потому что владельца там никто не
            // помнил. Здесь помнит.
            found = new ArrayList<>();
            for (Minion minion : minions.ofOwner(context.caster(), spec.tag())) {
                found.add(minion.entityId());
            }
        } else {
            found = world.resolveTargets(context, spec.type(), radius, angle);
        }
        if (spec.limit() > 0 && found.size() > spec.limit()) {
            found = nearestFirst(context, found).subList(0, spec.limit());
        }
        return found;
    }

    private List<UUID> nearestFirst(CastContext context, List<UUID> targets) {
        Position from = context.origin() != null
                ? context.origin()
                : world.positionOf(context.caster()).orElse(null);
        List<UUID> sorted = new ArrayList<>(targets);
        if (from == null) {
            return sorted;
        }
        sorted.sort(java.util.Comparator.comparingDouble(target ->
                world.positionOf(target).map(from::distanceTo).orElse(Double.MAX_VALUE)));
        return sorted;
    }

    // ------------------------------------------------------------------ условия

    private boolean matches(Condition condition, UUID subject, CastContext context) {
        boolean result = switch (condition.check()) {
            case HAS_STATUS -> statuses.isActing(subject, condition.value());
            case STATUS_STACKS -> {
                String[] parts = condition.value().split(":", 2);
                int needed = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                yield statuses.all(subject).stream()
                        .anyMatch(s -> s.id().equals(parts[0]) && s.stacks() >= needed);
            }
            case IS_PLAYER -> world.isPlayer(subject);
            case CHANCE -> random.getAsDouble() * 100 < Double.parseDouble(condition.value());
            case IN_ZONE -> {
                String[] parts = condition.value().split(":", 2);
                boolean ownOnly = parts.length > 1 && parts[1].equals("own");
                yield world.positionOf(subject)
                        .map(p -> zones.at(p, parts[0]).stream()
                                .anyMatch(z -> !ownOnly || z.ownedBy(context.caster())))
                        .orElse(false);
            }
            case HAS_MINION -> !minions.ofOwner(subject, condition.value()).isEmpty();
            // Проверяется всегда кастер относительно subject: «со спины» имеет
            // смысл только как «я зашёл ему за спину», и писать это наоборот
            // было бы той же ловушкой, из-за которой условия старого стека
            // выглядели так, будто проверяют цель.
            case DISTANCE -> {
                String[] parts = condition.value().split(":", 2);
                double from = Double.parseDouble(parts[0]);
                double to = parts.length > 1 ? Double.parseDouble(parts[1]) : Double.MAX_VALUE;
                Position here = world.positionOf(context.caster()).orElse(null);
                Position there = world.positionOf(subject).orElse(null);
                if (here == null || there == null) {
                    yield false;
                }
                double distance = here.distanceTo(there);
                yield distance >= from && distance < to;
            }
            case BEHIND -> !subject.equals(context.caster())
                    && world.isBehind(context.caster(), subject,
                    Double.parseDouble(condition.value()));
            case COUNTER -> {
                String[] parts = condition.value().split(":", 2);
                double needed = parts.length > 1 ? Double.parseDouble(parts[1]) : 1;
                yield context.counter(parts[0]) >= needed;
            }
            case ZONE_COUNT -> {
                String[] parts = condition.value().split(":", 2);
                int needed = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                yield world.positionOf(subject)
                        .map(p -> zones.at(p, parts[0]).size() >= needed)
                        .orElse(false);
            }
        };
        return condition.negated() != result;
    }

    // ------------------------------------------------------------------ действия

    private void perform(CastContext context, SkillDef skill, Action action,
                         List<UUID> targets, BalanceTable table, int depth) {
        int level = context.level();
        switch (action) {

            // Доля здоровья считается здесь, а не в конвейере: конвейер должен
            // получать одно число и обходиться с ним одинаково, иначе защита и
            // крит однажды перестанут применяться к одной из основ.
            case Action.Damage a -> {
                double written = a.amount().resolve(table, level, context.counters());
                forEach(targets, t -> world.dealDamage(context.caster(), t,
                        basisOf(a.basis(), written, t), a.school(), skill.id()));
            }

            // Лечение и длительности статусов усиливаются статами кастера.
            // Эти два стата были объявлены и не читались никем: числа, которые
            // игрок видит в меню и которые ничего не меняют, — худший вид
            // объявления без исполнения.
            case Action.Sacrifice a -> {
                double share = a.percent().resolve(table, level, context.counters());
                forEach(targets, t -> world.sacrifice(t, share));
            }

            case Action.Heal a -> {
                double healed = a.amount().resolve(table, level, context.counters())
                        * effectScale(context.caster());
                // Получаемое лечение — стат цели, а не кастера, и читается
                // здесь, в единственном месте, где лечение считается. В мире он
                // был бы недоступен, и анти-хил пришлось бы повторять в каждом
                // навыке, который лечит.
                forEach(targets, t -> world.heal(t, healed * incomingScale(t)));
            }

            case Action.ApplyStatus a -> {
                int duration = (int) Math.round(resolve(a.duration(), table, context, 0)
                        * durationScale(context.caster()));
                double amount = resolve(a.amount(), table, context, 0)
                        * effectScale(context.caster());
                // В источнике записан и навык, и тот, кто его применил. Второе
                // нужно там, где важно не «чем наложено», а «кем»: Присяга
                // рыцаря считает вызванным того, кто вызван именно им, и без
                // имени вызвавшего два рыцаря в одном бою делили бы один вызов.
                String source = "skill:" + skill.id() + ":" + context.caster();
                forEach(targets, t -> statuses.apply(t,
                        new StatusApplication(a.statusId(), duration, amount, source)));
            }

            case Action.RemoveStatus a -> forEach(targets, t -> {
                // Снятие по метке: берём первый подходящий статус, а не все
                // сразу. «Украсть усиление» — это одно усиление; снять их все
                // одним действием было бы совсем другим навыком.
                String id = a.tag() == null ? a.statusId() : statuses.all(t).stream()
                        .filter(s -> s.def().tags().contains(a.tag()))
                        .map(s -> s.def().id())
                        .findFirst().orElse(null);
                if (id == null) {
                    return;
                }
                if (a.stacks() > 0) {
                    for (int i = 0; i < a.stacks(); i++) {
                        statuses.removeStack(t, id);
                    }
                } else {
                    statuses.remove(t, id);
                }
            });

            case Action.ModifyStat a when a.statusId() != null -> {
                // Надбавка — часть статуса: живёт, пока он лежит, и не ложится
                // вовсе, если статус не лёг. Числа — по-прежнему из баланса навыка.
                double value = a.value().resolve(table, level, context.counters());
                forEach(targets, t -> statusStats.attach(t, a.statusId(), a.statId(), a.op(),
                        value));
            }

            case Action.ModifyStat a -> {
                double value = a.value().resolve(table, level, context.counters());
                int ticks = (int) resolve(a.duration(), table, context, 0);
                String source = "skill:" + skill.id() + ":" + a.statId();
                forEach(targets, t -> {
                    stats.setSource(t, source,
                            List.of(new StatModifier(a.statId(), a.op(), value, source)));
                    if (ticks > 0) {
                        // Снятие по таймеру: отдельного состояния для этого не
                        // заводим, иначе появился бы второй учёт времени рядом
                        // со статусами.
                        world.runLater(ticks, () -> stats.removeSource(t, source));
                    }
                });
            }

            case Action.ClearPotion a -> forEach(targets, t -> world.clearPotion(t, a.effect()));

            case Action.Potion a -> forEach(targets, t -> world.potion(t, a.effect(),
                    (int) resolve(a.duration(), table, context, 0), a.amplifier()));

            case Action.Push a -> {
                Position from = context.origin() != null ? context.origin()
                        : world.positionOf(context.caster()).orElse(null);
                if (from != null) {
                    double strength = a.strength().resolve(table, level, context.counters());
                    double lift = resolve(a.lift(), table, context, 0.3);
                    forEach(targets, t -> world.push(t, from, strength, lift));
                }
            }

            case Action.Pull a -> {
                Position to = context.origin() != null ? context.origin()
                        : world.positionOf(context.caster()).orElse(null);
                if (to != null) {
                    double strength = a.strength().resolve(table, level, context.counters());
                    // Несколько слабых импульсов вместо одного сильного: каждый
                    // пересчитывает направление, поэтому перелёт исправляется сам.
                    int ticks = Math.max(1, a.ticks());
                    for (int i = 0; i < ticks; i++) {
                        int delay = i * 4;
                        if (delay == 0) {
                            forEach(targets, t -> world.pullTowards(t, to, strength));
                        } else {
                            world.runLater(delay,
                                    () -> forEach(targets, t -> world.pullTowards(t, to, strength)));
                        }
                    }
                }
            }

            case Action.Teleport a -> forEach(targets, t -> {
                Optional<Position> to = context.origin() != null
                        ? Optional.of(context.origin())
                        : world.forwardOf(t, a.forward().resolve(table, level, context.counters()));
                to.ifPresent(position -> world.teleport(t, position));
            });

            case Action.Particles a -> {
                int count = (int) a.count().resolve(table, level, context.counters());
                double size = resolve(a.size(), table, context, 1);
                if (a.atOrigin()) {
                    positionFor(context).ifPresent(
                            p -> world.particles(p, a.particle(), a.shape(), count, size));
                } else {
                    forEach(targets, t -> world.positionOf(t).ifPresent(
                            p -> world.particles(p, a.particle(), a.shape(), count, size)));
                }
            }

            case Action.Sound a -> {
                if (a.atOrigin()) {
                    positionFor(context).ifPresent(
                            p -> world.sound(p, a.sound(), a.volume(), a.pitch()));
                } else {
                    forEach(targets, t -> world.positionOf(t).ifPresent(
                            p -> world.sound(p, a.sound(), a.volume(), a.pitch())));
                }
            }

            case Action.Message a -> forEach(targets, t -> world.message(t, a.text()));

            case Action.Dash a -> world.dash(context.caster(),
                    a.strength().resolve(table, level, context.counters()),
                    resolve(a.lift(), table, context, 0.2),
                    // Направление хода есть только у того каста, которому его
                    // прислали вместе с нажатием. Нет — рывок идёт по взгляду, и
                    // решается это здесь, а не в мире.
                    a.alongMovement() ? context.heading() : null);

            case Action.Approach a -> {
                // Сближение идёт к первой цели шага. С limit: 1 это ближайшая,
                // и так его и пишут: иначе «за спину» означало бы «за спину
                // кому-то из толпы».
                if (!targets.isEmpty()) {
                    world.offsetOf(targets.get(0),
                                    resolve(a.distance(), table, context, 1.2), a.behind())
                            .ifPresent(to -> world.teleport(context.caster(), to));
                }
            }

            case Action.Restore a -> {
                double amount = a.amount().resolve(table, level, context.counters());
                forEach(targets, t -> resources.accept(context.caster(), amount));
            }

            case Action.Count a -> {
                if (a.statusId() == null) {
                    context.count(a.counter(), targets.size());
                } else {
                    int stacks = statuses.all(context.caster()).stream()
                            .filter(status -> status.id().equals(a.statusId()))
                            .mapToInt(status -> status.stacks())
                            .findFirst().orElse(0);
                    context.count(a.counter(), stacks);
                }
            }

            case Action.PlaceZone a -> {
                double radius = resolve(a.radius(), table, context, 1);
                int ticks = (int) resolve(a.duration(), table, context, 20);
                double gap = resolve(a.minGap(), table, context, 0);
                if (a.atOrigin()) {
                    positionFor(context).ifPresent(p -> zones.place(a.tag(), context.caster(),
                            p, radius, ticks, a.particle(), gap, a.onEnter(), a.onTick(),
                            a.tickInterval()));
                } else {
                    forEach(targets, t -> world.positionOf(t).ifPresent(
                            p -> zones.place(a.tag(), context.caster(), p, radius, ticks,
                                    a.particle(), gap, a.onEnter(), a.onTick(),
                                    a.tickInterval())));
                }
            }

            case Action.ConsumeZones a -> {
                double radius = resolve(a.radius(), table, context, 1);
                UUID owner = a.ownOnly() ? context.caster() : null;
                int count = 0;
                if (a.atOrigin()) {
                    Optional<Position> at = positionFor(context);
                    if (at.isPresent()) {
                        count = zones.consume(at.get(), radius, a.tag(), owner);
                    }
                } else {
                    for (UUID target : targets) {
                        Optional<Position> at = world.positionOf(target);
                        if (at.isPresent()) {
                            count += zones.consume(at.get(), radius, a.tag(), owner);
                        }
                    }
                }
                // Счётчик пишется всегда, в том числе нулём: иначе прошлое
                // значение осталось бы видимым следующему шагу.
                context.count(a.counter(), count);
            }

            case Action.Swap ignored -> forEach(targets, t -> world.swap(context.caster(), t));

            case Action.Glow a -> {
                int ticks = (int) Math.round(a.duration().resolve(table, level,
                        context.counters()) * durationScale(context.caster()));
                forEach(targets, t -> world.glow(t, ticks));
            }

            case Action.DisableShield a -> {
                int ticks = (int) Math.round(a.duration().resolve(table, level,
                        context.counters()));
                forEach(targets, t -> world.disableShield(t, ticks));
            }

            case Action.Confuse a -> {
                double radius = a.radius().resolve(table, level, context.counters());
                forEach(targets, t -> world.confuse(t, radius));
            }

            case Action.Scatter a -> {
                double radius = a.radius().resolve(table, level, context.counters());
                forEach(targets, t -> world.scatter(t, radius));
            }

            case Action.ClearThreat a -> world.clearThreat(context.caster(),
                    a.radius().resolve(table, level, context.counters()));

            case Action.ResetCooldown a -> cooldownReset.accept(context.caster(), a.skillId());

            case Action.Cast a -> {
                Optional<SkillDef> sub = skills.find(a.skillId());
                if (sub.isEmpty()) {
                    return; // связывание это уже поймало, здесь просто не падаем
                }
                if (a.atTargets()) {
                    forEach(targets, t -> cast(
                            context.withCaster(t).withTrigger(context.caster()),
                            sub.get(), depth + 1));
                } else {
                    // Цели шага становятся точкой действия подчинённого навыка.
                    for (UUID target : targets) {
                        world.positionOf(target).ifPresent(p -> cast(
                                context.withOrigin(p).withTrigger(target), sub.get(), depth + 1));
                    }
                }
            }

            case Action.Summon a -> {
                int count = (int) resolve(a.count(), table, context, 1);
                int ticks = (int) resolve(a.duration(), table, context, 200);
                double health = resolve(a.health(), table, context, 0);
                List<Position> places = new ArrayList<>();
                if (a.atOrigin()) {
                    positionFor(context).ifPresent(places::add);
                } else {
                    for (UUID target : targets) {
                        world.positionOf(target).ifPresent(places::add);
                    }
                }
                for (Position place : places) {
                    for (int i = 0; i < count; i++) {
                        world.spawnMob(a.mob(), place, health).ifPresent(spawned -> {
                            minions.register(spawned, context.caster(), a.tag(), ticks,
                                    a.attacksEnemies()).ifPresent(world::despawn);
                            // Срок жизни снимает существо сам: иначе он зависел
                            // бы от того, тикает ли кто-то снаружи.
                            world.runLater(ticks, () -> {
                                if (minions.forget(spawned)) {
                                    world.despawn(spawned);
                                }
                            });
                        });
                    }
                }
            }

            case Action.Dismiss a -> {
                for (Minion minion : minions.ofOwner(context.caster(), a.tag())) {
                    minions.forget(minion.entityId());
                    world.despawn(minion.entityId());
                }
            }

            case Action.Projectile a -> {
                ProjectileSpec spec = new ProjectileSpec(
                        resolve(a.speed(), table, context, 1.2),
                        a.range().resolve(table, level, context.counters()),
                        resolve(a.hitRadius(), table, context, 1.2),
                        resolve(a.gravity(), table, context, 0),
                        a.pierce(), a.hitPlayers(), a.hitMobs(), a.stopAtBlock(),
                        a.particle(), a.yawOffset());
                world.launchProjectile(context.caster(), spec, new SkillWorld.ProjectileHandler() {
                    @Override
                    public void hit(Position point, UUID target) {
                        if (a.onHit() == null) {
                            return;
                        }
                        skills.find(a.onHit()).ifPresent(sub -> cast(
                                context.withOrigin(point).withTrigger(target),
                                sub, depth + 1));
                    }

                    @Override
                    public void end(Position point) {
                        if (a.onEnd() == null) {
                            return;
                        }
                        skills.find(a.onEnd()).ifPresent(sub -> cast(
                                context.withOrigin(point).withTrigger(null),
                                sub, depth + 1));
                    }
                });
            }

            case Action.Ray a -> {
                SkillWorld.RayHit hit = world.castRay(context.caster(),
                        a.range().resolve(table, level, context.counters()), a.stopAtEntity());
                if (hit == null) {
                    return;
                }
                skills.find(a.onHit()).ifPresent(sub -> cast(
                        context.withOrigin(hit.point()).withTrigger(hit.entity()),
                        sub, depth + 1));
            }
        }
    }

    /**
     * Во сколько раз сильнее эффекты этого кастера.
     *
     * <p>Стат задан процентами: ноль — как написано в балансе, двадцать — в
     * полтора... нет, ровно в 1.2 раза. Отрицательные значения тоже работают, и
     * нижняя граница в ноль обязательна: усиление «минус двести процентов»
     * лечило бы уроном.
     */
    /**
     * Число, которое войдёт в конвейер.
     *
     * <p>Доля здоровья считается от цели, а не от кастера: клеймо тем больнее,
     * чем крупнее жертва, и это единственное, чем основа отличается. Дальше
     * конвейер обходится с числом одинаково.
     */
    private double basisOf(Action.Basis basis, double written, UUID target) {
        return switch (basis) {
            case FLAT -> written;
            case TARGET_MAX_HEALTH -> written * world.maxHealthOf(target);
            case TARGET_CURRENT_HEALTH -> written * world.healthOf(target);
        };
    }

    /**
     * Во сколько раз лечение доходит до цели: ноль и ниже — не доходит вовсе.
     *
     * <p>Доля спрашивается у службы статов, а не считается делением на сто:
     * процентные статы — рейтинги, и кривая, применённая в одном месте и
     * забытая в другом, означала бы, что меню обещает не то, что происходит.
     */
    private double incomingScale(UUID target) {
        return Math.max(0, 1 + stats.share(target,
                ru.projectst.rpgcore.damage.StatIds.INCOMING_HEALING));
    }

    /** Во сколько раз шире площадь навыка: ноль и ниже невозможны. */
    private double radiusScale(UUID caster) {
        return Math.max(0.1, 1 + stats.share(caster,
                ru.projectst.rpgcore.damage.StatIds.SKILL_RADIUS));
    }

    private double effectScale(UUID caster) {
        return Math.max(0, 1 + stats.share(caster,
                ru.projectst.rpgcore.damage.StatIds.EFFECT_POWER));
    }

    /** Во сколько раз дольше держатся статусы от этого кастера. */
    private double durationScale(UUID caster) {
        return Math.max(0, 1 + stats.share(caster,
                ru.projectst.rpgcore.damage.StatIds.EFFECT_DURATION));
    }

    private Optional<Position> positionFor(CastContext context) {
        return context.origin() != null
                ? Optional.of(context.origin())
                : world.positionOf(context.caster());
    }

    private static void forEach(List<UUID> targets, java.util.function.Consumer<UUID> action) {
        for (UUID target : targets) {
            action.accept(target);
        }
    }

    private static double resolve(NumberRef ref, BalanceTable table, CastContext context,
                                 double fallback) {
        return ref == null ? fallback : ref.resolve(table, context.level(), context.counters());
    }
}
