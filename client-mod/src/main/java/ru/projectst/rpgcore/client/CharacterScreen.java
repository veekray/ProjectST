package ru.projectst.rpgcore.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.projectst.rpgcore.net.MenuData;
import ru.projectst.rpgcore.net.Protocol;

/**
 * Окно персонажа: класс, навыки, слоты, статы.
 *
 * <p>Экран только показывает и просит. Ни одной проверки здесь нет: можно ли
 * изучить навык, хватает ли очков, открыт ли уровень — решает сервер, и он же
 * отвечает словами. Повторять эти правила на клиенте значило бы держать их в
 * двух местах, и однажды клиент начал бы разрешать то, что сервер запрещает.
 *
 * <p>Подсказка под каждым навыком всё же есть: что мешает, видно до нажатия. Но
 * это <b>показ</b> присланных сервером чисел, а не собственное решение экрана.
 *
 * <p>Всё нарисовано своими руками, без ванильных виджетов: серые кнопки посреди
 * бронзовой панели выглядели как чужое окно, вставленное в наше, — потому что
 * ими и были.
 */
public final class CharacterScreen extends Screen {

    /** Вкладки. Порядок тот же, что в старом меню на сундуках: привычка дороже. */
    public enum Tab {
        CHARACTER("Персонаж"),
        SKILLS("Навыки"),
        SLOTS("Слоты"),
        STATS("Статы");

        private final String title;

        Tab(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 214;
    private static final int TAB_WIDTH = 80;
    private static final int TAB_HEIGHT = 18;
    private static final int ROW_HEIGHT = 12;
    private static final int CLASS_ROW_HEIGHT = 26;
    /** Сторона ромба со значком навыка. */
    private static final int ICON = 30;
    /** Шаг сетки значков: ромбы не должны соприкасаться углами. */
    private static final int ICON_STEP = 38;
    private static final int ICON_COLUMNS = 6;

    private Tab tab = Tab.CHARACTER;
    /**
     * Идёт ли выбор слота.
     *
     * <p>На вкладке слотов это номер слота, для которого выбирают навык; на
     * вкладке навыков — минус один, то есть «навык выбран, ждём слот». Одно поле
     * на оба случая: два независимых состояния выбора рано или поздно оказались
     * бы включены одновременно.
     */
    private int choosingSlot;

    /** Навык, которому ищут слот на вкладке навыков. */
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
        RpgStyle.panel(graphics, x, y, PANEL_WIDTH, PANEL_HEIGHT);

        for (Tab value : Tab.values()) {
            int tabX = tabX(value);
            RpgStyle.button(graphics, tabX, y + 6, TAB_WIDTH - 2, TAB_HEIGHT, value.title(),
                    RpgStyle.hit(mouseX, mouseY, tabX, y + 6, TAB_WIDTH - 2, TAB_HEIGHT),
                    value == tab);
        }
        RpgStyle.divider(graphics, x + 6, y + 27, PANEL_WIDTH - 12);

        int footerY = y + PANEL_HEIGHT - 24;
        RpgStyle.button(graphics, x + PANEL_WIDTH - 130, footerY, 124, 18,
                "Расставить интерфейс",
                RpgStyle.hit(mouseX, mouseY, x + PANEL_WIDTH - 130, footerY, 124, 18), false);

        MenuData menu = ClientNetwork.menu().orElse(null);
        if (menu == null) {
            graphics.drawCenteredString(font, Component.literal(
                            "Сервер не прислал данные: закройте и откройте окно"),
                    x + PANEL_WIDTH / 2, y + PANEL_HEIGHT / 2, RpgStyle.TEXT_DIM);
            return;
        }

        int contentX = x + 12;
        int contentY = y + 36;
        switch (tab) {
            case CHARACTER -> renderCharacter(graphics, menu, contentX, contentY, mouseY);
            case SKILLS -> renderSkills(graphics, menu, contentX, contentY, mouseX, mouseY);
            case SLOTS -> renderSlots(graphics, menu, contentX, contentY, mouseY);
            case STATS -> renderStats(graphics, menu, contentX, contentY);
        }
    }

