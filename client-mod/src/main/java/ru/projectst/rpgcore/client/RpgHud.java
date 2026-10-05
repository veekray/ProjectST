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
        drawCounters(graphics, client, state, width, height);
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
            String text = status.display()
                    + (status.stacks() > 1 ? " x" + status.stacks() : "")
                    + "  " + seconds(status.remaining());
            graphics.drawString(client.font, Component.literal(text), x, y,
                    colourOf(status.color(), status.category()), true);
            y += 10;
        }
        RenderSystem.disableBlend();
    }

    /**
     * Счётчики ядра класса: стаки Роста и Увядания у друида, души у колдуна.
     *
     * <p>Рисуются делениями, а не числом: «два из трёх» в бою читается взглядом,
     * а «2/3» требует прочесть. Числа тоже есть — но мелкие и рядом, для тех
     * случаев, когда делений больше пяти.
     *
     * <p>Место выбрано слева от полосы ресурса и над хотбаром: это то, на что
     * игрок смотрит, принимая решение, и смотреть он должен в одну точку.
     */
    private static void drawCounters(GuiGraphics graphics, Minecraft client,
                                     ClientState state, int width, int height) {
        if (state.counters().isEmpty()) {
            return;
        }
        int x = width / 2 - 182;
        int y = height - 54 - (state.counters().size() - 1) * 14;

        for (ClientState.CounterLine counter : state.counters()) {
            int colour = colourOf(counter.color(), "BUFF");
            graphics.drawString(client.font, Component.literal(counter.display()),
                    x, y, colour, true);

            int pips = Math.max(1, counter.maxStacks());
            if (pips <= 10) {
                // Делениями: видно не читая.
                int pipWidth = Math.max(4, Math.min(12, 72 / pips));
                int pipX = x;
                for (int i = 0; i < pips; i++) {
                    boolean filled = i < counter.stacks();
                    graphics.fill(pipX, y + 10, pipX + pipWidth - 2, y + 14,
                            filled ? colour : 0x66101010);
                    pipX += pipWidth;
                }
            } else {
                // Делений было бы двадцать — вместо них полоса и число.
                double share = Math.clamp((double) counter.stacks() / pips, 0, 1);
                graphics.fill(x, y + 10, x + 72, y + 14, 0x66101010);
                graphics.fill(x, y + 10, x + (int) Math.round(72 * share), y + 14, colour);
                graphics.drawString(client.font,
                        Component.literal(counter.stacks() + "/" + pips),
                        x + 76, y + 7, colour, true);
            }
            y += 14;
        }
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

    /**
     * Цвет: сначала свой из файла статуса, иначе по категории.
     *
     * <p>Цвет в контенте, а не в коде, по той же причине, что иконки навыков:
     * иначе новый статус нельзя покрасить, не трогая мод, то есть не пересобрав
     * и не раздав его заново всем игрокам.
     */
    static int colourOf(String own, String category) {
        if (own != null && !own.isEmpty()) {
            Integer named = named(own);
            if (named != null) {
                return named;
            }
        }
        return byCategory(category);
    }

    /** Ванильные имена цветов: те же, что в файлах статусов и редкостей. */
    private static Integer named(String name) {
        return switch (name) {
            case "BLACK" -> 0xFF000000;
            case "DARK_BLUE" -> 0xFF0000AA;
            case "DARK_GREEN" -> 0xFF00AA00;
            case "DARK_AQUA" -> 0xFF00AAAA;
            case "DARK_RED" -> 0xFFAA0000;
            case "DARK_PURPLE" -> 0xFFAA00AA;
            case "GOLD" -> 0xFFFFAA00;
            case "GRAY" -> 0xFFAAAAAA;
            case "DARK_GRAY" -> 0xFF555555;
            case "BLUE" -> 0xFF5555FF;
            case "GREEN" -> 0xFF55FF55;
            case "AQUA" -> 0xFF55FFFF;
            case "RED" -> 0xFFFF5555;
            case "LIGHT_PURPLE" -> 0xFFFF55FF;
            case "YELLOW" -> 0xFFFFFF55;
            case "WHITE" -> 0xFFFFFFFF;
            default -> null;
        };
    }

    private static int byCategory(String category) {
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
