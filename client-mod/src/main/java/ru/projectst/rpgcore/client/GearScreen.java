package ru.projectst.rpgcore.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ru.projectst.rpgcore.net.MenuData;

/**
 * Закладка «Снаряжение»: кукла с ячейками брони и украшений, сумка рядом.
 *
 * <p>Окно — контейнер сервера, нарисованный в виде книги героя: та же планка с
 * закладками, то же тёмное поле, тот же размер. Вещи таскаются курсором, как в
 * любом ванильном окне, а каждый щелчок решает сервер: он знает, что кольцо не
 * шлем, и отказ присылает словами — они появляются здесь же, под подсказкой,
 * а не в строке над хотбаром, которую окно закрывает.
 *
 * <p>Ячейка, в которой вещь лежит, но не действует, обведена красным, а
 * причина — в подсказке. Это те же отказы, по которым сервер считает статы:
 * свой расчёт здесь однажды разошёлся бы с боем.
 */
public final class GearScreen extends AbstractContainerScreen<GearMenu> {

    /** Сколько держится отказ сервера под подсказкой. */
    private static final long NOTICE_MILLIS = 5000;

    /** Куда вернуться по Esc: туда же, куда вернулась бы книга. */
    private final Screen parent;

    private Component notice;
    private long noticeUntil;

    public GearScreen(GearMenu menu, Inventory inventory, Component title, Screen parent) {
        super(menu, inventory, title);
        this.parent = parent;
        this.imageWidth = CharacterScreen.PANEL_WIDTH;
        this.imageHeight = CharacterScreen.PANEL_HEIGHT;
    }

    Screen parent() {
        return parent;
    }

    /** Отказ сервера: показать в окне, пока его можно прочесть. */
    void notice(Component message) {
        this.notice = message;
        this.noticeUntil = Util.getMillis() + NOTICE_MILLIS;
    }

    @Override
    public void onClose() {
        // Закрыть контейнер у сервера — это делает ванильное закрытие; книга
        // возвращает туда, откуда её открыли.
        super.onClose();
        if (parent != null) {
            minecraft.setScreen(parent);
        }
    }

