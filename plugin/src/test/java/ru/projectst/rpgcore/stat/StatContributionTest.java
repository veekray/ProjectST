package ru.projectst.rpgcore.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatContributionTest {

    private static StatDef rating(String id, double cap, boolean inverted) {
        return new StatDef(id, id, 0, -100, 10000, Rounding.NONE,
                new StatEffect(cap, "N% эффекта", inverted));
    }

    @Test
    @DisplayName("вклад считается кривой: те же 100 рейтинга дают меньше поверх уже набранных")
    void curveDecides() {
        StatDef speed = rating("movement_speed", 100, false);

        var fromZero = StatContribution.of(speed, 100, 0);
        var onTop = StatContribution.of(speed, 300, 200);

        assertEquals(50, fromZero.amount(), 1e-9, "100 при cap 100 — это 50%");
        assertEquals("+50%", fromZero.text());
        assertEquals(75 - 66.6667, onTop.amount(), 1e-3);
        assertEquals("+8.3%", onTop.text(), "мелкий вклад — с одним знаком после точки");
        assertTrue(onTop.good());
    }

    @Test
    @DisplayName("просевшая защита — минус и плохо, хотя фраза защиты перевёрнута")
    void invertedKeepsDirection() {
        StatDef defense = rating("physical_defense", 100, true);

        var down = StatContribution.of(defense, 0, 25);

        assertEquals(-20, down.amount(), 1e-9);
        assertEquals("-20%", down.text(), "под значком защиты «+20%» читалось бы как рост");
        assertFalse(down.good());
    }

    @Test
    @DisplayName("у стата без кривой вклад в его единицах, без процентов")
    void plainStatInUnits() {
        StatDef health = new StatDef("max_health", "Здоровье", 20, 1, 1000, Rounding.NONE);

        var bonus = StatContribution.of(health, 26, 20);

        assertEquals("+6", bonus.text());
    }

    @Test
    @DisplayName("нулевой вклад не показывается: «+0%» ничего не говорит")
    void zeroIsHidden() {
        StatDef speed = rating("movement_speed", 100, false);

        assertEquals("", StatContribution.of(speed, 10.01, 10).text());
    }
}
