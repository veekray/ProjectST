package ru.projectst.rpgcore.craft;

import java.util.Collection;
import ru.projectst.rpgcore.item.ItemRegistry;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.loader.SourceRef;

/**
 * Связывание рецептов: существуют ли предметы, на которые они ссылаются.
 *
 * <p>Рецепт, делающий несуществующий предмет, не должен молча исчезать из
 * верстака — именно так и выглядела половина поломок в прежнем стеке: игрок
 * знает, что рецепт был, а его нет, и причину не находит никто.
 */
public final class RecipeLinker {

    private RecipeLinker() {
    }

    public static void link(Collection<RecipeDef> recipes, ItemRegistry items,
                            ContentErrors errors) {
        for (RecipeDef recipe : recipes) {
            SourceRef where = SourceRef.ofFile(recipe.id() + ".yml");

            if (!items.has(recipe.resultItemId())) {
                errors.add(where, "result",
                        "ссылка на несуществующий предмет \"" + recipe.resultItemId() + "\"");
            }
            for (Ingredient ingredient : recipe.ingredients()) {
                if (ingredient.custom() && !items.has(ingredient.itemId())) {
                    errors.add(where, "ingredient",
                            "ссылка на несуществующий предмет \"" + ingredient.itemId() + "\"");
                }
            }
        }
    }
}
