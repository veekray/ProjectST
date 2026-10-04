package ru.projectst.rpgcore.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import ru.projectst.rpgcore.damage.DamageSchool;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.loader.SourceRef;
import ru.projectst.rpgcore.loader.YmlDoc;
import ru.projectst.rpgcore.loader.YmlMap;
import ru.projectst.rpgcore.loader.YmlNode;

/**
 * Читает файл навыка.
 *
 * <pre>
 * id: mage_mana_bolt
 * display: "Мановый разряд"
 * class: mage
 * tier: 2
 * mana: $mana
 * cooldown: $cooldown
 *
 * steps:
 *   - target: { type: enemies_in_radius, radius: $radius }
 *     do:
 *       - { action: damage, amount: $damage, school: magic }
 *       - { action: status, id: mark, duration: $mark_duration }
 * </pre>
 *
 * <p>Числа со знаком доллара — ссылки в слой баланса. Их существование
 * проверяет {@link SkillLinker} после загрузки всех файлов.
 */
public final class SkillLoader {

    private static final double LIMIT = 1_000_000;

    private SkillLoader() {
    }

    public static Optional<SkillDef> load(String file, String text, ContentErrors errors) {
        Optional<YmlDoc> parsed = YmlDoc.parse(file, text, errors);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        YmlDoc doc = parsed.get();
        YmlMap root = doc.root();

        // Все ключи читаются до любой проверки: иначе досрочный выход оставит
        // остальные непрочитанными, и они будут названы неизвестными. На этом
        // уже обжигались в загрузчике статусов.
        String id = root.str("id");
        String display = root.str("display", id);
        String classId = root.str("class");
        int tier = root.integer("tier", 1, 5, 1);
        NumberRef mana = number(root, "mana", errors, "skill", new NumberRef.Literal(0));
        NumberRef cooldown = number(root, "cooldown", errors, "skill", new NumberRef.Literal(0));
        List<Step> steps = readSteps(root, errors);

        doc.finish();

        boolean ok = true;
        if (id.isBlank()) {
            errors.add(root.at(), "id", "идентификатор навыка обязателен");
            ok = false;
        } else if (!id.equals(id.toLowerCase(Locale.ROOT))) {
            errors.add(root.at(), "id", "идентификатор навыка должен быть в нижнем регистре");
            ok = false;
        }
        if (classId.isBlank()) {
            errors.add(root.at(), "class", "навык должен принадлежать классу");
            ok = false;
        }
        if (steps.isEmpty()) {
            errors.add(root.at(), "steps", "навык без шагов ничего не делает");
            ok = false;
        }
        if (!ok) {
            return Optional.empty();
        }
        return Optional.of(new SkillDef(id, display, classId, tier, mana, cooldown, steps));
    }

    // ------------------------------------------------------------------ шаги

    private static List<Step> readSteps(YmlMap root, ContentErrors errors) {
        List<Step> steps = new ArrayList<>();
        if (root.rawKind("steps") == YmlMap.Kind.ABSENT) {
            return steps;
        }
        List<YmlNode> nodes = root.seq("steps");
        for (int i = 0; i < nodes.size(); i++) {
            YmlNode node = nodes.get(i);
            if (!(node instanceof YmlMap stepMap)) {
                errors.add(node.at(), "steps[" + i + "]", "шаг должен быть разделом");
                continue;
            }
            readStep(stepMap, "steps[" + i + "]", errors).ifPresent(steps::add);
        }
        return steps;
    }

    private static Optional<Step> readStep(YmlMap body, String path, ContentErrors errors) {
        Optional<TargetSpec> target = readTarget(body, path, errors);
        int delay = body.integer("delay", 0, 12_000, 0);
        List<Action> actions = readActions(body, path, errors);

        if (target.isEmpty() || actions.isEmpty()) {
            if (actions.isEmpty()) {
                errors.add(body.at(), path + ".do", "шаг без действий бессмыслен");
            }
            return Optional.empty();
        }
        return Optional.of(new Step(target.get(), actions, delay));
    }

