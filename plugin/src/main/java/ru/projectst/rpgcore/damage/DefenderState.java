package ru.projectst.rpgcore.damage;

/**
 * Состояние цели, которое конвейер урона не может узнать из статов.
 *
 * <p>Поставляется реестром статусов (M3): неуязвимость — это статус категории
 * IMMUNITY, запас щита — сумма статусов категории SHIELD. Конвейер о статусах
 * ничего не знает, иначе модули стали бы зависеть друг от друга в обе стороны.
 *
 * @param immune      цель неуязвима: урон отменяется целиком
 * @param shieldPool  сколько урона могут поглотить щиты
 */
public record DefenderState(boolean immune, double shieldPool) {

    public static final DefenderState NONE = new DefenderState(false, 0);

    public DefenderState {
        if (shieldPool < 0) {
            throw new IllegalArgumentException("запас щита не может быть отрицательным");
        }
    }

    public static DefenderState shielded(double pool) {
        return new DefenderState(false, pool);
    }

    /** Имя не {@code immune()}: так называется аксессор самого record'а. */
    public static DefenderState immuneTarget() {
        return new DefenderState(true, 0);
    }
}
