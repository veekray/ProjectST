package ru.projectst.rpgcore.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Расстановка интерфейса по экрану.
 *
 * <p>Здесь игрок двигает блоки туда, где ему удобно, — потому что экран
 * принадлежит ему. Чат внизу слева, карта вверху справа, чужие моды где угодно:
 * любое место, выбранное за игрока, рано или поздно окажется под чем-то чужим.
 *
 * <p>Двигаются рамки с подписями, а не живые полосы: рисовать настоящие во время
 * перетаскивания значило бы показывать боевые числа в меню и зависеть от того,
 * пришли ли они с сервера.
 */
public final class HudEditScreen extends Screen {

    private static final int BOX_WIDTH = 112;
    private static final int BOX_HEIGHT = 20;

    private final Screen parent;
    private HudLayout.Element dragging;
    private int grabX;
    private int grabY;

    public HudEditScreen(Screen parent) {
        super(Component.literal("Расстановка интерфейса"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Виджетов нет: кнопки рисуются и проверяются руками, в том же стиле,
        // что панель. Ванильная серая кнопка посреди бронзы выглядит как чужое
        // окно, вставленное в наше.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Свой фон без ванильного размытия: см. CharacterScreen. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY,
                                 float partialTick) {
        graphics.fill(0, 0, width, height, 0x900A0806);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, Component.literal(
                        "Перетащите блоки туда, где вам удобно"),
                width / 2, 12, RpgStyle.TEXT);
        graphics.drawCenteredString(font, Component.literal(
                        "Положение хранится долями экрана, поэтому не съедет в полном экране"),
                width / 2, 24, RpgStyle.TEXT_DIM);

        for (HudLayout.Element element : HudLayout.Element.values()) {
            int x = HudLayout.screenX(element, width) - BOX_WIDTH / 2;
            int y = HudLayout.screenY(element, height);
            boolean hovered = inside(mouseX, mouseY, x, y) || dragging == element;

            RpgStyle.panel(graphics, x, y, BOX_WIDTH, BOX_HEIGHT);
            if (hovered) {
                graphics.renderOutline(x, y, BOX_WIDTH, BOX_HEIGHT, RpgStyle.EDGE_BRIGHT);
            }
            graphics.drawCenteredString(font, Component.literal(element.title()),
                    x + BOX_WIDTH / 2, y + 6, hovered ? RpgStyle.TEXT_WARN : RpgStyle.TEXT);
        }
        RpgStyle.button(graphics, width / 2 - 104, height - 28, 100, 20, "Готово",
                RpgStyle.hit(mouseX, mouseY, width / 2 - 104, height - 28, 100, 20), false);
        RpgStyle.button(graphics, width / 2 + 4, height - 28, 100, 20, "Вернуть как было",
                RpgStyle.hit(mouseX, mouseY, width / 2 + 4, height - 28, 100, 20), false);
    }

    private boolean inside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX <= x + BOX_WIDTH && mouseY >= y && mouseY <= y + BOX_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (RpgStyle.hit(mouseX, mouseY, width / 2 - 104, height - 28, 100, 20)) {
            HudLayout.save();
            minecraft.setScreen(parent);
            return true;
        }
        if (RpgStyle.hit(mouseX, mouseY, width / 2 + 4, height - 28, 100, 20)) {
            HudLayout.reset();
            HudLayout.save();
            return true;
        }
        for (HudLayout.Element element : HudLayout.Element.values()) {
            int x = HudLayout.screenX(element, width) - BOX_WIDTH / 2;
            int y = HudLayout.screenY(element, height);
            if (inside(mouseX, mouseY, x, y)) {
                dragging = element;
                grabX = (int) mouseX - x;
                grabY = (int) mouseY - y;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (dragging == null) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        float fractionX = (float) (mouseX - grabX + BOX_WIDTH / 2.0) / width;
        float fractionY = (float) (mouseY - grabY) / height;
        HudLayout.move(dragging, fractionX, fractionY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null) {
            dragging = null;
            // Сохраняем на отпускание, а не на закрытие: закрыть окно можно и
            // клавишей, и тогда перетаскивание пропало бы молча.
            HudLayout.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        HudLayout.save();
        minecraft.setScreen(parent);
    }
}
