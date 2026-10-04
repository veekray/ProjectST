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
 * @param delayTicks задержка перед шагом; нужна там, где эффект должен
 *                   догнать анимацию или дождаться перемещения цели
 */
public record Step(TargetSpec target, List<Action> actions, int delayTicks) {

    public Step {
        if (target == null) {
            throw new IllegalArgumentException("у шага обязательны цели");
        }
        if (actions == null || actions.isEmpty()) {
            throw new IllegalArgumentException("шаг без действий бессмыслен");
        }
        actions = List.copyOf(actions);
        if (delayTicks < 0) {
            throw new IllegalArgumentException("задержка не может быть отрицательной");
        }
    }
}
