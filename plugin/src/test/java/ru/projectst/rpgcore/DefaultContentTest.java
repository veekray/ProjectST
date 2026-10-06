package ru.projectst.rpgcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.projectst.rpgcore.damage.StatIds;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.stat.StatDefLoader;
import ru.projectst.rpgcore.stat.StatRegistry;
import ru.projectst.rpgcore.status.StatusDefLoader;
import ru.projectst.rpgcore.status.StatusRegistry;

/**
 * Проверяет статы и статусы, которые плагин кладёт при первом запуске.
 *
 * <p>Навыки и классы целиком проверяет {@link ShippedContentTest}: он читает
 * файлы с диска, поэтому новый навык попадает под проверку самим фактом своего
 * существования. Здесь остались утверждения про сами статы и граф статусов.
 *
 * <p>Без этого теста опечатку в поставляемом stats.yml обнаружил бы только
 * сервер при старте — то есть уже у пользователя. А раз загрузчик строгий,
 * любая опечатка там гарантированно отключила бы весь домен.
 */
class DefaultContentTest {

    private static String resource(String name) throws IOException {
        try (InputStream in = DefaultContentTest.class.getClassLoader()
                .getResourceAsStream(name)) {
            assertTrue(in != null, "ресурс не найден: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("поставляемый stats.yml грузится без ошибок")
    void defaultStatsAreValid() throws IOException {
        ContentErrors errors = new ContentErrors();
        StatRegistry registry = StatDefLoader
                .load("stats.yml", resource("stats.yml"), errors).orElseThrow();

        assertTrue(errors.isEmpty(), () -> errors.all().toString());
        assertTrue(registry.size() >= 10, "статов должно быть объявлено заметно больше нуля");
    }

    @Test
    @DisplayName("конвейер урона находит все статы, которые он читает")
    void damagePipelineStatsExist() throws IOException {
        ContentErrors errors = new ContentErrors();
        StatRegistry registry = StatDefLoader
                .load("stats.yml", resource("stats.yml"), errors).orElseThrow();

        for (String id : new String[] {
                StatIds.SKILL_DAMAGE, StatIds.PHYSICAL_DAMAGE, StatIds.MAGIC_DAMAGE,
                StatIds.CRIT_CHANCE, StatIds.CRIT_POWER,
                StatIds.PHYSICAL_DEFENSE, StatIds.MAGIC_DEFENSE, StatIds.GENERAL_DEFENSE}) {
            assertTrue(registry.has(id), "в stats.yml нет стата " + id
                    + ", который читает конвейер урона");
        }
    }

    @Test
    @DisplayName("статы запасов объявлены: мана, сила духа и выносливость")
    void resourceStatsExist() throws IOException {
        ContentErrors errors = new ContentErrors();
        StatRegistry registry = StatDefLoader
                .load("stats.yml", resource("stats.yml"), errors).orElseThrow();

        // Выносливость читается у каждого игрока на каждом тике восстановления,
        // независимо от класса. Её отсутствие в файле — это не «нет стата», а
        // исключение при каждом касте и каждом тике.
        for (String id : new String[] {
                StatIds.MAX_MANA, StatIds.MANA_REGEN,
                StatIds.MAX_SPIRIT, StatIds.SPIRIT_REGEN,
                StatIds.MAX_STAMINA, StatIds.STAMINA_REGEN}) {
            assertTrue(registry.has(id), "в stats.yml нет стата запаса " + id);
        }
    }

    @Test
    @DisplayName("каждый процентный стат объявляет кривую: иначе он молча остался бы линейным")
    void percentStatsDeclareCurves() throws IOException {
        ContentErrors errors = new ContentErrors();
        StatRegistry registry = StatDefLoader
                .load("stats.yml", resource("stats.yml"), errors).orElseThrow();

        // Это те статы, которые движок читает как долю: урон, крит, защиты,
        // уклонение, перезарядка, вампиризм, скорости, радиус, эффекты. Стат без
        // кривой считается процентами напрямую — и тогда он упирается в потолок,
        // после которого следующий пункт молча ничего не значит. Забыть кривую
        // новому стату легко, поэтому список здесь, а не в чьей-то памяти.
        for (String id : new String[] {
                StatIds.SKILL_DAMAGE, StatIds.PHYSICAL_DAMAGE, StatIds.MAGIC_DAMAGE,
                StatIds.CRIT_CHANCE, StatIds.CRIT_POWER,
                StatIds.PHYSICAL_DEFENSE, StatIds.MAGIC_DEFENSE, StatIds.GENERAL_DEFENSE,
                StatIds.EFFECT_POWER, StatIds.EFFECT_DURATION, StatIds.COOLDOWN_REDUCTION,
                StatIds.DODGE_RATING, StatIds.INCOMING_HEALING, StatIds.ATTACK_SPEED,
                StatIds.SKILL_RADIUS, StatIds.LIFESTEAL, StatIds.MOVEMENT_SPEED}) {
            var def = registry.find(id).orElseThrow(() -> new AssertionError(
                    "в stats.yml нет стата " + id));
            assertTrue(def.effect() != null,
                    "стат " + id + " читается как проценты, но кривой не объявил:"
                            + " в меню он останется без пояснения, а в бою — без"
                            + " убывающей отдачи");
        }
    }

    @Test
    @DisplayName("запасы кривой не объявляют: сто процентов маны — не величина")
    void poolsHaveNoCurve() throws IOException {
        ContentErrors errors = new ContentErrors();
        StatRegistry registry = StatDefLoader
                .load("stats.yml", resource("stats.yml"), errors).orElseThrow();

        for (String id : new String[] {
                StatIds.MAX_HEALTH, StatIds.MAX_MANA, StatIds.MANA_REGEN,
                StatIds.MAX_SPIRIT, StatIds.SPIRIT_REGEN,
                StatIds.MAX_STAMINA, StatIds.STAMINA_REGEN}) {
            assertEquals(null, registry.find(id).orElseThrow().effect(),
                    "у стата " + id + " процентов не бывает, и подсказка о нём должна молчать");
        }
    }

    @Test
    @DisplayName("подсказка о статах читается как фраза, а не как набор чисел")
    void notesReadLikeSentences() throws IOException {
        ContentErrors errors = new ContentErrors();
        StatRegistry registry = StatDefLoader
                .load("stats.yml", resource("stats.yml"), errors).orElseThrow();

        // Сто пятьдесят рейтинга защиты — это шестьдесят процентов, и сказано
        // это должно быть со стороны игрока: урона он получает меньше.
        assertEquals("-60% получаемого физического урона",
                registry.find(StatIds.PHYSICAL_DEFENSE).orElseThrow().note(150));
        // Отрицательная защита — уязвимость, и фраза разворачивается сама.
        assertEquals("+25% получаемого физического урона",
                registry.find(StatIds.PHYSICAL_DEFENSE).orElseThrow().note(-25));
        assertEquals("+50% к физическому урону",
                registry.find(StatIds.PHYSICAL_DAMAGE).orElseThrow().note(200 / 3.0));
        assertEquals("", registry.find(StatIds.MAX_HEALTH).orElseThrow().note(24));
    }

    @Test
    @DisplayName("поставляемый statuses.yml грузится без ошибок, граф конфликтов цел")
    void defaultStatusesAreValid() throws IOException {
        ContentErrors errors = new ContentErrors();
        StatusRegistry registry = StatusDefLoader
                .load("statuses.yml", resource("statuses.yml"), errors).orElseThrow();

        assertTrue(errors.isEmpty(), () -> errors.all().toString());
        assertTrue(registry.has("stun"));
        assertTrue(registry.has("banish"));
    }

    @Test
    @DisplayName("изъятие из боя сильнее любого контроля")
    void banishOutranksControls() throws IOException {
        ContentErrors errors = new ContentErrors();
        StatusRegistry registry = StatusDefLoader
                .load("statuses.yml", resource("statuses.yml"), errors).orElseThrow();

        int banish = registry.require("banish").priority();
        assertTrue(banish > registry.require("stun").priority(), "иначе стан вытеснит кокон");
        assertTrue(banish > registry.require("root").priority());
        assertTrue(registry.require("banish").suppresses().contains("stun"));
    }

    @Test
    @DisplayName("счётчик объявлен как стакающийся: иначе max-stacks не имел бы смысла")
    void chargeStacks() throws IOException {
        ContentErrors errors = new ContentErrors();
        StatusRegistry registry = StatusDefLoader
                .load("statuses.yml", resource("statuses.yml"), errors).orElseThrow();

        assertEquals(5, registry.require("charge").maxStacks());
    }

    @Test
    @DisplayName("базовые статы класса объявлены в stats.yml")
    void classStatsExist() throws IOException {
        ContentErrors errors = new ContentErrors();
        var mage = ru.projectst.rpgcore.classes.ClassDefLoader
                .load("mage.yml", resource("classes/mage.yml"), errors).orElseThrow();
        var stats = StatDefLoader
                .load("stats.yml", resource("stats.yml"), errors).orElseThrow();

        for (String statId : mage.statCurves().keySet()) {
            assertTrue(stats.has(statId),
                    "класс даёт базу стату " + statId + ", которого нет в stats.yml");
        }
    }
}