    private static Optional<TargetSpec> readTarget(YmlMap body, String path, ContentErrors errors) {
        Optional<YmlMap> targetMap = body.map("target");
        if (targetMap.isEmpty()) {
            return Optional.empty();
        }
        YmlMap t = targetMap.get();
        TargetSpec.Type type = t.enumOf("type", TargetSpec.Type.class, null);
        NumberRef radius = number(t, "radius", errors, path + ".target", null);
        NumberRef angle = number(t, "angle", errors, path + ".target", null);

        if (type == null) {
            errors.add(t.at(), path + ".target.type", "обязательный ключ type отсутствует");
            return Optional.empty();
        }
        if (type.needsRadius() && radius == null) {
            errors.add(t.at(), path + ".target.radius",
                    "для цели " + type.name().toLowerCase(Locale.ROOT) + " нужен radius");
            return Optional.empty();
        }
        if (type.needsAngle() && angle == null) {
            errors.add(t.at(), path + ".target.angle", "для конуса нужен angle");
            return Optional.empty();
        }
        return Optional.of(new TargetSpec(type, radius, angle));
    }

    // ------------------------------------------------------------------ действия

    private static List<Action> readActions(YmlMap body, String path, ContentErrors errors) {
        List<Action> actions = new ArrayList<>();
        if (body.rawKind("do") == YmlMap.Kind.ABSENT) {
            return actions;
        }
        List<YmlNode> nodes = body.seq("do");
        for (int i = 0; i < nodes.size(); i++) {
            YmlNode node = nodes.get(i);
            String actionPath = path + ".do[" + i + "]";
            if (!(node instanceof YmlMap map)) {
                errors.add(node.at(), actionPath, "действие должно быть разделом");
                continue;
            }
            readAction(map, actionPath, errors).ifPresent(actions::add);
        }
        return actions;
    }

    private static Optional<Action> readAction(YmlMap body, String path, ContentErrors errors) {
        String kind = body.str("action");
        return switch (kind) {
            case "damage" -> {
                NumberRef amount = number(body, "amount", errors, path, null);
                DamageSchool school = body.enumOf("school", DamageSchool.class, DamageSchool.MAGIC);
                if (amount == null) {
                    errors.add(body.at(), path + ".amount", "обязательный ключ amount отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Damage(amount, school));
            }
            case "heal" -> {
                NumberRef amount = number(body, "amount", errors, path, null);
                if (amount == null) {
                    errors.add(body.at(), path + ".amount", "обязательный ключ amount отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Heal(amount));
            }
            case "status" -> {
                String statusId = body.str("id", "");
                NumberRef duration = number(body, "duration", errors, path, null);
                NumberRef amount = number(body, "amount", errors, path, null);
                if (statusId.isBlank()) {
                    errors.add(body.at(), path + ".id", "обязательный ключ id отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.ApplyStatus(statusId, duration, amount));
            }
            case "remove-status" -> {
                String statusId = body.str("id", "");
                if (statusId.isBlank()) {
                    errors.add(body.at(), path + ".id", "обязательный ключ id отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.RemoveStatus(statusId));
            }
            case "message" -> {
                String text = body.str("text", "");
                if (text.isBlank()) {
                    errors.add(body.at(), path + ".text", "обязательный ключ text отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Message(text));
            }
            case "" -> {
                errors.add(body.at(), path + ".action", "обязательный ключ action отсутствует");
                yield Optional.empty();
            }
            default -> {
                errors.add(body.at(), path + ".action",
                        "неизвестное действие \"" + kind + "\", допустимы: "
                                + "damage, heal, status, remove-status, message");
                yield Optional.empty();
            }
        };
    }

    // ------------------------------------------------------------------ числа

    /**
     * Читает число или ссылку {@code $ключ}.
     *
     * @param fallback что вернуть, если ключа нет; {@code null} означает
     *                 «ключ необязателен, вызывающий решит сам»
     */
    private static NumberRef number(YmlMap body, String key, ContentErrors errors,
                                    String path, NumberRef fallback) {
        if (body.rawKind(key) == YmlMap.Kind.ABSENT) {
            body.str(key, "");  // помечаем прочитанным, иначе будет "неизвестный ключ"
            return fallback;
        }
        String raw = body.str(key, "");
        try {
            return NumberRef.parse(raw);
        } catch (NumberFormatException e) {
            errors.add(sourceOf(body, key), path + "." + key,
                    "ожидалось число или ссылка вида $ключ, получено \"" + raw + "\"");
            return fallback;
        }
    }

    private static SourceRef sourceOf(YmlMap body, String key) {
        return body.at();
    }
}
