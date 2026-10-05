package ru.projectst.rpgcore.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
 * двух местах, и однажды клиент начал бы разрешать то, что сервер запрещает,
 * или наоборот — гасить кнопку, которая на самом деле работает.
 *
 * <p>Подсказка под каждым навыком всё же есть: что мешает, видно до нажатия. Но
 * это <b>показ</b> присланных сервером чисел, а не собственное решение экрана.
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

    private Tab tab = Tab.CHARACTER;
    private int scroll;
    /** Слот, для которого выбирают навык; ноль — никакой. */
    private int choosingSlot;

    public CharacterScreen() {
        super(Component.literal("RpgCore"));
    }

    /**
     * Во сколько раз крупнее рисуем окно.
     *
     * <p>Собственный масштаб, а не глобальный «Размер интерфейса» Minecraft: тот
     * растягивает всё сразу, включая хотбар и чат. Увеличить нужно окно, которое
     * читают, а не экран, в котором играют.
     *
     * <p>Только целое число: дробное растягивает пиксельный шрифт между
     * пикселями, и выходит ровно то мыло, от которого увеличение спасает.
     */
    private int scale() {
        return HudLayout.menuScale();
    }

    private int viewWidth() {
        return width / scale();
    }

    private int viewHeight() {
        return height / scale();
    }

    private int left() {
        return (viewWidth() - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (viewHeight() - PANEL_HEIGHT) / 2;
    }

    /** Мышь в координатах панели: вся разметка считается в них. */
    private double mx(double mouseX) {
        return mouseX / scale();
    }

    private double my(double mouseY) {
        return mouseY / scale();
    }

    @Override
    protected void init() {
        clearWidgets();
        int scale = scale();
        int x = (left() + 6) * scale;
        int y = (top() + 6) * scale;
        for (Tab value : Tab.values()) {
            Tab target = value;
            addRenderableWidget(Button.builder(Component.literal(value.title()), button -> {
                tab = target;
                scroll = 0;
                choosingSlot = 0;
                rebuild();
            }).bounds(x, y, 78 * scale, 18 * scale).build());
            x += 80 * scale;
        }

        // Масштаб окна: тут же, рядом с расстановкой — обе настройки про то,
        // как интерфейс выглядит, а не про персонажа.
        addRenderableWidget(Button.builder(
                        Component.literal("Размер: " + scale() + "×"), button -> {
                            HudLayout.menuScale(scale() % 3 + 1);
                            HudLayout.save();
                            rebuild();
                        })
                .bounds((left() + 6) * scale, (top() + PANEL_HEIGHT - 24) * scale,
                        70 * scale, 18 * scale)
                .build());

        // Расстановка интерфейса: отсюда, а не из настроек игры, потому что
        // ищут её вместе с остальным про персонажа.
        addRenderableWidget(Button.builder(Component.literal("Расставить интерфейс"),
                        button -> minecraft.setScreen(new HudEditScreen(this)))
                .bounds((left() + PANEL_WIDTH - 130) * scale,
                        (top() + PANEL_HEIGHT - 24) * scale, 124 * scale, 18 * scale)
                .build());
    }

    private void rebuild() {
        init();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Свой фон вместо ванильного.
     *
     * <p>Minecraft с 1.20.5 размывает мир под любым открытым экраном — тем самым
     * шейдером, из-за которого окно выглядит мыльным. Нам это размытие не нужно:
     * панель и так непрозрачная, а мыло с неё переходит на восприятие текста
     * поверх. Простое затемнение читается чище и стоит дешевле.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY,
                                 float partialTick) {
        graphics.fill(0, 0, width, height, 0xC00A0806);
    }

    /** Строка под курсором: нужна только для подсветки. */
    private int hoveredRow = -1;

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scroll = Math.max(0, scroll - (int) Math.signum(deltaY));
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int scale = scale();
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1);
        try {
            renderPanel(graphics, (int) mx(mouseX), (int) my(mouseY), partialTick);
        } finally {
            // Матрицу возвращаем всегда: забытый pop перекосил бы весь
            // последующий интерфейс, включая чужой.
            graphics.pose().popPose();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPanel(GuiGraphics graphics, int mouseX, int mouseY,
                             float partialTick) {
        int x = left();
        int y = top();
        RpgStyle.panel(graphics, x, y, PANEL_WIDTH, PANEL_HEIGHT);

        hoveredRow = rowAt(mouseY);

        MenuData menu = ClientNetwork.menu().orElse(null);
        if (menu == null) {
            graphics.drawCenteredString(font, Component.literal(
                            "Сервер не прислал данные: нажмите заново"),
                    x + PANEL_WIDTH / 2, y + PANEL_HEIGHT / 2, RpgStyle.TEXT_DIM);
            return;
        }

        // Полоса вкладки: видно, где находишься, без чтения заголовков.
        int tabX = left() + 6 + tab.ordinal() * 80;
        graphics.fill(left() + 6, y + 25, left() + PANEL_WIDTH - 6, y + 26, RpgStyle.EDGE);
        graphics.fill(tabX, y + 24, tabX + 78, y + 27, RpgStyle.EDGE_BRIGHT);

        switch (tab) {
            case CHARACTER -> renderCharacter(graphics, menu, x + 12, y + 36);
            case SKILLS -> renderSkills(graphics, menu, x + 12, y + 36, mouseX, mouseY);
            case SLOTS -> renderSlots(graphics, menu, x + 12, y + 36);
            case STATS -> renderStats(graphics, menu, x + 12, y + 36);
        }
    }

    // ------------------------------------------------------------------ вкладки

    private void renderCharacter(GuiGraphics graphics, MenuData menu, int x, int y) {
        if (menu.classId().isEmpty()) {
            graphics.drawString(font, Component.literal("Выберите класс"), x, y,
                    RpgStyle.TEXT_WARN, true);
            graphics.drawString(font, Component.literal(
                            "Это решение можно отменить только администратору"),
                    x, y + 12, RpgStyle.TEXT_DIM, true);

            int line = y + 30;
            int row = 0;
            for (MenuData.ClassLine klass : menu.classes()) {
                boolean hovered = hoveredRow == row;
                if (hovered) {
                    graphics.fill(x - 4, line - 2, x + PANEL_WIDTH - 28, line + 22,
                            0x33C9A227);
                }
                graphics.drawString(font, Component.literal(strip(klass.display())),
                        x, line, RpgStyle.TEXT_WARN, true);
                graphics.drawString(font, Component.literal("платит: "
                                + klass.resourceName() + ",  слотов: " + klass.slots()
                                + ",  предел уровня: " + klass.maxLevel()),
                        x, line + 11, RpgStyle.TEXT_DIM, true);
                line += 26;
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

        // Полоса опыта: доля посчитана сервером, клиент только делит на длину.
        int barWidth = PANEL_WIDTH - 36;
        int barY = y + 46;
        if (menu.xpToNext() > 0) {
            double total = menu.xp() + menu.xpToNext();
            double share = total <= 0 ? 0 : Math.clamp(menu.xp() / total, 0, 1);
            RpgStyle.bar(graphics, x, barY, barWidth, 6, share, RpgStyle.READY);
            graphics.drawString(font, Component.literal(Math.round(menu.xp()) + " / "
                            + Math.round(menu.xp() + menu.xpToNext())),
                    x, barY + 10, RpgStyle.TEXT_DIM, true);
        } else {
            RpgStyle.bar(graphics, x, barY, barWidth, 6, 1, RpgStyle.EDGE_BRIGHT);
            graphics.drawString(font, Component.literal("Предел уровня"), x, barY + 10,
                    RpgStyle.TEXT_WARN, true);
        }

        ClientNetwork.state().ifPresent(state -> {
            graphics.drawString(font, Component.literal(state.resourceName() + ": "
                            + Math.round(Math.floor(state.resource())) + " / "
                            + Math.round(state.resourceMax())),
                    x, barY + 28, RpgStyle.RESOURCE, true);
            if (!state.counters().isEmpty()) {
                int line = barY + 44;
                graphics.drawString(font, Component.literal("Ядро класса:"), x, line,
                        RpgStyle.TEXT_DIM, true);
                line += 12;
                for (var counter : state.counters()) {
                    graphics.drawString(font, Component.literal(counter.display() + ": "
                                    + counter.stacks() + " / " + counter.maxStacks()),
                            x, line, RpgHud.colourOf(counter.color(), "BUFF"), true);
                    line += 11;
                }
            }
        });
    }

    private void renderSkills(GuiGraphics graphics, MenuData menu, int x, int y,
                              int mouseX, int mouseY) {
        if (menu.classId().isEmpty()) {
            graphics.drawString(font, Component.literal("Сначала выберите класс"), x, y,
                    RpgStyle.HEALTH_LOW, true);
            return;
        }
        graphics.drawString(font, Component.literal("Свободных очков: " + menu.points()
                        + "   ЛКМ — изучить или вложить очко"),
                x, y, RpgStyle.TEXT_DIM, true);

        int line = y + 16;
        for (MenuData.SkillLine skill : visible(menu.skills())) {
            boolean learned = skill.level() > 0;
            int colour = learned ? RpgStyle.RESOURCE : RpgStyle.TEXT_DIM;
            String left = (learned ? skill.level() + "/" + skill.maxLevel() : "—")
                    + "  " + skill.display();
            String right = reason(menu, skill);

            graphics.drawString(font, Component.literal(left), x, line, colour, true);
            graphics.drawString(font, Component.literal(right),
                    x + PANEL_WIDTH - 36 - font.width(right), line,
                    right.startsWith("Нажмите") ? RpgStyle.READY : RpgStyle.COOLDOWN, true);
            line += 12;
        }
    }

    private void renderSlots(GuiGraphics graphics, MenuData menu, int x, int y) {
        if (menu.classId().isEmpty()) {
            graphics.drawString(font, Component.literal("Сначала выберите класс"), x, y,
                    RpgStyle.HEALTH_LOW, true);
            return;
        }
        if (choosingSlot > 0) {
            graphics.drawString(font, Component.literal("Слот " + choosingSlot
                            + ": выберите навык, ПКМ — освободить"),
                    x, y, RpgStyle.TEXT_WARN, true);
            int line = y + 16;
            for (MenuData.SkillLine skill : menu.skills()) {
                if (skill.level() == 0) {
                    continue;
                }
                graphics.drawString(font, Component.literal(skill.display()), x, line,
                        RpgStyle.RESOURCE, true);
                line += 12;
            }
            return;
        }

        graphics.drawString(font, Component.literal(
                        "ЛКМ — занять слот, ПКМ — освободить"),
                x, y, RpgStyle.TEXT_DIM, true);
        int line = y + 16;
        for (int slot = 1; slot <= menu.slots(); slot++) {
            String bound = "пусто";
            for (MenuData.SkillLine skill : menu.skills()) {
                if (skill.boundSlot() == slot) {
                    bound = skill.display();
                }
            }
            // Клавиша — та, что игрок назначил сам. Поэтому «Слот 3» в меню и
            // надпись на экране в бою всегда говорят одно и то же.
            String key = RpgKeys.slotKeyLabel(slot);
            graphics.drawString(font, Component.literal("Слот " + slot + ": " + bound),
                    x, line, bound.equals("пусто") ? RpgStyle.TEXT_DIM : RpgStyle.TEXT, true);
            graphics.drawString(font, Component.literal(key),
                    x + PANEL_WIDTH - 36 - font.width(key), line,
                    key.equals("не назначено") ? RpgStyle.TEXT_WARN : RpgStyle.RESOURCE, true);
            line += 12;
        }
        graphics.drawString(font, Component.literal(
                        "Клавиши меняются в настройках управления, раздел RpgCore"),
                x, line + 8, RpgStyle.TEXT_DIM, true);
    }

    private void renderStats(GuiGraphics graphics, MenuData menu, int x, int y) {
        int line = y;
        int column = 0;
        for (MenuData.StatLine stat : menu.stats()) {
            int columnX = x + column * 150;
            graphics.drawString(font, Component.literal(stat.display() + ": "
                            + trim(stat.value())), columnX, line, RpgStyle.TEXT, true);
            line += 11;
            if (line > top() + PANEL_HEIGHT - 20) {
                line = y;
                column++;
            }
        }
    }

    // ------------------------------------------------------------------ щелчки

    @Override
    public boolean mouseClicked(double rawX, double rawY, int button) {
        MenuData menu = ClientNetwork.menu().orElse(null);
        if (menu == null) {
            return super.mouseClicked(rawX, rawY, button);
        }
        // Кнопки живут в экранных координатах, строки — в координатах панели.
        // Поэтому ниже два разных набора, и путать их нельзя.
        double mouseX = mx(rawX);
        double mouseY = my(rawY);
        int y = top() + 36;
        int row = (int) ((mouseY - y - 16) / 12);

        if (tab == Tab.CHARACTER && menu.classId().isEmpty()) {
            // Строки выбора класса выше и в два раза толще остальных.
            int classRow = (int) ((mouseY - y - 30) / 26);
            List<MenuData.ClassLine> classes = menu.classes();
            if (classRow >= 0 && classRow < classes.size()) {
                ActionPayload.send(Protocol.Action.CHOOSE_CLASS, 0, classes.get(classRow).id());
                return true;
            }
        }
        if (tab == Tab.SKILLS && row >= 0) {
            List<MenuData.SkillLine> skills = visible(menu.skills());
            if (row < skills.size()) {
                MenuData.SkillLine skill = skills.get(row);
                // Изучение и вложение — одно нажатие: сервер сам знает, что
                // именно сейчас уместно, и откажет, если ни то ни другое.
                ActionPayload.send(skill.level() == 0
                        ? Protocol.Action.UNLOCK : Protocol.Action.UPGRADE, 0, skill.id());
                return true;
            }
        }
        if (tab == Tab.SLOTS && row >= 0) {
            if (choosingSlot > 0) {
                List<MenuData.SkillLine> learned = menu.skills().stream()
                        .filter(skill -> skill.level() > 0).toList();
                if (button == 1) {
                    ActionPayload.send(Protocol.Action.UNBIND, choosingSlot, "");
                    choosingSlot = 0;
                    return true;
                }
                if (row < learned.size()) {
                    ActionPayload.send(Protocol.Action.BIND, choosingSlot,
                            learned.get(row).id());
                    choosingSlot = 0;
                    return true;
                }
            } else if (row < menu.slots()) {
                int slot = row + 1;
                if (button == 1) {
                    ActionPayload.send(Protocol.Action.UNBIND, slot, "");
                } else {
                    choosingSlot = slot;
                }
                return true;
            }
        }
        return super.mouseClicked(rawX, rawY, button);
    }

    // ------------------------------------------------------------------ мелочи

    /** Какая строка списка под этой точкой: одна формула на показ и на щелчок. */
    private int rowAt(double mouseY) {
        int y = top() + 36;
        if (tab == Tab.CHARACTER) {
            return (int) ((mouseY - y - 30) / 26);
        }
        return (int) ((mouseY - y - 16) / 12);
    }

    private List<MenuData.SkillLine> visible(List<MenuData.SkillLine> skills) {
        int from = Math.min(scroll, Math.max(0, skills.size() - 1));
        return skills.subList(from, skills.size());
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
            return menu.points() > 0 ? "Нажмите — изучить" : "нет очков";
        }
        if (skill.level() >= skill.maxLevel()) {
            return "максимум";
        }
        return menu.points() > 0 ? "Нажмите — +уровень" : "нет очков";
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
