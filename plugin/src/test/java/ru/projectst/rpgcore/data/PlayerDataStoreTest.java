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

    // ------------------------------------------------------------------ артефакты

    @Test
    @DisplayName("артефакты переживают запись, а файл старой схемы читается без них")
    void artifactsRoundTrip() {
        PlayerData data = sample();
        // Что внутри записи, слой данных не знает: для него это строка, и
        // проверяется именно это — что строка доходит до диска и обратно целой.
        data.setArtifact(1, "cGVyc3Rlbg==");
        data.setArtifact(3, "a29sJ2Nv");

        PlayerData back = PlayerDataCodec.fromJson(ID, PlayerDataCodec.toJson(data));

        assertEquals(Map.of(1, "cGVyc3Rlbg==", 3, "a29sJ2Nv"), back.artifacts());

        PlayerData old = PlayerDataCodec.fromJson(ID, """
                {"schema":2,"uuid":"11111111-2222-3333-4444-555555555555",
                 "class":"mage","level":9,"xp":0,"points":0,
                 "unlocked":[],"slots":{},"levels":{}}
                """);
        assertTrue(old.artifacts().isEmpty(),
                "до появления ячеек артефактов не было — пусто это и значит");
    }

    @Test
    @DisplayName("пустая запись убирает артефакт, а не кладёт пустоту")
    void blankArtifactClearsTheSlot() {
        PlayerData data = new PlayerData(ID);
        data.setArtifact(2, "zzz");

        data.setArtifact(2, "");

        assertTrue(data.artifacts().isEmpty(), "иначе каждое чтение проверяло бы пустую строку");
        assertEquals(null, data.artifact(2));
    }

    @Test
    @DisplayName("свободная ячейка ищется слева, и ноль означает «нет свободных»")
    void firstFreeArtifactSlot() {
        PlayerData data = new PlayerData(ID);

        assertEquals(1, data.firstFreeArtifactSlot(4));

        data.setArtifact(1, "a");
        data.setArtifact(2, "b");
        assertEquals(3, data.firstFreeArtifactSlot(4), "слева направо, а не в любую щель");

        data.setArtifact(1, null);
        assertEquals(1, data.firstFreeArtifactSlot(4), "освободившаяся первая снова первая");

        data.setArtifact(1, "a");
        data.setArtifact(3, "c");
        data.setArtifact(4, "d");
        assertEquals(0, data.firstFreeArtifactSlot(4));
        assertEquals(0, data.firstFreeArtifactSlot(0), "ячеек нет — свободных тоже");
    }

    @Test
    @DisplayName("артефакты из исчезнувших ячеек перечислимы: их нужно вернуть, а не потерять")
    void artifactsBeyondSlots() {
        PlayerData data = new PlayerData(ID);
        data.setArtifact(1, "a");
        data.setArtifact(5, "b");
        data.setArtifact(7, "c");

        assertEquals(java.util.List.of(5, 7), data.artifactsBeyond(4));
        assertTrue(data.artifactsBeyond(7).isEmpty());
    }

    @Test
    @DisplayName("ячейка артефакта нумеруется с единицы: ноль — не ячейка")
    void artifactSlotNumbersStartAtOne() {
        PlayerData data = new PlayerData(ID);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> data.setArtifact(0, "a"));
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
