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
 * <p>Вид выбран под меч и магию: дубовая рама с латунными заклёпками, кожаное
 * поле под содержимым, утопленные плашки и самоцветы по углам. Никаких
 * картинок — всё рисуется заливками. Текстуры пришлось бы либо класть в
 * ресурспак (а он у игроков разный), либо везти чужую графику в моде, и у
 * первой же правки размера она растянулась бы мылом. Заливки такого не умеют:
 * рама одинаково выглядит при любом размере окна и интерфейса.
 *
 * <p>«Фактура» — не шум на каждый пиксель, а несколько десятков полос и крапин
 * по постоянному хэшу: рисунок один и тот же от кадра к кадру, поэтому панель не
 * мерцает, а вызовов заливки остаётся столько, сколько ванильный интерфейс
 * делает и без нас.
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

    // ---------------------------------------------------------------- материалы

    /** Дуб рамы. */
    private static final int WOOD = 0xFF4A3524;
    private static final int WOOD_DARK = 0xFF3A2918;
    private static final int WOOD_LIGHT = 0xFF5C442E;
    private static final int WOOD_SEAM = 0xFF241A10;
    /** Высота доски: по ней идут стыки. */
    private static final int PLANK = 13;

    /** Кожа поля под содержимым. */
    private static final int LEATHER = 0xFF241A12;
    private static final int LEATHER_DARK = 0xFF1C140D;
    private static final int LEATHER_LIGHT = 0xFF2E2217;

    private static final int GLINT = 0x55FFE3A0;
    private static final int SHADOW = 0x99000000;

    /** Ширина деревянной рамы окна. */
    public static final int FRAME = 7;
    /** Высота полосы с названием внутри рамы. */
    public static final int BANNER = 16;

    private RpgStyle() {
    }

    // ---------------------------------------------------------------- окно

    /**
     * Большое окно мода: рама, полоса с названием и кожаное поле.
     *
     * <p>Поле возвращается наружу не числами, а постоянными {@link #FRAME} и
     * {@link #BANNER}: экран считает по ним свои отступы, и рама с содержимым не
     * могут разъехаться при правке одной из сторон.
     */
    public static void window(GuiGraphics graphics, int x, int y, int width, int height,
                              String title) {
        // Тень: окно отделяется от мира, даже когда позади светлый пейзаж.
        graphics.fill(x + 5, y + 5, x + width + 5, y + height + 5, 0x70000000);

        wood(graphics, x, y, width, height);
        graphics.renderOutline(x, y, width, height, WOOD_SEAM);
        bevel(graphics, x + 1, y + 1, width - 2, height - 2, false);

        // Полоса с названием: утопленная плашка, по краям самоцветы.
        int bannerX = x + FRAME;
        int bannerY = y + FRAME;
        int bannerWidth = width - FRAME * 2;
        graphics.fill(bannerX, bannerY, bannerX + bannerWidth, bannerY + BANNER, 0xFF2A1E12);
        bevel(graphics, bannerX, bannerY, bannerWidth, BANNER, true);
        graphics.renderOutline(bannerX, bannerY, bannerWidth, BANNER, EDGE);

        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.literal(title),
                x + width / 2 - font.width(title) / 2, bannerY + 4, EDGE_BRIGHT, true);
        gem(graphics, bannerX + 9, bannerY + BANNER / 2, 7, 0xFF8E2B25);
        gem(graphics, bannerX + bannerWidth - 9, bannerY + BANNER / 2, 7, 0xFF8E2B25);

        // Кожаное поле под содержимым.
        int fieldY = bannerY + BANNER + 3;
        int fieldHeight = y + height - FRAME - fieldY;
        leather(graphics, bannerX, fieldY, bannerWidth, fieldHeight);
        bevel(graphics, bannerX, fieldY, bannerWidth, fieldHeight, true);
        graphics.renderOutline(bannerX, fieldY, bannerWidth, fieldHeight, EDGE);

        // Заклёпки по углам рамы: четыре точки, на которых держится всё
        // остальное, — и взгляд сразу находит края окна.
        stud(graphics, x + FRAME / 2 + 1, y + FRAME / 2 + 1);
        stud(graphics, x + width - FRAME / 2 - 1, y + FRAME / 2 + 1);
        stud(graphics, x + FRAME / 2 + 1, y + height - FRAME / 2 - 1);
        stud(graphics, x + width - FRAME / 2 - 1, y + height - FRAME / 2 - 1);
    }

    /** Левый край содержимого окна, стоящего в точке x. */
    public static int fieldX(int x) {
        return x + FRAME + 5;
    }

    /** Верх содержимого окна, стоящего в точке y. */
    public static int fieldY(int y) {
        return y + FRAME + BANNER + 3 + 5;
    }

    /** Ширина содержимого окна шириной width. */
    public static int fieldWidth(int width) {
        return width - (FRAME + 5) * 2;
    }

    // ---------------------------------------------------------------- фактуры

    /** Доски: полосы по постоянному хэшу и стыки через равные промежутки. */
    public static void wood(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, WOOD);
        for (int row = 0; row < height; row++) {
            int tone = noise(0, y + row);
            if (tone < 4) {
                graphics.fill(x, y + row, x + width, y + row + 1, WOOD_DARK);
            } else if (tone > 12) {
                graphics.fill(x, y + row, x + width, y + row + 1, WOOD_LIGHT);
            }
        }
        // Стыки считаются от абсолютной координаты, а не от края окна: иначе
        // рисунок досок прыгал бы при каждом изменении размера экрана.
        for (int row = PLANK - Math.floorMod(y, PLANK); row < height - 1; row += PLANK) {
            graphics.fill(x, y + row, x + width, y + row + 1, WOOD_SEAM);
            graphics.fill(x, y + row + 1, x + width, y + row + 2, 0x30FFE3A0);
        }
    }

    /** Кожа: редкие крапины, чтобы поле не выглядело залитым одним цветом. */
    public static void leather(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, LEATHER);
        for (int row = 0; row < height; row += 4) {
            for (int column = (row / 4 % 2) * 4; column < width; column += 8) {
                int tone = noise(x + column, y + row);
                if (tone < 3) {
                    graphics.fill(x + column, y + row, x + column + 3, y + row + 3,
                            LEATHER_DARK);
                } else if (tone > 13) {
                    graphics.fill(x + column, y + row, x + column + 2, y + row + 2,
                            LEATHER_LIGHT);
                }
            }
        }
    }

    /** Объём: светлая грань сверху и тёмная снизу, или наоборот у утопленного. */
    public static void bevel(GuiGraphics graphics, int x, int y, int width, int height,
                             boolean sunken) {
        int top = sunken ? SHADOW : GLINT;
        int bottom = sunken ? GLINT : SHADOW;
        graphics.fill(x, y, x + width, y + 1, top);
        graphics.fill(x, y, x + 1, y + height, top);
        graphics.fill(x, y + height - 1, x + width, y + height, bottom);
        graphics.fill(x + width - 1, y, x + width, y + height, bottom);
    }

    /** Латунная заклёпка. */
    public static void stud(GuiGraphics graphics, int centerX, int centerY) {
        graphics.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, 0xFF221810);
        graphics.fill(centerX - 2, centerY - 2, centerX + 2, centerY + 2, 0xFF8A6E2E);
        graphics.fill(centerX - 2, centerY - 2, centerX, centerY, 0xFFE0C65A);
    }

    /** Самоцвет: ромб с бликом. Украшение, не показатель — ничего не значит. */
    public static void gem(GuiGraphics graphics, int centerX, int centerY, int size,
                           int colour) {
        int half = size / 2;
        for (int row = -half; row <= half; row++) {
            int spread = half - Math.abs(row);
            graphics.fill(centerX - spread - 1, centerY + row, centerX + spread + 2,
                    centerY + row + 1, 0xFF1A120C);
            graphics.fill(centerX - spread, centerY + row, centerX + spread + 1,
                    centerY + row + 1, colour);
        }
        graphics.fill(centerX - 1, centerY - half + 1, centerX + 1, centerY - half + 3,
                lighten(colour));
    }

    // ---------------------------------------------------------------- плашки

    /** Простая панель: для мелких окон, где большая рама была бы тяжелее дела. */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.renderOutline(x, y, width, height, EDGE);
        graphics.renderOutline(x + 1, y + 1, width - 2, height - 2, 0x40C9A227);
    }

    /** Утопленная плашка внутри поля: ею отделяются части одной вкладки. */
    public static void plate(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0x55000000);
        bevel(graphics, x, y, width, height, true);
        graphics.renderOutline(x, y, width, height, 0x66C9A227);
    }

    /**
     * Заголовок части вкладки: надпись на линии.
     *
     * <p>Две части на одном экране нужно разделить не пустотой, а чертой с
     * именем: пустота читается как «место кончилось», черта — как «дальше
     * другое».
     */
    public static void caption(GuiGraphics graphics, int x, int y, int width, String title) {
        var font = Minecraft.getInstance().font;
        int textWidth = font.width(title);
        graphics.drawString(font, Component.literal(title), x, y, TEXT_WARN, true);
        graphics.fill(x + textWidth + 6, y + 3, x + width, y + 4, EDGE);
        graphics.fill(x + textWidth + 6, y + 4, x + width, y + 5, 0x33FFE3A0);
    }

    /**
     * Кнопка в том же стиле, что рама.
     *
     * <p>Рисуется и проверяется руками, без ванильного виджета: виджет приносит
     * свою серую текстуру, и посреди бронзы она выглядит как чужое окно,
     * вставленное в наше.
     *
     * @param active нажатая или выбранная: подсвечена постоянно
     */
    public static void button(GuiGraphics graphics, int x, int y, int width, int height,
                              String label, boolean hovered, boolean active) {
        wood(graphics, x, y, width, height);
        if (active) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x55C9A227);
        } else if (hovered) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x33C9A227);
        } else {
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x55000000);
        }
        bevel(graphics, x, y, width, height, active);
        graphics.renderOutline(x, y, width, height, hovered || active ? EDGE_BRIGHT : EDGE);

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
        graphics.fill(x, y + 1, x + width, y + 2, 0x33FFE3A0);
    }

    /** Та же линия, но стоймя: ею делятся колонки внутри вкладки. */
    public static void dividerVertical(GuiGraphics graphics, int x, int y, int height) {
        graphics.fill(x, y, x + 1, y + height, EDGE);
        graphics.fill(x + 1, y, x + 2, y + height, 0x33FFE3A0);
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

    static int lighten(int colour) {
        int a = colour >>> 24;
        int r = Math.min(255, ((colour >> 16) & 0xFF) + 60);
        int g = Math.min(255, ((colour >> 8) & 0xFF) + 60);
        int b = Math.min(255, (colour & 0xFF) + 60);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Постоянный «шум» по координате: четыре бита на точку.
     *
     * <p>Именно по координате, а не случайный: случайный пересчитывался бы
     * каждый кадр, и фактура мерцала бы.
     */
    private static int noise(int x, int y) {
        int hash = x * 374761393 + y * 668265263;
        hash = (hash ^ (hash >>> 13)) * 1274126177;
        return (hash ^ (hash >>> 16)) & 15;
    }
}
