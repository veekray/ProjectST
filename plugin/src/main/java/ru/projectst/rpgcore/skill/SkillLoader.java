package ru.projectst.rpgcore.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import ru.projectst.rpgcore.damage.DamageSchool;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.loader.YmlDoc;
import ru.projectst.rpgcore.loader.YmlMap;
import ru.projectst.rpgcore.loader.YmlNode;
import ru.projectst.rpgcore.stat.StatOp;

/**
 * Читает файл навыка.
 *
 * <pre>
 * id: mage_mana_bolt
 * class: mage
 * tier: 2
 * mana: $mana
 * cooldown: $cooldown
 *
 * steps:
 *   - target: { type: enemies_in_radius, radius: $radius }
 *     if:
 *       - { caster: has-status, value: empowered }
 *     do:
 *       - { action: damage, amount: $damage, school: magic }
 * </pre>
 *
 * <p>Числа со знаком доллара — ссылки в слой баланса, их существование
 * проверяет {@link SkillLinker}.
 */
public final class SkillLoader {

    private static final String ACTIONS =
            "damage, heal, status, remove-status, modify-stat, potion, push, pull, "
                    + "teleport, particles, sound, message, cast, ray";

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
        // остальные непрочитанными, и они будут названы неизвестными.
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
        List<Condition> conditions = readConditions(body, path, errors);
        List<Action> actions = readActions(body, path, errors);

        if (actions.isEmpty()) {
            errors.add(body.at(), path + ".do", "шаг без действий бессмыслен");
        }
        if (target.isEmpty() || actions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Step(target.get(), actions, conditions, delay));
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

    // ------------------------------------------------------------------ условия

    private static List<Condition> readConditions(YmlMap body, String path, ContentErrors errors) {
        List<Condition> out = new ArrayList<>();
        if (body.rawKind("if") == YmlMap.Kind.ABSENT) {
            return out;
        }
        List<YmlNode> nodes = body.seq("if");
        for (int i = 0; i < nodes.size(); i++) {
            String p = path + ".if[" + i + "]";
            if (!(nodes.get(i) instanceof YmlMap map)) {
                errors.add(nodes.get(i).at(), p, "условие должно быть разделом");
                continue;
            }
            readCondition(map, p, errors).ifPresent(out::add);
        }
        return out;
    }

    private static Optional<Condition> readCondition(YmlMap body, String path,
                                                     ContentErrors errors) {
        // Чей это: caster или target. Написано ключом, а не угадывается —
        // в старом стеке инлайновое условие всегда проверяло кастера, но
        // выглядело так, будто проверяет цель.
        String casterCheck = body.str("caster", "");
        String targetCheck = body.str("target", "");
        String value = body.str("value", "");
        boolean negated = body.bool("not", false);

        if (!casterCheck.isEmpty() == !targetCheck.isEmpty()) {
            errors.add(body.at(), path,
                    "у условия должен быть ровно один из ключей caster или target");
            return Optional.empty();
        }
        Condition.Scope scope = casterCheck.isEmpty()
                ? Condition.Scope.TARGET : Condition.Scope.CASTER;
        String raw = casterCheck.isEmpty() ? targetCheck : casterCheck;

        Condition.Check check = switch (raw) {
            case "has-status" -> Condition.Check.HAS_STATUS;
            case "status-stacks" -> Condition.Check.STATUS_STACKS;
            case "is-player" -> Condition.Check.IS_PLAYER;
            case "chance" -> Condition.Check.CHANCE;
            default -> null;
        };
        if (check == null) {
            errors.add(body.at(), path, "неизвестная проверка \"" + raw
                    + "\", допустимы: has-status, status-stacks, is-player, chance");
            return Optional.empty();
        }
        if (check != Condition.Check.IS_PLAYER && value.isBlank()) {
            errors.add(body.at(), path + ".value", "проверке " + raw + " нужен аргумент");
            return Optional.empty();
        }
        return Optional.of(new Condition(scope, check, value, negated));
    }

    // ------------------------------------------------------------------ действия

    private static List<Action> readActions(YmlMap body, String path, ContentErrors errors) {
        List<Action> actions = new ArrayList<>();
        if (body.rawKind("do") == YmlMap.Kind.ABSENT) {
            return actions;
        }
        List<YmlNode> nodes = body.seq("do");
        for (int i = 0; i < nodes.size(); i++) {
            String p = path + ".do[" + i + "]";
            if (!(nodes.get(i) instanceof YmlMap map)) {
                errors.add(nodes.get(i).at(), p, "действие должно быть разделом");
                continue;
            }
            readAction(map, p, errors).ifPresent(actions::add);
        }
        return actions;
    }

