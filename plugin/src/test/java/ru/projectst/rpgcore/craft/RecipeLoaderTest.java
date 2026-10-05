package ru.projectst.rpgcore.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.projectst.rpgcore.item.ItemDef;
import ru.projectst.rpgcore.item.ItemRegistry;
import ru.projectst.rpgcore.item.ItemSlot;
import ru.projectst.rpgcore.item.Rarity;
import ru.projectst.rpgcore.loader.ContentErrors;

/**
 * Чтение и связывание рецептов.
 *
 * <p>Рецепт, который читается двумя способами, однажды прочитают не тем, поэтому
 * обе формы сразу — ошибка, а не «возьмём первую».
 */
class RecipeLoaderTest {

    private static final String SHAPED = """
            id: mage_staff
            result: mage_staff
            amount: 1

            shaped:
              - "- item:focus -"
              - "- STICK -"
              - "- STICK -"
            """;

    private static RecipeDef load(String yaml, ContentErrors errors) {
        return RecipeLoader.load("recipe.yml", yaml, errors).orElseThrow(
                () -> new AssertionError(errors.all().toString()));
    }

    @Test
    @DisplayName("фигурный рецепт читается в девять клеток, пустые остаются пустыми")
    void shapedBecomesNineCells() {
        ContentErrors errors = new ContentErrors();
        RecipeDef recipe = load(SHAPED, errors);

        assertTrue(errors.isEmpty(), () -> errors.all().toString());
        assertTrue(recipe.shaped());
        assertEquals(9, recipe.grid().size());
        assertTrue(recipe.grid().get(0).empty());
        assertEquals("focus", recipe.grid().get(1).itemId());
        assertEquals("STICK", recipe.grid().get(4).material());
        assertEquals(3, recipe.ingredients().size(), "пустые клетки не ингредиенты");
    }

    @Test
    @DisplayName("наш предмет и материал различаются явно")
    void ingredientKinds() {
        assertTrue(Ingredient.parse("item:focus").custom());
        assertFalse(Ingredient.parse("stick").custom());
        assertEquals("STICK", Ingredient.parse("stick").material());
        assertTrue(Ingredient.parse("-").empty());
        assertTrue(Ingredient.parse("").empty());
    }

    @Test
    @DisplayName("бесформенный рецепт читается списком")
    void looseIsAList() {
        ContentErrors errors = new ContentErrors();
        RecipeDef recipe = load("""
                id: charm
                result: charm
                loose: [OAK_SAPLING, OAK_SAPLING, "item:focus"]
                """, errors);

        assertFalse(recipe.shaped());
        assertEquals(3, recipe.ingredients().size());
    }

    @Test
    @DisplayName("две формы сразу — ошибка, а не «возьмём первую»")
    void bothFormsRefused() {
        ContentErrors errors = new ContentErrors();
        RecipeLoader.load("recipe.yml", """
                id: thing
                result: thing
                shaped:
                  - "- STICK -"
                  - "- STICK -"
                  - "- STICK -"
                loose: [STICK]
                """, errors);

        assertTrue(errors.all().stream().anyMatch(e -> e.what().contains("одну форму")),
                errors.all().toString());
    }

    @Test
    @DisplayName("сетка не из трёх строк по три клетки не загружается")
    void gridMustBeThreeByThree() {
        ContentErrors errors = new ContentErrors();
        var bad = RecipeLoader.load("recipe.yml", """
                id: thing
                result: thing
                shaped:
                  - "STICK STICK"
                  - "STICK STICK"
                  - "STICK STICK"
                """, errors);

        assertTrue(bad.isEmpty());
        assertTrue(errors.all().stream().anyMatch(e -> e.what().contains("три клетки")),
                errors.all().toString());
    }

    @Test
    @DisplayName("рецепт без формы и рецепт из ничего не загружаются")
    void emptyRecipesRefused() {
        ContentErrors noForm = new ContentErrors();
        assertTrue(RecipeLoader.load("r.yml", """
                id: thing
                result: thing
                """, noForm).isEmpty());
        assertFalse(noForm.isEmpty());

        ContentErrors emptyGrid = new ContentErrors();
        assertTrue(RecipeLoader.load("r.yml", """
                id: thing
                result: thing
                shaped:
                  - "- - -"
                  - "- - -"
                  - "- - -"
                """, emptyGrid).isEmpty());
        assertTrue(emptyGrid.all().stream().anyMatch(e -> e.what().contains("ни из чего")),
                emptyGrid.all().toString());
    }

    // ------------------------------------------------------------------ связывание

    private static ItemRegistry items(String... ids) {
        Map<String, ItemDef> map = new java.util.LinkedHashMap<>();
        for (String id : ids) {
            map.put(id, new ItemDef(id, id, "STICK", 0, List.of(), "common", ItemSlot.HAND,
                    Map.of(), null, List.of(), false));
        }
        return new ItemRegistry(map, Map.of("common", Rarity.COMMON));
    }

    @Test
    @DisplayName("рецепт на существующие предметы связывается без ошибок")
    void linkingAcceptsGoodRecipe() {
        RecipeDef recipe = load(SHAPED, new ContentErrors());
        ContentErrors link = new ContentErrors();

        RecipeLinker.link(List.of(recipe), items("mage_staff", "focus"), link);

        assertTrue(link.isEmpty(), () -> link.all().toString());
    }

    @Test
    @DisplayName("рецепт несуществующего предмета ловится до того, как исчезнет из верстака")
    void linkingCatchesMissingResult() {
        RecipeDef recipe = load(SHAPED, new ContentErrors());
        ContentErrors link = new ContentErrors();

        RecipeLinker.link(List.of(recipe), items("focus"), link);

        assertEquals(1, link.count(), () -> link.all().toString());
        assertTrue(link.all().get(0).what().contains("mage_staff"));
    }

    @Test
    @DisplayName("несуществующий ингредиент тоже ловится")
    void linkingCatchesMissingIngredient() {
        RecipeDef recipe = load(SHAPED, new ContentErrors());
        ContentErrors link = new ContentErrors();

        RecipeLinker.link(List.of(recipe), items("mage_staff"), link);

        assertEquals(1, link.count(), () -> link.all().toString());
        assertTrue(link.all().get(0).what().contains("focus"));
    }
}
