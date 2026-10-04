package ru.projectst.rpgcore.loader;

/**
 * Одна ошибка загрузки контента.
 *
 * @param at    где это в файле
 * @param path  путь к ключу внутри документа, например {@code stats.strength.max}
 * @param what  что именно не так, человеческим языком
 */
public record ContentError(SourceRef at, String path, String what) {

    public ContentError {
        if (at == null) {
            throw new IllegalArgumentException("at обязателен");
        }
        if (path == null) {
            path = "";
        }
        if (what == null || what.isBlank()) {
            throw new IllegalArgumentException("what обязателен");
        }
    }

    @Override
    public String toString() {
        return path.isEmpty() ? at + "  " + what : at + "  " + path + ": " + what;
    }
}
