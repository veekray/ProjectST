package ru.projectst.rpgcore.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.projectst.rpgcore.loader.ContentErrors;

/**
 * Загрузка статусов и — главное — проверка графа конфликтов до запуска.
 * Противоречие в отношениях должно ловиться командой, а не игроком в бою.
 */
class StatusDefLoaderTest {

    private ContentErrors errors;

    private StatusRegistry load(String yaml) {
        errors = new ContentErrors();
        return StatusDefLoader.load("statuses.yml", yaml, errors).orElseThrow();
    }

    @Test
    @DisplayName("корректный файл грузится без ошибок")
    void validFileLoads() {
        StatusRegistry r = load("""
                statuses:
                  stun:
                    category: control
                    duration: 40
                    priority: 10
                    exclusive: control
                    tags: [hard]
                  banish:
                    category: immunity
                    duration: 80
                    priority: 100
                    exclusive: control
                    suppresses: [stun]
                """);

        assertTrue(errors.isEmpty(), () -> errors.all().toString());
        assertEquals(2, r.size());
        StatusDef stun = r.require("stun");
        assertEquals(StatusCategory.CONTROL, stun.category());
        assertEquals(StatusCategory.CONTROL, stun.exclusiveWith());
        assertTrue(stun.hasTag("hard"));
        assertEquals(Set.of("stun"), r.require("banish").suppresses());
    }

    @Test
    @DisplayName("необязательные ключи берут значения по умолчанию")
    void defaultsApply() {
        StatusRegistry r = load("""
                statuses:
                  poison:
                    category: debuff
                """);

        assertTrue(errors.isEmpty(), () -> errors.all().toString());
        StatusDef poison = r.require("poison");
        assertEquals(40, poison.duration());
        assertEquals(1, poison.maxStacks());
        assertEquals(Stacking.REFRESH, poison.stacking());
        assertEquals(0, poison.priority());
    }

    @Test
    @DisplayName("отсутствие category — ошибка")
    void categoryRequired() {
        load("""
                statuses:
                  broken:
                    duration: 40
                """);

        assertEquals(1, errors.count(), () -> errors.all().toString());
        assertTrue(errors.all().get(0).what().contains("category"));
    }

    @Test
    @DisplayName("ссылка на несуществующий статус ловится при загрузке")
    void danglingReferenceIsCaught() {
        load("""
                statuses:
                  banish:
                    category: immunity
                    suppresses: [нет_такого]
                """);

        assertEquals(1, errors.count(), () -> errors.all().toString());
        assertTrue(errors.all().get(0).what().contains("несуществующий"),
                errors.all().get(0).what());
    }

    @Test
    @DisplayName("статус, подавляющий сам себя, отвергается")
    void selfSuppressionIsCaught() {
        load("""
                statuses:
                  weird:
                    category: debuff
                    suppresses: [weird]
                """);

        assertTrue(errors.all().stream().anyMatch(e -> e.what().contains("сам себя")),
                errors.all().toString());
    }

    @Test
    @DisplayName("взаимное подавление отвергается: исход зависел бы от порядка")
    void mutualSuppressionIsCaught() {
        load("""
                statuses:
                  a:
                    category: debuff
                    suppresses: [b]
                  b:
                    category: debuff
                    suppresses: [a]
                """);

        assertTrue(errors.all().stream().anyMatch(e -> e.what().contains("взаимное подавление")),
                errors.all().toString());
    }

    @Test
    @DisplayName("взаимная блокировка законна: кто успел, тот и стоит")
    void mutualBlockingIsAllowed() {
        load("""
                statuses:
                  a:
                    category: debuff
                    blocks: [b]
                  b:
                    category: debuff
                    blocks: [a]
                """);

        assertTrue(errors.isEmpty(), () -> errors.all().toString());
    }

    @Test
    @DisplayName("max-stacks без stacking: stacks отвергается")
    void maxStacksRequiresStacking() {
        load("""
                statuses:
                  souls:
                    category: buff
                    max-stacks: 5
                """);

        assertTrue(errors.all().stream().anyMatch(e -> e.what().contains("max-stacks")),
                errors.all().toString());
    }

    @Test
    @DisplayName("неизвестный ключ в статусе — ошибка с номером строки")
    void unknownKeyInStatus() {
        load("""
                statuses:
                  stun:
                    category: control
                    durration: 40
                """);

        assertEquals(1, errors.count(), () -> errors.all().toString());
        assertEquals("неизвестный ключ", errors.all().get(0).what());
        assertEquals(4, errors.all().get(0).at().line());
    }

    @Test
    @DisplayName("неизвестная категория перечисляет допустимые")
    void unknownCategoryListsAllowed() {
        load("""
                statuses:
                  stun:
                    category: контроль
                """);

        assertTrue(errors.all().stream().anyMatch(e -> e.what().contains("control")),
                errors.all().toString());
    }
}
