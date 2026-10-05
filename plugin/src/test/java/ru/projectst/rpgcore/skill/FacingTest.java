package ru.projectst.rpgcore.skill;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
