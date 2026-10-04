package ru.projectst.rpgcore.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Порядок применения надбавок — то, из-за чего в старом стеке «+20% не работали».
 * Поэтому он проверяется не одним тестом «работает», а случаями, которые при
 * неверном порядке дали бы разные числа.
 */
class StatEngineTest {

    private static StatEngine engine(StatDef... defs) {
        Map<String, StatDef> map = new java.util.LinkedHashMap<>();
        for (StatDef d : defs) {
            map.put(d.id(), d);
        }
        return new StatEngine(new StatRegistry(map));
    }

    private static StatDef plain(String id) {
        return new StatDef(id, id, 100, 0, 100_000, Rounding.NONE);
    }

    @Test
    @DisplayName("FLAT применяется до PERCENT")
    void flatBeforePercent() {
        StatEngine e = engine(plain("dmg"));
        double value = e.compute("dmg", null, List.of(
                new StatModifier("dmg", StatOp.FLAT, 10, "item"),
                new StatModifier("dmg", StatOp.PERCENT, 100, "status")));

        // (100 + 10) * 2 = 220. Если бы PERCENT шёл первым, вышло бы 210.
        assertEquals(220, value, 1e-9);
    }

    @Test
    @DisplayName("PERCENT суммируются, а не перемножаются")
    void percentsAdd() {
        StatEngine e = engine(plain("dmg"));
        double value = e.compute("dmg", null, List.of(
                new StatModifier("dmg", StatOp.PERCENT, 50, "a"),
                new StatModifier("dmg", StatOp.PERCENT, 50, "b")));

        // 100 * (1 + 1.0) = 200. При перемножении было бы 225.
        assertEquals(200, value, 1e-9);
    }

    @Test
    @DisplayName("MULT перемножаются и идут после PERCENT")
    void multsMultiplyAfterPercent() {
        StatEngine e = engine(plain("dmg"));
        double value = e.compute("dmg", null, List.of(
                new StatModifier("dmg", StatOp.PERCENT, 100, "a"),
                new StatModifier("dmg", StatOp.MULT, 1.5, "b"),
                new StatModifier("dmg", StatOp.MULT, 2, "c")));

        // 100 * 2 * 1.5 * 2 = 600
        assertEquals(600, value, 1e-9);
    }

    @Test
    @DisplayName("порядок объявления надбавок на результат не влияет")
    void orderOfModifiersDoesNotMatter() {
        StatEngine e = engine(plain("dmg"));
        List<StatModifier> forward = List.of(
                new StatModifier("dmg", StatOp.FLAT, 10, "a"),
                new StatModifier("dmg", StatOp.PERCENT, 30, "b"),
                new StatModifier("dmg", StatOp.MULT, 1.2, "c"));
        List<StatModifier> reversed = List.of(
                new StatModifier("dmg", StatOp.MULT, 1.2, "c"),
                new StatModifier("dmg", StatOp.PERCENT, 30, "b"),
                new StatModifier("dmg", StatOp.FLAT, 10, "a"));

        assertEquals(e.compute("dmg", null, forward), e.compute("dmg", null, reversed), 1e-9);
    }

    @Test
    @DisplayName("ограничение диапазоном применяется последним")
    void clampIsLast() {
        StatEngine e = engine(new StatDef("res", "res", 0, 0, 75, Rounding.NONE));
        double value = e.compute("res", null, List.of(
                new StatModifier("res", StatOp.FLAT, 200, "item")));

        assertEquals(75, value, 1e-9);
    }

    @Test
    @DisplayName("округление применяется после ограничения")
    void roundingAfterClamp() {
        StatEngine e = engine(new StatDef("lvl", "lvl", 0, 0, 10.4, Rounding.FLOOR));
        double value = e.compute("lvl", null, List.of(
                new StatModifier("lvl", StatOp.FLAT, 99, "x")));

        assertEquals(10, value, 1e-9);
    }

    @Test
    @DisplayName("надбавки к чужим статам игнорируются")
    void foreignModifiersIgnored() {
        StatEngine e = engine(plain("dmg"), plain("def"));
        double value = e.compute("dmg", null, List.of(
                new StatModifier("def", StatOp.FLAT, 500, "item")));

        assertEquals(100, value, 1e-9);
    }

    @Test
    @DisplayName("база из класса перекрывает базу определения")
    void explicitBaseWins() {
        StatEngine e = engine(plain("dmg"));
        assertEquals(50, e.compute("dmg", 50.0, List.of()), 1e-9);
    }

    @Test
    @DisplayName("необъявленный стат — исключение, а не тихий ноль")
    void unknownStatThrows() {
        StatEngine e = engine(plain("dmg"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> e.compute("nope", null, List.of()));
    }

    @Test
    @DisplayName("снимок считает все объявленные статы")
    void snapshotCoversAllStats() {
        StatEngine e = engine(plain("dmg"), plain("def"));
        StatSnapshot snap = e.computeAll(Map.of("dmg", 10.0), List.of(
                new StatModifier("def", StatOp.FLAT, 5, "item")));

        assertEquals(10, snap.get("dmg"), 1e-9);
        assertEquals(105, snap.get("def"), 1e-9);
    }
}
