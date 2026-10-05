package ru.projectst.rpgcore.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.projectst.rpgcore.net.MenuData;
import ru.projectst.rpgcore.net.Protocol;

/**
 * Книга героя: герой со статами и навыки со слотами.
 *
 * <p>Вкладок две, а не четыре. Статы — это и есть описание героя, а слот без
 * навыка не имеет смысла: раньше, чтобы повесить изученный навык на клавишу,
 * нужно было уйти на соседнюю вкладку и вспомнить там его название. Теперь обе
 * половины видны разом и разделены не пустотой, а чертой с заголовком.
 *
 * <p>Вид — раскрытая книга: деревянная рама, закладки на планке, бумажное поле.
 * Тот же, что у окна инвентаря, из которого книга и открывается ({@link
 * RpgUiBridge}). Разный вид у двух окон, стоящих рядом в одном меню, читается
 * как «одно из них чужое», и выяснять, какое именно, приходится игроку.
 *
 * <p>Экран только показывает и просит. Ни одной проверки здесь нет: можно ли
 * изучить навык, хватает ли очков, открыт ли уровень — решает сервер, и он же
 * отвечает словами. Повторять эти правила на клиенте значило бы держать их в
 * двух местах, и однажды клиент начал бы разрешать то, что сервер запрещает.
 */
public final class CharacterScreen extends Screen {

    /** Вкладки: герой и навыки. Больше делить нечего. */
    public enum Tab {
        HERO("Герой"),
        SKILLS("Навыки");

        private final String title;

