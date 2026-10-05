package ru.projectst.rpgcore.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Зоны: печати, круги, ловушки.
 *
 * <p>Проверяется именно то, на чём ломались печати в старом стеке: зону видно
 * сразу после установки, срок её жизни решает один источник времени, снятие и
 * подсчёт происходят вместе, а чужая зона не считается своей.
 */
class ZoneServiceTest {

    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private long tick;

    private ZoneService service() {
        return new ZoneService(() -> tick);
    }

    private static Position at(double x, double z) {
        return new Position(WORLD, x, 64, z);
    }

    @Test
    @DisplayName("зона видна сразу после установки")
    void placedZoneIsVisibleImmediately() {
        ZoneService zones = service();

        zones.place("seal", OWNER, at(0, 0), 4, 200);

        assertEquals(1, zones.at(at(1, 1), "seal").size(),
                "именно это ломалось: установка шла на главном потоке, а проверка — асинхронно");
    }

    @Test
    @DisplayName("точка вне радиуса в зону не попадает")
    void radiusIsRespected() {
        ZoneService zones = service();
        zones.place("seal", OWNER, at(0, 0), 4, 200);

        assertTrue(zones.at(at(3.9, 0), "seal").isEmpty() == false);
        assertTrue(zones.at(at(4.1, 0), "seal").isEmpty());
    }

    @Test
    @DisplayName("зона из другого мира не считается, даже если координаты совпали")
    void worldMatters() {
        ZoneService zones = service();
        zones.place("seal", OWNER, at(0, 0), 10, 200);

        Position elsewhere = new Position(UUID.randomUUID(), 0, 64, 0);

        assertTrue(zones.at(elsewhere, "seal").isEmpty());
    }

    @Test
    @DisplayName("чужой тег не отвечает на запрос")
    void tagsDoNotMix() {
        ZoneService zones = service();
        zones.place("seal", OWNER, at(0, 0), 5, 200);

        assertTrue(zones.at(at(0, 0), "trap").isEmpty());
    }

    @Test
    @DisplayName("истёкшая зона исчезает при чтении, не дожидаясь таймера")
    void expiredZoneDisappearsOnRead() {
        ZoneService zones = service();
        zones.place("seal", OWNER, at(0, 0), 5, 40);

        tick += 39;
        assertEquals(1, zones.at(at(0, 0), "seal").size());

        tick += 1;
        assertTrue(zones.at(at(0, 0), "seal").isEmpty(),
                "иначе ответ зависел бы от того, успел ли пройти таймер");
    }

    @Test
    @DisplayName("снятие возвращает число снятых, и снимает именно их")
    void consumeCountsWhatItRemoved() {
        ZoneService zones = service();
        zones.place("seal", OWNER, at(0, 0), 3, 200);
        zones.place("seal", OWNER, at(1, 0), 3, 200);
        zones.place("seal", OWNER, at(30, 0), 3, 200);

        int taken = zones.consume(at(0, 0), 5, "seal", OWNER);

        assertEquals(2, taken);
        assertEquals(1, zones.size(), "дальняя печать осталась");
    }

    @Test
    @DisplayName("своё снятие не трогает чужие зоны")
    void consumeOwnOnly() {
        ZoneService zones = service();
        zones.place("seal", OWNER, at(0, 0), 3, 200);
        zones.place("seal", OTHER, at(0, 0), 3, 200);

        int taken = zones.consume(at(0, 0), 5, "seal", OWNER);

        assertEquals(1, taken);
        assertEquals(1, zones.size());
        assertTrue(zones.at(at(0, 0), "seal").get(0).ownedBy(OTHER));
    }

    @Test
    @DisplayName("без владельца снимаются все, включая чужие")
    void consumeAnyOwner() {
        ZoneService zones = service();
        zones.place("seal", OWNER, at(0, 0), 3, 200);
        zones.place("seal", OTHER, at(0, 0), 3, 200);

        assertEquals(2, zones.consume(at(0, 0), 5, "seal", null));
        assertEquals(0, zones.size());
    }

    @Test
    @DisplayName("выход владельца убирает его зоны, чужие остаются")
    void forgetOwnerRemovesOnlyOwn() {
        ZoneService zones = service();
        zones.place("seal", OWNER, at(0, 0), 3, 200);
        zones.place("seal", OWNER, at(9, 0), 3, 200);
        zones.place("seal", OTHER, at(0, 0), 3, 200);

        assertEquals(2, zones.forgetOwner(OWNER));
        assertEquals(1, zones.size());
    }

    @Test
    @DisplayName("своя зона отличается от чужой по владельцу, а не по совпадению")
    void ownershipIsExplicit() {
        ZoneService zones = service();
        Zone zone = zones.place("seal", OWNER, at(0, 0), 3, 200);

        assertTrue(zone.ownedBy(OWNER));
        assertFalse(zone.ownedBy(OTHER));
    }
}
