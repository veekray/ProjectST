package ru.projectst.rpgcore.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.UUID;

/**
 * Перевод {@link PlayerData} в JSON и обратно, с подъёмом старых версий схемы.
 *
 * <p>Gson взят из classpath ядра: сервер и так его тянет, поэтому в плагин
 * ничего добавлять не нужно. Автоматическое отображение полей сознательно не
 * используется — пишем и читаем поля руками. Так переименование поля в Java не
 * ломает молча существующие файлы игроков, а требует явной миграции.
 */
public final class PlayerDataCodec {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Сколько ячеек артефактов осталось после перехода на окно снаряжения.
     *
     * <p>Число записано здесь, а не взято у ячеек: миграция описывает, как было
     * и как стало в тот день, и не должна меняться, если ячеек потом добавят.
     */
    static final int ARTIFACT_CELLS = 3;

    private PlayerDataCodec() {
    }

    public static String toJson(PlayerData data) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", PlayerData.SCHEMA_VERSION);
        root.addProperty("uuid", data.uuid().toString());
        if (data.classId() != null) {
            root.addProperty("class", data.classId());
        }
        root.addProperty("level", data.level());
        root.addProperty("xp", data.xp());
        root.addProperty("points", data.unspentPoints());

        var skills = new com.google.gson.JsonArray();
        data.unlockedSkills().forEach(skills::add);
        root.add("unlocked", skills);

        JsonObject slots = new JsonObject();
        for (Map.Entry<Integer, String> e : data.slotBindings().entrySet()) {
            slots.addProperty(String.valueOf(e.getKey()), e.getValue());
        }
        root.add("slots", slots);

        JsonObject levels = new JsonObject();
        for (Map.Entry<String, Integer> e : data.skillLevels().entrySet()) {
            levels.addProperty(e.getKey(), e.getValue());
        }
        root.add("levels", levels);

        JsonObject gear = new JsonObject();
        for (Map.Entry<String, String> e : data.gear().entrySet()) {
            gear.addProperty(e.getKey(), e.getValue());
        }
        root.add("gear", gear);

        var returns = new com.google.gson.JsonArray();
        data.returns().forEach(returns::add);
        root.add("returns", returns);

        return GSON.toJson(root);
    }

    /**
     * Читает данные игрока.
     *
     * @throws PlayerDataException если файл не разбирается или схема из будущего.
     *         Молча подставлять пустые данные нельзя: это стёрло бы прогресс
     *         игрока при первой же ошибке формата.
     */
    public static PlayerData fromJson(UUID expectedUuid, String json) {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new PlayerDataException("файл не разбирается как JSON: " + e.getMessage(), e);
        }

        int schema = root.has("schema") ? root.get("schema").getAsInt() : 0;
        if (schema > PlayerData.SCHEMA_VERSION) {
            throw new PlayerDataException("файл сохранён более новой версией плагина: схема "
                    + schema + ", поддерживается до " + PlayerData.SCHEMA_VERSION);
        }
        root = migrate(root, schema);

        PlayerData data = new PlayerData(expectedUuid);
        if (root.has("class") && !root.get("class").isJsonNull()) {
            data.setClassId(root.get("class").getAsString());
        }
        if (root.has("level")) {
            data.setLevel(Math.max(1, root.get("level").getAsInt()));
        }
        if (root.has("xp")) {
            data.setXp(root.get("xp").getAsDouble());
        }
        if (root.has("points")) {
            data.setUnspentPoints(root.get("points").getAsInt());
        }
        if (root.has("unlocked")) {
            root.getAsJsonArray("unlocked")
                    .forEach(el -> data.unlockedSkills().add(el.getAsString()));
        }
        if (root.has("slots")) {
            for (Map.Entry<String, com.google.gson.JsonElement> e
                    : root.getAsJsonObject("slots").entrySet()) {
                try {
                    data.bind(Integer.parseInt(e.getKey()), e.getValue().getAsString());
                } catch (NumberFormatException ignored) {
                    throw new PlayerDataException("номер слота не число: " + e.getKey());
                }
            }
        }
        if (root.has("levels")) {
            for (Map.Entry<String, com.google.gson.JsonElement> e
                    : root.getAsJsonObject("levels").entrySet()) {
                data.setSkillLevel(e.getKey(), Math.max(1, e.getValue().getAsInt()));
            }
        }
        if (root.has("gear")) {
            for (Map.Entry<String, com.google.gson.JsonElement> e
                    : root.getAsJsonObject("gear").entrySet()) {
                data.setGear(e.getKey(), e.getValue().getAsString());
            }
        }
        if (root.has("returns")) {
            root.getAsJsonArray("returns").forEach(el -> data.addReturn(el.getAsString()));
        }
        return data;
    }

    /**
     * Подъём старых схем.
     *
     * <p>1 → 2: появились уровни навыков. Поля в старых файлах нет, и это
     * означает «все изученные на первом уровне» — ровно то, что они и были.
     * Запись здесь всё равно делается явной, чтобы миграция была видна, а не
     * спрятана в терпимости читателя к отсутствующему ключу.
     *
     * <p>2 → 3: появились слоты артефактов. Пустые у всех, кто играл до них, —
     * и это единственное верное значение: артефактов тогда не было.
     *
     * <p>3 → 4: слоты артефактов по номерам стали ячейками снаряжения по именам.
     * Первые {@link #ARTIFACT_CELLS} номера переезжают в {@code artifact_1..3};
     * всё, что лежало дальше, — в очередь возврата: таких ячеек больше нет, и
     * вещь из них отдаётся владельцу при входе, а не пропадает молча.
     */
    private static JsonObject migrate(JsonObject root, int fromSchema) {
        if (fromSchema < 2 && !root.has("levels")) {
            root.add("levels", new JsonObject());
        }
        if (fromSchema < 3 && !root.has("artifacts")) {
            root.add("artifacts", new JsonObject());
        }
        if (fromSchema < 4) {
            JsonObject gear = root.has("gear") ? root.getAsJsonObject("gear") : new JsonObject();
            var returns = root.has("returns") ? root.getAsJsonArray("returns")
                    : new com.google.gson.JsonArray();
            JsonObject artifacts = root.has("artifacts") ? root.getAsJsonObject("artifacts")
                    : new JsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> e : artifacts.entrySet()) {
                int slot;
                try {
                    slot = Integer.parseInt(e.getKey());
                } catch (NumberFormatException ignored) {
                    throw new PlayerDataException("номер слота артефакта не число: "
                            + e.getKey());
                }
                if (slot >= 1 && slot <= ARTIFACT_CELLS) {
                    gear.add("artifact_" + slot, e.getValue());
                } else {
                    returns.add(e.getValue());
                }
            }
            root.remove("artifacts");
            root.add("gear", gear);
            root.add("returns", returns);
        }
        return root;
    }
}