    private int tabX(Tab value) {
        return left() + 6 + value.ordinal() * TAB_WIDTH;
    }

    // ------------------------------------------------------------------ вкладки

    private void renderCharacter(GuiGraphics graphics, MenuData menu, int x, int y,
                                 int mouseY) {
        if (menu.classId().isEmpty()) {
            graphics.drawString(font, Component.literal("Выберите класс"), x, y,
                    RpgStyle.TEXT_WARN, true);
            graphics.drawString(font, Component.literal(
                            "Сменить его потом сможет только администратор"),
                    x, y + 12, RpgStyle.TEXT_DIM, true);

            int line = y + 30;
            int row = 0;
            for (MenuData.ClassLine klass : menu.classes()) {
                boolean hovered = rowAt(mouseY, y, CLASS_ROW_HEIGHT, 30) == row;
                RpgStyle.row(graphics, x - 6, line - 3, PANEL_WIDTH - 24,
                        CLASS_ROW_HEIGHT - 2, hovered);
                graphics.drawString(font, Component.literal(strip(klass.display())),
                        x, line, RpgStyle.TEXT_WARN, true);
                graphics.drawString(font, Component.literal("платит: " + klass.resourceName()
                                + ",  слотов: " + klass.slots()
                                + ",  предел уровня: " + klass.maxLevel()),
                        x, line + 11, RpgStyle.TEXT_DIM, true);
                line += CLASS_ROW_HEIGHT;
                row++;
            }
            return;
        }

        graphics.drawString(font, Component.literal(strip(classDisplay(menu))), x, y,
                RpgStyle.TEXT_WARN, true);
        graphics.drawString(font, Component.literal("Уровень: " + menu.level()), x, y + 16,
                RpgStyle.TEXT, true);
        graphics.drawString(font, Component.literal("Свободных очков: " + menu.points()),
                x, y + 28, menu.points() > 0 ? RpgStyle.READY : RpgStyle.TEXT_DIM, true);

        int barWidth = PANEL_WIDTH - 36;
        int barY = y + 48;
        if (menu.xpToNext() > 0) {
            double total = menu.xp() + menu.xpToNext();
            double share = total <= 0 ? 0 : Math.clamp(menu.xp() / total, 0, 1);
            RpgStyle.bar(graphics, x, barY, barWidth, 6, share, RpgStyle.READY);
            graphics.drawString(font, Component.literal("Опыт: " + Math.round(menu.xp())
                            + " / " + Math.round(total)),
                    x, barY + 11, RpgStyle.TEXT_DIM, true);
        } else {
            RpgStyle.bar(graphics, x, barY, barWidth, 6, 1, RpgStyle.EDGE_BRIGHT);
            graphics.drawString(font, Component.literal("Предел уровня"), x, barY + 11,
                    RpgStyle.TEXT_WARN, true);
        }

        ClientNetwork.state().ifPresent(state -> {
            graphics.drawString(font, Component.literal(state.resourceName() + ": "
                            + Math.round(Math.floor(state.resource())) + " / "
                            + Math.round(state.resourceMax())),
                    x, barY + 28, RpgStyle.RESOURCE, true);

            if (state.counters().isEmpty()) {
                return;
            }
            RpgStyle.divider(graphics, x, barY + 42, PANEL_WIDTH - 36);
            graphics.drawString(font, Component.literal("Ядро класса"), x, barY + 48,
                    RpgStyle.TEXT_DIM, true);

            int line = barY + 62;
            for (var counter : state.counters()) {
                int colour = RpgHud.colourOf(counter.color(), "BUFF");
                graphics.drawString(font, Component.literal(counter.display()), x, line,
                        colour, true);
                int pipX = x + 120;
                int pips = Math.max(1, counter.maxStacks());
                if (pips <= 10) {
                    for (int i = 0; i < pips; i++) {
                        RpgStyle.pip(graphics, pipX, line - 1, 8, i < counter.stacks(), colour);
                        pipX += 10;
                    }
                } else {
                    RpgStyle.bar(graphics, pipX, line, 90, 5,
                            (double) counter.stacks() / pips, colour);
                    graphics.drawString(font, Component.literal(counter.stacks() + "/" + pips),
                            pipX + 96, line, colour, true);
                }
                line += 14;
            }
        });
    }

