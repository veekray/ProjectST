package ru.projectst.rpgcore.skill;

import java.util.List;

/**
 * Шаг навыка: один набор целей и всё, что с ними делается.
 *
 * <p><b>Главное решение DSL.</b> Цели шага вычисляются <b>один раз</b>, и все
 * его действия видят ровно один и тот же список. В старом стеке таргетер
 * переоценивался на каждой механике, из-за чего урон и эффект могли уйти по
 * разным целям, а строка {@code limit=1} отрезала список раньше фильтра.
 * Здесь такой возможности нет по устройству.
 *
 * <p>Если нужны разные цели — это разные шаги, и в файле это видно.
 *
 * @param conditions условия; на кастере отменяют шаг, на целях отсеивают
 *                   не прошедших
 * @param delayTicks задержка перед шагом
 */
public record Step(TargetSpec target, List<Action> actions, List<Condition> conditions,
                   int delayTicks) {

    public Step {
        if (target == null) {
            throw new IllegalArgumentException("у шага обязательны цели");
        }
        if (actions == null || actions.isEmpty()) {
            throw new IllegalArgumentException("шаг без действий бессмыслен");
        }
        actions = List.copyOf(actions);
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        if (delayTicks < 0) {
            throw new IllegalArgumentException("задержка не может быть отрицательной");
        }
    }

    public Step(TargetSpec target, List<Action> actions, int delayTicks) {
        this(target, actions, List.of(), delayTicks);
    }
}
