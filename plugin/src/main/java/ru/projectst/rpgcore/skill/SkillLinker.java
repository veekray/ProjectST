package ru.projectst.rpgcore.skill;

import java.util.Collection;
import java.util.List;
import ru.projectst.rpgcore.balance.BalanceBook;
import ru.projectst.rpgcore.balance.BalanceTable;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.loader.SourceRef;
import ru.projectst.rpgcore.stat.StatRegistry;
import ru.projectst.rpgcore.status.StatusRegistry;

/**
 * Фаза связывания: проверяет ссылки между доменами.
 *
 * <p>До этого момента проверять было нечего — перекрёстных ссылок в контенте не
 * существовало. Теперь их три вида, и каждая способна сломать навык молча:
 * ссылка на ключ баланса, ссылка на статус, ссылка на класс. Все три
 * обнаруживаются здесь, то есть командой {@code /rpg validate}, а не игроком.
 *
 * <p>Ровно этого не умел старый стек: навык, ссылающийся на несуществующий
 * метаскилл, просто ничего не делал, и в логах не было ни строки. Граф по
 * конфигам сервера нашёл двадцать четыре таких ссылки, четыре из них — боевые
 * навыки воина.
 */
public final class SkillLinker {

    private SkillLinker() {
    }

    /**
     * @param skills   загруженные навыки
     * @param balance  слой баланса
     * @param statuses реестр статусов
     * @param classIds известные классы; пустая коллекция означает, что классы
     *                 ещё не загружены и проверять их рано
     */
    public static void link(Collection<SkillDef> skills, BalanceBook balance,
                            StatusRegistry statuses, Collection<String> classIds,
                            ContentErrors errors) {
        link(skills, balance, statuses, classIds, null, errors);
    }

    public static void link(Collection<SkillDef> skills, BalanceBook balance,
                            StatusRegistry statuses, Collection<String> classIds,
                            StatRegistry stats, ContentErrors errors) {
        java.util.Set<String> skillIds = new java.util.LinkedHashSet<>();
        skills.forEach(s -> skillIds.add(s.id()));

        // Счётчики, которые вообще кто-нибудь заполняет. Ссылка на незаполняемый
        // счётчик даёт нулевой урон — это ровно тот сорт тихого отказа, из-за
        // которого проект затевался, поэтому ловим его здесь.
        java.util.Set<String> writtenCounters = new java.util.LinkedHashSet<>();
        for (SkillDef skill : skills) {
            for (Step step : skill.steps()) {
                for (Action action : step.actions()) {
                    if (action instanceof Action.ConsumeZones c) {
                        writtenCounters.add(c.counter());
                    }
                    if (action instanceof Action.Count c) {
                        writtenCounters.add(c.counter());
                    }
                }
            }
        }
        for (SkillDef skill : skills) {
            SourceRef where = SourceRef.ofFile(skill.id() + ".yml");
            BalanceTable table = balance.table(skill.id());

            checkBalance(skill, where, table, "mana", skill.manaCost(), errors);
            checkBalance(skill, where, table, "cooldown", skill.cooldown(), errors);

            // Навык без класса — умение предмета; его класс проверять нечем.
            if (!classIds.isEmpty() && !skill.classId().isBlank()
                    && !classIds.contains(skill.classId())) {
                errors.add(where, "class",
                        "навык ссылается на несуществующий класс \"" + skill.classId() + "\"");
            }

            for (int s = 0; s < skill.steps().size(); s++) {
                Step step = skill.steps().get(s);
                String stepPath = "steps[" + s + "]";

                checkBalance(skill, where, table, stepPath + ".delay", step.delay(), errors);
                checkBalance(skill, where, table, stepPath + ".origin",
                        step.origin().distance(), errors);
                checkBalance(skill, where, table, stepPath + ".target.radius",
                        step.target().radius(), errors);
                checkBalance(skill, where, table, stepPath + ".target.angle",
                        step.target().angle(), errors);

                // Цель, которой нужна точка действия, не имеет смысла в навыке,
                // который её не задаёт. Это ловится здесь, а не пустым списком
                // целей в бою.
                if (step.target().type().needsOrigin() && !providesOrigin(skills, skill)) {
                    errors.add(where, stepPath + ".target.type",
                            "цель " + step.target().type().name().toLowerCase(java.util.Locale.ROOT)
                                    + " требует точку действия, но навык её не задаёт: "
                                    + "его должен вызывать ray или cast");
                }

                for (int a = 0; a < step.actions().size(); a++) {
                    Action action = step.actions().get(a);
                    String path = stepPath + ".do[" + a + "]";
                    checkAction(skill, where, table, statuses, stats, skillIds, action, path, errors);
                    checkCounters(where, action, path, writtenCounters, errors);
                }
            }
        }
    }

