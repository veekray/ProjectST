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

    @Test
    @DisplayName("уровни навыков переживают запись, а старый файл поднимается до первого уровня")
    void skillLevelsRoundTrip() {
        PlayerData data = sample();
        data.setSkillLevel("mage_collapse", 3);

        PlayerData back = PlayerDataCodec.fromJson(ID, PlayerDataCodec.toJson(data));
        assertEquals(3, back.skillLevel("mage_collapse"));
        assertEquals(1, back.skillLevel("mage_mana_bolt"), "изученный без записи — первый уровень");
        assertEquals(0, back.skillLevel("чужой_навык"), "не изученный — ноль");

        PlayerData old = PlayerDataCodec.fromJson(ID, """
                {"schema":1,"uuid":"11111111-2222-3333-4444-555555555555",
                 "class":"mage","level":9,"xp":0,"points":0,
                 "unlocked":["mage_mana_bolt"],"slots":{}}
                """);
        assertEquals(1, old.skillLevel("mage_mana_bolt"),
                "файл первой схемы — все изученные на первом уровне");
    }

    // ------------------------------------------------------------------ снаряжение

    @Test
    @DisplayName("снаряжение и очередь возврата переживают запись")
    void gearRoundTrip() {
        PlayerData data = sample();
        // Что внутри записи, слой данных не знает: для него это строка, и
        // проверяется именно это — что строка доходит до диска и обратно целой.
        data.setGear("ring_left", "cGVyc3Rlbg==");
        data.setGear("artifact_3", "a29sJ2Nv");
        data.addReturn("c3RhcnlqIGFydGVmYWt0");

        PlayerData back = PlayerDataCodec.fromJson(ID, PlayerDataCodec.toJson(data));

        assertEquals(Map.of("ring_left", "cGVyc3Rlbg==", "artifact_3", "a29sJ2Nv"), back.gear());
        assertEquals(java.util.List.of("c3RhcnlqIGFydGVmYWt0"), back.returns());
    }

    @Test
    @DisplayName("файл до ячеек артефактов читается с пустым снаряжением")
    void schemaTwoHasNoGear() {
        PlayerData old = PlayerDataCodec.fromJson(ID, """
                {"schema":2,"uuid":"11111111-2222-3333-4444-555555555555",
                 "class":"mage","level":9,"xp":0,"points":0,
                 "unlocked":[],"slots":{},"levels":{}}
                """);
        assertTrue(old.gear().isEmpty(),
                "до появления ячеек снаряжения не было — пусто это и значит");
        assertTrue(old.returns().isEmpty());
    }

    @Test
    @DisplayName("артефакты по номерам переезжают в ячейки, лишние — в возврат, а не в никуда")
    void numberedArtifactsMigrate() {
        PlayerData old = PlayerDataCodec.fromJson(ID, """
                {"schema":3,"uuid":"11111111-2222-3333-4444-555555555555",
                 "class":"mage","level":9,"xp":0,"points":0,
                 "unlocked":[],"slots":{},"levels":{},
                 "artifacts":{"1":"b2Rpbg==","3":"dHJp","4":"Y2hldHlyZQ==","7":"c2VtJw=="}}
                """);

        assertEquals(Map.of("artifact_1", "b2Rpbg==", "artifact_3", "dHJp"), old.gear());
        assertEquals(java.util.List.of("Y2hldHlyZQ==", "c2VtJw=="), old.returns(),
                "четвёртой ячейки больше нет: вещь из неё обязана вернуться владельцу");
    }

    @Test
    @DisplayName("испорченный номер старого артефакта — отказ, а не тихая потеря")
    void brokenArtifactNumberRefuses() {
        assertThrows(PlayerDataException.class, () -> PlayerDataCodec.fromJson(ID, """
                {"schema":3,"uuid":"11111111-2222-3333-4444-555555555555",
                 "class":"mage","level":9,"xp":0,"points":0,
                 "unlocked":[],"slots":{},"levels":{},
                 "artifacts":{"первый":"b2Rpbg=="}}
                """));
    }

    @Test
    @DisplayName("пустая запись убирает предмет из ячейки, а не кладёт пустоту")
    void blankGearClearsTheCell() {
        PlayerData data = new PlayerData(ID);
        data.setGear("amulet", "zzz");

        data.setGear("amulet", "");

        assertTrue(data.gear().isEmpty(), "иначе каждое чтение проверяло бы пустую строку");
        assertEquals(null, data.gearItem("amulet"));
    }

    @Test
    @DisplayName("у ячейки есть имя: без имени положить некуда")
    void gearCellNeedsName() {
        PlayerData data = new PlayerData(ID);

        assertThrows(IllegalArgumentException.class, () -> data.setGear(" ", "a"));
    }

    @Test
    @DisplayName("выключение дописывает последнее состояние, а не отложенное старое")
    void shutdownWinsOverQueuedWrite(@TempDir Path dir) throws IOException {
        PlayerDataStore store = new PlayerDataStore(dir, msg -> {
            throw new AssertionError(msg);
        });

        PlayerData data = store.load(ID);
        data.setLevel(2);
        store.saveLater(data);

        // Состояние меняется сразу после постановки записи в очередь: ровно так
        // и происходит при выключении сервера под нагрузкой.
        data.setLevel(40);
        store.shutdown();

        String json = Files.readString(dir.resolve(ID + ".json"), StandardCharsets.UTF_8);
        assertEquals(40, PlayerDataCodec.fromJson(ID, json).level(),
                "отложенная запись не должна лечь поверх финальной");
        try (var files = Files.list(dir)) {
            assertTrue(files.noneMatch(f -> f.toString().endsWith(".tmp")),
                    "временные файлы не остаются");
        }
    }
}
