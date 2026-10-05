package ru.projectst.rpgcore.craft;

import java.util.List;
import java.util.Locale;

/**
 * Рецепт: что получается и из чего.
 *
 * <p>Форма одна из двух, и она записана явно. У фигурного рецепта три строки по
 * три клетки — ровно сетка верстака; у бесформенного просто список. Угадывать
 * форму по тому, задана ли сетка, значило бы, что опечатка в одном ключе молча
 * меняет рецепт на другой.
 *
 * @param id       идентификатор, он же имя файла
 * @param resultItemId наш предмет, который получается
 * @param amount   сколько штук
 * @param shaped   фигурный ли рецепт
 * @param grid     девять клеток для фигурного, слева направо и сверху вниз
 * @param loose    ингредиенты для бесформенного
 */
public record RecipeDef(String id, String resultItemId, int amount, boolean shaped,
                        List<Ingredient> grid, List<Ingredient> loose) {

    public RecipeDef {
        if (id == null || !id.equals(id.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("id рецепта должен быть в нижнем регистре: " + id);
        }
        if (resultItemId == null || resultItemId.isBlank()) {
            throw new IllegalArgumentException("у рецепта обязателен результат: " + id);
        }
        if (amount < 1 || amount > 64) {
            throw new IllegalArgumentException("количество результата от 1 до 64: " + id);
        }
        grid = grid == null ? List.of() : List.copyOf(grid);
        loose = loose == null ? List.of() : List.copyOf(loose);
        if (shaped && grid.size() != 9) {
            throw new IllegalArgumentException("фигурному рецепту нужны девять клеток: " + id);
        }
        if (!shaped && loose.isEmpty()) {
            throw new IllegalArgumentException("бесформенному рецепту нужны ингредиенты: " + id);
        }
    }

    /** Все непустые ингредиенты: нужно связыванию и проверке осмысленности. */
    public List<Ingredient> ingredients() {
        List<Ingredient> out = new java.util.ArrayList<>();
        for (Ingredient one : shaped ? grid : loose) {
            if (!one.empty()) {
                out.add(one);
            }
        }
        return out;
    }
}
