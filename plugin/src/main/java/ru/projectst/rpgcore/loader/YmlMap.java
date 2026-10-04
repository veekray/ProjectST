package ru.projectst.rpgcore.loader;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Отображение «ключ → узел» с двумя свойствами, которых не хватало старому стеку.
 *
 * <p>Во-первых, каждый обращение к ключу помечает его прочитанным, и документ
 * в конце сообщает о непрочитанных как о неизвестных. Поэтому опечатку в имени
 * ключа нельзя не заметить: её не надо перечислять в схеме заранее, она
 * обнаруживается сама.
 *
 * <p>Во-вторых, ни один геттер не бросает исключение. Ошибка записывается в
 * сборщик и возвращается безопасное значение, чтобы за один проход собрать все
 * проблемы файла, а не первую.
 */
public final class YmlMap implements YmlNode {

    private final SourceRef at;
    private final String path;
    private final LinkedHashMap<String, YmlNode> entries;
    private final ContentErrors errors;
    private final Set<String> read = new HashSet<>();

    YmlMap(SourceRef at, String path, LinkedHashMap<String, YmlNode> entries, ContentErrors errors) {
        this.at = at;
        this.path = path;
        this.entries = entries;
        this.errors = errors;
    }

    @Override
    public SourceRef at() {
        return at;
    }

    public String path() {
        return path;
    }

    /** Ключи в порядке объявления. Чтением не считается: используется для обхода id-списков. */
    public Set<String> keys() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    public boolean has(String key) {
        return entries.containsKey(key);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    // ------------------------------------------------------------------ строки

    public String str(String key) {
        return scalar(key).map(s -> s.raw()).orElse("");
    }

    public String str(String key, String fallback) {
        if (!entries.containsKey(key)) {
            read.add(key);
            return fallback;
        }
        return str(key);
    }

    // ------------------------------------------------------------------ числа

    public int integer(String key, int min, int max) {
        return (int) Math.rint(number(key, min, max));
    }

    public int integer(String key, int min, int max, int fallback) {
        if (!entries.containsKey(key)) {
            read.add(key);
            return fallback;
        }
        return integer(key, min, max);
    }

    public double number(String key, double min, double max) {
        Optional<YmlNode.Scalar> s = scalar(key);
        if (s.isEmpty()) {
            return min;
        }
        String raw = s.get().raw();
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            errors.add(s.get().at(), child(key), "ожидалось число, получено \"" + raw + "\"");
            return min;
        }
        if (value < min || value > max) {
            errors.add(s.get().at(), child(key),
                    "значение " + raw + " вне диапазона " + min + ".." + max);
            return Math.clamp(value, min, max);
        }
        return value;
    }

    public double number(String key, double min, double max, double fallback) {
        if (!entries.containsKey(key)) {
            read.add(key);
            return fallback;
        }
        return number(key, min, max);
    }

    // ------------------------------------------------------------------ прочее

    public boolean bool(String key, boolean fallback) {
        if (!entries.containsKey(key)) {
            read.add(key);
            return fallback;
        }
        Optional<YmlNode.Scalar> s = scalar(key);
        if (s.isEmpty()) {
            return fallback;
        }
        String raw = s.get().raw().toLowerCase(Locale.ROOT);
        if (raw.equals("true")) {
            return true;
        }
        if (raw.equals("false")) {
            return false;
        }
        errors.add(s.get().at(), child(key), "ожидалось true или false, получено \"" + raw + "\"");
        return fallback;
    }

    public <E extends Enum<E>> E enumOf(String key, Class<E> type, E fallback) {
        if (!entries.containsKey(key)) {
            read.add(key);
            return fallback;
        }
        Optional<YmlNode.Scalar> s = scalar(key);
        if (s.isEmpty()) {
            return fallback;
        }
        String raw = s.get().raw();
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().equalsIgnoreCase(raw)) {
                return candidate;
            }
        }
        StringBuilder allowed = new StringBuilder();
        for (E candidate : type.getEnumConstants()) {
            if (!allowed.isEmpty()) {
                allowed.append(", ");
            }
            allowed.append(candidate.name().toLowerCase(Locale.ROOT));
        }
        errors.add(s.get().at(), child(key),
                "неизвестное значение \"" + raw + "\", допустимы: " + allowed);
        return fallback;
    }

    /** Вложенное отображение. Отсутствие — ошибка. */
    public Optional<YmlMap> map(String key) {
        read.add(key);
        YmlNode node = entries.get(key);
        if (node == null) {
            errors.add(at, child(key), "обязательный раздел отсутствует");
            return Optional.empty();
        }
        if (node instanceof YmlMap m) {
            return Optional.of(m);
        }
        errors.add(node.at(), child(key), "ожидался раздел, а не значение");
        return Optional.empty();
    }

    /** Вложенное отображение, отсутствие которого допустимо. */
    public Optional<YmlMap> mapOpt(String key) {
        if (!entries.containsKey(key)) {
            read.add(key);
            return Optional.empty();
        }
        return map(key);
    }

    public List<YmlNode> seq(String key) {
        read.add(key);
        YmlNode node = entries.get(key);
        if (node == null) {
            errors.add(at, child(key), "обязательный список отсутствует");
            return List.of();
        }
        if (node instanceof YmlNode.Seq seq) {
            return seq.items();
        }
        errors.add(node.at(), child(key), "ожидался список");
        return List.of();
    }

    // ------------------------------------------------------------------ внутреннее

    private Optional<YmlNode.Scalar> scalar(String key) {
        read.add(key);
        YmlNode node = entries.get(key);
        if (node == null) {
            errors.add(at, child(key), "обязательный ключ отсутствует");
            return Optional.empty();
        }
        if (node instanceof YmlNode.Scalar s) {
            return Optional.of(s);
        }
        errors.add(node.at(), child(key), "ожидалось одиночное значение");
        return Optional.empty();
    }

    private String child(String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    /** Сообщает о ключах, которых никто не прочитал. Вызывается документом. */
    void reportUnread(ContentErrors into) {
        for (var entry : entries.entrySet()) {
            if (!read.contains(entry.getKey())) {
                into.add(entry.getValue().at(), child(entry.getKey()), "неизвестный ключ");
            }
        }
    }
}
