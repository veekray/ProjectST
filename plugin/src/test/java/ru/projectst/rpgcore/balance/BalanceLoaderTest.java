package ru.projectst.rpgcore.balance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.projectst.rpgcore.loader.ContentErrors;

class BalanceLoaderTest {

    private ContentErrors errors;

    private BalanceBook load(String yaml) {
        errors = new ContentErrors();
        return BalanceLoader.load("balance.yml", yaml, errors).orElseThrow();
    }

    @Test
    @DisplayName("константы и кривые читаются из одного файла")
    void constantsAndCurves() {
        BalanceBook book = load("""
                balance:
                  mage_mana_bolt:
                    damage: 6
                    cooldown:
                      base: 4.0
                      per-level: -0.2
                      min: 1.6
                """);

        assertTrue(errors.isEmpty(), () -> errors.all().toString());
        BalanceTable table = book.table("mage_mana_bolt");
        assertEquals(6, table.require("damage", 1), 1e-9);
        assertEquals(6, table.require("damage", 50), 1e-9, "константа не зависит от уровня");
        assertEquals(4.0, table.require("cooldown", 1), 1e-9);
        assertEquals(3.0, table.require("cooldown", 6), 1e-9, "4.0 - 0.2 * 5");
    }

    @Test
    @DisplayName("кривая ограничивается снизу: иначе перезарядка уйдёт в минус")
    void curveRespectsFloor() {
        BalanceBook book = load("""
                balance:
                  s:
                    cooldown:
                      base: 4.0
                      per-level: -0.2
                      min: 1.6
                """);

        assertEquals(1.6, book.table("s").require("cooldown", 99), 1e-9);
    }

    @Test
    @DisplayName("уровень ниже первого считается первым")
    void levelFloor() {
        BalanceBook book = load("""
                balance:
                  s:
                    v:
                      base: 10
                      per-level: 5
                """);

        assertEquals(10, book.table("s").require("v", 0), 1e-9);
        assertEquals(10, book.table("s").require("v", 1), 1e-9);
        assertEquals(15, book.table("s").require("v", 2), 1e-9);
    }

    @Test
    @DisplayName("мусор вместо числа — ошибка с позицией")
    void garbageIsReported() {
        load("""
                balance:
                  s:
                    damage: много
                """);

        assertEquals(1, errors.count(), () -> errors.all().toString());
        assertTrue(errors.all().get(0).what().contains("много"));
        assertEquals(3, errors.all().get(0).at().line());
    }

    @Test
    @DisplayName("min больше max в кривой отвергается")
    void brokenCurveRejected() {
        load("""
                balance:
                  s:
                    v:
                      base: 1
                      min: 10
                      max: 1
                """);

        assertTrue(errors.all().stream().anyMatch(e -> e.what().contains("min больше max")),
                errors.all().toString());
    }

    @Test
    @DisplayName("неизвестный ключ внутри кривой ловится")
    void unknownCurveKey() {
        load("""
                balance:
                  s:
                    v:
                      base: 1
                      per_level: 2
                """);

        assertTrue(errors.all().stream().anyMatch(e -> e.what().equals("неизвестный ключ")),
                errors.all().toString());
    }

    @Test
    @DisplayName("навык без раздела баланса даёт пустую таблицу, а не падение")
    void missingTableIsEmpty() {
        BalanceBook book = load("balance: {}\n");

        assertTrue(book.table("нет_такого").keys().isEmpty());
    }

    @Test
    @DisplayName("промах по ключу — исключение: к исполнению ссылки уже проверены")
    void missingKeyThrows() {
        BalanceBook book = load("""
                balance:
                  s:
                    damage: 1
                """);

        assertThrows(IllegalArgumentException.class, () -> book.table("s").require("нет", 1));
    }
}
