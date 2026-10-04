package ru.projectst.rpgcore.skill;

/**
 * Кого затрагивает шаг навыка.
 *
 * <p>Набор намеренно маленький и закрытый. Когда понадобится новый способ
 * выбора целей, он добавляется сюда и в исполнитель — то есть компилятором, а
 * не очередной строкой в конфиге, которая может ничего не делать. Это прямое
 * следствие того, как в старом стеке таргетеры молча отбрасывали цели на трёх
 * разных стадиях.
 *
 * @param type   способ выбора
 * @param radius радиус или дальность, где применимо
 * @param angle  угол конуса в градусах, только для {@link Type#ENEMIES_IN_CONE}
 */
public record TargetSpec(Type type, NumberRef radius, NumberRef angle) {

    public enum Type {
        /** Сам кастер. */
        SELF,
        /** Враги вокруг кастера. */
        ENEMIES_IN_RADIUS,
        /** Союзники вокруг кастера, включая самого кастера. */
        ALLIES_IN_RADIUS,
        /** Враги в конусе по направлению взгляда. */
        ENEMIES_IN_CONE;

        public boolean needsRadius() {
            return this != SELF;
        }

        public boolean needsAngle() {
            return this == ENEMIES_IN_CONE;
        }
    }

    public static TargetSpec self() {
        return new TargetSpec(Type.SELF, null, null);
    }
}
