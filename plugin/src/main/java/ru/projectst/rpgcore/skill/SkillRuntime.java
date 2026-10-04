package ru.projectst.rpgcore.skill;

import java.util.List;
import java.util.UUID;
import ru.projectst.rpgcore.balance.BalanceBook;
import ru.projectst.rpgcore.balance.BalanceTable;
import ru.projectst.rpgcore.status.StatusApplication;
import ru.projectst.rpgcore.status.StatusService;

/**
 * Исполняет навыки.
 *
 * <p>Класс намеренно скучный: вся сложность живёт в модели, а здесь остаётся
 * обход шагов. Это проверка того, что DSL спроектирован правильно — если бы
 * исполнителю приходилось что-то домысливать, значит модель недоговаривает.
 *
 * <p>Ключевой инвариант: <b>цели шага вычисляются один раз</b>, и все действия
 * шага получают один и тот же список. Ровно это и нельзя было гарантировать в
 * старом стеке.
 */
public final class SkillRuntime {

    private final SkillWorld world;
    private final StatusService statuses;
    private final BalanceBook balance;

    public SkillRuntime(SkillWorld world, StatusService statuses, BalanceBook balance) {
        this.world = world;
        this.statuses = statuses;
        this.balance = balance;
    }

    /**
     * Исполняет навык.
     *
     * @param level уровень навыка: по нему разворачиваются кривые баланса
     */
    public void cast(UUID caster, SkillDef skill, int level) {
        BalanceTable table = balance.table(skill.id());
        for (Step step : skill.steps()) {
            if (step.delayTicks() > 0) {
                world.runLater(step.delayTicks(), () -> runStep(caster, skill, step, table, level));
            } else {
                runStep(caster, skill, step, table, level);
            }
        }
    }

    private void runStep(UUID caster, SkillDef skill, Step step, BalanceTable table, int level) {
        double radius = resolve(step.target().radius(), table, level, 0);
        double angle = resolve(step.target().angle(), table, level, 0);

        // Единственное место, где определяются цели шага.
        List<UUID> targets =
                world.resolveTargets(caster, step.target().type(), radius, angle);
        if (targets.isEmpty()) {
            return;
        }

        for (Action action : step.actions()) {
            for (UUID target : targets) {
                perform(caster, target, skill, action, table, level);
            }
        }
    }

    private void perform(UUID caster, UUID target, SkillDef skill, Action action,
                         BalanceTable table, int level) {
        switch (action) {
            case Action.Damage d -> world.dealDamage(caster, target,
                    d.amount().resolve(table, level), d.school(), skill.id());

            case Action.Heal h -> world.heal(target, h.amount().resolve(table, level));

            case Action.ApplyStatus s -> statuses.apply(target, new StatusApplication(
                    s.statusId(),
                    (int) resolve(s.duration(), table, level, 0),
                    resolve(s.amount(), table, level, 0),
                    "skill:" + skill.id()));

            case Action.RemoveStatus r -> statuses.remove(target, r.statusId());

            case Action.Message m -> world.message(target, m.text());
        }
    }

    private static double resolve(NumberRef ref, BalanceTable table, int level, double fallback) {
        return ref == null ? fallback : ref.resolve(table, level);
    }
}
