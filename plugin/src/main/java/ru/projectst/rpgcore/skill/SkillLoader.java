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
 * cost: $mana
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

    /** Значение {@code size}, означающее радиус выборки шага. */
    static final String SIZE_RADIUS = "radius";

    /** Какими бывают идентификаторы эффектов мода: имя файла в его ресурсах. */
    private static final java.util.regex.Pattern FX_ID =
            java.util.regex.Pattern.compile("[a-z0-9_]{1,48}");

    /** Метка «ключ fx был, но с ошибкой»: действие тогда не собирается. */
    private static final String FX_INVALID = "\0";

    /** Имя звука мода: как событие в его sounds.json. */
    private static final java.util.regex.Pattern SOUND_FX =
            java.util.regex.Pattern.compile("[a-z0-9_]+(\\.[a-z0-9_]+)*");

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
        // Без значения по умолчанию: пустой класс допустим у служебных навыков
        // предметов, и об отсутствии класса там, где он нужен, скажет проверка
        // ниже — своими словами, а не общим «обязательный ключ отсутствует».
        String classId = root.str("class", "");
        int tier = root.integer("tier", 1, 5, 1);
        NumberRef cost = number(root, "cost", errors, "skill", new NumberRef.Literal(0));
        NumberRef stamina = number(root, "stamina", errors, "skill", new NumberRef.Literal(0));
        NumberRef cooldown = number(root, "cooldown", errors, "skill", new NumberRef.Literal(0));
        NumberRef castTime = number(root, "cast-time", errors, "skill",
                new NumberRef.Literal(0));
        SkillTrigger trigger = readTrigger(root, errors);
        int interval = root.integer("every", 1, 12_000, 0);
        boolean internal = root.bool("internal", false);
        boolean innate = root.bool("innate", false);
        int charges = root.integer("charges", 1, 10, 1);
        String icon = root.str("icon", SkillDef.DEFAULT_ICON);
        List<String> description = new ArrayList<>(root.strings("description"));
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
        if (classId.isBlank() && !internal && !innate) {
            errors.add(root.at(), "class", "навык должен принадлежать классу");
            ok = false;
        }
        // Врождённый навык есть у каждого игрока, в том числе у того, кто класс
        // ещё не выбрал. Принадлежать классу он поэтому не может.
        if (innate && !classId.isBlank()) {
            errors.add(root.at(), "class",
                    "врождённый навык есть у всех, поэтому он не принадлежит классу");
            ok = false;
        }
        // Служебный и врождённый — разные вещи: служебный запускает предмет или
        // класс, врождённый нажимает сам игрок. Совмещение означало бы, что
        // непонятно, кто его применяет.
        if (innate && internal) {
            errors.add(root.at(), "innate",
                    "навык не может быть и служебным, и врождённым");
            ok = false;
        }
        if (innate && trigger != SkillTrigger.MANUAL) {
            errors.add(root.at(), "on", "врождённый навык применяет игрок, а не триггер");
            ok = false;
        }
        // Заряд без перезарядки не возвращается никогда, то есть навык с тремя
        // зарядами работает три раза за всю игру. Это ровно тот тихий отказ, от
        // которого проект уходит, поэтому он ловится здесь.
        if (charges > 1 && cooldown instanceof NumberRef.Literal literal
                && literal.value() <= 0) {
            errors.add(root.at(), "charges",
                    "зарядам нужна перезарядка: без неё потраченный заряд не вернётся");
            ok = false;
        }
        // Служебный навык без класса — это умение предмета: его запускает
        // предмет, а не триггер класса. Поэтому класс у него не обязателен, и
        // ровно поэтому такой навык никогда не срабатывает от событий класса.
        if (classId.isBlank() && trigger != SkillTrigger.MANUAL) {
            errors.add(root.at(), "on",
                    "навык без класса не может срабатывать по триггеру: его запускает предмет");
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
        // Подготовку готовит игрок, нажавший клавишу. Пассивку никто не
        // нажимает: каст у неё был бы задержкой, которую некому показать и
        // нечем сорвать честно.
        boolean hasCast = !(castTime instanceof NumberRef.Literal literal
                && literal.value() <= 0);
        if (hasCast && trigger != SkillTrigger.MANUAL) {
            errors.add(root.at(), "cast-time",
                    "подготовка бывает только у навыка, который применяют нажатием");
            ok = false;
        }
        if (!ok) {
            return Optional.empty();
        }
        return Optional.of(new SkillDef(id, display, classId, tier, cost, cooldown, steps,
                trigger, interval, internal, icon, description, stamina, charges, innate,
                castTime));
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
        String telegraph = body.rawKind("telegraph") == YmlMap.Kind.ABSENT ? null
                : body.str("telegraph", "").trim();
        List<Condition> conditions = readConditions(body, path, errors);
        List<Action> actions = readActions(body, path, errors, target.orElse(null));

        if (actions.isEmpty()) {
            errors.add(body.at(), path + ".do", "шаг без действий бессмыслен");
        }
        boolean ok = true;
        if (telegraph != null) {
            if (!FX_ID.matcher(telegraph).matches()) {
                errors.add(body.at(), path + ".telegraph", "эффект называется строчными"
                        + " латинскими буквами, цифрами и подчёркиванием, получено \""
                        + telegraph + "\"");
                ok = false;
            }
            // Предупреждение без задержки — это круг, который появляется
            // одновременно с уроном: от него не уйти, и он ни о чём не
            // предупреждает.
            if (delay instanceof NumberRef.Literal literal && literal.value() <= 0) {
                errors.add(body.at(), path + ".telegraph",
                        "предупреждению нужна задержка: delay — сколько тиков круг виден до удара");
                ok = false;
            }
            // Запомнить можно круг: центр и радиус. Конус от кастера смотрит туда,
            // куда кастер смотрит в момент удара, и запомненный конус бил бы не
            // туда, куда игрок развернулся. Конусам — подготовка каста.
            if (target.isPresent() && !target.get().type().lockable()) {
                errors.add(body.at(), path + ".telegraph",
                        "предупреждение бывает у области-круга: enemies_in_radius или"
                                + " цели у точки; у конуса — подготовка каста");
                ok = false;
            }
        }
        if (target.isEmpty() || actions.isEmpty() || !ok) {
            return Optional.empty();
        }
        return Optional.of(new Step(target.get(), actions, conditions, origin, delay,
                telegraph));
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
                    "для цели " + type.name().toLowerCase(Locale.ROOT) + " нужен tag");
            return Optional.empty();
        }
        if (!type.needsTag() && !tag.isBlank()) {
            errors.add(t.at(), path + ".target.tag",
                    "tag имеет смысл только у целей enemies_near_zone и own_minions");
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
            case "behind" -> Condition.Check.BEHIND;
            case "distance" -> Condition.Check.DISTANCE;
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

    private static List<Action> readActions(YmlMap body, String path, ContentErrors errors,
                                            TargetSpec target) {
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
            readAction(map, p, errors, target).ifPresent(actions::add);
        }
        return actions;
    }

    /**
     * Одно действие шага.
     *
     * @param target цели шага; {@code null}, если их не удалось прочитать. Нужны
     *               действиям, которые рисуют область шага: граница берётся из
     *               той же выборки, а не переписывается числом
     */
    private static Optional<Action> readAction(YmlMap b, String path, ContentErrors errors,
                                               TargetSpec target) {
        String kind = b.str("action");
        return switch (kind) {
            case "damage" -> require(b, path, errors, "amount", amount ->
                    new Action.Damage(amount,
                            b.enumOf("school", DamageSchool.class, DamageSchool.MAGIC),
                            b.enumOf("basis", Action.Basis.class, Action.Basis.FLAT)));

            case "heal" -> require(b, path, errors, "amount", Action.Heal::new);

            case "sacrifice" -> require(b, path, errors, "percent", Action.Sacrifice::new);

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

            case "confuse" -> require(b, path, errors, "radius", Action.Confuse::new);

            case "glow" -> require(b, path, errors, "duration", Action.Glow::new);

            case "disable-shield" ->
                    require(b, path, errors, "duration", Action.DisableShield::new);

            case "remove-status" -> {
                String statusId = b.str("id", "");
                String tag = b.str("tag", "");
                int stacks = b.integer("stacks", 1, 99, 0);
                if (statusId.isBlank() == tag.isBlank()) {
                    errors.add(b.at(), path,
                            "нужен ровно один ключ: id или tag");
                    yield Optional.empty();
                }
                yield Optional.of(statusId.isBlank()
                        ? new Action.RemoveStatus(null, tag, stacks)
                        : new Action.RemoveStatus(statusId, null, stacks));
            }

            case "modify-stat" -> {
                String statId = b.str("stat", "");
                StatOp op = b.enumOf("op", StatOp.class, StatOp.FLAT);
                NumberRef value = number(b, "value", errors, path, null);
                NumberRef duration = number(b, "duration", errors, path, null);
                String statusId = b.str("status", "");
                if (statId.isBlank() || value == null) {
                    errors.add(b.at(), path, "нужны ключи stat и value");
                    yield Optional.empty();
                }
                if (!statusId.isBlank() && duration != null) {
                    // Два срока у одного эффекта: какой из них верный, не скажет
                    // никто, и однажды они разойдутся.
                    errors.add(b.at(), path + ".duration",
                            "надбавка со status живёт сроком статуса: уберите duration");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.ModifyStat(statId, op, value, duration, statusId));
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

            case "teleport" -> {
                NumberRef forward = number(b, "forward", errors, path, null);
                String particle = b.str("particle", "");
                if (forward == null) {
                    errors.add(b.at(), path + ".forward", "обязательный ключ forward отсутствует");
                    yield Optional.empty();
                }
                String fx = readFx(b, path, errors, particle, false);
                if (fx == FX_INVALID) {
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Teleport(forward,
                        particle.isBlank() ? null : particle, fx));
            }

            case "dash" -> {
                NumberRef strength = number(b, "strength", errors, path, null);
                NumberRef lift = number(b, "lift", errors, path, null);
                String direction = b.str("direction", "look");
                if (!direction.equals("look") && !direction.equals("movement")) {
                    errors.add(b.at(), path + ".direction", "неизвестное направление "
                            + direction + ", допустимы: look, movement");
                    yield Optional.empty();
                }
                if (strength == null) {
                    errors.add(b.at(), path + ".strength",
                            "обязательный ключ strength отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Dash(strength, lift,
                        direction.equals("movement")));
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
                boolean fitRadius = b.rawKind("size") == YmlMap.Kind.SCALAR
                        && b.str("size", "").trim().equals(SIZE_RADIUS);
                NumberRef size = fitRadius ? null : number(b, "size", errors, path, null);
                boolean atOrigin = b.bool("at-origin", false);
                if (particle.isBlank()) {
                    errors.add(b.at(), path + ".particle", "обязательный ключ particle отсутствует");
                    yield Optional.empty();
                }
                if (shape == Action.Particles.Shape.CONE) {
                    // Конус — это всегда конус выборки шага: свой угол и свой
                    // радиус у картинки означали бы картинку, которая врёт.
                    if (size != null) {
                        errors.add(b.at(), path + ".size",
                                "у конуса размер — радиус выборки шага: size: radius или без size");
                        yield Optional.empty();
                    }
                    if (target != null && !target.type().isCone()) {
                        errors.add(b.at(), path + ".shape",
                                "конус рисуется только в шаге, который выбирает цели конусом");
                        yield Optional.empty();
                    }
                    fitRadius = true;
                }
                if (fitRadius) {
                    if (target != null && !target.type().needsRadius()) {
                        errors.add(b.at(), path + ".size",
                                "size: radius — радиус выборки шага, а у цели "
                                        + target.type().name().toLowerCase(Locale.ROOT)
                                        + " радиуса нет");
                        yield Optional.empty();
                    }
                    if (atOrigin) {
                        // Центр границы — центр выборки: у радиуса вокруг кастера
                        // это кастер, даже когда у шага есть точка впереди.
                        errors.add(b.at(), path + ".at-origin",
                                "граница по радиусу шага рисуется в центре выборки, "
                                        + "at-origin при ней не задаётся");
                        yield Optional.empty();
                    }
                }
                String fx = readFx(b, path, errors, particle, true);
                if (fx == FX_INVALID) {
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Particles(particle, shape, count, size, fitRadius,
                        atOrigin, fx));
            }

            case "sound" -> {
                String sound = b.str("sound", "");
                double volume = b.number("volume", 0, 10, 1);
                double pitch = b.number("pitch", 0.1, 2, 1);
                boolean atOrigin = b.bool("at-origin", false);
                String fx = b.rawKind("fx") == YmlMap.Kind.ABSENT ? null
                        : b.str("fx", "").trim();
                if (sound.isBlank()) {
                    errors.add(b.at(), path + ".sound", "обязательный ключ sound отсутствует");
                    yield Optional.empty();
                }
                // Звук мода называется как событие в sounds.json: буквы, цифры,
                // подчёркивание и точки — druid.roots.crack.
                if (fx != null && !SOUND_FX.matcher(fx).matches()) {
                    errors.add(b.at(), path + ".fx", "звук мода называется строчными латинскими"
                            + " буквами, цифрами, точками и подчёркиванием, получено \""
                            + fx + "\"");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Sound(sound, volume, pitch, atOrigin, fx));
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
                String fx = readFx(b, path, errors, particle, false);
                if (fx == FX_INVALID) {
                    yield Optional.empty();
                }
                yield Optional.of(new Action.Projectile(speed, range, hitRadius, gravity, pierce,
                        hitPlayers, hitMobs, stopAtBlock,
                        particle.isBlank() ? null : particle,
                        onHit.isBlank() ? null : onHit,
                        onEnd.isBlank() ? null : onEnd, yawOffset, fx));
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
                String fx = readFx(b, path, errors, particle, false);
                if (fx == FX_INVALID) {
                    yield Optional.empty();
                }
                yield Optional.of(new Action.PlaceZone(tag, radius, duration, atOrigin,
                        particle.isBlank() ? null : particle, minGap,
                        onEnter.isBlank() ? null : onEnter,
                        onTick.isBlank() ? null : onTick, tickInterval, fx));
            }

            case "consume-zones" -> {
                String tag = b.str("tag", "");
                NumberRef radius = number(b, "radius", errors, path, null);
                String counter = b.str("counter", "");
                boolean ownOnly = b.bool("own-only", true);
                boolean atOrigin = b.bool("at-origin", false);
                boolean inside = b.bool("inside", false);
                int limit = b.integer("limit", 1, 100, 0);
                if (tag.isBlank() || counter.isBlank()) {
                    errors.add(b.at(), path, "нужны ключи tag и counter");
                    yield Optional.empty();
                }
                // Ровно одно из двух: радиус поиска или «зоны, в которых стоишь».
                // Оба сразу — непонятно, какой из них решает; ни одного — нечем
                // искать.
                if (inside == (radius != null)) {
                    errors.add(b.at(), path + ".radius", inside
                            ? "при inside: true зоны снимаются по своему радиусу, radius не задаётся"
                            : "нужен radius или inside: true");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.ConsumeZones(tag, radius, counter, ownOnly,
                        atOrigin, inside, limit));
            }

            case "swap" -> {
                yield Optional.of(new Action.Swap());
            }

            case "scatter" -> require(b, path, errors, "radius", Action.Scatter::new);

            case "clear-threat" -> require(b, path, errors, "radius", Action.ClearThreat::new);

            case "reset-cooldown" -> {
                String skillId = b.str("skill", "");
                if (skillId.isBlank()) {
                    errors.add(b.at(), path + ".skill", "обязательный ключ skill отсутствует");
                    yield Optional.empty();
                }
                yield Optional.of(new Action.ResetCooldown(skillId));
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

    /**
     * Эффект мода: {@code fx: <id>}.
     *
     * <p>Без ванильной частицы рядом эффект — ошибка, а не тихий пропуск: игрок
     * без мода не увидел бы ничего, и зона без картинки стала бы ловушкой.
     * Есть ли такой эффект у мода, загрузчик не знает и знать не должен —
     * неизвестный эффект мод рисует общим по форме и цвету класса.
     *
     * <p>{@code fx: none} — только у частиц: украшение, которое мод не рисует,
     * потому что рядом в том же шаге уже есть свой эффект. Снаряд, зону или
     * след так спрятать нельзя: у игрока с модом их не стало бы видно вовсе.
     *
     * @param allowNone можно ли {@code none}
     * @return идентификатор, {@code null} без ключа или {@link #FX_INVALID}
     */
    private static String readFx(YmlMap b, String path, ContentErrors errors, String particle,
                                 boolean allowNone) {
        if (b.rawKind("fx") == YmlMap.Kind.ABSENT) {
            b.str("fx", "");
            return null;
        }
        String fx = b.str("fx", "").trim();
        if (!FX_ID.matcher(fx).matches()) {
            errors.add(b.at(), path + ".fx", "эффект называется строчными латинскими буквами, "
                    + "цифрами и подчёркиванием, получено \"" + fx + "\"");
            return FX_INVALID;
        }
        if (!allowNone && FxEvent.NONE.equals(fx)) {
            errors.add(b.at(), path + ".fx", "fx: none бывает только у частиц: "
                    + "снаряд, зона и след у игрока с модом обязаны быть видны");
            return FX_INVALID;
        }
        if (particle == null || particle.isBlank()) {
            errors.add(b.at(), path + ".fx", "у эффекта мода нужна ванильная particle: "
                    + "без неё игрок без мода не увидит ничего");
            return FX_INVALID;
        }
        return fx;
    }

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
