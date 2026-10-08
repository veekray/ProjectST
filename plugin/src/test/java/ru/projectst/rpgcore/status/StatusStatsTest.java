package ru.projectst.rpgcore.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.projectst.rpgcore.stat.Rounding;
import ru.projectst.rpgcore.stat.StatDef;
import ru.projectst.rpgcore.stat.StatEngine;
import ru.projectst.rpgcore.stat.StatOp;
import ru.projectst.rpgcore.stat.StatRegistry;
import ru.projectst.rpgcore.stat.StatService;

/**
 * Надбавка живёт сроком статуса: уходит с ним по любой причине и не ложится
 * без него.
 */
class StatusStatsTest {

    private static final UUID TARGET = UUID.randomUUID();

    private long tick;
    private StatusService statuses;
    private StatService stats;
    private StatusStats links;

    @BeforeEach
    void setUp() {
        tick = 0;
        Map<String, StatusDef> defs = new LinkedHashMap<>();
        defs.put("veil", def("veil", Set.of(), Set.of()));
        defs.put("dispel", def("dispel", Set.of(), Set.of("veil")));
        defs.put("hush", new StatusDef("hush", StatusCategory.DEBUFF, 40, 1, Stacking.REFRESH,
                0, null, Set.of("veil"), Set.of(), Set.of(), Set.of()));
        defs.put("ward", new StatusDef("ward", StatusCategory.BUFF, 40, 1, Stacking.REFRESH,
                0, null, Set.of(), Set.of(), Set.of("veil"), Set.of()));
        statuses = new StatusService(new StatusRegistry(defs), () -> tick);

        Map<String, StatDef> statDefs = new LinkedHashMap<>();
        statDefs.put("speed", new StatDef("speed", "Скорость", 0, -100, 1000, Rounding.NONE));
        stats = new StatService(new StatEngine(new StatRegistry(statDefs)));
        links = new StatusStats(statuses, stats);
    }

    private static StatusDef def(String id, Set<String> suppresses, Set<String> removes) {
        return new StatusDef(id, StatusCategory.BUFF, 40, 1, Stacking.REFRESH, 0, null,
                suppresses, removes, Set.of(), Set.of());
    }

    private double speed() {
        return stats.snapshot(TARGET).getOrZero("speed");
    }

    @Test
    @DisplayName("надбавка ложится вместе со статусом и уходит, когда он истёк")
    void endsWithExpiry() {
        statuses.apply(TARGET, StatusApplication.of("veil", "test"));
        assertTrue(links.attach(TARGET, "veil", "speed", StatOp.FLAT, 30));
        assertEquals(30, speed(), 1e-9);

        tick = 39;
        links.syncAll();
        assertEquals(30, speed(), 1e-9, "до конца срока надбавка на месте");

        tick = 40;
        links.syncAll();
        assertEquals(0, speed(), 1e-9, "статус истёк — и надбавки нет");
        assertTrue(links.of(TARGET, "veil").isEmpty());
    }

    @Test
    @DisplayName("снятый и вытесненный статус уносит надбавку с собой")
    void endsWithRemoval() {
        statuses.apply(TARGET, StatusApplication.of("veil", "test"));
        links.attach(TARGET, "veil", "speed", StatOp.FLAT, 30);

        statuses.apply(TARGET, StatusApplication.of("dispel", "test"));
        links.syncAll();
        assertEquals(0, speed(), 1e-9, "removes снял статус — снята и надбавка");

        statuses.apply(TARGET, StatusApplication.of("veil", "test"));
        links.attach(TARGET, "veil", "speed", StatOp.FLAT, 30);
        statuses.remove(TARGET, "veil");
        links.syncAll();
        assertEquals(0, speed(), 1e-9);
    }

    @Test
    @DisplayName("подавленный статус надбавку не даёт, но вернёт, когда подавление спадёт")
    void suppressionPausesIt() {
        statuses.apply(TARGET, StatusApplication.of("veil", "test"));
        links.attach(TARGET, "veil", "speed", StatOp.FLAT, 30);

        statuses.apply(TARGET, StatusApplication.of("hush", 10, "test"));
        links.syncAll();
        assertEquals(0, speed(), 1e-9);

        tick = 10;
        links.syncAll();
        assertEquals(30, speed(), 1e-9, "подавление кончилось, статус ещё лежит");
    }

    @Test
    @DisplayName("заблокированный статус не лёг — не ложится и надбавка")
    void noStatusNoBonus() {
        statuses.apply(TARGET, StatusApplication.of("ward", "test"));
        statuses.apply(TARGET, StatusApplication.of("veil", "test"));

        assertFalse(links.attach(TARGET, "veil", "speed", StatOp.FLAT, 30));
        assertEquals(0, speed(), 1e-9);
    }

    @Test
    @DisplayName("повторное наложение заменяет надбавку, а не складывает")
    void reapplyReplaces() {
        statuses.apply(TARGET, StatusApplication.of("veil", "test"));
        links.attach(TARGET, "veil", "speed", StatOp.FLAT, 30);
        statuses.apply(TARGET, StatusApplication.of("veil", "test"));
        links.attach(TARGET, "veil", "speed", StatOp.FLAT, 50);

        assertEquals(50, speed(), 1e-9);
        assertEquals(1, links.of(TARGET, "veil").size());
    }

    @Test
    @DisplayName("забытая цель уносит и надбавки")
    void forgottenTarget() {
        statuses.apply(TARGET, StatusApplication.of("veil", "test"));
        links.attach(TARGET, "veil", "speed", StatOp.FLAT, 30);

        statuses.forget(TARGET);
        links.syncAll();

        assertEquals(0, speed(), 1e-9);
    }
}