    private static Optional<Action> readAction(YmlMap b, String path, ContentErrors errors) {
        String kind = b.str("action");
        return switch (kind) {
            case "damage" -> require(b, path, errors, "amount", amount ->
                    new Action.Damage(amount,
                            b.enumOf("school", DamageSchool.class, DamageSchool.MAGIC)));

            case "heal" -> require(b, path, errors, "amount", Action.Heal::new);

            case "status" -> {
                String statusId = b.str("id", "");
                NumberRef duration = number(b, "duration", errors, path, null);
                NumberRef amount = number(b, "amount", errors, path, null);
                if (statusId.isBlank()) {
                    errors.add(b.at(), path + ".id", "обязательный ключ id отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.ApplyStatus(statusId, duration, amount));
            }

            case "remove-status" -> {
                String statusId = b.str("id", "");
                if (statusId.isBlank()) {
                    errors.add(b.at(), path + ".id", "обязательный ключ id отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.RemoveStatus(statusId));
            }

            case "modify-stat" -> {
                String statId = b.str("stat", "");
                StatOp op = b.enumOf("op", StatOp.class, StatOp.FLAT);
                NumberRef value = number(b, "value", errors, path, null);
                NumberRef duration = number(b, "duration", errors, path, null);
                if (statId.isBlank() || value == null) {
                    errors.add(b.at(), path, "нужны ключи stat и value");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.ModifyStat(statId, op, value, duration));
            }

            case "potion" -> {
                String effect = b.str("effect", "");
                NumberRef duration = number(b, "duration", errors, path, null);
                int amplifier = b.integer("amplifier", 0, 10, 0);
                if (effect.isBlank() || duration == null) {
                    errors.add(b.at(), path, "нужны ключи effect и duration");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Potion(effect, duration, amplifier));
            }

            case "push" -> {
                NumberRef strength = number(b, "strength", errors, path, null);
                NumberRef lift = number(b, "lift", errors, path, null);
                if (strength == null) {
                    errors.add(b.at(), path + ".strength", "обязательный ключ strength отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Push(strength, lift));
            }

            case "pull" -> {
                NumberRef strength = number(b, "strength", errors, path, null);
                int ticks = b.integer("ticks", 1, 20, 3);
                if (strength == null) {
                    errors.add(b.at(), path + ".strength", "обязательный ключ strength отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Pull(strength, ticks));
            }

            case "teleport" -> require(b, path, errors, "forward", Action.Teleport::new);

            case "particles" -> {
                String particle = b.str("particle", "");
                Action.Particles.Shape shape =
                        b.enumOf("shape", Action.Particles.Shape.class, Action.Particles.Shape.POINT);
                NumberRef count = number(b, "count", errors, path, new NumberRef.Literal(10));
                NumberRef size = number(b, "size", errors, path, null);
                boolean atOrigin = b.bool("at-origin", false);
                if (particle.isBlank()) {
                    errors.add(b.at(), path + ".particle", "обязательный ключ particle отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Particles(particle, shape, count, size, atOrigin));
            }

            case "sound" -> {
                String sound = b.str("sound", "");
                double volume = b.number("volume", 0, 10, 1);
                double pitch = b.number("pitch", 0.1, 2, 1);
                boolean atOrigin = b.bool("at-origin", false);
                if (sound.isBlank()) {
                    errors.add(b.at(), path + ".sound", "обязательный ключ sound отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Sound(sound, volume, pitch, atOrigin));
            }

            case "message" -> {
                String text = b.str("text", "");
                if (text.isBlank()) {
                    errors.add(b.at(), path + ".text", "обязательный ключ text отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Message(text));
            }

            case "cast" -> {
                String skillId = b.str("skill", "");
                boolean atTargets = b.bool("at-targets", false);
                if (skillId.isBlank()) {
                    errors.add(b.at(), path + ".skill", "обязательный ключ skill отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Cast(skillId, atTargets));
            }

            case "ray" -> {
                NumberRef range = number(b, "range", errors, path, null);
                String onHit = b.str("on-hit", "");
                boolean stopAtEntity = b.bool("stop-at-entity", true);
                if (range == null || onHit.isBlank()) {
                    errors.add(b.at(), path, "нужны ключи range и on-hit");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Ray(range, onHit, stopAtEntity));
            }

            case "" -> {
                errors.add(b.at(), path + ".action", "обязательный ключ action отсутствует");
                yield Optional.empty();
            }

            default -> {
                errors.add(b.at(), path + ".action",
                        "неизвестное действие \"" + kind + "\", допустимы: " + ACTIONS);
                yield Optional.empty();
            }
        };
    }

    /** Действие с единственным обязательным числом. */
    private static Optional<Action> require(YmlMap b, String path, ContentErrors errors,
                                            String key,
                                            java.util.function.Function<NumberRef, Action> build) {
        NumberRef value = number(b, key, errors, path, null);
        if (value == null) {
            errors.add(b.at(), path + "." + key, "обязательный ключ " + key + " отсутствует");
            return Optional.empty();
        }
        return Optional.of(build.apply(value));
    }

    // ------------------------------------------------------------------ числа

    private static NumberRef number(YmlMap body, String key, ContentErrors errors,
                                    String path, NumberRef fallback) {
        if (body.rawKind(key) == YmlMap.Kind.ABSENT) {
            body.str(key, ""); // помечаем прочитанным, иначе будет «неизвестный ключ»
            return fallback;
        }
        String raw = body.str(key, "");
        try {
            return NumberRef.parse(raw);
        } catch (NumberFormatException e) {
            errors.add(body.at(), path + "." + key,
                    "ожидалось число или ссылка вида $ключ, получено \"" + raw + "\"");
            return fallback;
        }
    }
}
