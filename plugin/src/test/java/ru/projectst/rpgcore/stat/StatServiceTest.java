package ru.projectst.rpgcore.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Кеш проверяется по тождеству объекта: если {@code snapshot()} вернул тот же
 * экземпляр, пересчёта не было. Это надёжнее, чем считать вызовы движка.
 */
class StatServiceTest {

    private static final UUID PLAYER = UUID.randomUUID();

    private static StatService service() {
        Map<String, StatDef> defs = new LinkedHashMap<>();
        defs.put("dmg", new StatDef("dmg", "dmg", 100, 0, 100_000, Rounding.NONE));
        defs.put("def", new StatDef("def", "def", 0, 0, 100_000, Rounding.NONE));
        return new StatService(new StatEngine(new StatRegistry(defs)));
    }

    @Test
    @DisplayName("повторный снимок берётся из кеша")
    void snapshotIsCached() {
        StatService s = service();
        assertSame(s.snapshot(PLAYER), s.snapshot(PLAYER));
    }

    @DisplayName("любое изменение сбрасывает кеш")
    @Test
    void anyChangeInvalidates() {
        StatService s = service();
        StatSnapshot first = s.snapshot(PLAYER);

        s.setSource(PLAYER, "item:sword", List.of(new StatModifier("dmg", StatOp.FLAT, 10, "item:sword")));
        StatSnapshot afterSource = s.snapshot(PLAYER);
        assertNotSame(first, afterSource);
        assertEquals(110, afterSource.get("dmg"), 1e-9);

        s.setBase(PLAYER, "dmg", 200);
        StatSnapshot afterBase = s.snapshot(PLAYER);
        assertNotSame(afterSource, afterBase);
        assertEquals(210, afterBase.get("dmg"), 1e-9);

        s.removeSource(PLAYER, "item:sword");
        StatSnapshot afterRemove = s.snapshot(PLAYER);
        assertNotSame(afterBase, afterRemove);
        assertEquals(200, afterRemove.get("dmg"), 1e-9);
    }

    @Test
    @DisplayName("снятие источника убирает ровно его надбавки")
    void removingSourceRemovesOnlyItsOwn()  {
        StatService s = service();
        s.setSource(PLAYER, "item:sword", List.of(new StatModifier("dmg", StatOp.FLAT, 10, "item:sword")));
        s.setSource(PLAYER, "status:rage", List.of(new StatModifier("dmg", StatOp.FLAT, 5, "status:rage")));

        s.removeSource(PLAYER, "item:sword");

        assertEquals(105, s.snapshot(PLAYER).get("dmg"), 1e-9);
        assertEquals(List.of("status:rage"), List.copyOf(s.sources(PLAYER)));
    }

    @Test
    @DisplayName("повторная установка источника заменяет его надбавки целиком")
    void settingSourceReplaces() {
        StatService s = service();
        s.setSource(PLAYER, "item:sword", List.of(new StatModifier("dmg", StatOp.FLAT, 10, "item:sword")));
        s.setSource(PLAYER, "item:sword", List.of(new StatModifier("dmg", StatOp.FLAT, 1, "item:sword")));

        assertEquals(101, s.snapshot(PLAYER).get("dmg"), 1e-9);
    }

    @Test
    @DisplayName("снятие несуществующего источника кеш не трогает")
    void removingMissingSourceKeepsCache() {
        StatService s = service();
        StatSnapshot first = s.snapshot(PLAYER);
        s.removeSource(PLAYER, "нет такого");
        assertSame(first, s.snapshot(PLAYER));
    }

    @Test
    @DisplayName("forget забывает игрока целиком")
    void forgetClearsEverything() {
        StatService s = service();
        s.setSource(PLAYER, "item:sword", List.of(new StatModifier("dmg", StatOp.FLAT, 10, "item:sword")));
        s.setBase(PLAYER, "dmg", 200);

        s.forget(PLAYER);

        assertEquals(100, s.snapshot(PLAYER).get("dmg"), 1e-9);
        assertEquals(List.of(), List.copyOf(s.sources(PLAYER)));
    }

    @Test
    @DisplayName("источник без имени отвергается: иначе надбавку нельзя снять")
    void blankSourceRejected() {
        StatService s = service();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> s.setSource(PLAYER, "  ", List.of()));
    }
}
