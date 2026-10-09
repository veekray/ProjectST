package ru.projectst.rpgcore.cast;

/**
 * Итог попытки применить навык.
 *
 * <p>Главное требование проекта к этому месту: нажатие, не приведшее к касту,
 * обязано назвать причину. В старом стеке их было сразу несколько — не хватило
 * маны, идёт перезарядка, висит {@code Silance}, навык не изучен, — и все они
 * выглядели одинаково: ничего не произошло.
 *
 * @param kind   что случилось
 * @param detail пояснение с числами, готовое для показа игроку
 */
public record CastOutcome(Kind kind, String detail) {

    public enum Kind {
        /** Навык применён, ресурсы списаны, перезарядка запущена. */
        CAST,
        /** У игрока не выбран класс. */
        NO_CLASS,
        /** Навык не загружен. */
        UNKNOWN_SKILL,
        /** Навык принадлежит другому классу. */
        WRONG_CLASS,
        /** Навык не изучен. */
        NOT_UNLOCKED,
        /** Слот пуст. */
        EMPTY_SLOT,
        /** Номер слота вне диапазона класса. */
        BAD_SLOT,
        /** Идёт перезарядка. */
        ON_COOLDOWN,
        /** Не хватает ресурса: маны у мага, выносливости у плута. */
        NOT_ENOUGH_RESOURCE,
        /** Каст запрещён действующим статусом. */
        BLOCKED,
        /** Навык срабатывает сам, вручную его не применить. */
        NOT_MANUAL,
        /** Уже идёт подготовка другого навыка: второй ждёт, пока она кончится. */
        BUSY
    }

    public boolean succeeded() {
        return kind == Kind.CAST;
    }

    public static CastOutcome cast() {
        return new CastOutcome(Kind.CAST, null);
    }

    public static CastOutcome of(Kind kind) {
        return new CastOutcome(kind, null);
    }

    public static CastOutcome of(Kind kind, String detail) {
        return new CastOutcome(kind, detail);
    }

    @Override
    public String toString() {
        return detail == null ? kind.name() : kind + ": " + detail;
    }
}