    /**
     * Навыки сеткой ромбов.
     *
     * <p>Значок вместо строки с названием: шесть названий в столбик читаются
     * дольше, чем шесть узнаваемых значков, а в бою вспоминают именно значок.
     * Всё остальное — название, стоимость, перезарядку, урон и что навык делает —
     * показывает подсказка под курсором, и там это не мешает смотреть на сетку.
     *
     * <p>Приглушённый значок значит «не изучен». Цвет появляется ровно тогда,
     * когда навык начинает работать.
     */
    private void renderSkills(GuiGraphics graphics, MenuData menu, int x, int y,
                              int mouseX, int mouseY) {
        if (menu.classId().isEmpty()) {
            graphics.drawString(font, Component.literal("Сначала выберите класс"), x, y,
                    RpgStyle.HEALTH_LOW, true);
            return;
        }
        graphics.drawString(font, Component.literal("Свободных очков: " + menu.points()),
                x, y, menu.points() > 0 ? RpgStyle.READY : RpgStyle.TEXT_DIM, true);
        graphics.drawString(font, Component.literal(
                        "нажатие — изучить или вложить очко,  правая кнопка — в слот"),
                x + 110, y, RpgStyle.TEXT_DIM, true);

        List<MenuData.SkillLine> skills = menu.skills();
        for (int i = 0; i < skills.size(); i++) {
            MenuData.SkillLine skill = skills.get(i);
            int iconX = iconX(x, i);
            int iconY = iconY(y, i);
            boolean learned = skill.level() > 0;

            SkillIcons.draw(graphics, skill.id(), iconX, iconY, ICON, learned);

            // Уровень под значком: сколько очков уже вложено, видно без наведения.
            String level = learned ? skill.level() + "/" + skill.maxLevel() : "—";
            graphics.drawString(font, Component.literal(level),
                    iconX + ICON / 2 - font.width(level) / 2, iconY + ICON - 1,
                    learned ? RpgStyle.TEXT : RpgStyle.TEXT_DIM, true);

            // Слот, если навык на него поставлен: иначе пришлось бы ходить на
            // другую вкладку, чтобы проверить, что именно висит на клавише.
            if (skill.boundSlot() > 0) {
                String slot = String.valueOf(skill.boundSlot());
                graphics.fill(iconX + ICON - 9, iconY + 1, iconX + ICON - 1, iconY + 9,
                        0xF01C1510);
                graphics.drawString(font, Component.literal(slot),
                        iconX + ICON - 7, iconY + 2, RpgStyle.TEXT_WARN, true);
            }
        }

        if (choosingSlot > 0) {
            graphics.drawString(font, Component.literal(
                            "Выберите слот для навыка, или нажмите мимо, чтобы отменить"),
                    x, y + 4 + rows(skills.size()) * ICON_STEP, RpgStyle.TEXT_WARN, true);
            for (int slot = 1; slot <= menu.slots(); slot++) {
                int slotX = x + (slot - 1) * 34;
                int slotY = y + 16 + rows(skills.size()) * ICON_STEP;
                boolean hovered = RpgStyle.hit(mouseX, mouseY, slotX, slotY, 30, 18);
                RpgStyle.button(graphics, slotX, slotY, 30, 18,
                        String.valueOf(slot), hovered, false);
            }
        }

        // Подсказка рисуется последней: поверх всего, иначе её перекроют значки.
        int hovered = iconAt(x, y, mouseX, mouseY, skills.size());
        if (hovered >= 0 && choosingSlot == 0) {
            tooltip(graphics, menu, skills.get(hovered), mouseX, mouseY);
        }
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

    private int iconX(int x, int index) {
        return x + (index % ICON_COLUMNS) * ICON_STEP;
    }

    private int iconY(int y, int index) {
        return y + 16 + (index / ICON_COLUMNS) * ICON_STEP;
    }

    private int rows(int count) {
        return (count + ICON_COLUMNS - 1) / ICON_COLUMNS;
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

    private void renderSlots(GuiGraphics graphics, MenuData menu, int x, int y, int mouseY) {
        if (menu.classId().isEmpty()) {
            graphics.drawString(font, Component.literal("Сначала выберите класс"), x, y,
                    RpgStyle.HEALTH_LOW, true);
            return;
        }

        if (choosingSlot > 0) {
            graphics.drawString(font, Component.literal("Слот " + choosingSlot
                            + ": выберите навык,  правая кнопка — освободить"),
                    x, y, RpgStyle.TEXT_WARN, true);
            int line = y + 16;
            int row = 0;
            for (MenuData.SkillLine skill : learned(menu)) {
                boolean hovered = rowAt(mouseY, y, ROW_HEIGHT, 16) == row;
                RpgStyle.row(graphics, x - 6, line - 2, PANEL_WIDTH - 24, ROW_HEIGHT, hovered);
                graphics.drawString(font, Component.literal(skill.display()), x, line,
                        RpgStyle.TEXT, true);
                line += ROW_HEIGHT;
                row++;
            }
            if (row == 0) {
                graphics.drawString(font, Component.literal(
                                "Изученных навыков нет — вкладка «Навыки»"),
                        x, line, RpgStyle.TEXT_DIM, true);
            }
            return;
        }

        graphics.drawString(font, Component.literal(
                        "Нажатие — занять слот,  правая кнопка — освободить"),
                x, y, RpgStyle.TEXT_DIM, true);

        int line = y + 16;
        for (int slot = 1; slot <= menu.slots(); slot++) {
            boolean hovered = rowAt(mouseY, y, ROW_HEIGHT, 16) == slot - 1;
            RpgStyle.row(graphics, x - 6, line - 2, PANEL_WIDTH - 24, ROW_HEIGHT, hovered);

            String bound = "пусто";
            String boundId = "";
            for (MenuData.SkillLine skill : menu.skills()) {
                if (skill.boundSlot() == slot) {
                    bound = skill.display();
                    boundId = skill.id();
                }
            }
            if (!boundId.isEmpty()) {
                SkillIcons.draw(graphics, boundId, x + PANEL_WIDTH - 60, line - 4, 12, true);
            }
            // Клавиша — та, что игрок назначил сам: подсказка, не совпадающая с
            // настройкой, врёт, и после неё перестают доверять всем остальным.
            String key = RpgKeys.slotKeyLabel(slot);
            graphics.drawString(font, Component.literal("Слот " + slot + ": " + bound),
                    x, line, bound.equals("пусто") ? RpgStyle.TEXT_DIM : RpgStyle.TEXT, true);
            graphics.drawString(font, Component.literal(key),
                    x + PANEL_WIDTH - 36 - font.width(key), line,
                    key.equals("не назначено") ? RpgStyle.TEXT_WARN : RpgStyle.RESOURCE, true);
            line += ROW_HEIGHT;
        }
        graphics.drawString(font, Component.literal(
                        "Клавиши меняются в настройках управления, раздел RpgCore"),
                x, line + 8, RpgStyle.TEXT_DIM, true);
    }

    private void renderStats(GuiGraphics graphics, MenuData menu, int x, int y) {
        int line = y;
        int column = 0;
        for (MenuData.StatLine stat : menu.stats()) {
            int columnX = x + column * 160;
            graphics.drawString(font, Component.literal(stat.display()), columnX, line,
                    RpgStyle.TEXT_DIM, true);
            String value = trim(stat.value());
            graphics.drawString(font, Component.literal(value),
                    columnX + 150 - font.width(value), line, RpgStyle.TEXT, true);
            line += 11;
            if (line > top() + PANEL_HEIGHT - 34) {
                line = y;
                column++;
            }
        }
    }

    // ------------------------------------------------------------------ щелчки

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int y = top();

        for (Tab value : Tab.values()) {
            if (RpgStyle.hit(mouseX, mouseY, tabX(value), y + 6, TAB_WIDTH - 2, TAB_HEIGHT)) {
                tab = value;
                choosingSlot = 0;
                return true;
            }
        }
        int footerY = y + PANEL_HEIGHT - 24;
        if (RpgStyle.hit(mouseX, mouseY, left() + PANEL_WIDTH - 130, footerY, 124, 18)) {
            minecraft.setScreen(new HudEditScreen(this));
            return true;
        }

        MenuData menu = ClientNetwork.menu().orElse(null);
        if (menu == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int contentY = y + 36;

        if (tab == Tab.CHARACTER && menu.classId().isEmpty()) {
            int row = rowAt(mouseY, contentY, CLASS_ROW_HEIGHT, 30);
            if (row >= 0 && row < menu.classes().size()) {
                ActionPayload.send(Protocol.Action.CHOOSE_CLASS, 0,
                        menu.classes().get(row).id());
                return true;
            }
        }
        if (tab == Tab.SKILLS) {
            List<MenuData.SkillLine> skills = menu.skills();
            int contentX = left() + 12;

            // Выбор слота открыт: сначала проверяем кнопки слотов, иначе щелчок
            // по ним улетел бы в значок под ними.
            if (choosingSlot > 0) {
                for (int slot = 1; slot <= menu.slots(); slot++) {
                    int slotX = contentX + (slot - 1) * 34;
                    int slotY = contentY + 16 + rows(skills.size()) * ICON_STEP;
                    if (RpgStyle.hit(mouseX, mouseY, slotX, slotY, 30, 18)) {
                        ActionPayload.send(Protocol.Action.BIND, slot, pendingSkill);
                        choosingSlot = 0;
                        pendingSkill = "";
                        return true;
                    }
                }
                choosingSlot = 0;
                pendingSkill = "";
                return true;
            }

            int index = iconAt(contentX, contentY, mouseX, mouseY, skills.size());
            if (index >= 0) {
                MenuData.SkillLine skill = skills.get(index);
                if (button == 1) {
                    // Правая кнопка — в слот, и только у изученного: ставить в
                    // слот неизученное сервер всё равно откажется.
                    if (skill.level() > 0) {
                        choosingSlot = -1;
                        pendingSkill = skill.id();
                    }
                    return true;
                }
                // Изучение и вложение — одно нажатие: сервер сам знает, что
                // сейчас уместно, и откажет, если ни то ни другое.
                ActionPayload.send(skill.level() == 0
                        ? Protocol.Action.UNLOCK : Protocol.Action.UPGRADE, 0, skill.id());
                return true;
            }
        }
        if (tab == Tab.SLOTS) {
            int row = rowAt(mouseY, contentY, ROW_HEIGHT, 16);
            if (choosingSlot > 0) {
                List<MenuData.SkillLine> learned = learned(menu);
                if (button == 1) {
                    ActionPayload.send(Protocol.Action.UNBIND, choosingSlot, "");
                    choosingSlot = 0;
                    return true;
                }
                if (row >= 0 && row < learned.size()) {
                    ActionPayload.send(Protocol.Action.BIND, choosingSlot,
                            learned.get(row).id());
                    choosingSlot = 0;
                    return true;
                }
            } else if (row >= 0 && row < menu.slots()) {
                int slot = row + 1;
                if (button == 1) {
                    ActionPayload.send(Protocol.Action.UNBIND, slot, "");
                } else {
                    choosingSlot = slot;
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    private List<MenuData.SkillLine> learned(MenuData menu) {
        List<MenuData.SkillLine> out = new ArrayList<>();
        for (MenuData.SkillLine skill : menu.skills()) {
            if (skill.level() > 0) {
                out.add(skill);
            }
        }
        return out;
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

    /** Убирает цветовые коды вида {@code &a}: в моде цвет задаётся иначе. */
    private static String strip(String text) {
        return text.replaceAll("&[0-9a-fk-or]", "");
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value)
                : String.valueOf(Math.round(value * 10) / 10.0);
    }
}
