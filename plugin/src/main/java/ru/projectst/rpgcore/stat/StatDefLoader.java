package ru.projectst.rpgcore.stat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.loader.YmlDoc;
import ru.projectst.rpgcore.loader.YmlMap;

/**
 * Читает определения статов из YAML через строгий загрузчик.
 *
 * <p>Ожидаемый вид файла:
 * <pre>
 * stats:
 *   skill_damage:
 *     display: "Урон навыков"
 *     base: 0
 *     min: 0
 *     max: 10000
 *     rounding: none
 * </pre>
 *
 * <p>Схема нигде не объявляется отдельно: допустимые ключи — это ровно те,
 * которые читает этот метод, а всё остальное загрузчик пометит неизвестным сам.
 * Поэтому схема не может разойтись с кодом.
 */
public final class StatDefLoader {

    private StatDefLoader() {
    }

    public static Optional<StatRegistry> load(String file, String text, ContentErrors errors) {
        Optional<YmlDoc> parsed = YmlDoc.parse(file, text, errors);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        YmlDoc doc = parsed.get();

        Map<String, StatDef> defs = new LinkedHashMap<>();
        Optional<YmlMap> stats = doc.root().map("stats");
        if (stats.isPresent()) {
            YmlMap section = stats.get();
            for (String id : section.keys()) {
                Optional<YmlMap> body = section.map(id);
                if (body.isEmpty()) {
                    continue;
                }
                read(id, body.get(), errors).ifPresent(def -> defs.put(def.id(), def));
            }
        }

        doc.finish();
        return Optional.of(new StatRegistry(defs));
    }

    private static Optional<StatDef> read(String id, YmlMap body, ContentErrors errors) {
        String lower = id.toLowerCase(java.util.Locale.ROOT);
        if (!lower.equals(id)) {
            errors.add(body.at(), "stats." + id,
                    "идентификатор стата должен быть в нижнем регистре");
            return Optional.empty();
        }

        String display = body.str("display", id);
        double base = body.number("base", -1_000_000, 1_000_000, 0);
        double min = body.number("min", -1_000_000, 1_000_000, 0);
        double max = body.number("max", -1_000_000, 1_000_000, 1_000_000);
        Rounding rounding = body.enumOf("rounding", Rounding.class, Rounding.NONE);

        if (min > max) {
            errors.add(body.at(), "stats." + id, "min больше max");
            return Optional.empty();
        }
        return Optional.of(new StatDef(id, display, base, min, max, rounding));
    }
}
