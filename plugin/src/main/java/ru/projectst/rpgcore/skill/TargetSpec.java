package ru.projectst.rpgcore.skill;

/**
 * Кого затрагивает шаг навыка.
 *
 * <p>Набор намеренно маленький и закрытый. Новый способ выбора добавляется
 * сюда и в исполнитель, то есть компилятором, а не очередной строкой в
 * конфиге, которая может ничего не делать. Это прямое следствие того, как в
 * старом стеке таргетеры молча отбрасывали цели на трёх разных стадиях.
 *
 * @param type   способ выбора
 * @param radius радиус или дальность, где применимо
 * @param angle  угол конуса в градусах, только для {@link Type#ENEMIES_IN_CONE}
 * @param tag    тег зоны, только для {@link Type#ENEMIES_NEAR_ZONE}
 * @param limit  сколько целей оставить, считая от ближайшей; ноль — все
 */
public record TargetSpec(Type type, NumberRef radius, NumberRef angle, String tag, int limit) {

    public TargetSpec(Type type, NumberRef radius, NumberRef angle) {
        this(type, radius, angle, null, 0);
    }

    public enum Type {
        /** Сам кастер. */
        SELF(false, false),

        /** Тот, кто спровоцировал каст: ударивший, вошедший в зону. */
        TRIGGER(false, false),

        /** Враги вокруг кастера. */
        ENEMIES_IN_RADIUS(true, false),

        /** Союзники вокруг кастера, включая самого кастера. */
        ALLIES_IN_RADIUS(true, false),

        /** Враги в конусе по направлению взгляда кастера. */
        ENEMIES_IN_CONE(true, true),

        /**
         * Враги в конусе с вершиной в точке действия, раскрытом к кастеру.
         *
         * <p>Сгон мага: вершина — печать в точке сбора, и конус накрывает
         * дорогу от неё до мага и дальше за него. Отдельный тип, а не флаг у
         * {@link #ENEMIES_IN_CONE}: у двух конусов разные вершина и ось, и
         * спутать их ключом было бы проще, чем заметить разницу.
         */
        ENEMIES_IN_CONE_TO_CASTER(true, true),

        /**
         * Враги вокруг точки действия.
         *
         * <p>Если точка не задана, список пуст — и это сознательно. Молчаливый
         * откат к позиции кастера означал бы, что взрыв в точке попадания
         * иногда гремит под ногами у мага, и никто бы не понял почему.
         */
        ENEMIES_NEAR_ORIGIN(true, false),

        /** Все живые вокруг точки действия, без разбора своих и чужих. */
        ALL_NEAR_ORIGIN(true, false),

        /** Союзники вокруг точки действия, включая кастера и его призванных. */
        ALLIES_NEAR_ORIGIN(true, false),

        /**
         * Враги рядом с любой своей зоной заданного тега.
         *
         * <p>Нужен там, где важно, кто стоит на поле, а не кто стоит рядом с
         * магом: Коллапс тянет тех, кого накрыли печати, и именно поэтому
         * расстановка печатей что-то решает.
         */
        ENEMIES_NEAR_ZONE(true, false),

        /**
         * Свои призванные с заданной меткой.
         *
         * <p>Нужен там, где действует не сам игрок, а его копии: веер ножей
         * бросает каждая со своего места, подрыв гремит там, где она стоит.
         * В старом стеке их искали по типу существа и отсеивали чужих
         * сравнением имени хозяина в переменной — здесь владелец и так известен
         * реестру призванных.
         */
        OWN_MINIONS(false, false);

        private final boolean needsRadius;
        private final boolean needsAngle;

        Type(boolean needsRadius, boolean needsAngle) {
            this.needsRadius = needsRadius;
            this.needsAngle = needsAngle;
        }

        public boolean needsRadius() {
            return needsRadius;
        }

        public boolean needsAngle() {
            return needsAngle;
        }

        /** Бьёт ли этот тип по своим: от этого зависит защита призванных. */
        public boolean hitsAllies() {
            return this == SELF || this == ALLIES_IN_RADIUS || this == ALLIES_NEAR_ORIGIN;
        }

        /** Конус ли это: ему нужны угол и ось. */
        public boolean isCone() {
            return this == ENEMIES_IN_CONE || this == ENEMIES_IN_CONE_TO_CASTER;
        }

        /** Нужна ли этому типу точка действия. */
        public boolean needsOrigin() {
            return this == ENEMIES_NEAR_ORIGIN || this == ALL_NEAR_ORIGIN
                    || this == ALLIES_NEAR_ORIGIN || this == ENEMIES_IN_CONE_TO_CASTER;
        }

        /** Нужен ли этому типу тег зоны. */
        public boolean needsTag() {
            return this == ENEMIES_NEAR_ZONE || this == OWN_MINIONS;
        }
    }

    public static TargetSpec self() {
        return new TargetSpec(Type.SELF, null, null);
    }
}
