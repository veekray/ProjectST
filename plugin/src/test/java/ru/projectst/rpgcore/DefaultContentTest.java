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
