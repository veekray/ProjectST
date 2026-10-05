package ru.projectst.rpgcore.item;

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
import ru.projectst.rpgcore.stat.StatOp;

/**
 * Читает файл предмета.
 *
 * <pre>
 * id: mage_apprentice_staff
 * display: "Посох ученика"
 * material: STICK
 * model-data: 4001
 * rarity: uncommon
 * slot: hand
 * unbreakable: true
 *
 * lore:
 *   - "Простой посох, с которого начинают."
 *
 * requires:
 *   class: mage
 *   level: 5
 *
 * stats:
 *   magic_damage: 6
 *   max_mana: { op: percent, value: 10 }
 *
 * abilities:
 *   - { skill: staff_bolt, trigger: right-click }
 * </pre>
 *
 * <p>Статы пишутся числом, когда надбавка плоская, и разделом, когда нужен другой
 * способ применения. Короткая форма не отдельный синтаксис, а тот же самый с
 * опущенным {@code op}: двух способов написать одно и то же в проекте нет.
 */
public final class ItemDefLoader {

    private static final double LIMIT = 1_000_000;

    private ItemDefLoader() {
    }

    public static Optional<ItemDef> load(String file, String text, ContentErrors errors) {
        Optional<YmlDoc> parsed = YmlDoc.parse(file, text, errors);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        YmlDoc doc = parsed.get();
        YmlMap root = doc.root();

        // Все ключи читаются до любой проверки: досрочный выход объявил бы
        // остальные неизвестными.
        String id = root.str("id");
        String display = root.str("display", id);
        String material = root.str("material", "");
        int modelData = root.integer("model-data", 0, 16_000_000, 0);
        String rarity = root.str("rarity", Rarity.COMMON.id());
        ItemSlot slot = root.enumOf("slot", ItemSlot.class, ItemSlot.HAND);
        boolean unbreakable = root.bool("unbreakable", false);
        List<String> lore = new ArrayList<>(root.strings("lore"));
        ItemRequirement requirement = readRequirement(root, errors);
        Map<String, ItemDef.ItemStatLine> stats = readStats(root, errors);
        List<ItemAbility> abilities = readAbilities(root, errors);

        doc.finish();

        boolean ok = true;
        if (id.isBlank()) {
            errors.add(root.at(), "id", "идентификатор предмета обязателен");
            ok = false;
        } else if (!id.equals(id.toLowerCase(Locale.ROOT))) {
            errors.add(root.at(), "id", "идентификатор предмета должен быть в нижнем регистре");
            ok = false;
        }
        if (material.isBlank()) {
            errors.add(root.at(), "material", "у предмета обязателен материал");
            ok = false;
        }
        if (!ok) {
            return Optional.empty();
        }
        return Optional.of(new ItemDef(id, display, material.toUpperCase(Locale.ROOT), modelData,
                lore, rarity, slot, stats, requirement, abilities, unbreakable));
    }

    private static ItemRequirement readRequirement(YmlMap root, ContentErrors errors) {
        Optional<YmlMap> section = root.mapOpt("requires");
        if (section.isEmpty()) {
            return ItemRequirement.NONE;
        }
        YmlMap body = section.get();
        String classId = body.str("class", "");
        int level = body.integer("level", 0, 1000, 0);
        if (classId.isBlank() && level == 0) {
            errors.add(body.at(), "requires",
                    "пустой раздел требований: уберите его или укажите class или level");
            return ItemRequirement.NONE;
        }
        return new ItemRequirement(classId.isBlank() ? null : classId, level);
    }

    /** Надбавки: число — плоская, раздел — со способом применения. */
    private static Map<String, ItemDef.ItemStatLine> readStats(YmlMap root,
                                                               ContentErrors errors) {
        Map<String, ItemDef.ItemStatLine> stats = new LinkedHashMap<>();
        Optional<YmlMap> section = root.mapOpt("stats");
        if (section.isEmpty()) {
            return stats;
        }
        YmlMap body = section.get();
        for (String statId : body.keys()) {
            if (body.rawKind(statId) == YmlMap.Kind.SECTION) {
                Optional<YmlMap> line = body.map(statId);
                if (line.isEmpty()) {
                    continue;
                }
                YmlMap l = line.get();
                StatOp op = l.enumOf("op", StatOp.class, StatOp.FLAT);
                double value = l.number("value", -LIMIT, LIMIT, 0);
                stats.put(statId, new ItemDef.ItemStatLine(op, value));
            } else {
                stats.put(statId, new ItemDef.ItemStatLine(StatOp.FLAT,
                        body.number(statId, -LIMIT, LIMIT)));
            }
        }
        return stats;
    }

    private static List<ItemAbility> readAbilities(YmlMap root, ContentErrors errors) {
        List<ItemAbility> out = new ArrayList<>();
        if (root.rawKind("abilities") == YmlMap.Kind.ABSENT) {
            return out;
        }
        List<YmlNode> nodes = root.seq("abilities");
        for (int i = 0; i < nodes.size(); i++) {
            String path = "abilities[" + i + "]";
            if (!(nodes.get(i) instanceof YmlMap body)) {
                errors.add(nodes.get(i).at(), path, "умение должно быть разделом");
                continue;
            }
            String skillId = body.str("skill", "");
            String raw = body.str("trigger", "");
            ItemTrigger trigger = switch (raw) {
                case "right-click" -> ItemTrigger.RIGHT_CLICK;
                case "left-click" -> ItemTrigger.LEFT_CLICK;
                case "sneak-right-click" -> ItemTrigger.SNEAK_RIGHT_CLICK;
                case "on-hit" -> ItemTrigger.ON_HIT;
                default -> null;
            };
            if (skillId.isBlank()) {
                errors.add(body.at(), path + ".skill", "обязательный ключ skill отсутствует");
                continue;
            }
            if (trigger == null) {
                errors.add(body.at(), path + ".trigger", "неизвестный триггер \"" + raw
                        + "\", допустимы: right-click, left-click, sneak-right-click, on-hit");
                continue;
            }
            out.add(new ItemAbility(skillId, trigger));
        }
        return out;
    }

    /**
     * Читает файл редкостей целиком.
     *
     * <pre>
     * rarities:
     *   uncommon:
     *     display: "Необычный"
     *     color: GREEN
     * </pre>
     */
    public static Optional<Map<String, Rarity>> loadRarities(String file, String text,
                                                             ContentErrors errors) {
        Optional<YmlDoc> parsed = YmlDoc.parse(file, text, errors);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        YmlDoc doc = parsed.get();
        Optional<YmlMap> section = doc.root().map("rarities");
        Map<String, Rarity> out = new LinkedHashMap<>();
        if (section.isPresent()) {
            YmlMap body = section.get();
            for (String id : body.keys()) {
                Optional<YmlMap> line = body.map(id);
                if (line.isEmpty()) {
                    continue;
                }
                YmlMap l = line.get();
                String display = l.str("display", id);
                String color = l.str("color", "GRAY");
                if (!id.equals(id.toLowerCase(Locale.ROOT))) {
                    errors.add(l.at(), "rarities." + id,
                            "идентификатор редкости должен быть в нижнем регистре");
                    continue;
                }
                out.put(id, new Rarity(id, display, color.toUpperCase(Locale.ROOT)));
            }
        }
        doc.finish();
        // Обычная редкость есть всегда: предмет без указанной редкости должен
        // чем-то выводиться, и это не повод объявлять её в каждом файле.
        out.putIfAbsent(Rarity.COMMON.id(), Rarity.COMMON);
        return Optional.of(out);
    }
}
