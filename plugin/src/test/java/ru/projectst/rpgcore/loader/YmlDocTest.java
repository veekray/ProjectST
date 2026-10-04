package ru.projectst.rpgcore.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проверяется не только факт ошибки, но и строка: именно отсутствие позиции
 * делало отладку старых конфигов дорогой.
 */
class YmlDocTest {

    private ContentErrors errors;

    private YmlDoc parse(String text) {
        errors = new ContentErrors();
        return YmlDoc.parse("test.yml", text, errors).orElseThrow();
    }

    @Test
    @DisplayName("неизвестный ключ — ошибка с точной строкой")
    void unknownKeyIsReportedWithLine() {
        YmlDoc doc = parse("""
                stats:
                  strength:
                    display: "Сила"
                    maxx: 10
                """);
        doc.root().map("stats").orElseThrow().map("strength").orElseThrow().str("display", "");
        doc.finish();

        assertEquals(1, errors.count(), () -> errors.all().toString());
        ContentError e = errors.all().get(0);
        assertEquals("неизвестный ключ", e.what());
        assertEquals("stats.strength.maxx", e.path());
        assertEquals(4, e.at().line(), "опечатка на четвёртой строке");
    }

    @Test
    @DisplayName("ключ, прочитанный со значением по умолчанию, неизвестным не считается")
    void readWithFallbackCountsAsRead() {
        YmlDoc doc = parse("""
                root:
                  present: 1
                """);
        YmlMap root = doc.root().map("root").orElseThrow();
        root.number("present", 0, 10);
        root.number("absent", 0, 10, 5);
        doc.finish();

        assertTrue(errors.isEmpty(), () -> errors.all().toString());
    }

    @Test
    @DisplayName("не-число вместо числа: ошибка называет полученное значение")
    void wrongTypeIsReported() {
        YmlDoc doc = parse("""
                root:
                  amount: abc
                """);
        doc.root().map("root").orElseThrow().number("amount", 0, 10);
        doc.finish();

        assertEquals(1, errors.count());
        assertTrue(errors.all().get(0).what().contains("abc"));
        assertEquals(2, errors.all().get(0).at().line());
    }

    @Test
    @DisplayName("значение вне диапазона называет сам диапазон")
    void outOfRangeIsReported() {
        YmlDoc doc = parse("""
                root:
                  amount: 50
                """);
        doc.root().map("root").orElseThrow().number("amount", 0, 10);
        doc.finish();

        assertEquals(1, errors.count());
        assertTrue(errors.all().get(0).what().contains("0.0..10.0"),
                errors.all().get(0).what());
    }

    @Test
    @DisplayName("отсутствие обязательного ключа — ошибка")
    void missingRequiredKey() {
        YmlDoc doc = parse("""
                root:
                  other: 1
                """);
        YmlMap root = doc.root().map("root").orElseThrow();
        root.str("name");
        root.number("other", 0, 10);
        doc.finish();

        assertEquals(1, errors.count());
        assertEquals("root.name", errors.all().get(0).path());
    }

    @Test
    @DisplayName("неизвестное значение перечисления показывает допустимые")
    void unknownEnumListsAllowed() {
        YmlDoc doc = parse("""
                root:
                  mode: sideways
                """);
        doc.root().map("root").orElseThrow()
                .enumOf("mode", TestMode.class, TestMode.FIRST);
        doc.finish();

        assertEquals(1, errors.count());
        String what = errors.all().get(0).what();
        assertTrue(what.contains("first") && what.contains("second"), what);
    }

    @Test
    @DisplayName("дубликат ключа не проходит молча")
    void duplicateKeyIsRejected() {
        ContentErrors errs = new ContentErrors();
        var parsed = YmlDoc.parse("test.yml", """
                root:
                  a: 1
                  a: 2
                """, errs);

        // Документ разбирается: мы собираем все ошибки за один проход, а не
        // падаем на первой. Важно, что дубликат назван и указана его строка.
        assertTrue(parsed.isPresent());
        assertEquals(1, errs.count());
        assertEquals("ключ объявлен дважды", errs.all().get(0).what());
        assertEquals(3, errs.all().get(0).at().line());
    }

    @Test
    @DisplayName("на верхнем уровне список вместо раздела — ошибка, а не пустой результат")
    void topLevelMustBeMapping() {
        ContentErrors errs = new ContentErrors();
        var parsed = YmlDoc.parse("test.yml", "- a\n- b\n", errs);

        assertTrue(parsed.isEmpty());
        assertEquals(1, errs.count());
    }

    private enum TestMode { FIRST, SECOND }
}
