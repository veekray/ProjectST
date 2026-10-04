package ru.projectst.rpgcore.damage;

/**
 * Что получилось из запроса на урон.
 *
 * <p>Результат всегда объясним: если урон не прошёл, видно почему. Молчаливого
 * нуля не бывает — это то же требование, что и у статусов.
 *
 * @param applied        сколько урона уходит в здоровье
 * @param absorbed       сколько поглотили щиты
 * @param crit           был ли крит
 * @param blockedBy      причина, если урон не прошёл вовсе, иначе {@code null}
 * @param afterScaling   промежуточное значение после усиления атакующим
 * @param afterMitigation промежуточное значение после снижения целью
 */
public record DamageResult(double applied, double absorbed, boolean crit, Blocker blockedBy,
                           double afterScaling, double afterMitigation) {

    /** Почему урон не прошёл. */
    public enum Blocker {
        /** Цель под статусом неуязвимости. */
        IMMUNITY,
        /** Щиты поглотили всё без остатка. */
        SHIELD,
        /** Отменено сторонним плагином через событие: регион, пати, арена. */
        EVENT
    }

    public boolean blocked() {
        return blockedBy != null;
    }

    public static DamageResult blockedBy(Blocker reason, double absorbed) {
        return new DamageResult(0, absorbed, false, reason, 0, 0);
    }
}
