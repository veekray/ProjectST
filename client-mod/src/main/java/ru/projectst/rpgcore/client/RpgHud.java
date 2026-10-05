package ru.projectst.rpgcore.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import ru.projectst.rpgcore.net.ClientState;

/**
 * Полоса ресурса, значки статусов и перезарядки.
 *
 * <p>Рисуется только то, что пришло. Если состояния нет — мод не рисует ничего и
 * не занимает места: игрок без принятого рукопожатия видит обычный экран, и это
 * то же требование, что у всего этапа — отсутствие мода ничего не меняет.
 *
 * <p>Числа не пересчитываются. Полоса — это {@code resource / resourceMax} с
 * сервера, перезарядка — {@code remaining / total} с сервера. Любая арифметика
 * здесь означала бы, что экран может разойтись с боем.
 */
@EventBusSubscriber(modid = RpgCoreClient.MOD_ID, value = Dist.CLIENT)
public final class RpgHud {

    private static final int BAR_WIDTH = 92;
    private static final int BAR_HEIGHT = 5;
    private static final int COLOUR_BACK = 0xAA101010;
    private static final int COLOUR_RESOURCE = 0xFF2E7FE0;
    private static final int COLOUR_COOLDOWN = 0xFFB4421F;
    private static final int COLOUR_READY = 0xFF3FA34D;

    private RpgHud() {
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        ClientState state = ClientNetwork.state().orElse(null);
        if (state == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui || client.player == null) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();

        drawResource(graphics, state, width, height);
        drawSlots(graphics, client, state, width, height);
        drawStatuses(graphics, client, state);
    }

    /** Полоса ресурса — под ванильными полосами, чтобы не спорить с ними за место. */
    private static void drawResource(GuiGraphics graphics, ClientState state,
                                     int width, int height) {
        if (state.resourceMax() <= 0) {
            return;
        }
        int x = width / 2 + 10;
        int y = height - 48;

        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, COLOUR_BACK);
        double share = Math.clamp(state.resource() / state.resourceMax(), 0, 1);
        graphics.fill(x, y, x + (int) Math.round(BAR_WIDTH * share), y + BAR_HEIGHT,
                COLOUR_RESOURCE);

        String text = Math.round(Math.floor(state.resource())) + " / "
                + Math.round(state.resourceMax());
        graphics.drawString(Minecraft.getInstance().font, Component.literal(text),
                x, y - 10, 0xFFFFFFFF, true);
    }

    /**
     * Слоты с перезарядками.
     *
     * <p>Рядом с номером написано нажатие: Shift и цифра. Мод не перехватывает
     * клавиши, он лишь напоминает, какие они, — поэтому подсказка одинакова у
     * игрока с модом и без.
     */
    private static void drawSlots(GuiGraphics graphics, Minecraft client, ClientState state,
                                  int width, int height) {
        if (state.slots().isEmpty()) {
            return;
        }
        int x = 8;
        int y = height - 20 - state.slots().size() * 11;

        for (ClientState.SlotLine slot : state.slots()) {
            String label = "Shift+" + slot.slot() + "  ";
            if (slot.skillId().isEmpty()) {
                graphics.drawString(client.font, Component.literal(label + "—"),
                        x, y, 0xFF8A8A8A, true);
                y += 11;
                continue;
            }
            int remaining = remainingOf(state, slot.skillId());
            int total = totalOf(state, slot.skillId());
            int colour = remaining > 0 ? COLOUR_COOLDOWN : COLOUR_READY;

            graphics.fill(x, y + 9, x + 70, y + 10, COLOUR_BACK);
            if (remaining > 0 && total > 0) {
                int filled = (int) Math.round(70.0 * (1.0 - (double) remaining / total));
                graphics.fill(x, y + 9, x + filled, y + 10, colour);
            } else {
                graphics.fill(x, y + 9, x + 70, y + 10, colour);
            }

            String text = label + slot.display()
                    + (remaining > 0 ? "  " + seconds(remaining) : "");
            graphics.drawString(client.font, Component.literal(text), x, y,
                    remaining > 0 ? 0xFFCCCCCC : 0xFFFFFFFF, true);
            y += 11;
        }
    }

    /** Значки статусов: имя, стаки и остаток. Цвет — по категории. */
    private static void drawStatuses(GuiGraphics graphics, Minecraft client,
                                     ClientState state) {
        int x = 8;
        int y = 8;
        RenderSystem.enableBlend();
        for (ClientState.StatusLine status : state.statuses()) {
            String text = status.id()
                    + (status.stacks() > 1 ? " x" + status.stacks() : "")
                    + "  " + seconds(status.remaining());
            graphics.drawString(client.font, Component.literal(text), x, y,
                    colourOf(status.category()), true);
            y += 10;
        }
        RenderSystem.disableBlend();
    }

    private static int remainingOf(ClientState state, String skillId) {
        for (ClientState.CooldownLine line : state.cooldowns()) {
            if (line.skillId().equals(skillId)) {
                return line.remaining();
            }
        }
        return 0;
    }

    private static int totalOf(ClientState state, String skillId) {
        for (ClientState.CooldownLine line : state.cooldowns()) {
            if (line.skillId().equals(skillId)) {
                return line.total();
            }
        }
        return 0;
    }

    private static String seconds(int ticks) {
        double value = ticks / 20.0;
        return value >= 10 ? String.valueOf(Math.round(value))
                : String.valueOf(Math.round(value * 10) / 10.0);
    }

    /** Цвет по категории статуса: контроль красный, щит синий, усиление зелёное. */
    private static int colourOf(String category) {
        return switch (category) {
            case "CONTROL" -> 0xFFE05A4F;
            case "DEBUFF" -> 0xFFE0A24F;
            case "SHIELD" -> 0xFF4F9FE0;
            case "IMMUNITY" -> 0xFFE0D74F;
            case "BUFF" -> 0xFF6FD07A;
            default -> 0xFFBFBFBF;
        };
    }
}
