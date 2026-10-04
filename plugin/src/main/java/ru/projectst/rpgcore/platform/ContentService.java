package ru.projectst.rpgcore.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.stat.StatDefLoader;
import ru.projectst.rpgcore.stat.StatRegistry;
import ru.projectst.rpgcore.status.StatusDefLoader;
import ru.projectst.rpgcore.status.StatusRegistry;

/**
 * Загрузка всего контента с диска и его проверка.
 *
 * <p>Живёт в {@code platform/}, потому что знает про файловую систему плагина.
 * Сами загрузчики домены к файлам не привязаны — им отдают текст, и поэтому их
 * можно проверить юнит-тестами.
 *
 * <p>Отказ закрытый, но локальный, как и записано в SPEC: битый файл оставляет
 * свой домен пустым и громко об этом сообщает, но сервер не роняет и остальной
 * контент не трогает.
 */
public final class ContentService {

    /** Файлы контента, которые плагин кладёт при первом запуске. */
    static final Map<String, String> DEFAULT_FILES = new LinkedHashMap<>();

    static {
        DEFAULT_FILES.put("stats.yml", "stats.yml");
        DEFAULT_FILES.put("statuses.yml", "statuses.yml");
    }

    private final Path folder;

    private StatRegistry stats = new StatRegistry(Map.of());
    private StatusRegistry statuses = new StatusRegistry(Map.of());

    public ContentService(Path folder) {
        this.folder = folder;
    }

    public StatRegistry stats() {
        return stats;
    }

    public StatusRegistry statuses() {
        return statuses;
    }

    /**
     * Перечитывает контент. Возвращает все ошибки с позициями: это же
     * используется командой {@code /rpg validate}.
     */
    public ContentErrors reload() {
        ContentErrors errors = new ContentErrors();

        read("stats.yml", errors).ifPresent(text ->
                StatDefLoader.load("stats.yml", text, errors).ifPresent(r -> stats = r));

        read("statuses.yml", errors).ifPresent(text ->
                StatusDefLoader.load("statuses.yml", text, errors).ifPresent(r -> statuses = r));

        return errors;
    }

    /**
     * Проверяет контент, ничего не применяя: загружает в выброшенные реестры.
     * Нужен именно отдельный путь, иначе «проверить» означало бы «применить»,
     * и битый файл всё равно попал бы в игру.
     */
    public ContentErrors validateOnly() {
        ContentErrors errors = new ContentErrors();
        read("stats.yml", errors).ifPresent(text -> StatDefLoader.load("stats.yml", text, errors));
        read("statuses.yml", errors)
                .ifPresent(text -> StatusDefLoader.load("statuses.yml", text, errors));
        return errors;
    }

    private Optional<String> read(String name, ContentErrors errors) {
        Path file = folder.resolve(name);
        if (!Files.isRegularFile(file)) {
            errors.add(ru.projectst.rpgcore.loader.SourceRef.ofFile(name), "",
                    "файл отсутствует");
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            errors.add(ru.projectst.rpgcore.loader.SourceRef.ofFile(name), "",
                    "не читается: " + e.getMessage());
            return Optional.empty();
        }
    }
}
