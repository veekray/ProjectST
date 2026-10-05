package ru.projectst.rpgcore.mob;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.loader.YmlDoc;
import ru.projectst.rpgcore.loader.YmlMap;
import ru.projectst.rpgcore.loader.YmlNode;

/**
 * Читает файл моба.
 *
 * <pre>
 * id: desert_scorpion
 * display: "Пустынный скорпион"
 * type: SILVERFISH
 * health: 60
 * damage: 7
 * experience: 12
 *
 * stats:
 *   defense: 4
 *   magic_resistance: 10
 *
 * skills:
 *   - { skill: mob_venom_bite, trigger: on-attack }
 *   - { skill: mob_burrow, trigger: interval, every: 100, chance: 40 }
 *
 * drops:
 *   - { material: STRING, min: 1, max: 3, chance: 60 }
 *   - { item: arcane_focus, chance: 5 }
 * </pre>
 *
 * <p>Шанс по умолчанию — сто процентов, и это сказано здесь: «без шанса» значит
 * «всегда», а не «никогда». Обратное прочтение стоило бы одного вечера на
 * выяснение, почему дроп не падает.
 */
public final class MobDefLoader {

    private static final double LIMIT = 100_000;

    private MobDefLoader() {
    }

    public static Optional<MobDef> load(String file, String text, ContentErrors errors) {
        Optional<YmlDoc> parsed = YmlDoc.parse(file, text, errors);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        YmlDoc doc = parsed.get();
        YmlMap root = doc.root();

        String id = root.str("id");
        String display = root.str("display", id);
        String type = root.str("type", "");
        double health = root.number("health", 1, LIMIT, 20);
        double damage = root.number("damage", 0, LIMIT, 0);
        int experience = root.integer("experience", -1, 100_000, -1);
        boolean glowing = root.bool("glowing", false);
        boolean baby = root.bool("baby", false);
        Map<String, Double> stats = readStats(root);
        List<MobSkill> skills = readSkills(root, errors);
        List<MobDrop> drops = readDrops(root, errors);

        doc.finish();

        boolean ok = true;
        if (id.isBlank()) {
            errors.add(root.at(), "id", "идентификатор моба обязателен");
            ok = false;
        } else if (!id.equals(id.toLowerCase(Locale.ROOT))) {
            errors.add(root.at(), "id", "идентификатор моба должен быть в нижнем регистре");
            ok = false;
        }
        if (type.isBlank()) {
            errors.add(root.at(), "type", "у моба обязателен тип существа");
            ok = false;
        }
        if (!ok) {
            return Optional.empty();
        }
        return Optional.of(new MobDef(id, display, type.toUpperCase(Locale.ROOT), health, damage,
                stats, skills, drops, experience, glowing, baby));
    }

    private static Map<String, Double> readStats(YmlMap root) {
        Map<String, Double> stats = new LinkedHashMap<>();
        Optional<YmlMap> section = root.mapOpt("stats");
        if (section.isEmpty()) {
            return stats;
        }
        YmlMap body = section.get();
        for (String statId : body.keys()) {
            stats.put(statId, body.number(statId, -LIMIT, LIMIT));
        }
        return stats;
    }

