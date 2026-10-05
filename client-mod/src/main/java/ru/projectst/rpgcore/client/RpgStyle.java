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
 * <p>Семей цветов две, и это намеренно. Книга героя — бумага в деревянной раме,
 * тёмные чернила: так же выглядит окно инвентаря, из которого она открывается,
 * и два окна рядом не читаются как «одно из них чужое». Экран в бою, наоборот,
 * тёмный и полупрозрачный: светлая заливка поверх мира слепила бы.
 *
 * <p>Никаких картинок. Текстуру пришлось бы либо класть в ресурспак (а он у
 * игроков разный), либо везти чужую графику в сборке, и у первой же правки
 * размера она растянулась бы мылом. Заливки такого не умеют: окно одинаково
 * выглядит при любом масштабе интерфейса.
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

    private static final int GLINT = 0x55FFE3A0;
    private static final int SHADOW = 0x99000000;

    // ---------------------------------------------------------------- пергамент
    // Окно светлое, а экран в бою тёмный, и это намеренно: поверх мира светлая
    // заливка слепила бы, а в раскрытой книге тёмное поле с белым текстом
    // читается хуже бумаги. Цвета поэтому две семьи, а не одна.

    /** Бумага: поле раскрытой книги. */
    public static final int PARCHMENT = 0xFFE3D5B0;
    private static final int PARCHMENT_SPOT = 0x18000000;

    /** Чернила: основной текст на бумаге. */
    public static final int INK = 0xFF5A4530;
    public static final int INK_DIM = 0xFF8E7E62;
    /** Заголовки: темнее и тяжелее обычного текста. */
    public static final int INK_TITLE = 0xFF3F2E1A;
    public static final int INK_GOOD = 0xFF3F6B2A;
    public static final int INK_BAD = 0xFF9E2B25;
    public static final int INK_MANA = 0xFF2F5FA8;

    /** Гнездо на бумаге: ровно то же, что ячейка в инвентаре. */
    public static final int SLOT = 0xFFC9B88E;
    private static final int SLOT_EDGE = 0xFF9A8862;
    private static final int SLOT_HOT = 0xFFD07A3A;

    /** Сукно закладок: выбранная ярче. */
    private static final int TAB_ACTIVE = 0xFFB03A32;
    private static final int TAB_IDLE = 0xFF6E3A2E;
    /** Золото названия на деревянной планке. */
    public static final int TITLE_GOLD = 0xFFE8C86A;

    /** Ширина деревянной рамы окна. */
    public static final int FRAME = 7;
    /** Высота верхней планки книги: на ней стоят закладки и название. */
    public static final int HEAD = 30;
    /** Высота полосы с названием внутри рамы. */
    public static final int BANNER = 16;

    private RpgStyle() {
    }

    // ---------------------------------------------------------------- книга

    /**
     * Раскрытая книга: деревянная рама, планка с закладками и бумажное поле.
     *
     * <p>Под тот же вид, что у окна инвентаря, из которого эта книга и
     * открывается. Разный вид у двух окон, стоящих рядом в одном меню, читается
     * как «одно из них чужое», и выяснять, какое именно, приходится игроку.
     *
     * <p>Размеры поля наружу отдаются не числами, а {@link #FRAME} и
     * {@link #HEAD}: экран считает по ним свои отступы, и рама с содержимым не
     * могут разъехаться при правке одной из сторон.
     */
    public static void book(GuiGraphics graphics, int x, int y, int width, int height,
                            String title) {
        // Тень: книга отделяется от мира, даже когда позади светлый пейзаж.
        graphics.fill(x + 5, y + 5, x + width + 5, y + height + 5, 0x70000000);

        wood(graphics, x, y, width, height);
        graphics.renderOutline(x, y, width, height, WOOD_SEAM);
        bevel(graphics, x + 1, y + 1, width - 2, height - 2, false);

        var font = Minecraft.getInstance().font;
        String caps = title.toUpperCase();
        graphics.drawString(font, Component.literal(caps),
                x + width - FRAME - 4 - font.width(caps), y + HEAD / 2 - 8,
                TITLE_GOLD, true);

        int fieldLeft = x + FRAME;
        int fieldTop = y + HEAD;
        int fieldRight = x + width - FRAME;
        int fieldBottom = y + height - FRAME;
        parchment(graphics, fieldLeft, fieldTop, fieldRight - fieldLeft,
                fieldBottom - fieldTop);
        graphics.renderOutline(fieldLeft - 1, fieldTop - 1,
                fieldRight - fieldLeft + 2, fieldBottom - fieldTop + 2, WOOD_SEAM);

        // Заклёпки по углам рамы: взгляд сразу находит края окна.
        stud(graphics, x + FRAME / 2 + 1, y + FRAME / 2 + 1);
        stud(graphics, x + width - FRAME / 2 - 1, y + FRAME / 2 + 1);
        stud(graphics, x + FRAME / 2 + 1, y + height - FRAME / 2 - 1);
        stud(graphics, x + width - FRAME / 2 - 1, y + height - FRAME / 2 - 1);
    }

    /** Левый край содержимого книги, стоящей в точке x. */
    public static int fieldX(int x) {
        return x + FRAME + 6;
    }

    /** Верх содержимого книги, стоящей в точке y. */
    public static int fieldY(int y) {
        return y + HEAD + 6;
    }

    /** Ширина содержимого книги шириной width. */
    public static int fieldWidth(int width) {
        return width - (FRAME + 6) * 2;
    }

    /** Бумага: редкие разводы, чтобы поле не выглядело залитым одним цветом. */
    static void parchment(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PARCHMENT);
        // Разводы крупные и редкие: мелкая крапина на светлом поле читается как
        // грязь на экране, а не как бумага.
        for (int row = 0; row < height; row += 11) {
            for (int column = (row / 11 % 2) * 11; column < width; column += 22) {
                int tone = noise(x + column, y + row);
                if (tone < 4) {
                    int size = 8 + tone * 3;
                    graphics.fill(x + column, y + row,
                            Math.min(x + width, x + column + size),
                            Math.min(y + height, y + row + size), PARCHMENT_SPOT);
                }
            }
        }
        bevel(graphics, x, y, width, height, true);
    }

    /**
     * Закладка на планке книги.
     *
     * <p>Выбранная выше остальных и ярче: по тому же правилу, что в инвентаре,
     * из которого книга открывается.
     */
    public static void tabPlate(GuiGraphics graphics, int x, int y, int size,
                                boolean active, boolean hovered) {
        int top = active ? y - 2 : y;
        int height = active ? size + 2 : size;
        graphics.fill(x, top, x + size, top + height, active ? TAB_ACTIVE : TAB_IDLE);
        if (hovered && !active) {
            graphics.fill(x, top, x + size, top + height, 0x33FFFFFF);
        }
        bevel(graphics, x, top, size, height, false);
        graphics.renderOutline(x, top, size, height, WOOD_SEAM);
    }

    /** Гнездо: ровно то же, что ячейка в инвентаре, чтобы его так и читали. */
    public static void slot(GuiGraphics graphics, int x, int y, int width, int height,
                            boolean hot) {
        graphics.fill(x, y, x + width, y + height, SLOT);
        graphics.renderOutline(x, y, width, height, hot ? SLOT_HOT : SLOT_EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0x22000000);
    }

    /**
     * Заголовок части страницы: надпись посередине, под ней черта с точкой.
     *
     * <p>Две части на одной странице нужно разделить не пустотой, а чертой с
     * именем: пустота читается как «место кончилось», черта — как «дальше
     * другое».
     */
    public static void caption(GuiGraphics graphics, int x, int y, int width, String title) {
        var font = Minecraft.getInstance().font;
        String caps = title.toUpperCase();
        graphics.drawString(font, Component.literal(caps),
                x + width / 2 - font.width(caps) / 2, y, INK_TITLE, false);
        rule(graphics, x, y + 12, width);
    }

    /** Черта с точкой посередине: ею делятся части страницы. */
    static void rule(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, SLOT_EDGE);
        int center = x + width / 2;
        graphics.fill(center - 2, y - 1, center + 3, y + 2, PARCHMENT);
        pip(graphics, center - 2, y - 2, 5, true, INK_TITLE);
    }

    /** Та же черта, но стоймя: ею делятся колонки страницы. */
    public static void ruleVertical(GuiGraphics graphics, int x, int y, int height) {
        graphics.fill(x, y, x + 1, y + height, SLOT_EDGE);
        int center = y + height / 2;
        graphics.fill(x - 1, center - 2, x + 2, center + 3, PARCHMENT);
        pip(graphics, x - 2, center - 2, 5, true, INK_TITLE);
    }

    /** Кнопка на бумаге: светлая плашка с чернильной подписью. */
    public static void inkButton(GuiGraphics graphics, int x, int y, int width, int height,
                                 String label, boolean hovered) {
        graphics.fill(x, y, x + width, y + height, hovered ? 0xFFD6C08A : SLOT);
        graphics.renderOutline(x, y, width, height, hovered ? SLOT_HOT : SLOT_EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0x33FFFFFF);

        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.literal(label),
                x + width / 2 - font.width(label) / 2, y + (height - 8) / 2 + 1,
                INK_TITLE, false);
    }

    // ---------------------------------------------------------------- фактуры

    /** Доски: полосы по постоянному хэшу и стыки через равные промежутки. */
    static void wood(GuiGraphics graphics, int x, int y, int width, int height) {
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

    /** Объём: светлая грань сверху и тёмная снизу, или наоборот у утопленного. */
    static void bevel(GuiGraphics graphics, int x, int y, int width, int height,
                             boolean sunken) {
        int top = sunken ? SHADOW : GLINT;
        int bottom = sunken ? GLINT : SHADOW;
        graphics.fill(x, y, x + width, y + 1, top);
        graphics.fill(x, y, x + 1, y + height, top);
        graphics.fill(x, y + height - 1, x + width, y + height, bottom);
        graphics.fill(x + width - 1, y, x + width, y + height, bottom);
    }

    /** Латунная заклёпка. */
    static void stud(GuiGraphics graphics, int centerX, int centerY) {
        graphics.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, 0xFF221810);
        graphics.fill(centerX - 2, centerY - 2, centerX + 2, centerY + 2, 0xFF8A6E2E);
        graphics.fill(centerX - 2, centerY - 2, centerX, centerY, 0xFFE0C65A);
    }

    // ---------------------------------------------------------------- плашки

    /** Простая панель: для мелких окон, где большая рама была бы тяжелее дела. */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.renderOutline(x, y, width, height, EDGE);
        graphics.renderOutline(x + 1, y + 1, width - 2, height - 2, 0x40C9A227);
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
