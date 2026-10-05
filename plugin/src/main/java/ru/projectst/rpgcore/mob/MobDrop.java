package ru.projectst.rpgcore.mob;

/**
 * Строка дропа.
 *
 * <p>Предмет или материал, количество «от и до» и шанс. Вложенных таблиц дропа
 * здесь нет намеренно: они давали гибкость, которой почти никто не пользовался, и
 * непредсказуемость, на которую жаловались все.
 *
 * @param itemId   наш предмет; {@code null} — ванильный материал
 * @param material ванильный материал; {@code null} — наш предмет
 * @param min      наименьшее количество
 * @param max      наибольшее количество
 * @param chance   вероятность в процентах
 */
public record MobDrop(String itemId, String material, int min, int max, double chance) {

    public MobDrop {
        boolean hasItem = itemId != null && !itemId.isBlank();
        boolean hasMaterial = material != null && !material.isBlank();
        if (hasItem == hasMaterial) {
            throw new IllegalArgumentException(
                    "дроп — либо наш предмет, либо материал, но не оба и не ничто");
        }
        if (min < 1 || max < min || max > 64) {
            throw new IllegalArgumentException("количество дропа от 1 до 64 и min не больше max");
        }
        if (chance <= 0 || chance > 100) {
            throw new IllegalArgumentException("вероятность дропа от единицы до ста");
        }
    }

    public boolean custom() {
        return itemId != null && !itemId.isBlank();
    }

    /** Сколько выпадет при этом броске; ноль — не выпало вовсе. */
    public int roll(double chanceRoll, double amountRoll) {
        if (chanceRoll * 100 >= chance) {
            return 0;
        }
        return min + (int) Math.floor(amountRoll * (max - min + 1));
    }
}