    /**
     * Навык, вызываемый лучом или другим навыком, точку действия получает
     * извне. Отличить такой от самостоятельного по одному файлу нельзя,
     * поэтому проверка мягкая: ошибка только если на него никто не ссылается.
     */
    private static boolean providesOrigin(Collection<SkillDef> all, SkillDef skill) {
        for (SkillDef other : all) {
            for (Step step : other.steps()) {
                for (Action action : step.actions()) {
                    if (action instanceof Action.Ray r && r.onHit().equals(skill.id())) {
                        return true;
                    }
                    if (action instanceof Action.Projectile p
                            && (skill.id().equals(p.onHit())
                                || skill.id().equals(p.onEnd()))) {
                        return true;
                    }
                    if (action instanceof Action.PlaceZone z
                            && (skill.id().equals(z.onEnter())
                                || skill.id().equals(z.onTick()))) {
                        return true;
                    }
                    if (action instanceof Action.Cast c && c.skillId().equals(skill.id())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Ссылки на счётчики: заполняет ли их хоть кто-нибудь. */
    private static void checkCounters(SourceRef where, Action action, String path,
                                      java.util.Set<String> written, ContentErrors errors) {
        for (NumberRef ref : numbersOf(action)) {
            if (ref == null) {
                continue;
            }
            String counter = ref.counterName();
            if (counter != null && !written.contains(counter)) {
                errors.add(where, path, "число умножается на счётчик \"" + counter
                        + "\", который ничем не заполняется");
            }
        }
    }

    private static List<NumberRef> numbersOf(Action action) {
        return switch (action) {
            case Action.Damage a -> List.of(a.amount());
            case Action.Heal a -> List.of(a.amount());
            case Action.ApplyStatus a -> refs(a.duration(), a.amount());
            case Action.ModifyStat a -> refs(a.value(), a.duration());
            case Action.Potion a -> refs(a.duration());
            case Action.ClearPotion ignored -> List.of();
            case Action.Push a -> refs(a.strength(), a.lift());
            case Action.Pull a -> refs(a.strength());
            case Action.Teleport a -> refs(a.forward());
            case Action.Particles a -> refs(a.count(), a.size());
            case Action.Ray a -> refs(a.range());
            case Action.Projectile a ->
                    refs(a.speed(), a.range(), a.hitRadius(), a.gravity());
            case Action.Summon a -> refs(a.count(), a.duration(), a.health());
            case Action.Dismiss ignored -> List.of();
            case Action.Dash a -> refs(a.strength(), a.lift());
            case Action.Approach a -> refs(a.distance());
            case Action.Restore a -> refs(a.amount());
            case Action.Count ignored -> List.of();
            case Action.PlaceZone a -> refs(a.radius(), a.duration(), a.minGap());
            case Action.ConsumeZones a -> refs(a.radius());
            case Action.RemoveStatus ignored -> List.of();
            case Action.Sound ignored -> List.of();
            case Action.Message ignored -> List.of();
            case Action.Cast ignored -> List.of();
            case Action.Swap ignored -> List.of();
            case Action.Scatter a -> refs(a.radius());
            case Action.ClearThreat a -> refs(a.radius());
            case Action.ResetCooldown ignored -> List.of();
        };
    }

    private static List<NumberRef> refs(NumberRef... values) {
        List<NumberRef> out = new java.util.ArrayList<>();
        for (NumberRef value : values) {
            if (value != null) {
                out.add(value);
            }
        }
        return out;
    }

    private static void checkAction(SkillDef skill, SourceRef where, BalanceTable table,
                                    StatusRegistry statuses, StatRegistry stats,
                                    java.util.Set<String> skillIds, Action action, String path,
                                    ContentErrors errors) {
        switch (action) {
            case Action.Damage d ->
                    checkBalance(skill, where, table, path + ".amount", d.amount(), errors);
            case Action.Heal h ->
                    checkBalance(skill, where, table, path + ".amount", h.amount(), errors);
            case Action.ApplyStatus s -> {
                checkBalance(skill, where, table, path + ".duration", s.duration(), errors);
                checkBalance(skill, where, table, path + ".amount", s.amount(), errors);
                checkStatus(statuses, s.statusId(), where, path + ".id", errors);
            }
            case Action.RemoveStatus r ->
                    checkStatus(statuses, r.statusId(), where, path + ".id", errors);
            case Action.ModifyStat m -> {
                checkBalance(skill, where, table, path + ".value", m.value(), errors);
                checkBalance(skill, where, table, path + ".duration", m.duration(), errors);
                if (stats != null && !stats.has(m.statId())) {
                    errors.add(where, path + ".stat",
                            "ссылка на необъявленный стат \"" + m.statId() + "\"");
                }
            }
            case Action.Potion p ->
                    checkBalance(skill, where, table, path + ".duration", p.duration(), errors);
            case Action.ClearPotion ignored -> {
                // ссылок не содержит
            }
            case Action.Push p -> {
                checkBalance(skill, where, table, path + ".strength", p.strength(), errors);
                checkBalance(skill, where, table, path + ".lift", p.lift(), errors);
            }
            case Action.Pull p ->
                    checkBalance(skill, where, table, path + ".strength", p.strength(), errors);
            case Action.Teleport t ->
                    checkBalance(skill, where, table, path + ".forward", t.forward(), errors);
            case Action.Particles p -> {
                checkBalance(skill, where, table, path + ".count", p.count(), errors);
                checkBalance(skill, where, table, path + ".size", p.size(), errors);
            }
            case Action.Cast c -> {
                if (!skillIds.contains(c.skillId())) {
                    errors.add(where, path + ".skill",
                            "ссылка на несуществующий навык \"" + c.skillId() + "\"");
                }
                if (c.skillId().equals(skill.id())) {
                    errors.add(where, path + ".skill", "навык вызывает сам себя");
                }
            }
            case Action.Swap ignored -> {
                // ссылок не содержит
            }
            case Action.Scatter sc ->
                    checkBalance(skill, where, table, path + ".radius", sc.radius(), errors);
            case Action.ClearThreat ct ->
                    checkBalance(skill, where, table, path + ".radius", ct.radius(), errors);
            case Action.ResetCooldown rc -> {
                // Сброс перезарядки несуществующего навыка молча не делал бы
                // ничего — ровно тот класс ошибок, ради которого всё затевалось.
                if (!skillIds.contains(rc.skillId())) {
                    errors.add(where, path + ".skill",
                            "ссылка на несуществующий навык \"" + rc.skillId() + "\"");
                }
            }
            case Action.Ray r -> {
                checkBalance(skill, where, table, path + ".range", r.range(), errors);
                if (!skillIds.contains(r.onHit())) {
                    errors.add(where, path + ".on-hit",
                            "ссылка на несуществующий навык \"" + r.onHit() + "\"");
                }
            }
            case Action.Projectile p -> {
                checkBalance(skill, where, table, path + ".speed", p.speed(), errors);
                checkBalance(skill, where, table, path + ".range", p.range(), errors);
                checkBalance(skill, where, table, path + ".hit-radius", p.hitRadius(), errors);
                checkBalance(skill, where, table, path + ".gravity", p.gravity(), errors);
                if (p.onHit() != null && !skillIds.contains(p.onHit())) {
                    errors.add(where, path + ".on-hit",
                            "ссылка на несуществующий навык \"" + p.onHit() + "\"");
                }
                if (p.onEnd() != null && !skillIds.contains(p.onEnd())) {
                    errors.add(where, path + ".on-end",
                            "ссылка на несуществующий навык \"" + p.onEnd() + "\"");
                }
            }
            case Action.Summon s -> {
                checkBalance(skill, where, table, path + ".count", s.count(), errors);
                checkBalance(skill, where, table, path + ".duration", s.duration(), errors);
                checkBalance(skill, where, table, path + ".health", s.health(), errors);
            }
            case Action.Dismiss ignored -> {
                // ссылок не содержит: тег проверить нечем, своих мобов пока нет
            }
            case Action.Dash d -> {
                checkBalance(skill, where, table, path + ".strength", d.strength(), errors);
                checkBalance(skill, where, table, path + ".lift", d.lift(), errors);
            }
            case Action.Approach a ->
                    checkBalance(skill, where, table, path + ".distance", a.distance(), errors);
            case Action.Restore r ->
                    checkBalance(skill, where, table, path + ".amount", r.amount(), errors);
            case Action.Count c -> {
                if (c.statusId() != null) {
                    checkStatus(statuses, c.statusId(), where, path + ".status", errors);
                }
            }
            case Action.PlaceZone z -> {
                checkBalance(skill, where, table, path + ".radius", z.radius(), errors);
                checkBalance(skill, where, table, path + ".duration", z.duration(), errors);
                checkBalance(skill, where, table, path + ".min-gap", z.minGap(), errors);
                if (z.onEnter() != null && !skillIds.contains(z.onEnter())) {
                    errors.add(where, path + ".on-enter",
                            "ссылка на несуществующий навык \"" + z.onEnter() + "\"");
                }
                if (z.onTick() != null && !skillIds.contains(z.onTick())) {
                    errors.add(where, path + ".on-tick",
                            "ссылка на несуществующий навык \"" + z.onTick() + "\"");
                }
            }
            case Action.ConsumeZones z ->
                    checkBalance(skill, where, table, path + ".radius", z.radius(), errors);
            case Action.Sound ignored -> {
                // ссылок не содержит
            }
            case Action.Message ignored -> {
                // ссылок не содержит
            }
        }
    }

    private static void checkBalance(SkillDef skill, SourceRef where, BalanceTable table,
                                     String path, NumberRef ref, ContentErrors errors) {
        if (ref == null) {
            return;
        }
        String key = ref.balanceKey();
        if (key != null && !table.has(key)) {
            errors.add(where, path, "в балансе навыка " + skill.id()
                    + " нет ключа \"" + key + "\"");
        }
    }

    private static void checkStatus(StatusRegistry statuses, String statusId, SourceRef where,
                                    String path, ContentErrors errors) {
        if (!statuses.has(statusId)) {
            errors.add(where, path, "ссылка на несуществующий статус \"" + statusId + "\"");
        }
    }
}
