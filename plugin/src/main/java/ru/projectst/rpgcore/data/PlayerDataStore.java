package ru.projectst.rpgcore.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Хранилище данных игроков: по файлу JSON на игрока.
 *
 * <p>SQLite не взят осознанно, причина записана в SPEC: драйвер стал бы
 * рантайм-зависимостью, а игроков на сервере 44. Триггер пересмотра тоже
 * зафиксирован — 500 игроков или заметные задержки записи.
 *
 * <p>Запись асинхронная и атомарная: сначала во временный файл, потом
 * перемещение. Без этого выключение сервера посреди записи оставляло бы
 * обрезанный JSON, то есть потерянного игрока.
 */
public final class PlayerDataStore {

    private final Path directory;
    private final Map<UUID, PlayerData> loaded = new ConcurrentHashMap<>();
    private final ExecutorService writer;
    private final Consumer<String> log;

    /**
     * @param directory куда писать файлы
     * @param log       куда сообщать о проблемах записи: в фоне бросать
     *                  исключение некуда, а молчать нельзя
     */
    public PlayerDataStore(Path directory, Consumer<String> log) {
        this.directory = directory;
        this.log = log;
        this.writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "rpgcore-data-writer");
            t.setDaemon(true);
            return t;
        });
    }

    /** Данные игрока из памяти; при промахе читаются с диска. */
    public PlayerData load(UUID uuid) {
        PlayerData cached = loaded.get(uuid);
        if (cached != null) {
            return cached;
        }
        PlayerData data = readFromDisk(uuid).orElseGet(() -> new PlayerData(uuid));
        loaded.put(uuid, data);
        return data;
    }

    public Optional<PlayerData> cached(UUID uuid) {
        return Optional.ofNullable(loaded.get(uuid));
    }

    /**
     * Читает файл игрока.
     *
     * @throws PlayerDataException если файл есть, но не читается. Пустые данные
     *         вместо испорченного файла не подставляются — см. исключение.
     */
    public Optional<PlayerData> readFromDisk(UUID uuid) {
        Path file = fileOf(uuid);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return Optional.of(PlayerDataCodec.fromJson(uuid, json));
        } catch (IOException e) {
            throw new PlayerDataException("не читается файл " + file + ": " + e.getMessage(), e);
        }
    }

    /** Ставит запись в очередь. Возврат немедленный. */
    public void saveLater(PlayerData data) {
        String json = PlayerDataCodec.toJson(data);
        writer.execute(() -> writeAtomically(data.uuid(), json));
    }

    /** Пишет немедленно в текущем потоке. Для выключения сервера. */
    public void saveNow(PlayerData data) {
        writeAtomically(data.uuid(), PlayerDataCodec.toJson(data));
    }

    /** Выгружает игрока из памяти, записав его данные. */
    public void unload(UUID uuid) {
        PlayerData data = loaded.remove(uuid);
        if (data != null) {
            saveLater(data);
        }
    }

    /** Пишет всех загруженных немедленно и останавливает поток записи. */
    public void shutdown() {
        loaded.values().forEach(this::saveNow);
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                log.accept("поток записи не успел завершиться за 10 секунд");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public int loadedCount() {
        return loaded.size();
    }

    Path fileOf(UUID uuid) {
        return directory.resolve(uuid + ".json");
    }

    /**
     * Запись через временный файл: обрезанный JSON после падения сервера
     * означал бы потерянного игрока.
     */
    private void writeAtomically(UUID uuid, String json) {
        try {
            Files.createDirectories(directory);
            Path target = fileOf(uuid);
            Path temp = directory.resolve(uuid + ".json.tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.accept("не удалось сохранить данные " + uuid + ": " + e.getMessage());
        }
    }
}
