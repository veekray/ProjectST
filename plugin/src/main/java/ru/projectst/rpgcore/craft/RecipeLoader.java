package ru.projectst.rpgcore.craft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.loader.YmlDoc;
import ru.projectst.rpgcore.loader.YmlMap;

/**
 * Читает файл рецепта.
 *
 * <pre>
 * id: mage_apprentice_staff
 * result: mage_apprentice_staff
 * amount: 1
 *
 * shaped:
 *   - "- AMETHYST_SHARD -"
 *   - "- STICK -"
 *   - "- STICK -"
 * </pre>
 *
 * <p>Или бесформенный:
 *
 * <pre>
 * loose: [STICK, STICK, "item:mage_focus"]
 * </pre>
 *
 * <p>Обе формы сразу — ошибка, а не «возьмём первую»: рецепт, который читается
 * двумя способами, однажды прочитают не тем.
 */
public final class RecipeLoader {

    private RecipeLoader() {
    }

    public static Optional<RecipeDef> load(String file, String text, ContentErrors errors) {
        Optional<YmlDoc> parsed = YmlDoc.parse(file, text, errors);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        YmlDoc doc = parsed.get();
        YmlMap root = doc.root();

        String id = root.str("id");
        String result = root.str("result", "");
        int amount = root.integer("amount", 1, 64, 1);
        List<String> rows = new ArrayList<>(root.strings("shaped"));
        List<String> loose = new ArrayList<>(root.strings("loose"));

        doc.finish();

        boolean ok = true;
        if (id.isBlank()) {
            errors.add(root.at(), "id", "идентификатор рецепта обязателен");
            ok = false;
        } else if (!id.equals(id.toLowerCase(Locale.ROOT))) {
            errors.add(root.at(), "id", "идентификатор рецепта должен быть в нижнем регистре");
            ok = false;
        }
        if (result.isBlank()) {
            errors.add(root.at(), "result", "у рецепта обязателен результат");
            ok = false;
        }
        if (!rows.isEmpty() && !loose.isEmpty()) {
            errors.add(root.at(), "shaped",
                    "рецепт задан и сеткой, и списком: оставьте одну форму");
            ok = false;
        }
        if (rows.isEmpty() && loose.isEmpty()) {
            errors.add(root.at(), "shaped", "нужна форма: раздел shaped или loose");
            ok = false;
        }
        if (!rows.isEmpty() && rows.size() != 3) {
            errors.add(root.at(), "shaped",
                    "в сетке три строки, получено " + rows.size());
            ok = false;
        }
        if (!ok) {
            return Optional.empty();
        }

        if (!rows.isEmpty()) {
            List<Ingredient> grid = new ArrayList<>();
            for (String row : rows) {
                String[] cells = row.trim().split("\\s+");
                if (cells.length != 3) {
                    errors.add(root.at(), "shaped",
                            "в строке сетки три клетки, получено " + cells.length
                                    + ": \"" + row + "\"");
                    return Optional.empty();
                }
                for (String cell : cells) {
                    grid.add(Ingredient.parse(cell));
                }
            }
            if (grid.stream().allMatch(Ingredient::empty)) {
                errors.add(root.at(), "shaped", "пустая сетка: рецепт ни из чего");
                return Optional.empty();
            }
            return Optional.of(new RecipeDef(id, result, amount, true, grid, List.of()));
        }

        List<Ingredient> parts = new ArrayList<>();
        for (String one : loose) {
            Ingredient ingredient = Ingredient.parse(one);
            if (!ingredient.empty()) {
                parts.add(ingredient);
            }
        }
        if (parts.isEmpty()) {
            errors.add(root.at(), "loose", "список ингредиентов пуст");
            return Optional.empty();
        }
        if (parts.size() > 9) {
            errors.add(root.at(), "loose",
                    "в верстак влезает девять ингредиентов, указано " + parts.size());
            return Optional.empty();
        }
        return Optional.of(new RecipeDef(id, result, amount, false, List.of(), parts));
    }
}
