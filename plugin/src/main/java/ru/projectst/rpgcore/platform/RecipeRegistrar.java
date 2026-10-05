package ru.projectst.rpgcore.platform;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;
import ru.projectst.rpgcore.craft.Ingredient;
import ru.projectst.rpgcore.craft.RecipeDef;
import ru.projectst.rpgcore.craft.RecipeRegistry;
import ru.projectst.rpgcore.item.ItemRegistry;

/**
 * Регистрирует наши рецепты в ванильном верстаке.
 *
 * <p>Своя станция была бы ещё одним окном, которое нужно объяснять игроку;
 * верстак он уже знает.
 *
 * <p>Перерегистрация при перезагрузке контента обязательна и идёт с удалением:
 * Bukkit держит рецепты по ключу до перезапуска, и без удаления второй
 * {@code /rpg reload} оставлял бы в верстаке прежний рецепт рядом с новым. Что
 * сработает из двух — лотерея, а лотерей в этом проекте нет.
 */
public final class RecipeRegistrar {

    private final Plugin plugin;
    private final RpgItems items;
    private final ItemRegistry registry;
    private final List<NamespacedKey> registered = new ArrayList<>();

    public RecipeRegistrar(Plugin plugin, RpgItems items, ItemRegistry registry) {
        this.plugin = plugin;
        this.items = items;
        this.registry = registry;
    }

    /** Снимает прежние рецепты и ставит текущие. */
    public int reload(RecipeRegistry recipes) {
        for (NamespacedKey key : registered) {
            Bukkit.removeRecipe(key);
        }
        registered.clear();

        int added = 0;
        for (RecipeDef def : recipes.all()) {
            Recipe recipe = build(def);
            if (recipe == null) {
                continue;
            }
            try {
                Bukkit.addRecipe(recipe);
                registered.add(new NamespacedKey(plugin, def.id()));
                added++;
            } catch (IllegalStateException e) {
                // Ключ уже занят: рецепт с таким именем остался от прошлой жизни
                // сервера. Это не повод терять остальные.
                plugin.getLogger().warning("рецепт " + def.id() + " не добавлен: "
                        + e.getMessage());
            }
        }
        return added;
    }

    private Recipe build(RecipeDef def) {
        var item = registry.find(def.resultItemId());
        if (item.isEmpty()) {
            // Связывание об этом уже сказало; здесь просто не падаем.
            return null;
        }
        ItemStack result = items.build(item.get(), def.amount());
        NamespacedKey key = new NamespacedKey(plugin, def.id());

        if (def.shaped()) {
            ShapedRecipe shaped = new ShapedRecipe(key, result);
            // Буквы раздаются по порядку ингредиентов: сама сетка остаётся той
            // же, что в файле, и менять её при правке буквы не нужно.
            char[] letters = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i'};
            StringBuilder rows = new StringBuilder();
            List<RecipeChoice> choices = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                Ingredient ingredient = def.grid().get(i);
                if (ingredient.empty()) {
                    rows.append(' ');
                    choices.add(null);
                } else {
                    rows.append(letters[i]);
                    choices.add(choiceOf(ingredient));
                }
            }
            shaped.shape(rows.substring(0, 3), rows.substring(3, 6), rows.substring(6, 9));
            for (int i = 0; i < 9; i++) {
                RecipeChoice choice = choices.get(i);
                if (choice != null) {
                    shaped.setIngredient(letters[i], choice);
                }
            }
            return shaped;
        }

        ShapelessRecipe shapeless = new ShapelessRecipe(key, result);
        for (Ingredient ingredient : def.ingredients()) {
            RecipeChoice choice = choiceOf(ingredient);
            if (choice != null) {
                shapeless.addIngredient(choice);
            }
        }
        return shapeless;
    }

    /**
     * Во что превращается ингредиент.
     *
     * <p>Наш предмет сравнивается точным предметом: тот же материал, то же имя и
     * та же метка. Сравнение по одному материалу позволяло бы скрафтить посох
     * мастера из обычной палки, что в прежнем стеке и происходило.
     */
    private RecipeChoice choiceOf(Ingredient ingredient) {
        if (ingredient.custom()) {
            var def = registry.find(ingredient.itemId());
            if (def.isEmpty()) {
                return null;
            }
            return new RecipeChoice.ExactChoice(items.build(def.get(), 1));
        }
        Material material = Material.matchMaterial(ingredient.material());
        if (material == null) {
            plugin.getLogger().warning("неизвестный материал в рецепте: "
                    + ingredient.material());
            return null;
        }
        return new RecipeChoice.MaterialChoice(material);
    }
}
