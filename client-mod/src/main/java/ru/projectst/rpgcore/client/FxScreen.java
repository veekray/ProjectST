package ru.projectst.rpgcore.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Экранные эффекты: тряска, рамка по краям, тень снизу.
 *
 * <p>Решение владельца: работают всегда, без переключателя. Поэтому они мягкие
 * по построению — тряска не больше полутора градусов и гаснет за долю секунды,
 * рамка лежит по краям и не трогает середину, где прицел и цель. Видит только
 * тот, кого это касается: тряску — кто стоит в области, вспышку крита — кто
 * ударил, корни снизу экрана — на ком висят корни. Решает это мод сам, по своей
 * позиции и числам из события: серверу не нужно знать, кому что трясти.
 */
@EventBusSubscriber(modid = RpgCoreClient.MOD_ID, value = Dist.CLIENT)
public final class FxScreen {

    /** Предел тряски в градусах: дальше она мешает целиться. */
    private static final float MAX_SHAKE = 1.5f;

    private static float shake;
    private static int shakeAge;
    private static int shakeLife;
    private static long clock;

    /** Вспышка или рамка по краям. */
    private record Edge(int argb, int life, boolean pulse) {
    }

    private static final List<Edge> EDGES = new ArrayList<>();
    private static final List<Integer> EDGE_AGES = new ArrayList<>();

    private FxScreen() {
    }

    /**
     * Тряска: сильнее та, что сильнее, слабые не складываются в землетрясение.
     *
     * @param strength от 0 до 1
     */
    static void shake(float strength, int ticks) {
        float wanted = Math.clamp(strength, 0f, 1f) * MAX_SHAKE;
        if (wanted >= currentShake()) {
            shake = wanted;
            shakeAge = 0;
            shakeLife = Math.max(1, ticks);
        }
    }

    /**
     * Тряска для того, кто стоит в области: у центра сильнее, у края слабее,
     * снаружи — никак.
     */
    static void shakeIfInside(double cx, double cy, double cz, double radius, float strength,
                              int ticks) {
        var player = Minecraft.getInstance().player;
        if (player == null || radius <= 0) {
            return;
        }
        double dx = player.getX() - cx;
        double dz = player.getZ() - cz;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > radius || Math.abs(player.getY() - cy) > 4) {
            return;
        }
        shake(strength * (float) (1 - 0.6 * d / radius), ticks);
    }

    /** Вспышка цветом по краям экрана. */
    static void flash(int argb, int ticks) {
        EDGES.add(new Edge(argb, Math.max(1, ticks), false));
        EDGE_AGES.add(0);
    }

    private static float currentShake() {
        if (shakeLife <= 0 || shakeAge >= shakeLife) {
            return 0;
        }
        return shake * (1f - (float) shakeAge / shakeLife);
    }

    static void tick() {
        clock++;
        if (shakeAge < shakeLife) {
            shakeAge++;
        }
        for (int i = EDGES.size() - 1; i >= 0; i--) {
            int age = EDGE_AGES.get(i) + 1;
            if (age >= EDGES.get(i).life()) {
                EDGES.remove(i);
                EDGE_AGES.remove(i);
            } else {
                EDGE_AGES.set(i, age);
            }
        }
    }

    static void clear() {
        EDGES.clear();
        EDGE_AGES.clear();
        shakeLife = 0;
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        float s = currentShake();
        if (s <= 0.001f) {
            return;
        }
        double t = (clock + event.getPartialTick()) * 1.7;
        // Три несвязанные частоты: ровная синусоида читается как качка, а не удар.
        event.setYaw(event.getYaw() + s * (float) Math.sin(t * 2.3));
        event.setPitch(event.getPitch() + s * (float) Math.sin(t * 3.1 + 1.3) * 0.8f);
        event.setRoll(event.getRoll() + s * (float) Math.sin(t * 1.7 + 0.4) * 0.5f);
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        int self = client.player.getId();

        // Состояния на себе: корни держат — тень корней снизу; яд — зелёные края.
        if (FxStatuses.has(self, "root")) {
            bottom(g, w, h, 0xFF2B1A0C, 0.55f);
        }
        if (FxStatuses.has(self, "potion:poison")) {
            float pulse = 0.55f + 0.2f * (float) Math.sin((clock + partial) * 0.25);
            edges(g, w, h, 0xFF4FB33A, 0.32f * pulse);
        }
        for (int i = 0; i < EDGES.size(); i++) {
            Edge edge = EDGES.get(i);
            float k = (EDGE_AGES.get(i) + partial) / edge.life();
            float alpha = (1f - k) * (edge.pulse() ? 0.6f : 0.45f);
            edges(g, w, h, edge.argb(), alpha);
        }
        FxCasts.drawBar(g, w, h, partial);
    }

    /** Мягкая рамка: от края к середине прозрачнее, середина чистая. */
    private static void edges(GuiGraphics g, int w, int h, int argb, float alpha) {
        int band = Math.max(12, Math.min(w, h) / 7);
        int solid = FxDraw.withAlpha(argb, alpha);
        int clear = FxDraw.withAlpha(argb, 0f);
        g.fillGradient(0, 0, w, band, solid, clear);
        g.fillGradient(0, h - band, w, h, clear, solid);
        // Боковые полосы — горизонтальным градиентом из вертикальных ступенек:
        // у fillGradient он только вертикальный.
        int steps = 8;
        for (int i = 0; i < steps; i++) {
            float k = 1f - (float) i / steps;
            int c = FxDraw.withAlpha(argb, alpha * k * k);
            int x0 = band * i / steps;
            int x1 = band * (i + 1) / steps;
            g.fill(x0, 0, x1, h, c);
            g.fill(w - x1, 0, w - x0, h, c);
        }
    }

    /** Тень снизу экрана: на ногах что-то держит. */
    private static void bottom(GuiGraphics g, int w, int h, int argb, float alpha) {
        int band = h / 4;
        g.fillGradient(0, h - band, w, h, FxDraw.withAlpha(argb, 0f),
                FxDraw.withAlpha(argb, alpha));
    }
}
