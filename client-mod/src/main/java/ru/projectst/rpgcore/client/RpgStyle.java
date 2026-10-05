package ru.projectst.rpgcore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Единый вид всего, что рисует мод.
 *
 * <p>Один стиль собран здесь, а не повторён в каждом экране: иначе полоса в бою
 * и полоса в меню расходятся по цвету и толщине, и собрать их обратно уже
 * некому.
 *
 * <p>Вид выбран под меч и магию: тёмный пергамент вместо чёрного прямоугольника,
 * тёплая бронзовая рамка вместо синей, засечки на полосах вместо гладкой
 * заливки. Никаких текстур — только заливки: своя текстура потребовала бы
 * ресурспака, а он у игроков разный.
 *
 * <p>Кнопки тоже свои. Ванильные серые прямоугольники посреди бронзовой панели
 * выглядят как чужое окно, вставленное в наше, — а именно так и было.
 */
public final class RpgStyle {

    // Цвета подобраны под тёмный фон и мелкий шрифт: приглушённый текст всё
    // равно должен читаться, иначе «тише» превращается в «мутно».

    /** Тёмный пергамент: фон панелей и полос. */
    public static final int PANEL = 0xF5140F0B;
    /** Чуть светлее: внутренняя подложка полос и кнопок. */
    public static final int TRACK = 0xCC2A2118;
    /** Бронза: рамки и разделители. */
    public static final int EDGE = 0xFF6E5A36;
    /** Светлая бронза: рамка под курсором и выделение. */
    public static final int EDGE_BRIGHT = 0xFFC9A227;

    /** Кровь: здоровье. */
    public static final int HEALTH = 0xFF9E2B25;
    public static final int HEALTH_LOW = 0xFFD94A3D;
    /** Лазурь: мана и прочие ресурсы. */
    public static final int RESOURCE = 0xFF2F6FA8;
    /** Зелень: готовность. */
    public static final int READY = 0xFF4F7A3A;
    /** Пепел: перезарядка. */
    public static final int COOLDOWN = 0xFF6B4A2A;

    public static final int TEXT = 0xFFF2E8CE;
    public static final int TEXT_DIM = 0xFFB9AC92;
    public static final int TEXT_WARN = 0xFFC9A227;

    private RpgStyle() {
    }

    /** Панель с бронзовой рамкой: основа всех окон мода. */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.renderOutline(x, y, width, height, EDGE);
        // Внутренняя линия: даёт рамке толщину, которой не бывает у одного
        // пикселя, и отличает панель мода от ванильных окон.
        graphics.renderOutline(x + 1, y + 1, width - 2, height - 2, 0x40C9A227);
    }

    /**
     * Кнопка в том же стиле, что панель.
     *
     * <p>Рисуется и проверяется руками, без ванильного виджета: виджет приносит
     * свою серую текстуру, и посреди бронзы она выглядит как чужое окно,
     * вставленное в наше.
     *
     * @param active нажатая или выбранная: подсвечена постоянно
     */
    public static void button(GuiGraphics graphics, int x, int y, int width, int height,
                              String label, boolean hovered, boolean active) {
        graphics.fill(x, y, x + width, y + height, active ? 0xFF3A2C18 : TRACK);
        graphics.renderOutline(x, y, width, height, hovered || active ? EDGE_BRIGHT : EDGE);

        // Верхний блик: кнопка перестаёт быть плоским прямоугольником.
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, active ? 0x40C9A227 : 0x20C9A227);

        var font = Minecraft.getInstance().font;
        int textColour = active ? TEXT_WARN : hovered ? TEXT : TEXT_DIM;
        graphics.drawString(font, Component.literal(label),
                x + width / 2 - font.width(label) / 2, y + (height - 8) / 2 + 1,
                textColour, true);
    }

    /** Попала ли точка в прямоугольник: один расчёт на показ и на щелчок. */
    public static boolean hit(double mouseX, double mouseY, int x, int y,
                              int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** Строка списка: подложка под курсором, чтобы было видно, что выбрано. */
    public static void row(GuiGraphics graphics, int x, int y, int width, int height,
                           boolean hovered) {
        if (hovered) {
            graphics.fill(x, y, x + width, y + height, 0x33C9A227);
            graphics.fill(x, y, x + 1, y + height, EDGE_BRIGHT);
        }
    }

    /** Разделительная линия: бронзовая, тонкая, без заголовков. */
    public static void divider(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, EDGE);
    }

    /**
     * Полоса с засечками.
     *
     * <p>Засечки каждые десять процентов: доля читается взглядом, без чтения
     * числа. Без них полоса в бою отличается от полной только краем.
     */
    public static void bar(GuiGraphics graphics, int x, int y, int width, int height,
                           double share, int colour) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, PANEL);
        graphics.fill(x, y, x + width, y + height, TRACK);

        int filled = (int) Math.round(width * Math.clamp(share, 0, 1));
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + height, colour);
            graphics.fill(x, y, x + filled, y + 1, lighten(colour));
        }
        for (int i = 1; i < 10; i++) {
            int notch = x + width * i / 10;
            graphics.fill(notch, y, notch + 1, y + height, 0x33000000);
        }
        graphics.renderOutline(x - 1, y - 1, width + 2, height + 2, EDGE);
    }

    /** Значок-деление для счётчиков ядра: ромб заполнен или пуст. */
    public static void pip(GuiGraphics graphics, int x, int y, int size, boolean filled,
                           int colour) {
        int half = size / 2;
        for (int row = 0; row < size; row++) {
            int spread = half - Math.abs(row - half);
            int from = x + half - spread;
            int to = x + half + spread + 1;
            graphics.fill(from, y + row, to, y + row + 1, filled ? colour : 0x55120E0A);
        }
        if (filled) {
            graphics.fill(x + half - 1, y + 1, x + half + 1, y + 2, lighten(colour));
        }
    }

    private static int lighten(int colour) {
        int a = colour >>> 24;
        int r = Math.min(255, ((colour >> 16) & 0xFF) + 60);
        int g = Math.min(255, ((colour >> 8) & 0xFF) + 60);
        int b = Math.min(255, (colour & 0xFF) + 60);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
