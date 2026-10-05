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
 */
public record TargetSpec(Type type, NumberRef radius, NumberRef angle) {

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
         * Враги вокруг точки действия.
         *
         * <p>Если точка не задана, список пуст — и это сознательно. Молчаливый
         * откат к позиции кастера означал бы, что взрыв в точке попадания
         * иногда гремит под ногами у мага, и никто бы не понял почему.
         */
        ENEMIES_NEAR_ORIGIN(true, false),

        /** Все живые вокруг точки действия, без разбора своих и чужих. */
        ALL_NEAR_ORIGIN(true, false);

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

        /** Нужна ли этому типу точка действия. */
        public boolean needsOrigin() {
            return this == ENEMIES_NEAR_ORIGIN || this == ALL_NEAR_ORIGIN;
        }
    }

    public static TargetSpec self() {
        return new TargetSpec(Type.SELF, null, null);
    }
}
