package ru.projectst.rpgcore.skill;

import java.util.Collection;
import ru.projectst.rpgcore.balance.BalanceBook;
import ru.projectst.rpgcore.balance.BalanceTable;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.loader.SourceRef;
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
        for (SkillDef skill : skills) {
            SourceRef where = SourceRef.ofFile(skill.id() + ".yml");
            BalanceTable table = balance.table(skill.id());

            checkBalance(skill, where, table, "mana", skill.manaCost(), errors);
            checkBalance(skill, where, table, "cooldown", skill.cooldown(), errors);

            if (!classIds.isEmpty() && !classIds.contains(skill.classId())) {
                errors.add(where, "class",
                        "навык ссылается на несуществующий класс \"" + skill.classId() + "\"");
            }

            for (int s = 0; s < skill.steps().size(); s++) {
                Step step = skill.steps().get(s);
                String stepPath = "steps[" + s + "]";

                checkBalance(skill, where, table, stepPath + ".target.radius",
                        step.target().radius(), errors);
                checkBalance(skill, where, table, stepPath + ".target.angle",
                        step.target().angle(), errors);

                for (int a = 0; a < step.actions().size(); a++) {
                    Action action = step.actions().get(a);
                    String path = stepPath + ".do[" + a + "]";
                    checkAction(skill, where, table, statuses, action, path, errors);
                }
            }
        }
    }

    private static void checkAction(SkillDef skill, SourceRef where, BalanceTable table,
                                    StatusRegistry statuses, Action action, String path,
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
