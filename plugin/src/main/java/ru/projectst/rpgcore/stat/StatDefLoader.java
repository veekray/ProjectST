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
 *     effect:
 *       cap: 200
 *       text: "N% к урону навыков"
 * </pre>
 *
 * <p>Раздел {@code effect} означает «это рейтинг, а не проценты»: сколько
 * процентов он даёт, считает кривая, а {@code text} — фраза, которую увидит
 * игрок, с буквой N на месте числа. Нет раздела — стат не про проценты, и
 * пояснять в меню нечего.
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
        StatEffect effect = readEffect(id, body, errors);

        if (min > max) {
            errors.add(body.at(), "stats." + id, "min больше max");
            return Optional.empty();
        }
        return Optional.of(new StatDef(id, display, base, min, max, rounding, effect));
    }

    /**
     * Кривая рейтинга и фраза о ней.
     *
     * <p>Отсутствие раздела — не ошибка: запасу здоровья проценты не нужны.
     * Ошибка — раздел, по которому нельзя ничего показать: без текста или с
     * текстом, в котором негде поставить число.
     */
    private static StatEffect readEffect(String id, YmlMap body, ContentErrors errors) {
        // mapOpt, а не map: отсутствие раздела — обычное дело, а не промах.
        // map объявил бы «обязательный раздел отсутствует» у каждого запаса.
        Optional<YmlMap> section = body.mapOpt("effect");
        if (section.isEmpty()) {
            return null;
        }
        YmlMap effect = section.get();
        double cap = effect.number("cap", 0.1, 100_000, 100);
        String text = effect.str("text", "");
        boolean inverted = effect.bool("inverted", false);

        if (text.isBlank()) {
            errors.add(effect.at(), "stats." + id + ".effect.text",
                    "нужна фраза для игрока, иначе показывать нечего");
            return null;
        }
        if (!text.contains(StatEffect.MARK)) {
            errors.add(effect.at(), "stats." + id + ".effect.text",
                    "в фразе нет буквы " + StatEffect.MARK + ": некуда поставить процент");
            return null;
        }
        return new StatEffect(cap, text, inverted);
    }
}
