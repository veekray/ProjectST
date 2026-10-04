package ru.projectst.rpgcore.skill;

import ru.projectst.rpgcore.damage.DamageSchool;

/**
 * Примитив, который умеет делать навык.
 *
 * <p>Закрытый набор: новый примитив добавляется сюда и в исполнитель, то есть
 * компилятором. Это и есть обещание «если механики не хватает — она
 * добавляется в код, а не обходится костылём». Обратная сторона — список
 * короткий, и так и задумано: в старом стеке механик было несколько сотен, из
 * которых половина делала почти то же самое чуть иначе.
 */
public sealed interface Action {

    /** Имя для сообщений об ошибках и отладки. */
    String name();

    /** Урон по целям шага. */
    record Damage(NumberRef amount, DamageSchool school) implements Action {
        @Override
        public String name() {
            return "damage";
        }
    }

    /** Лечение целей шага. */
    record Heal(NumberRef amount) implements Action {
        @Override
        public String name() {
            return "heal";
        }
    }

    /**
     * Наложение статуса.
     *
     * @param duration длительность; если {@code null}, берётся из статуса
     * @param amount   полезная нагрузка: запас щита. Может быть {@code null}
     */
    record ApplyStatus(String statusId, NumberRef duration, NumberRef amount) implements Action {
        public ApplyStatus {
            if (statusId == null || statusId.isBlank()) {
                throw new IllegalArgumentException("statusId обязателен");
            }
        }

        @Override
        public String name() {
            return "status";
        }
    }

    /** Снятие статуса с целей шага. */
    record RemoveStatus(String statusId) implements Action {
        @Override
        public String name() {
            return "remove-status";
        }
    }

    /** Сообщение целям шага. Пока единственный способ что-то показать игроку. */
    record Message(String text) implements Action {
        @Override
        public String name() {
            return "message";
        }
    }
}
