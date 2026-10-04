package ru.projectst.rpgcore.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerDataStoreTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static PlayerData sample() {
        PlayerData data = new PlayerData(ID);
        data.setClassId("mage");
        data.setLevel(17);
        data.setXp(1234.5);
        data.setUnspentPoints(3);
        data.unlockedSkills().add("mage_mana_bolt");
        data.unlockedSkills().add("mage_collapse");
        data.bind(1, "mage_mana_bolt");
        data.bind(4, "mage_collapse");
        return data;
    }

    @Test
    @DisplayName("запись и чтение не теряют ни одного поля")
    void roundTrip() {
        String json = PlayerDataCodec.toJson(sample());
        PlayerData back = PlayerDataCodec.fromJson(ID, json);

        assertEquals("mage", back.classId());
        assertEquals(17, back.level());
        assertEquals(1234.5, back.xp(), 1e-9);
        assertEquals(3, back.unspentPoints());
        assertEquals(java.util.Set.of("mage_mana_bolt", "mage_collapse"), back.unlockedSkills());
        assertEquals(Map.of(1, "mage_mana_bolt", 4, "mage_collapse"), back.slotBindings());
    }

    @Test
    @DisplayName("новый игрок читается как чистые данные первого уровня")
    void freshPlayer() {
        PlayerData fresh = PlayerDataCodec.fromJson(ID, PlayerDataCodec.toJson(new PlayerData(ID)));

        assertEquals(1, fresh.level());
        assertEquals(0, fresh.xp(), 1e-9);
        assertTrue(fresh.unlockedSkills().isEmpty());
    }

    @Test
    @DisplayName("испорченный файл даёт исключение, а не пустые данные")
    void brokenFileThrows() {
        assertThrows(PlayerDataException.class,
                () -> PlayerDataCodec.fromJson(ID, "{это не json"));
    }

    @Test
    @DisplayName("схема из будущего отвергается: иначе плагин затрёт прогресс")
    void futureSchemaRejected() {
        String json = "{\"schema\": 999, \"level\": 5}";
        PlayerDataException e = assertThrows(PlayerDataException.class,
                () -> PlayerDataCodec.fromJson(ID, json));
        assertTrue(e.getMessage().contains("более новой версией"), e.getMessage());
    }

    @Test
    @DisplayName("файл без номера схемы читается как нулевая версия")
    void missingSchemaIsLegacy() {
        PlayerData data = PlayerDataCodec.fromJson(ID, "{\"level\": 7}");
        assertEquals(7, data.level());
    }

    @Test
    @DisplayName("store пишет на диск и читает обратно")
    void storeWritesAndReads(@TempDir Path dir) throws IOException {
        PlayerDataStore store = new PlayerDataStore(dir, msg -> {
            throw new AssertionError("неожиданная ошибка записи: " + msg);
        });
        store.saveNow(sample());

        assertTrue(Files.isRegularFile(dir.resolve(ID + ".json")));
        PlayerData back = store.readFromDisk(ID).orElseThrow();
        assertEquals(17, back.level());

        // временный файл не остался
        assertFalse(Files.exists(dir.resolve(ID + ".json.tmp")));
        store.shutdown();
    }

    @Test
    @DisplayName("отсутствующий файл — пустой Optional, а не исключение")
    void missingFileIsEmpty(@TempDir Path dir) {
        PlayerDataStore store = new PlayerDataStore(dir, msg -> { });
        assertTrue(store.readFromDisk(UUID.randomUUID()).isEmpty());
        store.shutdown();
    }

    @Test
    @DisplayName("load для нового игрока отдаёт чистые данные и кеширует их")
    void loadCreatesAndCaches(@TempDir Path dir) {
        PlayerDataStore store = new PlayerDataStore(dir, msg -> { });
        PlayerData first = store.load(ID);
        first.setLevel(42);

        assertEquals(42, store.load(ID).level(), "второй load отдал тот же объект");
        assertEquals(1, store.loadedCount());
        store.shutdown();
    }

    @Test
    @DisplayName("испорченный файл на диске роняет load, а не затирает данные")
    void corruptFileOnDisk(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(ID + ".json"), "{сломано", StandardCharsets.UTF_8);
        PlayerDataStore store = new PlayerDataStore(dir, msg -> { });

        assertThrows(PlayerDataException.class, () -> store.load(ID));
        // файл на месте: его ещё можно починить руками
        assertTrue(Files.isRegularFile(dir.resolve(ID + ".json")));
        store.shutdown();
    }

    @Test
    @DisplayName("unload записывает и убирает из памяти")
    void unloadPersists(@TempDir Path dir) {
        PlayerDataStore store = new PlayerDataStore(dir, msg -> { });
        store.load(ID).setLevel(9);
        store.unload(ID);
        store.shutdown();

        assertEquals(0, store.loadedCount());
        assertEquals(9, store.readFromDisk(ID).orElseThrow().level());
    }

    @Test
    @DisplayName("уровень ниже первого отвергается")
    void levelFloor() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerData(ID).setLevel(0));
    }
}