        Tab(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    private static final int PANEL_WIDTH = 416;
    private static final int PANEL_HEIGHT = 240;
    private static final int TAB_SIZE = 26;
    private static final int TAB_STEP = 34;
    private static final int CLASS_ROW_HEIGHT = 26;
    private static final int STAT_ROW_HEIGHT = 12;

    /** Сторона ромба со значком навыка. */
    private static final int ICON = 34;
    /** Шаг сетки значков: ромбы не должны соприкасаться углами. */
    private static final int ICON_STEP = 52;
    private static final int ICON_COLUMNS = 6;

    /** Гнездо слота под сеткой навыков. */
    private static final int SLOT_WIDTH = 48;
    private static final int SLOT_HEIGHT = 44;

    /** Ширина левой колонки вкладки героя. */
    private static final int HERO_COLUMN = 160;

    /** Куда вернуться по Esc; null — закрыть совсем. */
    private final Screen parent;

    private Tab tab = Tab.HERO;

    /**
     * Навык, которому ищут слот.
     *
     * <p>Пусто — никто ничего не ждёт. Одно поле вместо прежней пары «номер
     * слота» и «выбранный навык»: два независимых состояния выбора рано или
     * поздно оказались бы включены одновременно.
     */
    private String pendingSkill = "";

    public CharacterScreen() {
        this(null);
    }

    /**
     * Книга, открытая из чужого окна.
     *
     * @param parent куда вернуться по Esc: окно инвентаря, если книгу открыли
     *               закладкой в нём
     */
    public CharacterScreen(Screen parent) {
        super(Component.literal("RpgCore"));
        this.parent = parent;
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private int contentX() {
        return RpgStyle.fieldX(left());
    }

    private int contentY() {
        return RpgStyle.fieldY(top());
    }

    private int contentWidth() {
        return RpgStyle.fieldWidth(PANEL_WIDTH);
    }

    private int contentBottom() {
        return top() + PANEL_HEIGHT - RpgStyle.FRAME - 8;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (parent != null) {
            minecraft.setScreen(parent);
            return;
        }
        super.onClose();
    }

    /**
     * Свой фон вместо ванильного.
     *
     * <p>Minecraft с 1.20.5 размывает мир под любым открытым экраном тем самым
     * шейдером, из-за которого окно выглядит мыльным. Нам размытие не нужно:
     * книга непрозрачная, а мыло с фона переходит на восприятие текста поверх.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY,
                                 float partialTick) {
        graphics.fill(0, 0, width, height, 0xC00A0806);
    }

    // ------------------------------------------------------------------ отрисовка

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int x = left();
        int y = top();
        MenuData menu = ClientNetwork.menu().orElse(null);

        RpgStyle.book(graphics, x, y, PANEL_WIDTH, PANEL_HEIGHT, bannerTitle(menu));

        for (Tab value : Tab.values()) {
            int tabX = tabX(value);
            int tabY = tabY();
            boolean hovered = RpgStyle.hit(mouseX, mouseY, tabX, tabY, TAB_SIZE, TAB_SIZE);
            RpgStyle.tabPlate(graphics, tabX, tabY, TAB_SIZE, value == tab, hovered);
            tabGlyph(graphics, value, tabX + TAB_SIZE / 2, tabY + TAB_SIZE / 2,
                    value == tab || hovered);
        }

        RpgStyle.inkButton(graphics, footerX(), footerY(), 128, 18, "Расставить интерфейс",
                RpgStyle.hit(mouseX, mouseY, footerX(), footerY(), 128, 18));

        if (menu == null) {
            graphics.drawCenteredString(font, Component.literal(
                            "Сервер не прислал данные: закройте и откройте окно"),
                    x + PANEL_WIDTH / 2, y + PANEL_HEIGHT / 2, RpgStyle.INK_DIM);
            return;
        }

        switch (tab) {
            case HERO -> renderHero(graphics, menu, contentX(), contentY(), mouseX, mouseY);
            case SKILLS -> renderSkills(graphics, menu, contentX(), contentY(), mouseX, mouseY);
        }

        // Подпись закладки под курсором рисуется последней: поверх страницы.
        for (Tab value : Tab.values()) {
            if (RpgStyle.hit(mouseX, mouseY, tabX(value), tabY(), TAB_SIZE, TAB_SIZE)) {
                graphics.renderTooltip(font, Component.literal(value.title()), mouseX, mouseY);
            }
        }
    }

    /** Что написать золотом на планке: кто ты и какого уровня. */
    private String bannerTitle(MenuData menu) {
        if (menu == null || menu.classId().isEmpty()) {
            return "Книга героя";
        }
        return strip(classDisplay(menu)) + "  ·  уровень " + menu.level();
    }

    private int tabX(Tab value) {
        return contentX() + value.ordinal() * TAB_STEP;
    }

    private int tabY() {
        return top() + (RpgStyle.HEAD - TAB_SIZE) / 2 - 2;
    }

    private int footerX() {
        return contentX() + contentWidth() - 128;
    }

    private int footerY() {
        return contentBottom() - 18;
    }

    /** Значок закладки: рисуется, а не берётся предметом, — предмета для этого нет. */
    private void tabGlyph(GuiGraphics graphics, Tab value, int centerX, int centerY,
                          boolean lit) {
        int ink = lit ? RpgStyle.INK_TITLE : RpgStyle.INK_DIM;
        if (value == Tab.HERO) {
            // Голова и плечи: самая короткая запись слова «персонаж».
            graphics.fill(centerX - 3, centerY - 7, centerX + 3, centerY - 1, ink);
            graphics.fill(centerX - 6, centerY + 1, centerX + 6, centerY + 7, ink);
        } else {
            RpgStyle.pip(graphics, centerX - 7, centerY - 7, 14, true, ink);
        }
    }

    // ------------------------------------------------------------------ герой

    /**
     * Герой и статы на одной странице.
     *
     * <p>Слева то, что растёт со временем: опыт, очки, ресурс, ядро класса.
     * Справа то, что растёт от снаряжения: статы. Между ними черта — две
     * колонки без неё читаются как один сбившийся список.
     */
    private void renderHero(GuiGraphics graphics, MenuData menu, int x, int y,
                            int mouseX, int mouseY) {
        if (menu.classId().isEmpty()) {
            renderClassChoice(graphics, menu, x, y, mouseY);
            return;
        }

        RpgStyle.ruleVertical(graphics, x + HERO_COLUMN + 8, y, footerY() - y - 6);

        renderProgress(graphics, menu, x, y, HERO_COLUMN);
        renderStats(graphics, menu, x + HERO_COLUMN + 18, y,
                contentWidth() - HERO_COLUMN - 18, mouseX, mouseY);
    }

    /** Выбор класса: один раз и навсегда, поэтому крупно и с предупреждением. */
    private void renderClassChoice(GuiGraphics graphics, MenuData menu, int x, int y,
                                   int mouseY) {
        RpgStyle.caption(graphics, x, y, contentWidth(), "Выберите класс");
        graphics.drawString(font, Component.literal(
                        "Сменить его потом сможет только администратор"),
                x, y + 18, RpgStyle.INK_DIM, false);

        int line = y + 36;
        int row = 0;
        for (MenuData.ClassLine klass : menu.classes()) {
            boolean hovered = rowAt(mouseY, y, CLASS_ROW_HEIGHT, 36) == row;
            if (hovered) {
                graphics.fill(x - 4, line - 3, x + contentWidth() - 4,
                        line + CLASS_ROW_HEIGHT - 5, 0x22D8C68A);
                graphics.fill(x - 4, line - 3, x - 3, line + CLASS_ROW_HEIGHT - 5,
                        RpgStyle.INK_TITLE);
            }
            graphics.drawString(font, Component.literal(strip(klass.display())),
                    x, line, RpgStyle.INK_TITLE, false);
            graphics.drawString(font, Component.literal("платит: " + klass.resourceName()
                            + ",  слотов: " + klass.slots()
                            + ",  предел уровня: " + klass.maxLevel()),
                    x, line + 11, RpgStyle.INK_DIM, false);
            line += CLASS_ROW_HEIGHT;
            row++;
        }
    }

    /** Левая колонка: опыт, очки, ресурс и ядро класса. */
    private void renderProgress(GuiGraphics graphics, MenuData menu, int x, int y,
                                int barWidth) {
        RpgStyle.caption(graphics, x, y, barWidth, "Герой");

        int line = y + 20;
        graphics.drawString(font, Component.literal("Свободных очков"), x, line,
                RpgStyle.INK_DIM, false);
        String points = String.valueOf(menu.points());
        graphics.drawString(font, Component.literal(points),
                x + barWidth - font.width(points), line,
                menu.points() > 0 ? RpgStyle.INK_GOOD : RpgStyle.INK, false);

        line += 16;
        if (menu.xpToNext() > 0) {
            double total = menu.xp() + menu.xpToNext();
            double share = total <= 0 ? 0 : Math.clamp(menu.xp() / total, 0, 1);
            RpgStyle.bar(graphics, x, line, barWidth, 6, share, RpgStyle.READY);
            graphics.drawString(font, Component.literal("Опыт: " + Math.round(menu.xp())
                            + " / " + Math.round(total)),
                    x, line + 10, RpgStyle.INK_DIM, false);
        } else {
            RpgStyle.bar(graphics, x, line, barWidth, 6, 1, RpgStyle.EDGE_BRIGHT);
            graphics.drawString(font, Component.literal("Предел уровня"), x, line + 10,
                    RpgStyle.INK_BAD, false);
        }

        int resourceLine = line + 26;
        ClientNetwork.state().ifPresent(state -> {
            RpgStyle.bar(graphics, x, resourceLine, barWidth, 6,
                    state.resourceMax() <= 0 ? 0 : state.resource() / state.resourceMax(),
                    RpgStyle.RESOURCE);
            graphics.drawString(font, Component.literal(state.resourceName() + ": "
                            + Math.round(Math.floor(state.resource())) + " / "
                            + Math.round(state.resourceMax())),
                    x, resourceLine + 10, RpgStyle.INK_MANA, false);

            if (state.counters().isEmpty()) {
                return;
            }
            RpgStyle.caption(graphics, x, resourceLine + 28, barWidth, "Ядро класса");

            int counterLine = resourceLine + 48;
            for (var counter : state.counters()) {
                graphics.drawString(font, Component.literal(counter.display()), x,
                        counterLine, RpgStyle.INK, false);
                int colour = RpgHud.colourOf(counter.color(), "BUFF");
                int pips = Math.max(1, counter.maxStacks());
                if (pips <= 10) {
                    int pipX = x + barWidth - pips * 10;
                    for (int i = 0; i < pips; i++) {
                        RpgStyle.pip(graphics, pipX, counterLine - 1, 8,
                                i < counter.stacks(), colour);
                        pipX += 10;
                    }
                } else {
                    RpgStyle.bar(graphics, x + barWidth - 74, counterLine, 50, 5,
                            (double) counter.stacks() / pips, colour);
                    graphics.drawString(font,
                            Component.literal(counter.stacks() + "/" + pips),
                            x + barWidth - 20, counterLine, RpgStyle.INK, false);
                }
                counterLine += 14;
            }
        });
    }

    /**
     * Правая колонка: статы значком и коротким именем.
     *
     * <p>«Восстановление выносливости» рядом с числом не оставляет места ни на
     * что, поэтому в списке стоит короткое имя, а полное показывает подсказка.
     * Значок нужен для того же: по нему строка находится взглядом, без чтения.
     */
    private void renderStats(GuiGraphics graphics, MenuData menu, int x, int y, int width,
                             int mouseX, int mouseY) {
        RpgStyle.caption(graphics, x, y, width, "Свойства");

        List<MenuData.StatLine> stats = menu.stats();
        int columnWidth = width / 2;
        int perColumn = Math.max(1, (stats.size() + 1) / 2);

        MenuData.StatLine hovered = null;
        for (int i = 0; i < stats.size(); i++) {
            MenuData.StatLine stat = stats.get(i);
            int columnX = x + (i / perColumn) * columnWidth;
            int line = y + 20 + (i % perColumn) * STAT_ROW_HEIGHT;

            boolean under = RpgStyle.hit(mouseX, mouseY, columnX - 2, line - 2,
                    columnWidth - 4, STAT_ROW_HEIGHT);
            if (under) {
                graphics.fill(columnX - 2, line - 2, columnX + columnWidth - 6,
                        line + STAT_ROW_HEIGHT - 2, 0x22D8C68A);
                hovered = stat;
            }

            StatIcons.draw(graphics, stat.id(), columnX, line);

            String value = trim(stat.value());
            int valueX = columnX + columnWidth - 8 - font.width(value);
            graphics.drawString(font, Component.literal(value), valueX, line,
                    stat.value() == 0 ? RpgStyle.INK_DIM : RpgStyle.INK_TITLE, false);

            int nameX = columnX + StatIcons.SIZE + 3;
            String name = fit(StatIcons.shortName(stat.id(), stat.display()),
                    valueX - nameX - 3);
            graphics.drawString(font, Component.literal(name), nameX, line,
                    RpgStyle.INK, false);
        }

        if (hovered != null) {
            List<Component> about = new ArrayList<>();
            about.add(Component.literal(hovered.display()));
            // Пояснение пришло готовым. Мод не считает его сам: формула живёт в
            // конвейере урона, и второй её расчёт здесь однажды разошёлся бы с
            // тем, что происходит в бою.
            if (!hovered.note().isEmpty()) {
                about.add(Component.literal(hovered.note())
                        .withStyle(style -> style.withColor(0xFF7FC25A)));
            }
            about.add(Component.literal(hovered.id())
                    .withStyle(style -> style.withColor(0xFFB9AC92)));
            graphics.renderComponentTooltip(font, about, mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------ навыки

    /**
     * Навыки сеткой ромбов и слоты под ними.
     *
     * <p>Значок вместо строки с названием: шесть названий в столбик читаются
     * дольше, чем шесть узнаваемых значков, а в бою вспоминают именно значок.
     * Всё остальное — название, стоимость, перезарядку, урон и что навык делает —
     * показывает подсказка под курсором, и там это не мешает смотреть на сетку.
     *
     * <p>Приглушённый значок значит «не изучен». Цвет появляется ровно тогда,
     * когда навык начинает работать.
     *
     * <p>Слоты здесь же, но под своим заголовком и в гнёздах: это другое
     * действие над теми же навыками, и видно должно быть и то и другое сразу.
     */
    private void renderSkills(GuiGraphics graphics, MenuData menu, int x, int y,
                              int mouseX, int mouseY) {
        if (menu.classId().isEmpty()) {
            graphics.drawString(font, Component.literal(
                            "Сначала выберите класс — закладка «Герой»"),
                    x, y, RpgStyle.INK_BAD, false);
            return;
        }

        RpgStyle.caption(graphics, x, y, contentWidth(), "Навыки");
        String points = "очков: " + menu.points();
        graphics.drawString(font, Component.literal(points),
                x + contentWidth() - font.width(points), y,
                menu.points() > 0 ? RpgStyle.INK_GOOD : RpgStyle.INK_DIM, false);

        List<MenuData.SkillLine> skills = menu.skills();
        for (int i = 0; i < skills.size(); i++) {
            MenuData.SkillLine skill = skills.get(i);
            int iconX = iconX(x, i);
            int iconY = iconY(y, i);
            boolean learned = skill.level() > 0;

            // Выбранный навык ждёт слот: обводим его, чтобы было видно, о ком
            // сейчас речь.
            if (skill.id().equals(pendingSkill)) {
                SkillIcons.ring(graphics, iconX, iconY, ICON, 1, false);
            }
            SkillIcons.draw(graphics, skill.id(), iconX, iconY, ICON, learned);

            // Уровень под значком: сколько очков вложено, видно без наведения.
            String level = learned ? skill.level() + "/" + skill.maxLevel() : "—";
            graphics.drawString(font, Component.literal(level),
                    iconX + ICON / 2 - font.width(level) / 2, iconY + ICON + 3,
                    learned ? RpgStyle.INK_TITLE : RpgStyle.INK_DIM, false);
        }

        renderSlotBar(graphics, menu, x, slotCaptionY(menu), mouseX, mouseY);

        // Подсказка рисуется последней: поверх всего, иначе её перекроют значки.
        int hovered = iconAt(x, y, mouseX, mouseY, skills.size());
        if (hovered >= 0) {
            tooltip(graphics, menu, skills.get(hovered), mouseX, mouseY);
        }
    }

    /** Слоты: гнёзда с ромбом и назначенной клавишей. */
    private void renderSlotBar(GuiGraphics graphics, MenuData menu, int x, int y,
                               int mouseX, int mouseY) {
        RpgStyle.caption(graphics, x, y, contentWidth(), "Слоты");

        int barY = y + 20;
        for (int slot = 1; slot <= Math.max(1, menu.slots()); slot++) {
            int slotX = slotX(menu, slot);
            boolean under = RpgStyle.hit(mouseX, mouseY, slotX, barY, SLOT_WIDTH, SLOT_HEIGHT);
            RpgStyle.slot(graphics, slotX, barY, SLOT_WIDTH, SLOT_HEIGHT,
                    under || !pendingSkill.isEmpty());

            String bound = "";
            for (MenuData.SkillLine skill : menu.skills()) {
                if (skill.boundSlot() == slot) {
                    bound = skill.id();
                }
            }
            SkillIcons.draw(graphics, bound, slotX + (SLOT_WIDTH - 26) / 2, barY + 3, 26,
                    !bound.isEmpty());

            // Клавиша — та, что игрок назначил сам: подсказка, не совпадающая с
            // настройкой, врёт, и после неё перестают доверять всем остальным.
            String key = RpgKeys.slotKeyLabel(slot);
            String label = key.equals("не назначено") ? "клавиша?" : key;
            graphics.drawString(font, Component.literal(label),
                    slotX + SLOT_WIDTH / 2 - font.width(label) / 2, barY + SLOT_HEIGHT - 11,
                    key.equals("не назначено") ? RpgStyle.INK_BAD : RpgStyle.INK_TITLE,
                    false);
        }

        // Подсказка под гнёздами, а не рядом с заголовком: там она наезжала бы
        // на него, и длина строки зависела бы от выбранного навыка.
        if (pendingSkill.isEmpty()) {
            graphics.drawString(font, Component.literal(
                            "Правая кнопка по навыку — занять слот, по гнезду — освободить"),
                    x, barY + SLOT_HEIGHT + 4, RpgStyle.INK_DIM, false);
        } else {
            graphics.drawString(font, Component.literal(
                            "Выберите гнездо для навыка,  правая кнопка — отмена"),
                    x, barY + SLOT_HEIGHT + 4, RpgStyle.INK_BAD, false);
        }
        graphics.drawString(font, Component.literal(
                        "Клавиши меняются в настройках управления, раздел RpgCore"),
                x, barY + SLOT_HEIGHT + 16, RpgStyle.INK_DIM, false);
    }

    /** Подсказка о навыке: всё, что сервер посчитал, одним столбиком. */
    private void tooltip(GuiGraphics graphics, MenuData menu, MenuData.SkillLine skill,
                         int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(skill.display())
                .withStyle(style -> style.withColor(skill.level() > 0
                        ? 0xFFC9A227 : 0xFFB9AC92)));
        lines.add(Component.literal("Ступень " + skill.tier())
                .withStyle(style -> style.withColor(0xFFB9AC92)));

        for (String row : skill.description()) {
            lines.add(Component.literal(row)
                    .withStyle(style -> style.withColor(0xFFF2E8CE)));
        }

        lines.add(Component.literal(" "));
        if (skill.damage() > 0) {
            lines.add(Component.literal("Урон за попадание: до " + trim(skill.damage()))
                    .withStyle(style -> style.withColor(0xFFD94A3D)));
        }
        if (skill.mana() > 0) {
            lines.add(Component.literal("Стоимость: " + trim(skill.mana()))
                    .withStyle(style -> style.withColor(0xFF6FA8D9)));
        }
        if (skill.cooldown() > 0) {
            lines.add(Component.literal("Перезарядка: " + trim(skill.cooldown()) + " с")
                    .withStyle(style -> style.withColor(0xFFB9AC92)));
        }

        lines.add(Component.literal(" "));
        String reason = reason(menu, skill);
        lines.add(Component.literal(reason)
                .withStyle(style -> style.withColor(
                        reason.startsWith("нажмите") ? 0xFF7FC25A : 0xFFC98A3D)));
        if (skill.level() > 0) {
            lines.add(Component.literal(skill.boundSlot() > 0
                            ? "в слоте " + skill.boundSlot() + ", правая кнопка — сменить"
                            : "правая кнопка — поставить в слот")
                    .withStyle(style -> style.withColor(0xFFB9AC92)));
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ сетка

    private int iconX(int x, int index) {
        return x + gridOffset() + (index % ICON_COLUMNS) * ICON_STEP
                + (ICON_STEP - ICON) / 2;
    }

    private int iconY(int y, int index) {
        return y + 20 + (index / ICON_COLUMNS) * ICON_STEP;
    }

    /** Отступ, которым сетка ставится посередине страницы. */
    private int gridOffset() {
        return Math.max(0, (contentWidth() - ICON_COLUMNS * ICON_STEP) / 2);
    }

    /** Какой значок под курсором; -1 — никакой. */
    private int iconAt(int x, int y, double mouseX, double mouseY, int count) {
        for (int i = 0; i < count; i++) {
            if (RpgStyle.hit(mouseX, mouseY, iconX(x, i), iconY(y, i), ICON, ICON)) {
                return i;
            }
        }
        return -1;
    }

    /** Сколько рядов занимает сетка навыков. */
    private int skillRows(MenuData menu) {
        int count = Math.max(1, menu.skills().size());
        return (count + ICON_COLUMNS - 1) / ICON_COLUMNS;
    }

    /**
     * Где начинается нижняя половина страницы.
     *
     * <p>Считается от сетки, а не задано числом: класс с другим числом навыков
     * иначе получил бы слоты поверх значков.
     */
    private int slotCaptionY(MenuData menu) {
        return contentY() + 20 + skillRows(menu) * ICON_STEP - 4;
    }

    private int slotBarX(MenuData menu) {
        int slots = Math.max(1, menu.slots());
        return contentX() + Math.max(0, (contentWidth() - slots * ICON_STEP) / 2)
                + (ICON_STEP - SLOT_WIDTH) / 2;
    }

    private int slotX(MenuData menu, int slot) {
        return slotBarX(menu) + (slot - 1) * ICON_STEP;
    }

    private int slotBarY(MenuData menu) {
        return slotCaptionY(menu) + 20;
    }

    // ------------------------------------------------------------------ щелчки

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Tab value : Tab.values()) {
            if (RpgStyle.hit(mouseX, mouseY, tabX(value), tabY(), TAB_SIZE, TAB_SIZE)) {
                tab = value;
                pendingSkill = "";
                return true;
            }
        }
        if (RpgStyle.hit(mouseX, mouseY, footerX(), footerY(), 128, 18)) {
            minecraft.setScreen(new HudEditScreen(this));
            return true;
        }

        MenuData menu = ClientNetwork.menu().orElse(null);
        if (menu == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (tab == Tab.HERO && menu.classId().isEmpty()) {
            int row = rowAt(mouseY, contentY(), CLASS_ROW_HEIGHT, 36);
            if (row >= 0 && row < menu.classes().size()) {
                ActionPayload.send(Protocol.Action.CHOOSE_CLASS, 0,
                        menu.classes().get(row).id());
                return true;
            }
        }

        if (tab == Tab.SKILLS && !menu.classId().isEmpty()) {
            return clickedSkills(menu, mouseX, mouseY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickedSkills(MenuData menu, double mouseX, double mouseY, int button) {
        // Слоты проверяются первыми: они нарисованы ниже сетки, и щелчок по ним
        // не должен уходить в значок.
        for (int slot = 1; slot <= Math.max(1, menu.slots()); slot++) {
            if (!RpgStyle.hit(mouseX, mouseY, slotX(menu, slot), slotBarY(menu),
                    SLOT_WIDTH, SLOT_HEIGHT)) {
                continue;
            }
            if (button == 1) {
                ActionPayload.send(Protocol.Action.UNBIND, slot, "");
                pendingSkill = "";
            } else if (!pendingSkill.isEmpty()) {
                ActionPayload.send(Protocol.Action.BIND, slot, pendingSkill);
                pendingSkill = "";
            }
            return true;
        }

        List<MenuData.SkillLine> skills = menu.skills();
        int index = iconAt(contentX(), contentY(), mouseX, mouseY, skills.size());
        if (index >= 0) {
            MenuData.SkillLine skill = skills.get(index);
            if (button == 1) {
                // Правая кнопка — выбрать для слота, и только у изученного:
                // неизученное сервер в слот всё равно не поставит.
                pendingSkill = skill.level() > 0 && !skill.id().equals(pendingSkill)
                        ? skill.id() : "";
                return true;
            }
            // Изучение и вложение — одно нажатие: сервер сам знает, что сейчас
            // уместно, и откажет, если ни то ни другое.
            ActionPayload.send(skill.level() == 0
                    ? Protocol.Action.UNLOCK : Protocol.Action.UPGRADE, 0, skill.id());
            return true;
        }

        // Мимо всего: снимаем выбор, иначе он остался бы висеть незаметно.
        pendingSkill = "";
        return true;
    }

    // ------------------------------------------------------------------ мелочи

    /**
     * Какая строка списка под этой точкой.
     *
     * <p>Одна формула на показ и на щелчок: две разные означали бы, что
     * подсвечивается одна строка, а нажимается другая, и искать это пришлось бы
     * глазами.
     */
    private int rowAt(double mouseY, int contentY, int rowHeight, int offset) {
        double relative = mouseY - contentY - offset;
        return relative < 0 ? -1 : (int) (relative / rowHeight);
    }

    /**
     * Почему навык не берётся.
     *
     * <p>Показ присланных чисел, а не решение: уровень и очки посчитал сервер,
     * и он же откажет теми же словами, если нажать всё равно.
     */
    private String reason(MenuData menu, MenuData.SkillLine skill) {
        if (skill.level() == 0) {
            if (menu.level() < skill.required()) {
                return "с уровня " + skill.required();
            }
            return menu.points() > 0 ? "нажмите — изучить" : "нет очков";
        }
        if (skill.level() >= skill.maxLevel()) {
            return "максимум";
        }
        return menu.points() > 0 ? "нажмите — уровень" : "нет очков";
    }

    private String classDisplay(MenuData menu) {
        for (MenuData.ClassLine klass : menu.classes()) {
            if (klass.id().equals(menu.classId())) {
                return klass.display();
            }
        }
        return menu.classId();
    }

    /** Обрезает подпись по ширине, чтобы она не налезала на число. */
    private String fit(String text, int limit) {
        if (font.width(text) <= limit) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, limit - font.width("…"))) + "…";
    }

    /** Убирает цветовые коды вида {@code &a}: в моде цвет задаётся иначе. */
    private static String strip(String text) {
        return text.replaceAll("&[0-9a-fk-or]", "");
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value)
                : String.valueOf(Math.round(value * 10) / 10.0);
    }
}
