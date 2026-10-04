package ru.projectst.rpgcore.status;

/**
 * Запрос на наложение статуса.
 *
 * @param statusId идентификатор статуса
 * @param duration длительность в тиках; если не положительна, берётся из
 *                 определения статуса
 * @param amount   числовая полезная нагрузка: запас щита, сила эффекта.
 *                 Для статусов без величины — ноль
 * @param source   кто наложил: {@code skill:warlock_agony_cocoon}.
 *                 Обязателен, чтобы статус можно было снять источником и
 *                 объяснить в отладке
 */
public record StatusApplication(String statusId, int duration, double amount, String source) {

    public StatusApplication {
        if (statusId == null || statusId.isBlank()) {
            throw new IllegalArgumentException("statusId обязателен");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source обязателен: без него статус нельзя "
                    + "ни снять источником, ни объяснить в отладке");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("amount не может быть отрицательным");
        }
    }

    /** Наложение с длительностью из определения статуса. */
    public static StatusApplication of(String statusId, String source) {
        return new StatusApplication(statusId, 0, 0, source);
    }

    public static StatusApplication of(String statusId, int duration, String source) {
        return new StatusApplication(statusId, duration, 0, source);
    }

    public static StatusApplication shield(String statusId, int duration, double pool, String source) {
        return new StatusApplication(statusId, duration, pool, source);
    }
}
