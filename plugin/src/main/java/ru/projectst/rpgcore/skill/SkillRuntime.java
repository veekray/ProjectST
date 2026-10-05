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
    private final DoubleSupplier random;

    public SkillRuntime(SkillWorld world, StatusService statuses, StatService stats,
                        BalanceBook balance, SkillRegistry skills, DoubleSupplier random) {
        this.world = world;
        this.statuses = statuses;
        this.stats = stats;
        this.balance = balance;
        this.skills = skills;
        this.random = random;
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
            if (step.delayTicks() > 0) {
                world.runLater(step.delayTicks(),
                        () -> runStep(context, skill, step, table, depth));
            } else {
                runStep(context, skill, step, table, depth);
            }
        }
    }

    private void runStep(CastContext context, SkillDef skill, Step step,
                         BalanceTable table, int depth) {
        // Условия на кастере отменяют шаг целиком.
        for (Condition condition : step.conditions()) {
            if (condition.scope() == Condition.Scope.CASTER
                    && !matches(condition, context.caster(), context)) {
                return;
            }
        }

        double radius = resolve(step.target().radius(), table, context.level(), 0);
        double angle = resolve(step.target().angle(), table, context.level(), 0);

        // Единственное место, где определяются цели шага.
        List<UUID> targets =
                world.resolveTargets(context, step.target().type(), radius, angle);

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
        };
        return condition.negated() != result;
    }

    // ------------------------------------------------------------------ действия

    private void perform(CastContext context, SkillDef skill, Action action,
                         List<UUID> targets, BalanceTable table, int depth) {
        int level = context.level();
        switch (action) {

            case Action.Damage a -> forEach(targets, t -> world.dealDamage(context.caster(), t,
                    a.amount().resolve(table, level), a.school(), skill.id()));

            case Action.Heal a -> forEach(targets,
                    t -> world.heal(t, a.amount().resolve(table, level)));

            case Action.ApplyStatus a -> forEach(targets, t -> statuses.apply(t,
                    new StatusApplication(a.statusId(),
                            (int) resolve(a.duration(), table, level, 0),
                            resolve(a.amount(), table, level, 0),
                            "skill:" + skill.id())));

            case Action.RemoveStatus a -> forEach(targets, t -> statuses.remove(t, a.statusId()));

            case Action.ModifyStat a -> {
                double value = a.value().resolve(table, level);
                int ticks = (int) resolve(a.duration(), table, level, 0);
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

            case Action.Potion a -> forEach(targets, t -> world.potion(t, a.effect(),
                    (int) resolve(a.duration(), table, level, 0), a.amplifier()));

            case Action.Push a -> {
                Position from = context.origin() != null ? context.origin()
                        : world.positionOf(context.caster()).orElse(null);
                if (from != null) {
                    double strength = a.strength().resolve(table, level);
                    double lift = resolve(a.lift(), table, level, 0.3);
                    forEach(targets, t -> world.push(t, from, strength, lift));
                }
            }

            case Action.Pull a -> {
                Position to = context.origin() != null ? context.origin()
                        : world.positionOf(context.caster()).orElse(null);
                if (to != null) {
                    double strength = a.strength().resolve(table, level);
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
                        : world.forwardOf(t, a.forward().resolve(table, level));
                to.ifPresent(position -> world.teleport(t, position));
            });

            case Action.Particles a -> {
                int count = (int) a.count().resolve(table, level);
                double size = resolve(a.size(), table, level, 1);
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

            case Action.Cast a -> {
                Optional<SkillDef> sub = skills.find(a.skillId());
                if (sub.isEmpty()) {
                    return; // связывание это уже поймало, здесь просто не падаем
                }
                if (a.atTargets()) {
                    forEach(targets, t -> cast(
                            new CastContext(t, level, context.origin(), context.caster()),
                            sub.get(), depth + 1));
                } else {
                    // Цели шага становятся точкой действия подчинённого навыка.
                    for (UUID target : targets) {
                        world.positionOf(target).ifPresent(p -> cast(
                                context.withOrigin(p).withTrigger(target), sub.get(), depth + 1));
                    }
                }
            }

            case Action.Ray a -> {
                SkillWorld.RayHit hit = world.castRay(context.caster(),
                        a.range().resolve(table, level), a.stopAtEntity());
                if (hit == null) {
                    return;
                }
                skills.find(a.onHit()).ifPresent(sub -> cast(
                        new CastContext(context.caster(), level, hit.point(), hit.entity()),
                        sub, depth + 1));
            }
        }
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

    private static double resolve(NumberRef ref, BalanceTable table, int level, double fallback) {
        return ref == null ? fallback : ref.resolve(table, level);
    }
}
