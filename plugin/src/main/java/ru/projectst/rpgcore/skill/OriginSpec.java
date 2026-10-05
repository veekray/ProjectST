package ru.projectst.rpgcore.skill;

/**
 * Откуда считается шаг: его точка действия.
 *
 * <p>Задаётся у шага, а не меняется по ходу каста. В старом стеке точку
 * подменяли ключом {@code origin=@Forward{f=9}} при каждом вызове, и чтобы
 * понять, откуда считается конкретная механика, приходилось разворачивать всю
 * цепочку вложенных метаскиллов. Здесь это одна строка в том же шаге.
 *
 * @param kind     как получается точка
 * @param distance для {@link Kind#FORWARD} — сколько блоков вперёд
 */
public record OriginSpec(Kind kind, NumberRef distance) {

    public enum Kind {
        /**
         * Точка приходит извне: от луча, снаряда или вызвавшего навыка. Если её
         * нет, цели вокруг точки дадут пустой список — молчаливого отката к
         * позиции кастера нет нигде.
         */
        INHERIT,

        /** Точка — там, где стоит кастер. */
        SELF,

        /**
         * Точка в нескольких блоках по взгляду кастера.
         *
         * <p>Упирается в блок: рывок и сбор не уводят за стену. Иначе точка
         * сбора оказывалась бы в камне, и притяжение било бы цели о стену.
         */
        FORWARD
    }

    public static final OriginSpec INHERIT = new OriginSpec(Kind.INHERIT, null);

    public OriginSpec {
        if (kind == null) {
            throw new IllegalArgumentException("у точки действия обязателен вид");
        }
        if (kind == Kind.FORWARD && distance == null) {
            throw new IllegalArgumentException("для точки впереди нужна дистанция");
        }
    }

    public static OriginSpec self() {
        return new OriginSpec(Kind.SELF, null);
    }

    public static OriginSpec forward(NumberRef distance) {
        return new OriginSpec(Kind.FORWARD, distance);
    }

    public boolean inherited() {
        return kind == Kind.INHERIT;
    }
}
