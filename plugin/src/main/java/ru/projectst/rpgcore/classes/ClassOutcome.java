package ru.projectst.rpgcore.classes;

/**
 * Итоги операций с классом.
 *
 * <p>Та же философия, что у статусов: отказ всегда назван и объясним. Игрок,
 * нажавший «изучить» и не получивший ничего, должен видеть причину, а не
 * гадать — не хватило уровня, очков или навык вообще не от его класса.
 */
public final class ClassOutcome {

    private ClassOutcome() {
    }

    /** Результат попытки изучить навык. */
    public record Unlock(Kind kind, String detail) {

        public enum Kind {
            UNLOCKED,
            /** Уже изучен: повторная трата очка не происходит. */
            ALREADY_UNLOCKED,
            /** Навык принадлежит другому классу. */
            WRONG_CLASS,
            /** Уровень игрока ниже требуемого ступенью. */
            LEVEL_TOO_LOW,
            /** Нет свободных очков. */
            NO_POINTS,
            /** Навык не загружен. */
            UNKNOWN_SKILL,
            /** У игрока не выбран класс. */
            NO_CLASS
        }

        public boolean succeeded() {
            return kind == Kind.UNLOCKED;
        }

        @Override
        public String toString() {
            return detail == null ? kind.name() : kind + ": " + detail;
        }
    }

    /** Результат попытки повесить навык на слот. */
    public record Bind(Kind kind, String detail) {

        public enum Kind {
            BOUND,
            /** Слот занят, прежний навык заменён. */
            REPLACED,
            /** Навык не изучен. */
            NOT_UNLOCKED,
            /** Номер слота вне диапазона класса. */
            BAD_SLOT,
            UNKNOWN_SKILL,
            NO_CLASS
        }

        public boolean succeeded() {
            return kind == Kind.BOUND || kind == Kind.REPLACED;
        }

        @Override
        public String toString() {
            return detail == null ? kind.name() : kind + ": " + detail;
        }
    }

    /** Результат вложения очка в уровень изученного навыка. */
    public record Upgrade(Kind kind, String detail) {

        public enum Kind {
            UPGRADED,
            /** Навык не изучен: вкладывать очки некуда. */
            NOT_UNLOCKED,
            /** Уровень уже максимальный. */
            MAX_LEVEL,
            /** Нет свободных очков. */
            NO_POINTS,
            UNKNOWN_SKILL,
            NO_CLASS
        }

        public boolean succeeded() {
            return kind == Kind.UPGRADED;
        }

        @Override
        public String toString() {
            return detail == null ? kind.name() : kind + ": " + detail;
        }
    }

    /**
     * Результат начисления опыта.
     *
     * @param levelsGained сколько уровней взято этим начислением
     * @param pointsGained сколько очков навыков выдано
     * @param level        уровень после начисления
     * @param xp           остаток опыта на текущем уровне
     * @param atMaxLevel   предел достигнут, опыт больше не копится
     */
    public record Experience(int levelsGained, int pointsGained, int level, double xp,
                             boolean atMaxLevel) {

        public boolean leveledUp() {
            return levelsGained > 0;
        }

        @Override
        public String toString() {
            if (atMaxLevel) {
                return "предел уровня " + level;
            }
            return leveledUp()
                    ? "уровень " + level + " (+" + pointsGained + " очк.)"
                    : "опыт " + Math.round(xp);
        }
    }
}
