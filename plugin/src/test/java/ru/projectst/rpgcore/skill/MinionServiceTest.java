package ru.projectst.rpgcore.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Призванные существа.
 *
 * <p>Главное, что проверяется: «свой» определяется владельцем, а не типом
 * существа. Именно на этом ломался зверь друида — все волки на карте считались
 * своими для любого друида, потому что проверка смотрела на тип моба.
 */
class MinionServiceTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private long tick;

    private MinionService service() {
        return new MinionService(() -> tick);
    }

    @Test
    @DisplayName("владелец записан сразу при постановке на учёт")
    void ownerIsKnownImmediately() {
        MinionService minions = service();
        UUID wolf = UUID.randomUUID();

        minions.register(wolf, OWNER, "beast", 200, true);

        assertTrue(minions.of(wolf).orElseThrow().ownedBy(OWNER));
    }

    @Test
    @DisplayName("свой призванный защищён, чужой такой же — нет")
    void onlyOwnMinionIsProtected() {
        MinionService minions = service();
        UUID mine = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        minions.register(mine, OWNER, "beast", 200, true);
        minions.register(theirs, OTHER, "beast", 200, true);

        assertTrue(minions.isOwnMinion(OWNER, mine));
        assertFalse(minions.isOwnMinion(OWNER, theirs),
                "иначе бой двух друидов стал бы боем с неуязвимыми волками");
    }

    @Test
    @DisplayName("истёкшие возвращаются списком: их надо убрать из мира")
    void expiredAreReturned() {
        MinionService minions = service();
        UUID wolf = UUID.randomUUID();
        minions.register(wolf, OWNER, "beast", 40, true);

        tick += 39;
        assertTrue(minions.expired().isEmpty());

        tick += 1;
        assertEquals(java.util.List.of(wolf), minions.expired());
        assertTrue(minions.of(wolf).isEmpty());
    }

    @Test
    @DisplayName("тег разделяет призванных одного владельца")
    void tagSeparatesMinions() {
        MinionService minions = service();
        minions.register(UUID.randomUUID(), OWNER, "beast", 200, true);
        minions.register(UUID.randomUUID(), OWNER, "turret", 200, false);

        assertEquals(1, minions.ofOwner(OWNER, "beast").size());
        assertEquals(2, minions.ofOwner(OWNER).size());
    }

    @Test
    @DisplayName("при переполнении снимается самый старый, а не отклоняется новый")
    void limitEvictsOldest() {
        MinionService minions = service();
        UUID first = UUID.randomUUID();
        minions.register(first, OWNER, "beast", 100, true);
        for (int i = 1; i < MinionService.PER_OWNER_LIMIT; i++) {
            minions.register(UUID.randomUUID(), OWNER, "beast", 100 + i * 10, true);
        }

        var evicted = minions.register(UUID.randomUUID(), OWNER, "beast", 500, true);

        assertEquals(java.util.Optional.of(first), evicted,
                "отказ был бы хуже: навык сработал бы вполовину и промолчал");
        assertEquals(MinionService.PER_OWNER_LIMIT, minions.ofOwner(OWNER).size());
    }

    @Test
    @DisplayName("выход владельца снимает его призванных и возвращает их для удаления")
    void forgetOwnerReturnsEntities() {
        MinionService minions = service();
        UUID mine = UUID.randomUUID();
        minions.register(mine, OWNER, "beast", 200, true);
        minions.register(UUID.randomUUID(), OTHER, "beast", 200, true);

        assertEquals(java.util.List.of(mine), minions.forgetOwner(OWNER));
        assertEquals(1, minions.size(), "чужой остался");
    }

    @Test
    @DisplayName("снятый с учёта больше не защищён: запись не переживает смерть")
    void forgottenMinionIsNotProtected() {
        MinionService minions = service();
        UUID wolf = UUID.randomUUID();
        minions.register(wolf, OWNER, "beast", 200, true);

        assertTrue(minions.forget(wolf));

        assertFalse(minions.isOwnMinion(OWNER, wolf));
    }
}
