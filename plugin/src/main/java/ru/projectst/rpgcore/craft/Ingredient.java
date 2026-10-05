package ru.projectst.rpgcore.craft;

import java.util.Locale;

/**
 * Что кладут в рецепт.
 *
 * <p>Либо ванильный материал, либо наш предмет по идентификатору. Второй случай
 * сравнивается по метке предмета, а не по имени или описанию: предмет,
 * узнаваемый по описанию, перестаёт узнаваться после первой же наковальни.
 *
 * @param material материал, если ингредиент ванильный
 * @param itemId   наш предмет, если ингредиент наш
 */
public record Ingredient(String material, String itemId) {

    public Ingredient {
        boolean hasMaterial = material != null && !material.isBlank();
        boolean hasItem = itemId != null && !itemId.isBlank();
        if (hasMaterial == hasItem) {
            throw new IllegalArgumentException(
                    "ингредиент — либо материал, либо предмет, но не оба и не ничто");
        }
    }

    /** Пустая клетка сетки. */
    public static final Ingredient EMPTY = new Ingredient("AIR", null);

    public boolean empty() {
        return "AIR".equals(material);
    }

    public boolean custom() {
        return itemId != null;
    }

    /**
     * Разбирает запись: {@code item:mage_staff} — наш предмет, {@code STICK} —
     * материал, {@code -} или пусто — пустая клетка.
     */
    public static Ingredient parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty() || text.equals("-")) {
            return EMPTY;
        }
        if (text.startsWith("item:")) {
            return new Ingredient(null, text.substring("item:".length()).trim());
        }
        return new Ingredient(text.toUpperCase(Locale.ROOT), null);
    }
}
