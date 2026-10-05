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
        return data;
    }

    /**
     * Подъём старых схем.
     *
     * <p>1 → 2: появились уровни навыков. Поля в старых файлах нет, и это
     * означает «все изученные на первом уровне» — ровно то, что они и были.
     * Запись здесь всё равно делается явной, чтобы миграция была видна, а не
     * спрятана в терпимости читателя к отсутствующему ключу.
     */
    private static JsonObject migrate(JsonObject root, int fromSchema) {
        if (fromSchema < 2 && !root.has("levels")) {
            root.add("levels", new JsonObject());
        }
        return root;
    }
}
