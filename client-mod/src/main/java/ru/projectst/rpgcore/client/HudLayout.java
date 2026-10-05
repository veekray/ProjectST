package ru.projectst.rpgcore.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * Где на экране что рисовать.
 *
 * <p>Раскладку двигает игрок, а не мод. Любое место, выбранное за него, рано или
 * поздно окажется под чужим интерфейсом: чат слева снизу, карта справа сверху,
 * у кого-то ещё полоса жажды посередине. Спорить с этим бессмысленно — экран
 * принадлежит игроку.
 *
 * <p>Координаты хранятся долями от размера окна, а не пикселями. Иначе
 * раскладка, собранная в окне, разъезжается при переходе в полный экран, и
 * собирать её приходится заново.
 */
public final class HudLayout {

    /** Что можно двигать. */
    public enum Element {
        HEALTH("Здоровье", 0.5f, 0.86f),
        RESOURCE("Ресурс", 0.5f, 0.90f),
        COUNTERS("Счётчики ядра", 0.18f, 0.80f),
        SLOTS("Слоты навыков", 0.03f, 0.70f),
        STATUSES("Статусы", 0.03f, 0.08f);

        private final String title;
        private final float defaultX;
        private final float defaultY;

        Element(String title, float defaultX, float defaultY) {
            this.title = title;
            this.defaultX = defaultX;
            this.defaultY = defaultY;
        }

        public String title() {
            return title;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Element, float[]> POSITIONS = new EnumMap<>(Element.class);

    private static Path file;

    private HudLayout() {
    }

    /** Доля ширины окна по горизонтали. */
    public static float x(Element element) {
        return POSITIONS.computeIfAbsent(element,
                key -> new float[] {key.defaultX, key.defaultY})[0];
    }

    /** Доля высоты окна по вертикали. */
    public static float y(Element element) {
        return POSITIONS.computeIfAbsent(element,
                key -> new float[] {key.defaultX, key.defaultY})[1];
    }

    public static int screenX(Element element, int width) {
        return Math.round(x(element) * width);
    }

    public static int screenY(Element element, int height) {
        return Math.round(y(element) * height);
    }

    public static void move(Element element, float fractionX, float fractionY) {
        POSITIONS.put(element, new float[] {
                Math.clamp(fractionX, 0f, 1f), Math.clamp(fractionY, 0f, 1f)});
    }

    public static void reset() {
        POSITIONS.clear();
        for (Element element : Element.values()) {
            POSITIONS.put(element, new float[] {element.defaultX, element.defaultY});
        }
    }

    // ------------------------------------------------------------------ файл

    private static Path file() {
        if (file == null) {
            file = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config").resolve("rpgcore-hud.json");
        }
        return file;
    }

    /**
     * Читает раскладку.
     *
     * <p>Испорченный файл — не повод падать и не повод молчать: берутся
     * значения по умолчанию, а строка уходит в лог. Экран на месте по умолчанию
     * виден сразу; экран, которого нет вовсе, ищут в настройках.
     */
    public static void load() {
        reset();
        try {
            if (!Files.isRegularFile(file())) {
                return;
            }
            String json = Files.readString(file(), StandardCharsets.UTF_8);
            Map<String, float[]> saved = GSON.fromJson(json,
                    new com.google.gson.reflect.TypeToken<LinkedHashMap<String, float[]>>() {
                    }.getType());
            if (saved == null) {
                return;
            }
            saved.forEach((name, point) -> {
                if (point == null || point.length != 2) {
                    return;
                }
                if (name.equals("MENU_SCALE")) {
                    // Масштаб окна убран: при 3× панель уезжала за край экрана
                    // вместе с кнопкой, которая его меняет, и вернуть всё назад
                    // было нечем. Старое значение просто забываем.
                    return;
                }
                try {
                    move(Element.valueOf(name), point[0], point[1]);
                } catch (IllegalArgumentException ignored) {
                    // Элемент из будущей версии мода: пропускаем, а не падаем.
                }
            });
        } catch (IOException | RuntimeException e) {
            System.err.println("RpgCore: раскладка интерфейса не читается: " + e.getMessage());
        }
    }

    public static void save() {
        Map<String, float[]> out = new LinkedHashMap<>();
        for (Element element : Element.values()) {
            out.put(element.name(), new float[] {x(element), y(element)});
        }

        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(out), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("RpgCore: раскладка интерфейса не сохраняется: "
                    + e.getMessage());
        }
    }
}