    private static List<MobSkill> readSkills(YmlMap root, ContentErrors errors) {
        List<MobSkill> out = new ArrayList<>();
        if (root.rawKind("skills") == YmlMap.Kind.ABSENT) {
            return out;
        }
        List<YmlNode> nodes = root.seq("skills");
        for (int i = 0; i < nodes.size(); i++) {
            String path = "skills[" + i + "]";
            if (!(nodes.get(i) instanceof YmlMap body)) {
                errors.add(nodes.get(i).at(), path, "навык должен быть разделом");
                continue;
            }
            String skillId = body.str("skill", "");
            String raw = body.str("trigger", "");
            double chance = body.number("chance", 1, 100, 100);
            int every = body.integer("every", 1, 12_000, 0);

            MobTrigger trigger = switch (raw) {
                case "spawn" -> MobTrigger.ON_SPAWN;
                case "interval" -> MobTrigger.ON_INTERVAL;
                case "damaged" -> MobTrigger.ON_DAMAGED;
                case "on-attack", "attack" -> MobTrigger.ON_ATTACK;
                case "death" -> MobTrigger.ON_DEATH;
                default -> null;
            };
            if (skillId.isBlank()) {
                errors.add(body.at(), path + ".skill", "обязательный ключ skill отсутствует");
                continue;
            }
            if (trigger == null) {
                errors.add(body.at(), path + ".trigger", "неизвестный триггер \"" + raw
                        + "\", допустимы: spawn, interval, damaged, attack, death");
                continue;
            }
            if (trigger == MobTrigger.ON_INTERVAL && every < 1) {
                errors.add(body.at(), path + ".every",
                        "периодическому навыку нужен ключ every: как часто он срабатывает");
                continue;
            }
            if (trigger != MobTrigger.ON_INTERVAL && every > 0) {
                errors.add(body.at(), path + ".every",
                        "ключ every имеет смысл только при trigger: interval");
                continue;
            }
            out.add(new MobSkill(skillId, trigger, chance, every));
        }
        return out;
    }

    private static List<MobDrop> readDrops(YmlMap root, ContentErrors errors) {
        List<MobDrop> out = new ArrayList<>();
        if (root.rawKind("drops") == YmlMap.Kind.ABSENT) {
            return out;
        }
        List<YmlNode> nodes = root.seq("drops");
        for (int i = 0; i < nodes.size(); i++) {
            String path = "drops[" + i + "]";
            if (!(nodes.get(i) instanceof YmlMap body)) {
                errors.add(nodes.get(i).at(), path, "строка дропа должна быть разделом");
                continue;
            }
            String itemId = body.str("item", "");
            String material = body.str("material", "");
            int min = body.integer("min", 1, 64, 1);
            int max = body.integer("max", 1, 64, Math.max(1, min));
            double chance = body.number("chance", 0.01, 100, 100);

            if (itemId.isBlank() == material.isBlank()) {
                errors.add(body.at(), path,
                        "строка дропа — либо item, либо material, но не оба и не ничто");
                continue;
            }
            if (max < min) {
                errors.add(body.at(), path, "max меньше min");
                continue;
            }
            out.add(new MobDrop(itemId.isBlank() ? null : itemId,
                    material.isBlank() ? null : material.toUpperCase(Locale.ROOT),
                    min, max, chance));
        }
        return out;
    }

    /**
     * Читает файл правил спавна целиком.
     *
     * <pre>
     * rules:
     *   - { mob: desert_scorpion, replaces: SILVERFISH, chance: 50, worlds: [world] }
     * </pre>
     */
    public static Optional<List<SpawnRule>> loadRules(String file, String text,
                                                      ContentErrors errors) {
        Optional<YmlDoc> parsed = YmlDoc.parse(file, text, errors);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        YmlDoc doc = parsed.get();
        YmlMap root = doc.root();
        List<SpawnRule> out = new ArrayList<>();

        if (root.rawKind("rules") != YmlMap.Kind.ABSENT) {
            List<YmlNode> nodes = root.seq("rules");
            for (int i = 0; i < nodes.size(); i++) {
                String path = "rules[" + i + "]";
                if (!(nodes.get(i) instanceof YmlMap body)) {
                    errors.add(nodes.get(i).at(), path, "правило должно быть разделом");
                    continue;
                }
                String mobId = body.str("mob", "");
                String replaces = body.str("replaces", "");
                double chance = body.number("chance", 0.01, 100, 100);
                List<String> worlds = new ArrayList<>(body.strings("worlds"));
                if (mobId.isBlank() || replaces.isBlank()) {
                    errors.add(body.at(), path, "нужны ключи mob и replaces");
                    continue;
                }
                out.add(new SpawnRule(mobId, replaces, worlds, chance));
            }
        }
        doc.finish();
        return Optional.of(out);
    }
}