    // ------------------------------------------------------------------ отрисовка

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        CharacterScreen.renderTabTooltip(graphics, leftPos, topPos, mouseX, mouseY);
    }

    /** Свой фон, как у книги: без ванильного размытия мира. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY,
                                 float partialTick) {
        graphics.fill(0, 0, width, height, 0xC00A0806);
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        MenuData data = ClientNetwork.menu().orElse(null);
        int x = leftPos;
        int y = topPos;

        RpgStyle.book(graphics, x, y, imageWidth, imageHeight, CharacterScreen.bannerTitle(data));
        CharacterScreen.renderTabs(graphics, x, y, CharacterScreen.Tab.GEAR, mouseX, mouseY);

        int fieldX = RpgStyle.fieldX(x);
        int fieldY = RpgStyle.fieldY(y);
        int leftWidth = GearMenu.BAG_X - 18 - (fieldX - x);

        RpgStyle.caption(graphics, fieldX, fieldY, leftWidth, "Снаряжение");
        RpgStyle.ruleVertical(graphics, x + GearMenu.BAG_X - 9, fieldY,
                y + GearMenu.HOTBAR_Y + 18 - fieldY);

        renderDoll(graphics, x + GearMenu.DOLL_X, y + GearMenu.DOLL_Y, mouseX, mouseY);
        RpgStyle.caption(graphics, fieldX, y + GearMenu.ARTIFACT_ROW - 18, leftWidth, "Артефакты");

        for (Slot slot : menu.slots) {
            if (slot instanceof GearMenu.CellSlot cell) {
                renderCell(graphics, cell, refusal(data, cell.cell().key()), mouseX, mouseY);
            }
        }

        renderHelp(graphics, x + GearMenu.BAG_X, fieldY, 162);
        RpgStyle.caption(graphics, x + GearMenu.BAG_X, y + GearMenu.BAG_Y - 20, 162, "Сумка");
        for (Slot slot : menu.slots) {
            if (slot.isActive() && !(slot instanceof GearMenu.CellSlot)) {
                RpgStyle.slot(graphics, x + slot.x - 1, y + slot.y - 1, 18, 18, false);
            }
        }
    }

    /**
     * Гнездо ячейки снаряжения.
     *
     * <p>Пустое показывает силуэт того, что в него кладут; под курсором — золотая
     * обводка, как у кнопок книги; с недействующей вещью — красная.
     */
    private void renderCell(GuiGraphics graphics, GearMenu.CellSlot slot, String refusal,
                            int mouseX, int mouseY) {
        int socketX = leftPos + slot.cell().x();
        int socketY = topPos + slot.cell().y();
        int size = GearMenu.SOCKET;
        boolean hovered = RpgStyle.hit(mouseX, mouseY, socketX, socketY, size, size);

        RpgStyle.socket(graphics, socketX, socketY, size, slot.hasItem() && !refusal.isEmpty());
        if (hovered && refusal.isEmpty()) {
            graphics.renderOutline(socketX, socketY, size, size, RpgStyle.EDGE_BRIGHT);
        }
        if (!slot.hasItem()) {
            int inset = (size - GearIcons.SIZE) / 2;
            GearIcons.draw(graphics, slot.cell().key(), socketX + inset, socketY + inset);
        }
    }

    /**
     * Кукла: сам игрок в нише между бронёй и краем колонки.
     *
     * <p>Надетое видно сразу, на нём самом, — ради этого броня и осталась
     * ванильной. Кукла поворачивается за курсором, как в обычном инвентаре.
     */
    private void renderDoll(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int width = GearMenu.DOLL_WIDTH;
        int height = GearMenu.DOLL_HEIGHT;
        graphics.fillGradient(x, y, x + width, y + height, 0x00000000, 0x26D8C68A);
        // Пол под ногами: без него кукла висит в воздухе.
        graphics.fill(x + 8, y + height - 3, x + width - 8, y + height - 2, 0x66D8C68A);
        if (minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x, y + 2,
                    x + width, y + height - 2, 36, 0.0625F, mouseX, mouseY, minecraft.player);
        }
    }

    /** Подсказка над сумкой и последний отказ сервера под ней. */
    private void renderHelp(GuiGraphics graphics, int x, int y, int width) {
        int line = y;
        for (String row : List.of("Перетащите вещь в ячейку.",
                "Shift+щелчок — надеть или снять.",
                "Красная рамка — вещь не действует.")) {
            for (FormattedCharSequence part : font.split(Component.literal(row), width)) {
                graphics.drawString(font, part, x, line, RpgStyle.INK_DIM, false);
                line += 10;
            }
        }
        if (notice != null && Util.getMillis() < noticeUntil) {
            line += 6;
            for (FormattedCharSequence part : font.split(notice, width)) {
                graphics.drawString(font, part, x, line, RpgStyle.INK_BAD, false);
                line += 10;
            }
        }
    }

    /** Подписей сундука нет: заголовок на планке, подписи частей — свои. */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    // ------------------------------------------------------------------ подсказки

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.getCarried().isEmpty() && hoveredSlot instanceof GearMenu.CellSlot cell
                && !cell.hasItem()) {
            List<Component> about = new ArrayList<>();
            about.add(Component.literal(cell.cell().display())
                    .withStyle(style -> style.withColor(RpgStyle.INK_TITLE)));
            about.add(Component.literal("Пусто. Сюда: " + cell.cell().accepts())
                    .withStyle(style -> style.withColor(RpgStyle.INK_DIM)));
            graphics.renderComponentTooltip(font, about, mouseX, mouseY);
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    /** Подсказка вещи в ячейке: своя, ванильная, и ниже — почему не действует. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (hoveredSlot instanceof GearMenu.CellSlot cell) {
            String refusal = refusal(ClientNetwork.menu().orElse(null), cell.cell().key());
            if (!refusal.isEmpty()) {
                lines.add(Component.literal("Не действует: " + refusal)
                        .withStyle(style -> style.withColor(RpgStyle.INK_BAD)));
            }
        }
        return lines;
    }

    private static String refusal(MenuData data, String cell) {
        if (data == null) {
            return "";
        }
        for (MenuData.GearLine line : data.gear()) {
            if (line.cell().equals(cell)) {
                return line.refusal();
            }
        }
        return "";
    }

    // ------------------------------------------------------------------ щелчки

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        CharacterScreen.Tab clicked = CharacterScreen.tabAt(leftPos, topPos, mouseX, mouseY);
        if (clicked != null && clicked != CharacterScreen.Tab.GEAR) {
            // Закрываем контейнер у сервера по-честному: вещь на курсоре сервер
            // вернёт в инвентарь сам, а не повиснет между окнами.
            Screen back = parent;
            minecraft.player.closeContainer();
            minecraft.setScreen(new CharacterScreen(back, clicked));
            return true;
        }
        if (clicked == CharacterScreen.Tab.GEAR) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
