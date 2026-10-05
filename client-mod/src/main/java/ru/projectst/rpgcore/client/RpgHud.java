package ru.projectst.rpgcore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import ru.projectst.rpgcore.net.ClientState;

/**
 * Полосы, счётчики, слоты и статусы.
 *
 * <p>Ванильные сердца и броня убираются: две шкалы здоровья на одном экране —
 * это две правды, и игрок всё равно смотрит на одну. Остальное ванильное
 * (голод, опыт, хотбар) остаётся: его мод не заменяет и трогать не должен.
 *
 * <p>Ничего не пересчитывается. Доли приходят с сервера, здоровье берётся у
 * самого игрока. Любая арифметика здесь означала бы, что экран может разойтись с
 * боем.
 *
 * <p>Где что стоит, решает игрок: {@link HudLayout}. Любое место, выбранное за
 * него, рано или поздно окажется под чужим интерфейсом.
 */
@EventBusSubscriber(modid = RpgCoreClient.MOD_ID, value = Dist.CLIENT)
public final class RpgHud {

    private static final int BAR_WIDTH = 110;
    /** Сторона ромба со значком навыка на экране. */
    private static final int ICON = 22;
    /** Шаг ряда слотов: ромб плюс кольцо вокруг него. */
    private static final int STEP = ICON + 7;
    private static final int BAR_HEIGHT = 7;

    private RpgHud() {
    }

    /**
     * Прячет ванильные сердца.
     *
     * <p>Броню тоже: она рисуется вплотную к сердцам и без них висит в пустоте.
     * Голод остаётся — его мод не заменяет, и убирать чужую шкалу, ничего не
     * давая взамен, нечестно.
     */
    @SubscribeEvent
    public static void onLayer(RenderGuiLayerEvent.Pre event) {
        if (ClientNetwork.state().isEmpty()) {
            return;
        }
        if (event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)
                || event.getName().equals(VanillaGuiLayers.ARMOR_LEVEL)) {
            event.setCanceled(true);
        }
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

