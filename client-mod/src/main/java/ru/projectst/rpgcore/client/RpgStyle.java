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
 * <p>Вид один на всё: чёрное поле, золотая волосяная черта, много воздуха.
 * Рамы нет намеренно — рама вокруг тёмного поля всегда спорит с тем, что в поле
 * нарисовано, а здесь самое яркое и есть содержимое: цветные ромбы навыков и
 * полосы. Чем тише окно, тем лучше их видно. По той же причине нет ни фактур,
 * ни плашек под закладками: каждая из них — ещё одна рамка внутри окна, у
 * которого рамки нет.
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
 * <p>Кнопки тоже свои. Ванильные серые прямоугольники посреди тёмного поля
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

    // ---------------------------------------------------------------- тушь

    /**
     * Чёрное и золото.
     *
     * <p>Рамы нет: вместо неё одна золотая волосяная черта по краю и много
     * воздуха. Рама вокруг тёмного поля всегда спорит с тем, что в поле
     * нарисовано, — а в этом окне самое яркое и есть содержимое: цветные ромбы
     * навыков и полосы. Чем тише окно, тем лучше их видно.
     *
     * <p>Тёмное и для экрана в бою, и для меню: цветовая семья теперь одна, и
     * полоса, увиденная в бою, выглядит в меню так же.
     */
    private static final int VOID = 0xF00B0B0D;
    /** Волосяная черта: края, разделители, обводка гнёзд. */
    private static final int HAIR = 0xFF3A372E;

    /** Тушь: основной текст. */
    public static final int INK = 0xFF9A9280;
    public static final int INK_DIM = 0xFF6E6858;
    /** Золото: заголовки и всё, что важнее остального. */
    public static final int INK_TITLE = 0xFFD8C68A;
    public static final int INK_GOOD = 0xFF7FA85A;
    public static final int INK_BAD = 0xFFC9544A;
    public static final int INK_MANA = 0xFF6A9AD8;
    public static final int TITLE_GOLD = 0xFFD8C68A;

    /** Отступ волосяной черты от края окна. */
    public static final int FRAME = 8;
    /** Высота верхней строки: на ней стоят закладки и название. */
    public static final int HEAD = 38;

    private RpgStyle() {
    }

    // ---------------------------------------------------------------- окно

    /**
     * Окно: тёмное поле, золотая черта по краю, название в верхней строке.
     *
     * <p>Размеры поля наружу отдаются не числами, а {@link #FRAME} и
     * {@link #HEAD}: экран считает по ним свои отступы, и край с содержимым не
     * могут разъехаться при правке одной из сторон.
     */
    public static void book(GuiGraphics graphics, int x, int y, int width, int height,
                            String title) {
        graphics.fill(x, y, x + width, y + height, VOID);
        // Одна черта вместо рамы, и с отступом: прижатая к краю читается как
        // обводка выделения, отодвинутая — как край страницы.
        graphics.renderOutline(x + FRAME, y + FRAME, width - FRAME * 2, height - FRAME * 2,
                HAIR);

        var font = Minecraft.getInstance().font;
        String caps = title.toUpperCase();
        graphics.drawString(font, Component.literal(caps),
                x + width - FRAME - 14 - font.width(caps), y + 18, TITLE_GOLD, false);
    }

    /** Левый край содержимого окна, стоящего в точке x. */
    public static int fieldX(int x) {
        return x + FRAME + 14;
    }

    /** Верх содержимого окна, стоящего в точке y. */
    public static int fieldY(int y) {
        return y + HEAD + 8;
    }

    /** Ширина содержимого окна шириной width. */
    public static int fieldWidth(int width) {
        return width - (FRAME + 14) * 2;
    }

    /**
     * Закладка: значок, под выбранной — золотая черта.
     *
     * <p>Без плашки. Плашка закладки — это ещё одна рамка внутри окна, у
     * которого рамки нет намеренно; черта под значком говорит ровно то же и не
     * спорит с содержимым.
     */
    public static void tabPlate(GuiGraphics graphics, int x, int y, int size,
                                boolean active, boolean hovered) {
        if (active) {
            graphics.fill(x, y + size + 2, x + size, y + size + 3, TITLE_GOLD);
        } else if (hovered) {
            graphics.fill(x, y + size + 2, x + size, y + size + 3, HAIR);
        }
    }

    /** Гнездо: только обводка. Заливка здесь спорила бы со значком внутри. */
    public static void slot(GuiGraphics graphics, int x, int y, int width, int height,
                            boolean hot) {
        graphics.renderOutline(x, y, width, height, hot ? TITLE_GOLD : HAIR);
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
        graphics.fill(x, y, x + width, y + 1, HAIR);
        int center = x + width / 2;
        graphics.fill(center - 3, y - 2, center + 4, y + 3, VOID);
        pip(graphics, center - 2, y - 2, 5, true, INK_TITLE);
    }

    /** Та же черта, но стоймя: ею делятся колонки страницы. */
    public static void ruleVertical(GuiGraphics graphics, int x, int y, int height) {
        graphics.fill(x, y, x + 1, y + height, HAIR);
        int center = y + height / 2;
        graphics.fill(x - 2, center - 3, x + 3, center + 4, VOID);
        pip(graphics, x - 2, center - 2, 5, true, INK_TITLE);
    }

    /** Кнопка: обводка и подпись, без заливки. */
    public static void inkButton(GuiGraphics graphics, int x, int y, int width, int height,
                                 String label, boolean hovered) {
        graphics.renderOutline(x, y, width, height, hovered ? TITLE_GOLD : HAIR);

        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.literal(label),
                x + width / 2 - font.width(label) / 2, y + (height - 8) / 2 + 1,
                hovered ? INK_TITLE : INK, false);
    }

    /**
     * Кнопка на тёмном: обводка и подпись.
     *
     * <p>Рисуется и проверяется руками, без ванильного виджета: виджет приносит
     * свою серую текстуру, и посреди тёмного поля она выглядит как чужое окно,
     * вставленное в наше.
     *
     * @param active нажатая или выбранная: подсвечена постоянно
     */
    public static void button(GuiGraphics graphics, int x, int y, int width, int height,
                              String label, boolean hovered, boolean active) {
        graphics.fill(x, y, x + width, y + height, active ? 0x55D8C68A : TRACK);
        graphics.renderOutline(x, y, width, height,
                hovered || active ? TITLE_GOLD : HAIR);

        var font = Minecraft.getInstance().font;
        int textColour = active ? INK_TITLE : hovered ? TEXT : INK;
        graphics.drawString(font, Component.literal(label),
                x + width / 2 - font.width(label) / 2, y + (height - 8) / 2 + 1,
                textColour, false);
    }

    /** Простая панель: для мелких окон, где большое поле было бы тяжелее дела. */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, VOID);
        graphics.renderOutline(x, y, width, height, HAIR);
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
