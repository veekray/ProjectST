package ru.projectst.rpgcore.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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

    /** Сторона ромба значка статуса: крупнее квадрата, потому что ромб срезает углы. */
    private static final int AURA = 24;
    /** Воздух между значками в ряду. */
    private static final int AURA_GAP = 4;
    /** Сколько значков в строке ряда, дальше — перенос. */
    private static final int AURA_PER_LINE = 10;
    /** Высота строки подписи под значком: мелкий шрифт и пиксель воздуха. */
    private static final int AURA_TEXT = 8;
    /** Масштаб подписей под значком: полный шрифт растащил бы ряд вдвое. */
    private static final float SMALL = 0.75f;
    /** С какого остатка значок начинает мигать: три секунды. */
    private static final int ENDING_TICKS = 60;

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
        drawStamina(graphics, client, state, width, height);
        drawDash(graphics, client, state, width, height);
        drawCounters(graphics, client, state, width, height);
        drawSlots(graphics, client, state, width, height);
        drawAuras(graphics, client, state, width, height);
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

    /**
     * Выносливость: общий запас, из которого платится рывок.
     *
     * <p>Отдельной полосой, а не второй частью полосы ресурса: это другой запас,
     * и слитая полоса означала бы, что у них общий предел.
     */
    private static void drawStamina(GuiGraphics graphics, Minecraft client,
                                    ClientState state, int width, int height) {
        if (state.staminaMax() <= 0) {
            return;
        }
        int x = HudLayout.screenX(HudLayout.Element.STAMINA, width) - BAR_WIDTH / 2;
        int y = HudLayout.screenY(HudLayout.Element.STAMINA, height);

        RpgStyle.bar(graphics, x, y, BAR_WIDTH, BAR_HEIGHT - 2,
                state.stamina() / state.staminaMax(), RpgStyle.STAMINA);
        String text = Math.round(Math.floor(state.stamina())) + " / "
                + Math.round(state.staminaMax());
        graphics.drawString(client.font, Component.literal(text),
                x + BAR_WIDTH / 2 - client.font.width(text) / 2, y - 2, RpgStyle.TEXT_DIM,
                true);
    }

    /**
     * Заряды рывка.
     *
     * <p>Ромбами, как счётчики ядра: «два из трёх» читается взглядом. Тот, что
     * сейчас возвращается, залит наполовину своего пути — долю считает экран, но
     * из чисел, присланных сервером, а не из своего таймера: свой таймер
     * разошёлся бы с боем на первом же лаге.
     */
    private static void drawDash(GuiGraphics graphics, Minecraft client,
                                 ClientState state, int width, int height) {
        ClientState.DashLine dash = state.dash();
        if (dash == null || dash.maxCharges() <= 0) {
            return;
        }
        int x = HudLayout.screenX(HudLayout.Element.DASH, width);
        int y = HudLayout.screenY(HudLayout.Element.DASH, height);

        // Имя и клавиша — двумя строками разного цвета, а не одной со служебными
        // символами: подпись клавиши должна читаться как подсказка, а не как имя.
        String title = dash.display();
        graphics.drawString(client.font, Component.literal(title), x, y - 10,
                dash.charges() > 0 ? RpgStyle.TEXT : RpgStyle.TEXT_DIM, true);
        graphics.drawString(client.font, Component.literal(RpgKeys.dashKeyLabel()),
                x + client.font.width(title) + 4, y - 10, RpgStyle.TEXT_DIM, true);

        int pipX = x;
        for (int i = 0; i < dash.maxCharges(); i++) {
            boolean full = i < dash.charges();
            RpgStyle.pip(graphics, pipX, y, 9, full, RpgStyle.STAMINA);
            pipX += 11;
        }
        // Время до ближайшего заряда — только когда он действительно в пути.
        if (dash.remaining() > 0 && dash.charges() < dash.maxCharges()) {
            graphics.drawString(client.font, Component.literal(seconds(dash.remaining())),
                    pipX + 3, y, RpgStyle.TEXT_DIM, true);
        }
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

    /**
     * Бафы и дебафы: два ряда значков.
     *
     * <p>Значками, а не списком строк: в бою на ряд смотрят боковым зрением, и
     * узнаётся рисунок, а не слово. Ряда два, потому что вопросов два: «что мне
     * помогает» и «что мне мешает», — и в общем списке ответ на второй терялся
     * среди первого. Щит и неуязвимость — к бафам, контроль и метки — к дебафам:
     * делит польза для игрока, а не устройство статуса.
     */
    private static void drawAuras(GuiGraphics graphics, Minecraft client, ClientState state,
                                  int width, int height) {
        List<ClientState.StatusLine> buffs = new ArrayList<>();
        List<ClientState.StatusLine> debuffs = new ArrayList<>();
        for (ClientState.StatusLine line : state.statuses()) {
            (helps(line.category()) ? buffs : debuffs).add(line);
        }
        drawAuraRow(graphics, client.font, buffs,
                HudLayout.screenX(HudLayout.Element.BUFFS, width),
                HudLayout.screenY(HudLayout.Element.BUFFS, height));
        drawAuraRow(graphics, client.font, debuffs,
                HudLayout.screenX(HudLayout.Element.DEBUFFS, width),
                HudLayout.screenY(HudLayout.Element.DEBUFFS, height));
    }

    /** Помогает ли статус своей категории игроку: баф, щит, неуязвимость. */
    static boolean helps(String category) {
        return category.equals("BUFF") || category.equals("SHIELD")
                || category.equals("IMMUNITY");
    }

    /**
     * Один ряд. Порядок — как прислал сервер, то есть по времени наложения:
     * значок, прыгающий по ряду при каждом обновлении, не найти взглядом.
     */
    private static void drawAuraRow(GuiGraphics graphics, Font font,
                                    List<ClientState.StatusLine> row, int x, int y) {
        int cursorX = x;
        int cursorY = y;
        int inLine = 0;
        // Строка ряда высотой с самый высокий значок в ней: у статуса с тремя
        // надбавками три строки подписи, и следующая строка ряда не должна
        // наехать на них.
        int tallest = 0;
        for (ClientState.StatusLine line : row) {
            if (inLine == AURA_PER_LINE) {
                cursorX = x;
                cursorY += tallest + AURA_GAP;
                inLine = 0;
                tallest = 0;
            }
            cursorX += drawAura(graphics, font, line, cursorX, cursorY) + AURA_GAP;
            tallest = Math.max(tallest, auraHeight(line));
            inLine++;
        }
    }

    /**
     * Значок статуса и то, что он даёт.
     *
     * <p>Под значком — всё, что статус делает со статами, по строке на стат:
     * значок стата и вклад в процентах, зелёным или красным. Число готовое, с сервера:
     * кривая рейтинга живёт там, и второй её расчёт здесь однажды показал бы не
     * то, что в бою. У статуса без чисел вместо них — описание в два-три слова.
     * Ниже — сколько осталось.
     *
     * @return ширина ячейки: подпись бывает шире значка, и ряд раздвигается под неё
     */
    private static int drawAura(GuiGraphics graphics, Font font, ClientState.StatusLine line,
                                int x, int y) {
        int cell = Math.max(AURA, labelWidth(font, line));
        int boxX = x + (cell - AURA) / 2;
        int colour = colourOf(line.color(), line.category());
        boolean ending = line.remaining() <= ENDING_TICKS;

        // Ромб, как у навыков: значок статуса и значок навыка — одна семья, и
        // глаз ищет их по одной форме.
        RpgStyle.diamondRows(graphics, boxX, y, AURA, 0, AURA, 0xE0120E0A);
        StatusIcons.draw(graphics, line.id(), line.display(), boxX, y, AURA, colour);

        // Часы: сколько срока уже прошло, затемняется сверху вниз. Убывание
        // видно краем глаза, не читая числа.
        double left = line.total() <= 0 ? 1
                : Math.clamp((double) line.remaining() / line.total(), 0, 1);
        int shade = (int) Math.round(AURA * (1 - left));
        if (shade > 0) {
            RpgStyle.diamondRows(graphics, boxX, y, AURA, 0, shade, 0x99000000);
        }
        // Последние секунды — мигание: кончающийся баф пора обновлять, и
        // заметить это надо до того, как он кончится.
        if (ending && (Util.getMillis() / 250) % 2 == 0) {
            RpgStyle.diamondRows(graphics, boxX, y, AURA, 0, AURA, 0x66000000);
        }

        // Контроль — двойной красной рамкой: оглушение надо заметить сразу.
        if (line.category().equals("CONTROL")) {
            RpgStyle.diamondEdge(graphics, boxX, y, AURA, RpgStyle.INK_BAD);
            RpgStyle.diamondEdge(graphics, boxX + 1, y + 1, AURA - 2, RpgStyle.INK_BAD);
        } else {
            RpgStyle.diamondEdge(graphics, boxX, y, AURA, colour);
        }

        if (line.stacks() > 1) {
            String stacks = String.valueOf(line.stacks());
            // В правом нижнем углу квадрата, за краем ромба: там рисунка нет,
            // и число не закрывает значок.
            small(graphics, font, stacks, boxX + AURA - scaled(font.width(stacks)),
                    y + AURA - 6, RpgStyle.TEXT);
        }

        // Все надбавки, по строке на стат: игрок должен видеть всё, что даёт
        // статус, а не первое и многоточие. Самая заметная — первой: так её
        // прислал сервер.
        int labelY = y + AURA + 2;
        if (line.effects().isEmpty()) {
            if (!line.description().isEmpty()) {
                int width = scaled(font.width(line.description()));
                small(graphics, font, line.description(), x + (cell - width) / 2, labelY,
                        RpgStyle.TEXT_DIM);
                labelY += AURA_TEXT;
            }
        } else {
            for (ClientState.EffectLine effect : line.effects()) {
                int width = StatIcons.SIZE + 1 + scaled(font.width(effect.text()));
                int effectX = x + (cell - width) / 2;
                StatIcons.draw(graphics, effect.statId(), effectX, labelY - 1);
                small(graphics, font, effect.text(), effectX + StatIcons.SIZE + 1, labelY,
                        effect.good() ? RpgStyle.INK_GOOD : RpgStyle.INK_BAD);
                labelY += AURA_TEXT;
            }
        }

        String time = timeLeft(line.remaining());
        small(graphics, font, time, x + (cell - scaled(font.width(time))) / 2, labelY,
                ending ? RpgStyle.INK_BAD : RpgStyle.TEXT);
        return cell;
    }

    /** Ширина подписи под значком: самая длинная строка надбавок или описание. */
    private static int labelWidth(Font font, ClientState.StatusLine line) {
        if (line.effects().isEmpty()) {
            return line.description().isEmpty() ? 0 : scaled(font.width(line.description()));
        }
        int widest = 0;
        for (ClientState.EffectLine effect : line.effects()) {
            widest = Math.max(widest, StatIcons.SIZE + 1 + scaled(font.width(effect.text())));
        }
        return widest;
    }

    /** Полная высота значка с подписью: ромб, строки надбавок и время. */
    private static int auraHeight(ClientState.StatusLine line) {
        int rows = line.effects().isEmpty() ? (line.description().isEmpty() ? 0 : 1)
                : line.effects().size();
        return AURA + 2 + (rows + 1) * AURA_TEXT;
    }

    /** Подпись мелким шрифтом: под значком полный шрифт растащил бы ряд вдвое. */
    private static void small(GuiGraphics graphics, Font font, String text, int x, int y,
                              int colour) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(SMALL, SMALL, 1);
        graphics.drawString(font, Component.literal(text), 0, 0, colour, true);
        graphics.pose().popPose();
    }

    private static int scaled(int width) {
        return Math.round(width * SMALL);
    }

    /** Сколько осталось: минуты — минутами, последние десять секунд — с десятыми. */
    private static String timeLeft(int ticks) {
        int secondsLeft = ticks / 20;
        if (secondsLeft >= 60) {
            return (secondsLeft / 60) + "м";
        }
        return seconds(ticks);
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
