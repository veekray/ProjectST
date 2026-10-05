package ru.projectst.rpgcore.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.projectst.rpgcore.net.MenuData;
import ru.projectst.rpgcore.net.Protocol;

/**
 * Окно персонажа: герой со статами и навыки со слотами.
 *
 * <p>Вкладок две, а не четыре. Статы — это и есть описание героя, а слот без
 * навыка не имеет смысла: раньше, чтобы повесить изученный навык на клавишу,
 * нужно было уйти на соседнюю вкладку и вспомнить там его название. Теперь обе
 * половины видны разом и разделены не пустотой, а чертой с заголовком и
 * утопленной плашкой: «дальше другое», а не «место кончилось».
 *
 * <p>Экран только показывает и просит. Ни одной проверки здесь нет: можно ли
 * изучить навык, хватает ли очков, открыт ли уровень — решает сервер, и он же
 * отвечает словами. Повторять эти правила на клиенте значило бы держать их в
 * двух местах, и однажды клиент начал бы разрешать то, что сервер запрещает.
 *
 * <p>Всё нарисовано своими руками, без ванильных виджетов: серые кнопки посреди
 * дубовой рамы выглядели как чужое окно, вставленное в наше, — потому что ими и
 * были.
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

    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 244;
    private static final int TAB_WIDTH = 96;
    private static final int TAB_HEIGHT = 18;
    private static final int CLASS_ROW_HEIGHT = 26;
    private static final int STAT_ROW_HEIGHT = 12;

    /** Сторона ромба со значком навыка. */
    private static final int ICON = 32;
    /** Шаг сетки значков: ромбы не должны соприкасаться углами. */
    private static final int ICON_STEP = 46;
    private static final int ICON_COLUMNS = 6;

    /** Плашка слота под сеткой навыков. */
    private static final int SLOT_WIDTH = 44;
    private static final int SLOT_HEIGHT = 42;
    private static final int SLOT_STEP = 46;

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
        super(Component.literal("RpgCore"));
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
        return RpgStyle.fieldY(top()) + 26;
    }

    private int contentWidth() {
        return RpgStyle.fieldWidth(PANEL_WIDTH);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Свой фон вместо ванильного.
     *
     * <p>Minecraft с 1.20.5 размывает мир под любым открытым экраном тем самым
     * шейдером, из-за которого окно выглядит мыльным. Нам размытие не нужно:
     * панель непрозрачная, а мыло с фона переходит на восприятие текста поверх.
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

        RpgStyle.window(graphics, x, y, PANEL_WIDTH, PANEL_HEIGHT, bannerTitle(menu));

        for (Tab value : Tab.values()) {
            int tabX = tabX(value);
            RpgStyle.button(graphics, tabX, RpgStyle.fieldY(y), TAB_WIDTH - 4, TAB_HEIGHT,
                    value.title(),
                    RpgStyle.hit(mouseX, mouseY, tabX, RpgStyle.fieldY(y), TAB_WIDTH - 4,
                            TAB_HEIGHT),
                    value == tab);
        }

        int footerY = footerY();
        RpgStyle.button(graphics, x + PANEL_WIDTH - RpgStyle.FRAME - 5 - 124, footerY, 124, 18,
                "Расставить интерфейс",
                RpgStyle.hit(mouseX, mouseY, x + PANEL_WIDTH - RpgStyle.FRAME - 5 - 124,
                        footerY, 124, 18), false);

        if (menu == null) {
            graphics.drawCenteredString(font, Component.literal(
                            "Сервер не прислал данные: закройте и откройте окно"),
                    x + PANEL_WIDTH / 2, y + PANEL_HEIGHT / 2, RpgStyle.TEXT_DIM);
            return;
        }

        switch (tab) {
            case HERO -> renderHero(graphics, menu, contentX(), contentY(), mouseX, mouseY);
            case SKILLS -> renderSkills(graphics, menu, contentX(), contentY(), mouseX, mouseY);
        }
    }

    /** Что написать на полосе окна: кто ты и какого уровня. */
    private String bannerTitle(MenuData menu) {
        if (menu == null || menu.classId().isEmpty()) {
            return "Книга героя";
        }
        return strip(classDisplay(menu)) + "  ·  уровень " + menu.level();
    }

    private int tabX(Tab value) {
        return contentX() + value.ordinal() * TAB_WIDTH;
    }

    private int footerY() {
        return top() + PANEL_HEIGHT - RpgStyle.FRAME - 5 - 18;
    }

    // ------------------------------------------------------------------ герой

    /**
     * Герой и статы на одной вкладке.
     *
     * <p>Слева то, что растёт со временем: уровень, опыт, ресурс, ядро класса.
     * Справа то, что растёт от снаряжения: статы. Между ними черта — две
     * колонки без неё читаются как один сбившийся список.
     */
    private void renderHero(GuiGraphics graphics, MenuData menu, int x, int y,
                            int mouseX, int mouseY) {
        if (menu.classId().isEmpty()) {
            renderClassChoice(graphics, menu, x, y, mouseY);
            return;
        }

        int leftWidth = 150;
        RpgStyle.dividerVertical(graphics, x + leftWidth + 6, y - 4,
                footerY() - y - 2);

        renderProgress(graphics, menu, x, y, leftWidth);
        renderStats(graphics, menu, x + leftWidth + 16, y,
                contentWidth() - leftWidth - 16, mouseX, mouseY);
    }

    /** Выбор класса: один раз и навсегда, поэтому крупно и с предупреждением. */
    private void renderClassChoice(GuiGraphics graphics, MenuData menu, int x, int y,
                                   int mouseY) {
        RpgStyle.caption(graphics, x, y, contentWidth(), "Выберите класс");
        graphics.drawString(font, Component.literal(
                        "Сменить его потом сможет только администратор"),
                x, y + 14, RpgStyle.TEXT_DIM, true);

        int line = y + 32;
        int row = 0;
        for (MenuData.ClassLine klass : menu.classes()) {
            boolean hovered = rowAt(mouseY, y, CLASS_ROW_HEIGHT, 32) == row;
            RpgStyle.row(graphics, x - 4, line - 3, contentWidth(), CLASS_ROW_HEIGHT - 2,
                    hovered);
            graphics.drawString(font, Component.literal(strip(klass.display())),
                    x, line, RpgStyle.TEXT_WARN, true);
            graphics.drawString(font, Component.literal("платит: " + klass.resourceName()
                            + ",  слотов: " + klass.slots()
                            + ",  предел уровня: " + klass.maxLevel()),
                    x, line + 11, RpgStyle.TEXT_DIM, true);
            line += CLASS_ROW_HEIGHT;
            row++;
        }
    }

    /** Левая колонка: опыт, очки, ресурс и ядро класса. */
    private void renderProgress(GuiGraphics graphics, MenuData menu, int x, int y,
                                int barWidth) {
        RpgStyle.caption(graphics, x, y, barWidth, "Герой");

        int line = y + 16;
        graphics.drawString(font, Component.literal("Свободных очков: " + menu.points()),
                x, line, menu.points() > 0 ? RpgStyle.READY : RpgStyle.TEXT_DIM, true);

        line += 16;
        if (menu.xpToNext() > 0) {
            double total = menu.xp() + menu.xpToNext();
            double share = total <= 0 ? 0 : Math.clamp(menu.xp() / total, 0, 1);
            RpgStyle.bar(graphics, x, line, barWidth, 6, share, RpgStyle.READY);
            graphics.drawString(font, Component.literal("Опыт: " + Math.round(menu.xp())
                            + " / " + Math.round(total)),
                    x, line + 10, RpgStyle.TEXT_DIM, true);
        } else {
            RpgStyle.bar(graphics, x, line, barWidth, 6, 1, RpgStyle.EDGE_BRIGHT);
            graphics.drawString(font, Component.literal("Предел уровня"), x, line + 10,
                    RpgStyle.TEXT_WARN, true);
        }

        int resourceLine = line + 26;
        ClientNetwork.state().ifPresent(state -> {
            RpgStyle.bar(graphics, x, resourceLine, barWidth, 6,
                    state.resourceMax() <= 0 ? 0 : state.resource() / state.resourceMax(),
                    RpgStyle.RESOURCE);
            graphics.drawString(font, Component.literal(state.resourceName() + ": "
                            + Math.round(Math.floor(state.resource())) + " / "
                            + Math.round(state.resourceMax())),
                    x, resourceLine + 10, RpgStyle.RESOURCE, true);

            if (state.counters().isEmpty()) {
                return;
            }
            RpgStyle.caption(graphics, x, resourceLine + 26, barWidth, "Ядро класса");

            int counterLine = resourceLine + 42;
            for (var counter : state.counters()) {
                int colour = RpgHud.colourOf(counter.color(), "BUFF");
                graphics.drawString(font, Component.literal(counter.display()), x,
                        counterLine, colour, true);
                int pips = Math.max(1, counter.maxStacks());
                int pipX = x + barWidth - Math.min(pips, 10) * 10;
                if (pips <= 10) {
                    for (int i = 0; i < pips; i++) {
                        RpgStyle.pip(graphics, pipX, counterLine - 1, 8,
                                i < counter.stacks(), colour);
                        pipX += 10;
                    }
                } else {
                    RpgStyle.bar(graphics, x + barWidth - 70, counterLine, 50, 5,
                            (double) counter.stacks() / pips, colour);
                    graphics.drawString(font,
                            Component.literal(counter.stacks() + "/" + pips),
                            x + barWidth - 16, counterLine, colour, true);
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
        RpgStyle.caption(graphics, x, y, width, "Статы");

        List<MenuData.StatLine> stats = menu.stats();
        int columnWidth = width / 2;
        int perColumn = Math.max(1, (stats.size() + 1) / 2);

        MenuData.StatLine hovered = null;
        for (int i = 0; i < stats.size(); i++) {
            MenuData.StatLine stat = stats.get(i);
            int columnX = x + (i / perColumn) * columnWidth;
            int line = y + 16 + (i % perColumn) * STAT_ROW_HEIGHT;

            boolean under = RpgStyle.hit(mouseX, mouseY, columnX - 2, line - 2,
                    columnWidth - 4, STAT_ROW_HEIGHT);
            RpgStyle.row(graphics, columnX - 2, line - 2, columnWidth - 4,
                    STAT_ROW_HEIGHT, under);
            if (under) {
                hovered = stat;
            }

            StatIcons.draw(graphics, stat.id(), columnX, line);

            String value = trim(stat.value());
            int valueX = columnX + columnWidth - 8 - font.width(value);
            graphics.drawString(font, Component.literal(value), valueX, line,
                    stat.value() == 0 ? RpgStyle.TEXT_DIM : RpgStyle.TEXT, true);

            int nameX = columnX + StatIcons.SIZE + 3;
            String name = fit(StatIcons.shortName(stat.id(), stat.display()),
                    valueX - nameX - 3);
            graphics.drawString(font, Component.literal(name), nameX, line,
                    RpgStyle.TEXT_DIM, true);
        }

        if (hovered != null) {
            graphics.renderComponentTooltip(font, List.of(
                            Component.literal(hovered.display())
                                    .withStyle(style -> style.withColor(RpgStyle.TEXT)),
                            Component.literal(hovered.id())
                                    .withStyle(style -> style.withColor(RpgStyle.TEXT_DIM))),
                    mouseX, mouseY);
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
     * <p>Слоты здесь же, но на отдельной утопленной плашке: это другое действие
     * над теми же навыками, и видно должно быть и то и другое сразу.
     */
    private void renderSkills(GuiGraphics graphics, MenuData menu, int x, int y,
                              int mouseX, int mouseY) {
        if (menu.classId().isEmpty()) {
            graphics.drawString(font, Component.literal(
                            "Сначала выберите класс — вкладка «Герой»"),
                    x, y, RpgStyle.HEALTH_LOW, true);
            return;
        }

        String points = "очков: " + menu.points();
        RpgStyle.caption(graphics, x, y, contentWidth() - font.width(points) - 6, "Навыки");
        graphics.drawString(font, Component.literal(points),
                x + contentWidth() - font.width(points), y,
                menu.points() > 0 ? RpgStyle.READY : RpgStyle.TEXT_DIM, true);

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
                    iconX + ICON / 2 - font.width(level) / 2, iconY + ICON + 2,
                    learned ? RpgStyle.TEXT : RpgStyle.TEXT_DIM, true);
        }

        renderSlotBar(graphics, menu, x, slotCaptionY(menu), mouseX, mouseY);

        // Подсказка рисуется последней: поверх всего, иначе её перекроют значки.
        int hovered = iconAt(x, y, mouseX, mouseY, skills.size());
        if (hovered >= 0) {
            tooltip(graphics, menu, skills.get(hovered), mouseX, mouseY);
        }
    }

    /** Слоты: плашки с ромбом и назначенной клавишей. */
    private void renderSlotBar(GuiGraphics graphics, MenuData menu, int x, int y,
                               int mouseX, int mouseY) {
        String hint = pendingSkill.isEmpty()
                ? "правая кнопка по навыку — занять слот"
                : "выберите слот,  правая кнопка — отмена";
        RpgStyle.caption(graphics, x, y, contentWidth() - font.width(hint) - 6, "Слоты");
        graphics.drawString(font, Component.literal(hint),
                x + contentWidth() - font.width(hint), y,
                pendingSkill.isEmpty() ? RpgStyle.TEXT_DIM : RpgStyle.TEXT_WARN, true);

        int barY = y + 16;
        int slots = Math.max(1, menu.slots());
        RpgStyle.plate(graphics, slotBarX(menu) - 4, barY - 4,
                slots * SLOT_STEP + 6, SLOT_HEIGHT + 8);

        for (int slot = 1; slot <= slots; slot++) {
            int slotX = slotX(menu, slot);
            boolean under = RpgStyle.hit(mouseX, mouseY, slotX, barY, SLOT_WIDTH, SLOT_HEIGHT);

            graphics.fill(slotX, barY, slotX + SLOT_WIDTH, barY + SLOT_HEIGHT, 0x66000000);
            RpgStyle.bevel(graphics, slotX, barY, SLOT_WIDTH, SLOT_HEIGHT, true);
            graphics.renderOutline(slotX, barY, SLOT_WIDTH, SLOT_HEIGHT,
                    !pendingSkill.isEmpty() || under ? RpgStyle.EDGE_BRIGHT : RpgStyle.EDGE);

            String bound = "";
            for (MenuData.SkillLine skill : menu.skills()) {
                if (skill.boundSlot() == slot) {
                    bound = skill.id();
                }
            }
            SkillIcons.draw(graphics, bound, slotX + (SLOT_WIDTH - 24) / 2, barY + 3, 24,
                    !bound.isEmpty());

            // Клавиша — та, что игрок назначил сам: подсказка, не совпадающая с
            // настройкой, врёт, и после неё перестают доверять всем остальным.
            String key = RpgKeys.slotKeyLabel(slot);
            String label = key.equals("не назначено") ? "клавиша?" : key;
            graphics.drawString(font, Component.literal(label),
                    slotX + SLOT_WIDTH / 2 - font.width(label) / 2, barY + SLOT_HEIGHT - 11,
                    key.equals("не назначено") ? RpgStyle.HEALTH_LOW : RpgStyle.RESOURCE,
                    true);
        }

        graphics.drawString(font, Component.literal(
                        "Клавиши меняются в настройках управления, раздел RpgCore."
                                + "  Правая кнопка по слоту — освободить"),
                x, barY + SLOT_HEIGHT + 10, RpgStyle.TEXT_DIM, true);
    }

    /** Подсказка о навыке: всё, что сервер посчитал, одним столбиком. */
    private void tooltip(GuiGraphics graphics, MenuData menu, MenuData.SkillLine skill,
                         int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(skill.display())
                .withStyle(style -> style.withColor(skill.level() > 0
                        ? RpgStyle.TEXT_WARN : RpgStyle.TEXT_DIM)));
        lines.add(Component.literal("Ступень " + skill.tier())
                .withStyle(style -> style.withColor(RpgStyle.TEXT_DIM)));

        for (String row : skill.description()) {
            lines.add(Component.literal(row)
                    .withStyle(style -> style.withColor(RpgStyle.TEXT)));
        }

        lines.add(Component.literal(" "));
        if (skill.damage() > 0) {
            lines.add(Component.literal("Урон за попадание: до " + trim(skill.damage()))
                    .withStyle(style -> style.withColor(RpgStyle.HEALTH_LOW)));
        }
        if (skill.mana() > 0) {
            lines.add(Component.literal("Стоимость: " + trim(skill.mana()))
                    .withStyle(style -> style.withColor(RpgStyle.RESOURCE)));
        }
        if (skill.cooldown() > 0) {
            lines.add(Component.literal("Перезарядка: " + trim(skill.cooldown()) + " с")
                    .withStyle(style -> style.withColor(RpgStyle.TEXT_DIM)));
        }

        lines.add(Component.literal(" "));
        lines.add(Component.literal(reason(menu, skill))
                .withStyle(style -> style.withColor(
                        reason(menu, skill).startsWith("нажмите")
                                ? RpgStyle.READY : RpgStyle.COOLDOWN)));
        if (skill.level() > 0) {
            lines.add(Component.literal(skill.boundSlot() > 0
                            ? "в слоте " + skill.boundSlot() + ", правая кнопка — сменить"
                            : "правая кнопка — поставить в слот")
                    .withStyle(style -> style.withColor(RpgStyle.TEXT_DIM)));
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ сетка

    private int iconX(int x, int index) {
        return x + gridOffset() + (index % ICON_COLUMNS) * ICON_STEP
                + (ICON_STEP - ICON) / 2;
    }

    private int iconY(int y, int index) {
        return y + 16 + (index / ICON_COLUMNS) * ICON_STEP;
    }

    /** Отступ, которым сетка ставится посередине поля. */
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

    private int slotBarX(MenuData menu) {
        int slots = Math.max(1, menu.slots());
        return contentX() + Math.max(0, (contentWidth() - slots * SLOT_STEP) / 2);
    }

    private int slotX(MenuData menu, int slot) {
        return slotBarX(menu) + (slot - 1) * SLOT_STEP;
    }

    /** Сколько рядов занимает сетка навыков. */
    private int skillRows(MenuData menu) {
        int count = Math.max(1, menu.skills().size());
        return (count + ICON_COLUMNS - 1) / ICON_COLUMNS;
    }

    /**
     * Где начинается нижняя половина вкладки.
     *
     * <p>Считается от сетки, а не задано числом: класс с другим числом навыков
     * иначе получил бы слоты поверх значков.
     */
    private int slotCaptionY(MenuData menu) {
        return contentY() + 16 + skillRows(menu) * ICON_STEP + 10;
    }

    private int slotBarY(MenuData menu) {
        return slotCaptionY(menu) + 16;
    }

    // ------------------------------------------------------------------ щелчки

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Tab value : Tab.values()) {
            if (RpgStyle.hit(mouseX, mouseY, tabX(value), RpgStyle.fieldY(top()),
                    TAB_WIDTH - 4, TAB_HEIGHT)) {
                tab = value;
                pendingSkill = "";
                return true;
            }
        }
        int footerX = left() + PANEL_WIDTH - RpgStyle.FRAME - 5 - 124;
        if (RpgStyle.hit(mouseX, mouseY, footerX, footerY(), 124, 18)) {
            minecraft.setScreen(new HudEditScreen(this));
            return true;
        }

        MenuData menu = ClientNetwork.menu().orElse(null);
        if (menu == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (tab == Tab.HERO && menu.classId().isEmpty()) {
            int row = rowAt(mouseY, contentY(), CLASS_ROW_HEIGHT, 32);
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
        // Слоты проверяются первыми: они нарисованы поверх плашки, и щелчок по
        // ним не должен уходить в сетку выше.
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
