package ru.projectst.rpgcore.status;

/**
 * Итог попытки наложить статус.
 *
 * <p>Главное требование SPEC к статусам: <b>наложение всегда возвращает явный
 * исход</b>. Молчаливого «ничего не произошло» не существует, и каждый исход
 * несёт имя сработавшего правила — именно это показывает команда
 * {@code /rpg why}.
 *
 * @param kind  что произошло
 * @param other статус, из-за которого это произошло, либо {@code null}
 * @param rule  имя правила человеческим языком
 */
public record StatusOutcome(Kind kind, String other, String rule) {

    public enum Kind {
        /** Наложен и действует. */
        APPLIED,
        /** Наложен, тикает, но не действует: его подавляет другой статус. */
        APPLIED_SUPPRESSED,
        /** Уже был активен, длительность сброшена на новую. */
        REFRESHED,
        /** Уже был активен, длительность прибавлена к остатку. */
        EXTENDED,
        /** Уже был активен, добавлен стак. */
        STACKED,
        /** Наложен, вытеснив другой статус той же категории. */
        REPLACED,
        /** Не наложен: мешает активный статус. */
        BLOCKED,
        /** Не наложен и ничего не изменилось: так велит правило наложения. */
        IGNORED
    }

    public boolean succeeded() {
        return switch (kind) {
            case APPLIED, APPLIED_SUPPRESSED, REFRESHED, EXTENDED, STACKED, REPLACED -> true;
            case BLOCKED, IGNORED -> false;
        };
    }

    /** Действует ли статус прямо сейчас: подавленный наложен, но бездействует. */
    public boolean active() {
        return succeeded() && kind != Kind.APPLIED_SUPPRESSED;
    }

    @Override
    public String toString() {
        return other == null ? kind + " (" + rule + ")"
                : kind + " из-за " + other + " (" + rule + ")";
    }

    static StatusOutcome of(Kind kind, String rule) {
        return new StatusOutcome(kind, null, rule);
    }

    static StatusOutcome of(Kind kind, String other, String rule) {
        return new StatusOutcome(kind, other, rule);
    }
}
