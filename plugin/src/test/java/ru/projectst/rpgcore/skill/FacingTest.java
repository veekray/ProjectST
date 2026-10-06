package ru.projectst.rpgcore.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Геометрия спины. Случаи подобраны так, что при перепутанных осях или знаке
 * поворота они дали бы противоположный ответ: «не падает» тут ничего не
 * доказывает, потому что в старом стеке эта же арифметика не падала и врала.
 */
class FacingTest {

    /** Ширина тыльного сектора по умолчанию в контенте. */
    private static final double ARC = 120;

    @Test
    @DisplayName("при нулевом повороте существо смотрит в сторону роста Z")
    void zeroYawLooksSouth() {
        // Стоим позади, то есть со стороны меньших Z.
        assertTrue(Facing.isBehind(0, 0, -3, ARC));
        // Стоим перед лицом.
        assertFalse(Facing.isBehind(0, 0, 3, ARC));
    }

    @Test
    @DisplayName("поворот на девяносто градусов смотрит в сторону убывания X")
    void ninetyYawLooksWest() {
        assertTrue(Facing.isBehind(90, 3, 0, ARC),
                "спина повёрнутого на девяносто смотрит в сторону роста X");
        assertFalse(Facing.isBehind(90, -3, 0, ARC));
    }

    @Test
    @DisplayName("сбоку — не со спины")
    void sideIsNotBehind() {
        assertFalse(Facing.isBehind(0, 3, 0, ARC));
        assertFalse(Facing.isBehind(0, -3, 0, ARC));
    }

    @Test
    @DisplayName("сектор задаётся целиком и отсчитывается от хвоста")
    void arcIsSplitAroundTheTail() {
        // Ровно на границе: 120 градусов это по 60 в каждую сторону, значит
        // точка под 120 градусами от взгляда входит, а под 119 — нет.
        assertTrue(Facing.isBehind(0, Math.sin(Math.toRadians(120)),
                -Math.cos(Math.toRadians(60)), ARC));
        assertFalse(Facing.isBehind(0, Math.sin(Math.toRadians(100)),
                Math.cos(Math.toRadians(100)) * -1, ARC));
    }

    @Test
    @DisplayName("отрицательный поворот работает так же, как положительный")
    void negativeYawBehavesTheSame() {
        assertTrue(Facing.isBehind(-90, -3, 0, ARC));
        assertFalse(Facing.isBehind(-90, 3, 0, ARC));
    }

    @Test
    @DisplayName("стоя ровно в цели, спины нет ни у кого")
    void sameSpotIsNeverBehind() {
        assertFalse(Facing.isBehind(0, 0, 0, 360));
    }

    // ------------------------------------------------------------------ ход

    /**
     * Направление сверяется с допуском, а не по равенству записей.
     *
     * <p>Синус девяноста градусов в двоичной арифметике не ровно единица, а
     * минус ноль не равен нулю: тест на точное равенство проверял бы
     * представление чисел, а не геометрию.
     */
    private static void assertHeading(double x, double z, Heading actual, String what) {
        assertNotNull(actual, what + ": направление должно быть");
        assertEquals(x, actual.x(), 1e-9, what + ": X");
        assertEquals(z, actual.z(), 1e-9, what + ": Z");
    }

    @Test
    @DisplayName("ход вперёд идёт туда же, куда взгляд")
    void forwardFollowsLook() {
        // Нулевой поворот смотрит на юг: это рост Z.
        assertHeading(0, 1, Facing.headingOf(0, 1, 0), "вперёд при нулевом повороте");
        // Поворот на девяносто смотрит на запад: это убыль X.
        assertHeading(-1, 0, Facing.headingOf(90, 1, 0), "вперёд при повороте на запад");
    }

    @Test
    @DisplayName("ход назад и влево — именно назад и влево, а не к взгляду")
    void backwardAndLeftAreThemselves() {
        assertHeading(0, -1, Facing.headingOf(0, -1, 0), "назад");
        // Смотрящий на юг игрок имеет слева от себя восток: это рост X.
        assertHeading(1, 0, Facing.headingOf(0, 0, 1), "влево");
        assertHeading(-1, 0, Facing.headingOf(0, 0, -1), "вправо");
    }

    @Test
    @DisplayName("по диагонали рывок не уносит дальше: длина всегда единичная")
    void diagonalIsNotLonger() {
        Heading diagonal = Facing.headingOf(0, 1, 1);

        double length = Math.sqrt(diagonal.x() * diagonal.x() + diagonal.z() * diagonal.z());
        assertEquals(1, length, 1e-9,
                "иначе вперёд-влево уносило бы на сорок процентов дальше, чем вперёд");
    }

    @Test
    @DisplayName("стоящий на месте не задаёт направления, и это не ошибка")
    void standingStillHasNoHeading() {
        assertNull(Facing.headingOf(0, 0, 0),
                "пусть решает тот, кто знает, чем заменить: рывок возьмёт взгляд");
    }
}
