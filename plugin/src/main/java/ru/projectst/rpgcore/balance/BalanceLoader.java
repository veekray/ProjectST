package ru.projectst.rpgcore.balance;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.loader.YmlDoc;
import ru.projectst.rpgcore.loader.YmlMap;

/**
 * Читает слой баланса.
 *
 * <pre>
 * balance:
 *   mage_mana_bolt:
 *     damage: 6
 *     damage_empowered: 12
 *     cooldown:
 *       base: 4.0
 *       per-level: -0.2
 *       min: 1.6
 * </pre>
 *
 * <p>Значение — либо число, либо раздел с кривой. Третьего не дано: любая
 * другая форма будет названа ошибкой с номером строки.
 */
public final class BalanceLoader {

    private static final double LIMIT = 1_000_000;

    private BalanceLoader() {
    }

    public static Optional<BalanceBook> load(String file, String text, ContentErrors errors) {
        Optional<YmlDoc> parsed = YmlDoc.parse(file, text, errors);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        YmlDoc doc = parsed.get();

        Map<String, BalanceTable> tables = new LinkedHashMap<>();
        Optional<YmlMap> section = doc.root().map("balance");
        if (section.isPresent()) {
            YmlMap balance = section.get();
            for (String skillId : balance.keys()) {
                balance.map(skillId).ifPresent(body ->
                        tables.put(skillId, readTable(skillId, body, errors)));
            }
        }

        doc.finish();
        return Optional.of(new BalanceBook(tables));
    }

    private static BalanceTable readTable(String skillId, YmlMap body, ContentErrors errors) {
        Map<String, BalanceValue> values = new LinkedHashMap<>();
        for (String key : body.keys()) {
            readValue(skillId, key, body, errors).ifPresent(v -> values.put(key, v));
        }
        return new BalanceTable(skillId, values);
    }

    private static Optional<BalanceValue> readValue(String skillId, String key,
                                                    YmlMap body, ContentErrors errors) {
        // Раздел — значит кривая. Иначе ждём число.
        if (body.has(key) && isSection(body, key)) {
            Optional<YmlMap> curve = body.map(key);
            if (curve.isEmpty()) {
                return Optional.empty();
            }
            YmlMap c = curve.get();
            double base = c.number("base", -LIMIT, LIMIT, 0);
            double perLevel = c.number("per-level", -LIMIT, LIMIT, 0);
            double min = c.number("min", -LIMIT, LIMIT, -LIMIT);
            double max = c.number("max", -LIMIT, LIMIT, LIMIT);
            if (min > max) {
                errors.add(c.at(), "balance." + skillId + "." + key, "min больше max");
                return Optional.empty();
            }
            return Optional.of(new BalanceValue.Curve(base, perLevel, min, max));
        }
        return Optional.of(new BalanceValue.Constant(body.number(key, -LIMIT, LIMIT)));
    }

    /**
     * Проверка без побочного эффекта: обычные геттеры пометили бы ключ
     * прочитанным, а нам нужно только узнать его форму.
     */
    private static boolean isSection(YmlMap body, String key) {
        return body.rawKind(key) == YmlMap.Kind.SECTION;
    }
}
