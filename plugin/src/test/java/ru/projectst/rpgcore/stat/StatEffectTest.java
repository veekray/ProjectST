package ru.projectst.rpgcore.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.projectst.rpgcore.loader.ContentErrors;

/**
 * Кривая рейтинга: во что превращается число стата.
 *
 * <p>Случаи подобраны так, чтобы при подмене кривой линейной арифметикой они
 * дали другой ответ. «Не падает» здесь не доказывает ничего: до кривой стат
 * прямо равнялся проценту и тоже не падал — он просто упирался в потолок,
 * после которого следующий пункт стата молча переставал что-либо значить.
 */
class StatEffectTest {

    private static final StatEffect HUNDRED = new StatEffect(100, "N% урона", false);

    @Test
    @DisplayName("рейтинг, равный потолку, даёт ровно половину потолка")
    void halfAtCap() {
        assertEquals(50, HUNDRED.percent(100), 1e-9);
        assertEquals(30, new StatEffect(60, "N%", false).percent(60), 1e-9);
    }

    @Test
    @DisplayName("у нуля наклон единичный: первые пункты дают почти столько же процентов")
    void slopeAtZeroIsOne() {
        // Единица рейтинга — почти процент: старые числа контента, писавшиеся
        // процентами, в малых значениях продолжают значить то же самое.
        assertEquals(0.99, HUNDRED.percent(1), 0.02);
        assertEquals(4.8, HUNDRED.percent(5), 0.05);
    }

    @Test
    @DisplayName("каждый следующий пункт даёт меньше предыдущего")
    void diminishingReturns() {
        double first = HUNDRED.percent(100) - HUNDRED.percent(0);
        double second = HUNDRED.percent(200) - HUNDRED.percent(100);
        double third = HUNDRED.percent(300) - HUNDRED.percent(200);

        assertTrue(first > second && second > third,
                "иначе вторая сотня рейтинга стоила бы столько же, сколько первая");
    }

    @Test
    @DisplayName("потолка кривая не достигает ни при каком рейтинге")
    void capIsNeverReached() {
        assertTrue(HUNDRED.percent(1_000_000) < 100);
        assertTrue(HUNDRED.percent(1_000_000) > 99, "но подходит к нему вплотную");
        assertTrue(new StatEffect(50, "N%", false).percent(1_000_000) < 50,
                "ста процентов уклонения не бывает — это и есть смысл потолка");
    }

    @Test
    @DisplayName("ниже нуля кривая линейна и ограничена тем же потолком")
    void negativeIsLinearAndBounded() {
        assertEquals(-25, HUNDRED.percent(-25), 1e-9, "минус двадцать пять — минус четверть");
        assertEquals(-100, HUNDRED.percent(-500), 1e-9, "хуже потолка не бывает и вниз");
    }

    @Test
    @DisplayName("доля и процент — одно и то же число в разных единицах")
    void shareIsPercentOverHundred() {
        assertEquals(HUNDRED.percent(300) / 100, HUNDRED.share(300), 1e-12);
    }

    // ------------------------------------------------------------------ слова

    @Test
    @DisplayName("фраза получает процент со знаком")
    void noteCarriesTheSign() {
        assertEquals("+50% урона", HUNDRED.note(100));
        assertEquals("-25% урона", HUNDRED.note(-25));
    }

    @Test
    @DisplayName("перевёрнутый эффект разворачивает знак, а не величину")
    void invertedFlipsOnlyTheSign() {
        StatEffect defense = new StatEffect(100, "N% получаемого урона", true);

        assertEquals("-50% получаемого урона", defense.note(100));
        assertEquals("+25% получаемого урона", defense.note(-25),
                "отрицательная защита — это уязвимость, и сказать нужно именно так");
        assertEquals(0.5, defense.share(100), 1e-9,
                "на расчёт боя переворот не влияет: конвейер берёт саму кривую");
    }

    @Test
    @DisplayName("нулевой процент не показывается вовсе")
    void zeroIsSilent() {
        assertEquals("", HUNDRED.note(0));
        assertEquals("", HUNDRED.note(0.2), "«+0%» занимает строку и не говорит ничего");
    }

    @Test
    @DisplayName("эффект без места для числа не создаётся")
    void textMustHaveTheMark() {
        assertThrows(IllegalArgumentException.class,
                () -> new StatEffect(100, "много урона", false));
        assertThrows(IllegalArgumentException.class, () -> new StatEffect(0, "N%", false));
    }

    // ------------------------------------------------------------------ файл

    private static StatRegistry load(String text, ContentErrors errors) {
        return StatDefLoader.load("stats.yml", text, errors).orElseThrow();
    }

    @Test
    @DisplayName("кривая читается из файла стата")
    void loadedFromFile() {
        ContentErrors errors = new ContentErrors();
        StatRegistry registry = load("""
                stats:
                  dodge_rating:
                    display: "Уклонение"
                    max: 10000
                    effect:
                      cap: 50
                      text: "N% ударов уходит в пустоту"
                """, errors);

        assertTrue(errors.isEmpty(), () -> errors.all().toString());
        assertEquals(25, registry.percent("dodge_rating", 50), 1e-9);
        assertEquals("+25% ударов уходит в пустоту",
                registry.find("dodge_rating").orElseThrow().note(50));
    }

    @Test
    @DisplayName("стат без кривой остаётся процентами напрямую")
    void withoutEffectValueIsPercent() {
        ContentErrors errors = new ContentErrors();
        StatRegistry registry = load("""
                stats:
                  max_health:
                    display: "Здоровье"
                    base: 20
                    max: 1000
                """, errors);

        assertTrue(errors.isEmpty(), () -> errors.all().toString());
        assertEquals(0.4, registry.share("max_health", 40), 1e-9,
                "так вёл себя весь конвейер до появления кривых, и так он ведёт себя"
                        + " со статами, которым кривая не нужна");
        assertEquals("", registry.find("max_health").orElseThrow().note(40),
                "сто процентов здоровья — не величина, а бессмыслица");
    }

    @Test
    @DisplayName("незнакомый стат считается процентами, а не роняет расчёт")
    void unknownStatIsPercent() {
        assertEquals(0.25, new StatRegistry(java.util.Map.of()).share("ничего", 25), 1e-9);
    }

    @Test
    @DisplayName("кривая без фразы названа ошибкой с номером строки")
    void effectWithoutTextIsRefused() {
        ContentErrors errors = new ContentErrors();
        load("""
                stats:
                  dodge_rating:
                    max: 100
                    effect:
                      cap: 50
                """, errors);

        assertFalse(errors.isEmpty(), "показывать игроку было бы нечего");
        assertTrue(errors.all().get(0).what().contains("фраза"),
                () -> errors.all().toString());
    }

    @Test
    @DisplayName("фраза без буквы N названа ошибкой: число некуда поставить")
    void textWithoutMarkIsRefused() {
        ContentErrors errors = new ContentErrors();
        load("""
                stats:
                  dodge_rating:
                    max: 100
                    effect:
                      cap: 50
                      text: "уклонение растёт"
                """, errors);

        assertFalse(errors.isEmpty());
        assertTrue(errors.all().get(0).what().contains("N"), () -> errors.all().toString());
    }
}
