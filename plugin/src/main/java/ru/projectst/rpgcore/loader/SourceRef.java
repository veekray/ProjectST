package ru.projectst.rpgcore.loader;

/**
 * Место в файле контента: файл, строка, колонка.
 *
 * <p>Существует ровно для того, чтобы ни одна ошибка загрузки не выглядела как
 * «что-то не так в конфигах». На старом стеке именно отсутствие позиции
 * превращало опечатку в полдня поиска.
 *
 * <p>Строки и колонки здесь считаются с единицы, как их показывает редактор.
 * SnakeYAML отдаёт их с нуля, поправка делается при создании.
 */
public record SourceRef(String file, int line, int column) {

    public SourceRef {
        if (file == null || file.isBlank()) {
            throw new IllegalArgumentException("file обязателен");
        }
    }

    /** Позиция неизвестна: используется, когда ошибка относится к файлу целиком. */
    public static SourceRef ofFile(String file) {
        return new SourceRef(file, 0, 0);
    }

    public boolean hasPosition() {
        return line > 0;
    }

    @Override
    public String toString() {
        return hasPosition() ? file + ":" + line + ":" + column : file;
    }
}
