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
                    + "teleport, dash, approach, particles, sound, message, cast, ray, "
                    + "projectile, summon, dismiss, zone, consume-zones, restore, count";

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
        SkillTrigger trigger = readTrigger(root, errors);
        int interval = root.integer("every", 1, 12_000, 0);
        boolean internal = root.bool("internal", false);
        String icon = root.str("icon", SkillDef.DEFAULT_ICON);
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
        if (trigger == SkillTrigger.ON_INTERVAL && interval < 1) {
            errors.add(root.at(), "every",
                    "периодическому навыку нужен ключ every: как часто он срабатывает");
            ok = false;
        }
        if (trigger != SkillTrigger.ON_INTERVAL && interval > 0) {
            errors.add(root.at(), "every",
                    "ключ every имеет смысл только при on: interval");
            ok = false;
        }
        if (!ok) {
            return Optional.empty();
        }
        return Optional.of(new SkillDef(id, display, classId, tier, mana, cooldown, steps,
                trigger, interval, internal, icon));
    }

    /**
     * Что запускает навык. Отсутствие ключа — ручное применение: самый частый
     * случай не должен требовать строки.
     */
    private static SkillTrigger readTrigger(YmlMap root, ContentErrors errors) {
        String raw = root.str("on", "");
        if (raw.isBlank()) {
            return SkillTrigger.MANUAL;
        }
        SkillTrigger trigger = switch (raw) {
            case "manual" -> SkillTrigger.MANUAL;
            case "damaged" -> SkillTrigger.ON_DAMAGED;
            case "deal-damage" -> SkillTrigger.ON_DEAL_DAMAGE;
            case "kill" -> SkillTrigger.ON_KILL;
            case "interval" -> SkillTrigger.ON_INTERVAL;
            default -> null;
        };
        if (trigger == null) {
            errors.add(root.at(), "on", "неизвестный триггер \"" + raw
                    + "\", допустимы: manual, damaged, deal-damage, kill, interval");
            return SkillTrigger.MANUAL;
        }
        return trigger;
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
        NumberRef delay = number(body, "delay", errors, path, new NumberRef.Literal(0));
        OriginSpec origin = readOrigin(body, path, errors);
        List<Condition> conditions = readConditions(body, path, errors);
        List<Action> actions = readActions(body, path, errors);

        if (actions.isEmpty()) {
            errors.add(body.at(), path + ".do", "шаг без действий бессмыслен");
        }
        if (target.isEmpty() || actions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Step(target.get(), actions, conditions, origin, delay));
    }

    /**
     * Точка действия шага: {@code origin: self} или {@code origin: forward 9}.
     *
     * <p>Отсутствие ключа означает «точка приходит извне» — от луча, снаряда или
     * вызвавшего навыка. Это не то же самое, что «позиция кастера», и именно
     * поэтому у него отдельное имя: молчаливый откат к кастеру был бы взрывом
     * под ногами вместо взрыва в точке попадания.
     */
    private static OriginSpec readOrigin(YmlMap body, String path, ContentErrors errors) {
        if (body.rawKind("origin") == YmlMap.Kind.ABSENT) {
            body.str("origin", "");
            return OriginSpec.INHERIT;
        }
        String raw = body.str("origin", "").trim();
        if (raw.equals("self")) {
            return OriginSpec.self();
        }
        if (raw.startsWith("forward")) {
            String rest = raw.substring("forward".length()).trim();
            if (rest.isEmpty()) {
                errors.add(body.at(), path + ".origin",
                        "для точки впереди нужна дистанция: origin: forward 9");
                return OriginSpec.INHERIT;
            }
            try {
                return OriginSpec.forward(NumberRef.parse(rest));
            } catch (NumberFormatException e) {
                errors.add(body.at(), path + ".origin",
                        "дистанция точки впереди: " + e.getMessage());
                return OriginSpec.INHERIT;
            }
        }
        errors.add(body.at(), path + ".origin",
                "неизвестная точка действия \"" + raw + "\", допустимы: self, forward <блоков>");
        return OriginSpec.INHERIT;
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
        String tag = t.str("tag", "");
        int limit = t.integer("limit", 1, 100, 0);

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
        if (type.needsTag() && tag.isBlank()) {
            errors.add(t.at(), path + ".target.tag",
                    "для цели " + type.name().toLowerCase(Locale.ROOT) + " нужен tag зоны");
            return Optional.empty();
        }
        if (!type.needsTag() && !tag.isBlank()) {
            errors.add(t.at(), path + ".target.tag",
                    "tag имеет смысл только у цели enemies_near_zone");
            return Optional.empty();
        }
        return Optional.of(new TargetSpec(type, radius, angle,
                tag.isBlank() ? null : tag, limit));
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
            case "in-zone" -> Condition.Check.IN_ZONE;
            case "counter" -> Condition.Check.COUNTER;
            case "has-minion" -> Condition.Check.HAS_MINION;
            case "zone-count" -> Condition.Check.ZONE_COUNT;
            default -> null;
        };
        if (check == null) {
            errors.add(body.at(), path, "неизвестная проверка \"" + raw
                    + "\", допустимы: has-status, status-stacks, is-player, chance, "
                    + "in-zone, zone-count, counter, has-minion");
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
                int stacks = b.integer("stacks", 1, 99, 0);
                if (statusId.isBlank()) {
                    errors.add(b.at(), path + ".id", "обязательный ключ id отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.RemoveStatus(statusId, stacks));
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

            case "clear-potion" -> {
                String effect = b.str("effect", "");
                if (effect.isBlank()) {
                    errors.add(b.at(), path + ".effect", "обязательный ключ effect отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.ClearPotion(effect));
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

            case "dash" -> {
                NumberRef strength = number(b, "strength", errors, path, null);
                NumberRef lift = number(b, "lift", errors, path, null);
                if (strength == null) {
                    errors.add(b.at(), path + ".strength",
                            "обязательный ключ strength отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Dash(strength, lift));
            }

            case "approach" -> {
                NumberRef distance = number(b, "distance", errors, path,
                        new NumberRef.Literal(1.2));
                boolean behind = b.bool("behind", true);
                yield Optional.of(new Action.Approach(distance, behind));
            }

            case "restore" -> require(b, path, errors, "amount", Action.Restore::new);

            case "count" -> {
                String counter = b.str("counter", "");
                String ofStatus = b.str("status", "");
                if (counter.isBlank()) {
                    errors.add(b.at(), path + ".counter",
                            "обязательный ключ counter отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Count(counter,
                        ofStatus.isBlank() ? null : ofStatus));
            }

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

            case "projectile" -> {
                NumberRef speed = number(b, "speed", errors, path, new NumberRef.Literal(1.2));
                NumberRef range = number(b, "range", errors, path, null);
                NumberRef hitRadius = number(b, "hit-radius", errors, path,
                        new NumberRef.Literal(1.2));
                NumberRef gravity = number(b, "gravity", errors, path, new NumberRef.Literal(0));
                int pierce = b.integer("pierce", 1, 20, 1);
                boolean hitPlayers = b.bool("hit-players", true);
                boolean hitMobs = b.bool("hit-mobs", true);
                boolean stopAtBlock = b.bool("stop-at-block", true);
                String particle = b.str("particle", "");
                String onHit = b.str("on-hit", "");
                String onEnd = b.str("on-end", "");
                double yawOffset = b.number("yaw-offset", -180, 180, 0);
                if (range == null || (onHit.isBlank() && onEnd.isBlank())) {
                    errors.add(b.at(), path, "нужны ключ range и хотя бы один из on-hit, on-end");
                    yield Optional.empty();
                }
                if (!hitPlayers && !hitMobs && onEnd.isBlank()) {
                    errors.add(b.at(), path,
                            "снаряд не задевает ни игроков, ни мобов и не имеет on-end: "
                                    + "он ни во что не попадёт и ничего не сделает");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Projectile(speed, range, hitRadius, gravity, pierce,
                        hitPlayers, hitMobs, stopAtBlock,
                        particle.isBlank() ? null : particle,
                        onHit.isBlank() ? null : onHit,
                        onEnd.isBlank() ? null : onEnd, yawOffset));
            }

            case "summon" -> {
                String mob = b.str("mob", "");
                String tag = b.str("tag", "");
                NumberRef count = number(b, "count", errors, path, new NumberRef.Literal(1));
                NumberRef duration = number(b, "duration", errors, path, null);
                NumberRef health = number(b, "health", errors, path, new NumberRef.Literal(0));
                boolean attacks = b.bool("attacks-enemies", true);
                boolean atOrigin = b.bool("at-origin", false);
                if (mob.isBlank() || tag.isBlank() || duration == null) {
                    errors.add(b.at(), path, "нужны ключи mob, tag и duration");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Summon(mob, count, duration, health, tag,
                        attacks, atOrigin));
            }

            case "dismiss" -> {
                String tag = b.str("tag", "");
                if (tag.isBlank()) {
                    errors.add(b.at(), path + ".tag", "обязательный ключ tag отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Dismiss(tag));
            }

            case "zone" -> {
                String tag = b.str("tag", "");
                NumberRef radius = number(b, "radius", errors, path, null);
                NumberRef duration = number(b, "duration", errors, path, null);
                boolean atOrigin = b.bool("at-origin", false);
                String particle = b.str("particle", "");
                NumberRef minGap = number(b, "min-gap", errors, path, null);
                String onEnter = b.str("on-enter", "");
                String onTick = b.str("on-tick", "");
                int tickInterval = b.integer("tick-interval", 1, 1200, 0);
                if (tag.isBlank() || radius == null || duration == null) {
                    errors.add(b.at(), path, "нужны ключи tag, radius и duration");
                    yield Optional.empty();
                }
                if (!onTick.isBlank() && tickInterval < 1) {
                    errors.add(b.at(), path + ".tick-interval",
                            "зоне с on-tick нужен tick-interval");
                    yield Optional.empty();
                }
                if (onTick.isBlank() && tickInterval > 0) {
                    errors.add(b.at(), path + ".tick-interval",
                            "tick-interval имеет смысл только с on-tick");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.PlaceZone(tag, radius, duration, atOrigin,
                        particle.isBlank() ? null : particle, minGap,
                        onEnter.isBlank() ? null : onEnter,
                        onTick.isBlank() ? null : onTick, tickInterval));
            }

            case "consume-zones" -> {
                String tag = b.str("tag", "");
                NumberRef radius = number(b, "radius", errors, path, null);
                String counter = b.str("counter", "");
                boolean ownOnly = b.bool("own-only", true);
                boolean atOrigin = b.bool("at-origin", false);
                if (tag.isBlank() || radius == null || counter.isBlank()) {
                    errors.add(b.at(), path, "нужны ключи tag, radius и counter");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.ConsumeZones(tag, radius, counter, ownOnly,
                        atOrigin));
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
                    "ожидалось число, ссылка вида $ключ или число со счётчиком вида "
                            + "N * @имя, получено \"" + raw + "\"");
            return fallback;
        }
    }
}
