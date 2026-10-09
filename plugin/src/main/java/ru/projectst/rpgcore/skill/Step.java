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
 * @param origin     откуда считается шаг; по умолчанию точка приходит извне
 * @param delay      задержка перед шагом в тиках; ссылка в баланс допустима,
 *                   потому что задержка — такое же число для балансировки, как
 *                   урон: «секунда на сбор» однажды станет полутора
 */
public record Step(TargetSpec target, List<Action> actions, List<Condition> conditions,
                   OriginSpec origin, NumberRef delay, String telegraph) {

    /** Шаг без предупреждения: удар приходит, когда приходит. */
    public Step(TargetSpec target, List<Action> actions, List<Condition> conditions,
                OriginSpec origin, NumberRef delay) {
        this(target, actions, conditions, origin, delay, null);
    }

    /**
     * Есть ли у шага предупреждение.
     *
     * <p>Шаг с предупреждением запоминает свою область в момент каста — центр и
     * радиус — и сразу показывает её, а бьёт по ней же после задержки. Из круга
     * можно выйти, и кастер, ушедший с места, круг с собой не уносит.
     */
    public boolean telegraphed() {
        return telegraph != null;
    }

    public Step {
        if (target == null) {
            throw new IllegalArgumentException("у шага обязательны цели");
        }
        if (actions == null || actions.isEmpty()) {
            throw new IllegalArgumentException("шаг без действий бессмыслен");
        }
        actions = List.copyOf(actions);
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        origin = origin == null ? OriginSpec.INHERIT : origin;
        delay = delay == null ? new NumberRef.Literal(0) : delay;
    }

    public Step(TargetSpec target, List<Action> actions, int delayTicks) {
        this(target, actions, List.of(), OriginSpec.INHERIT, new NumberRef.Literal(delayTicks));
    }

    public Step(TargetSpec target, List<Action> actions, List<Condition> conditions,
                int delayTicks) {
        this(target, actions, conditions, OriginSpec.INHERIT,
                new NumberRef.Literal(delayTicks));
    }
}
