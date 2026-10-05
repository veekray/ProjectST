package ru.projectst.rpgcore.classes;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import ru.projectst.rpgcore.balance.BalanceValue;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.loader.YmlDoc;
import ru.projectst.rpgcore.loader.YmlMap;

/**
 * Читает файл класса.
 *
 * <pre>
 * id: mage
 * display: "&amp;bМаг"
 * slots: 6
 * max-level: 60
 * points-per-level: 1
 *
 * xp:
 *   base: 100
 *   per-level: 50
 *
 * tiers:
 *   1: 1
 *   2: 5
 *   3: 15
 *
 * stats:
 *   magic_damage: { base: 0, per-level: 0.5 }
 *   defense: 5
 * </pre>
 *
 * <p>Имя не {@code ClassLoader}: так называется класс из {@code java.lang}, и
 * совпадение путало бы и в импортах, и в стеках.
 */
public final class ClassDefLoader {

    private static final double LIMIT = 1_000_000;

    private ClassDefLoader() {
    }

    public static Optional<ClassDef> load(String file, String text, ContentErrors errors) {
        Optional<YmlDoc> parsed = YmlDoc.parse(file, text, errors);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        YmlDoc doc = parsed.get();
        YmlMap root = doc.root();

        // Все ключи читаются до проверок: досрочный выход объявил бы
        // остальные неизвестными.
        String id = root.str("id");
        String display = root.str("display", id);
        int slots = root.integer("slots", 1, 12, 6);
        int maxLevel = root.integer("max-level", 1, 1000, 60);
        int pointsPerLevel = root.integer("points-per-level", 0, 10, 1);
        BalanceValue xp = readXp(root, errors);
        ResourceSpec resource = readResource(root, errors);
        String icon = root.str("icon", ClassDef.DEFAULT_ICON);
        Map<Integer, Integer> tiers = readTiers(root, errors);
        Map<String, BalanceValue> stats = readStats(root, errors);

        doc.finish();

        boolean ok = true;
        if (id.isBlank()) {
            errors.add(root.at(), "id", "идентификатор класса обязателен");
            ok = false;
        } else if (!id.equals(id.toLowerCase(Locale.ROOT))) {
            errors.add(root.at(), "id", "идентификатор класса должен быть в нижнем регистре");
            ok = false;
        }
        if (!ok) {
            return Optional.empty();
        }
        return Optional.of(new ClassDef(id, display, slots, tiers, stats,
                maxLevel, xp, pointsPerLevel, resource, icon));
    }

    /**
     * Ресурс класса. Без раздела — мана: самый частый случай не должен
     * требовать строки, но плут объявляет выносливость явно.
     */
    private static ResourceSpec readResource(YmlMap root, ContentErrors errors) {
        Optional<YmlMap> section = root.mapOpt("resource");
        if (section.isEmpty()) {
            return ResourceSpec.MANA;
        }
        YmlMap body = section.get();
        String display = body.str("name", ResourceSpec.MANA.display());
        String max = body.str("max", ResourceSpec.MANA.maxStat());
        String regen = body.str("regen", ResourceSpec.MANA.regenStat());
        if (display.isBlank() || max.isBlank() || regen.isBlank()) {
            errors.add(body.at(), "resource", "нужны name, max и regen");
            return ResourceSpec.MANA;
        }
        return new ResourceSpec(display, max, regen);
    }

    /**
     * Кривая опыта. Отсутствие раздела — это осознанный ответ «сто плюс
     * пятьдесят за уровень», а не нулевая стоимость: с нулём один убитый моб
     * поднимал бы игрока до предела.
     */
    private static BalanceValue readXp(YmlMap root, ContentErrors errors) {
        Optional<YmlMap> section = root.mapOpt("xp");
        if (section.isEmpty()) {
            return new BalanceValue.Curve(100, 50, 1, LIMIT);
        }
        YmlMap c = section.get();
        double first = c.number("base", 1, LIMIT, 100);
        double perLevel = c.number("per-level", -LIMIT, LIMIT, 50);
        double min = c.number("min", 1, LIMIT, 1);
        double max = c.number("max", 1, LIMIT, LIMIT);
        if (min > max) {
            errors.add(c.at(), "xp", "min больше max");
            return new BalanceValue.Curve(first, perLevel, 1, LIMIT);
        }
        return new BalanceValue.Curve(first, perLevel, min, max);
    }

    private static Map<Integer, Integer> readTiers(YmlMap root, ContentErrors errors) {
        Map<Integer, Integer> tiers = new LinkedHashMap<>();
        Optional<YmlMap> section = root.mapOpt("tiers");
        if (section.isEmpty()) {
            return tiers;
        }
        YmlMap body = section.get();
        for (String key : body.keys()) {
            int tier;
            try {
                tier = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                errors.add(body.at(), "tiers." + key, "ступень должна быть числом");
                continue;
            }
            if (tier < 1 || tier > 5) {
                errors.add(body.at(), "tiers." + key, "ступень от 1 до 5");
                continue;
            }
            tiers.put(tier, body.integer(key, 1, 1000, 1));
        }
        return tiers;
    }

    /** Базовые статы: число или кривая, как в слое баланса. */
    private static Map<String, BalanceValue> readStats(YmlMap root, ContentErrors errors) {
        Map<String, BalanceValue> stats = new LinkedHashMap<>();
        Optional<YmlMap> section = root.mapOpt("stats");
        if (section.isEmpty()) {
            return stats;
        }
        YmlMap body = section.get();
        for (String statId : body.keys()) {
            if (body.rawKind(statId) == YmlMap.Kind.SECTION) {
                Optional<YmlMap> curve = body.map(statId);
                if (curve.isEmpty()) {
                    continue;
                }
                YmlMap c = curve.get();
                double base = c.number("base", -LIMIT, LIMIT, 0);
                double perLevel = c.number("per-level", -LIMIT, LIMIT, 0);
                double min = c.number("min", -LIMIT, LIMIT, -LIMIT);
                double max = c.number("max", -LIMIT, LIMIT, LIMIT);
                if (min > max) {
                    errors.add(c.at(), "stats." + statId, "min больше max");
                    continue;
                }
                stats.put(statId, new BalanceValue.Curve(base, perLevel, min, max));
            } else {
                stats.put(statId, new BalanceValue.Constant(body.number(statId, -LIMIT, LIMIT)));
            }
        }
        return stats;
    }
}