        drawHealth(graphics, client, width, height);
        drawResource(graphics, client, state, width, height);
        drawCounters(graphics, client, state, width, height);
        drawSlots(graphics, client, state, width, height);
        drawStatuses(graphics, client, state, width, height);
    }

    // ------------------------------------------------------------------ полосы

    /** Здоровье: число берётся у игрока, сервер здесь ни при чём. */
    private static void drawHealth(GuiGraphics graphics, Minecraft client,
                                   int width, int height) {
        var player = client.player;
        if (player == null) {
            return;
        }
        double max = Math.max(1, player.getMaxHealth());
        double now = Math.clamp(player.getHealth(), 0, max);
        double share = now / max;

        int x = HudLayout.screenX(HudLayout.Element.HEALTH, width) - BAR_WIDTH / 2;
        int y = HudLayout.screenY(HudLayout.Element.HEALTH, height);

        RpgStyle.bar(graphics, x, y, BAR_WIDTH, BAR_HEIGHT, share,
                share < 0.3 ? RpgStyle.HEALTH_LOW : RpgStyle.HEALTH);
        String text = Math.round(now) + " / " + Math.round(max);
        graphics.drawString(client.font, Component.literal(text),
                x + BAR_WIDTH / 2 - client.font.width(text) / 2, y - 1, RpgStyle.TEXT, true);
    }

    private static void drawResource(GuiGraphics graphics, Minecraft client,
                                     ClientState state, int width, int height) {
        if (state.resourceMax() <= 0) {
            return;
        }
        int x = HudLayout.screenX(HudLayout.Element.RESOURCE, width) - BAR_WIDTH / 2;
        int y = HudLayout.screenY(HudLayout.Element.RESOURCE, height);

        RpgStyle.bar(graphics, x, y, BAR_WIDTH, BAR_HEIGHT,
                state.resource() / state.resourceMax(), RpgStyle.RESOURCE);
        String text = Math.round(Math.floor(state.resource())) + " / "
                + Math.round(state.resourceMax());
        graphics.drawString(client.font, Component.literal(text),
                x + BAR_WIDTH / 2 - client.font.width(text) / 2, y - 1, RpgStyle.TEXT, true);
    }

    // ------------------------------------------------------------------ ядро

    /**
     * Счётчики ядра класса: Рост и Увядание у друида, души у колдуна.
     *
     * <p>Ромбами, а не числом: «два из трёх» читается взглядом, «2/3» нужно
     * прочесть. Когда делений больше десяти, вместо них полоса — двадцать
     * ромбов уже не считаются взглядом тоже.
     */
    private static void drawCounters(GuiGraphics graphics, Minecraft client,
                                     ClientState state, int width, int height) {
        if (state.counters().isEmpty()) {
            return;
        }
        int x = HudLayout.screenX(HudLayout.Element.COUNTERS, width);
        int y = HudLayout.screenY(HudLayout.Element.COUNTERS, height);

        for (ClientState.CounterLine counter : state.counters()) {
            int colour = colourOf(counter.color(), "BUFF");
            graphics.drawString(client.font, Component.literal(counter.display()),
                    x, y, colour, true);

            int pips = Math.max(1, counter.maxStacks());
            if (pips <= 10) {
                int pipX = x;
                for (int i = 0; i < pips; i++) {
                    RpgStyle.pip(graphics, pipX, y + 10, 8, i < counter.stacks(), colour);
                    pipX += 10;
                }
            } else {
                RpgStyle.bar(graphics, x, y + 11, 72, 5,
                        (double) counter.stacks() / pips, colour);
                graphics.drawString(client.font,
                        Component.literal(counter.stacks() + "/" + pips),
                        x + 78, y + 10, colour, true);
            }
            y += 22;
        }
    }

    // ------------------------------------------------------------------ слоты

    private static void drawSlots(GuiGraphics graphics, Minecraft client,
                                  ClientState state, int width, int height) {
        if (state.slots().isEmpty()) {
            return;
        }
        int x = HudLayout.screenX(HudLayout.Element.SLOTS, width);
        int y = HudLayout.screenY(HudLayout.Element.SLOTS, height);
        // Ряд слотов стал выше: считаем от его низа, чтобы он не уезжал под
        // хотбар при шести слотах.
        y -= Math.max(0, state.slots().size() - 4) * STEP;

        if (!RpgKeys.anySlotBound()) {
            graphics.drawString(client.font, Component.literal(
                            "Клавиши навыков не назначены — настройки управления, RpgCore"),
                    x, y - 12, RpgStyle.TEXT_WARN, true);
        }

        for (ClientState.SlotLine slot : state.slots()) {
            String key = RpgKeys.slotKeyLabel(slot.slot());
            if (slot.skillId().isEmpty()) {
                // Пустой слот — пустой ромб: ряд не рвётся, и видно, сколько
                // слотов вообще есть.
                SkillIcons.draw(graphics, "", x + 2, y + 2, ICON, false);
                graphics.drawString(client.font, Component.literal(key),
                        x + ICON + 10, y + ICON / 2 - 2, RpgStyle.TEXT_DIM, true);
                y += STEP;
                continue;
            }
            int remaining = remainingOf(state, slot.skillId());
            int total = totalOf(state, slot.skillId());
            boolean ready = remaining <= 0;

            // Кольцо вокруг ромба вместо полоски сбоку: взгляд в бою уже на
            // значке, и возвращать его к отдельной шкале — лишнее движение.
            // Готовый навык горит зелёным целиком, и это видно боковым зрением.
            SkillIcons.ring(graphics, x + 2, y + 2, ICON,
                    ready ? 1 : 1.0 - (double) remaining / Math.max(1, total), ready);
            SkillIcons.draw(graphics, slot.skillId(), x + 2, y + 2, ICON, ready);

            graphics.drawString(client.font, Component.literal(key),
                    x + ICON + 10, y + ICON / 2 - 2,
                    ready ? RpgStyle.TEXT : RpgStyle.TEXT_DIM, true);
            if (!ready) {
                graphics.drawString(client.font, Component.literal(seconds(remaining)),
                        x + ICON + 10, y + ICON / 2 + 8, RpgStyle.TEXT_WARN, true);
            }
            y += STEP;
        }
    }

    private static void drawStatuses(GuiGraphics graphics, Minecraft client,
                                     ClientState state, int width, int height) {
        if (state.statuses().isEmpty()) {
            return;
        }
        int x = HudLayout.screenX(HudLayout.Element.STATUSES, width);
        int y = HudLayout.screenY(HudLayout.Element.STATUSES, height);

        for (ClientState.StatusLine status : state.statuses()) {
            String text = status.display()
                    + (status.stacks() > 1 ? " ×" + status.stacks() : "")
                    + "  " + seconds(status.remaining());
            graphics.drawString(client.font, Component.literal(text), x, y,
                    colourOf(status.color(), status.category()), true);
            y += 11;
        }
    }

    // ------------------------------------------------------------------ мелочи

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
     * иначе новый статус нельзя покрасить, не пересобрав мод и не раздав его
     * заново всем игрокам.
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
            case "BLACK" -> 0xFF101010;
            case "DARK_BLUE" -> 0xFF1E3A8A;
            case "DARK_GREEN" -> 0xFF2F6B2F;
            case "DARK_AQUA" -> 0xFF2E7F7F;
            case "DARK_RED" -> 0xFF8B1A1A;
            case "DARK_PURPLE" -> 0xFF6B2E8B;
            case "GOLD" -> 0xFFC9A227;
            case "GRAY" -> 0xFFA39B88;
            case "DARK_GRAY" -> 0xFF5A5346;
            case "BLUE" -> 0xFF4A7FC1;
            case "GREEN" -> 0xFF6FA84F;
            case "AQUA" -> 0xFF6FC1C1;
            case "RED" -> 0xFFC14A3D;
            case "LIGHT_PURPLE" -> 0xFFB06FC1;
            case "YELLOW" -> 0xFFE0C65A;
            case "WHITE" -> 0xFFE8DCC0;
            default -> null;
        };
    }

    private static int byCategory(String category) {
        return switch (category) {
            case "CONTROL" -> 0xFFC14A3D;
            case "DEBUFF" -> 0xFFC98A3D;
            case "SHIELD" -> 0xFF4A7FC1;
            case "IMMUNITY" -> 0xFFE0C65A;
            case "BUFF" -> 0xFF6FA84F;
            default -> RpgStyle.TEXT_DIM;
        };
    }
}
