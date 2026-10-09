package ru.projectst.rpgcore.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Arrays;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Рисование эффектов: кольца по земле, секторы, ленты, спрайты к камере.
 *
 * <p><b>Без новых записей в реестрах.</b> Ни одного своего {@code ParticleType}:
 * реестр типов частиц NeoForge синхронизирует с сервером, и при входе клиент
 * стирает у своих записей числовые номера, оставляя только серверные. Запись,
 * которой нет на сервере, остаётся без номера — отключения не будет, но любой
 * кодек, пишущий частицу по номеру, получит −1. Проверено по исходникам NeoForge
 * 21.1: {@code NeoForgeRegistriesSetup.VANILLA_SYNC_REGISTRIES} включает
 * {@code PARTICLE_TYPE}, {@code RegistryManager.applySnapshot} делает
 * {@code clear(false)} и ставит номера только из снимка сервера. Риск есть, а
 * выгоды нет: здесь всё рисуется геометрией в {@code RenderLevelStageEvent}.
 *
 * <p><b>Свечение — аддитивное смешивание.</b> Цвет прибавляется к тому, что уже
 * на экране ({@code SRC_ALPHA, ONE}, как у молнии), поэтому пересечения светятся
 * ярче, а не перекрывают друг друга. Глубина проверяется, но не пишется:
 * эффекты прячутся за стенами и не прячут друг друга.
 *
 * <p>Вершины копятся в пачки по текстуре и уходят одним вызовом на текстуру за
 * кадр: двадцать магов с печатями — это шесть вызовов отрисовки, а не тысяча.
 */
final class FxDraw {

    /** Картинки эффектов: белые с прозрачностью, цвет даёт вершина. */
    enum Tex {
        /** Мягкое пятно: ядра, вспышки, заливки. */
        GLOW,
        /** Полоса границы: яркая середина и мягкий край поперёк. */
        RING,
        /** Пояс рун, повторяется вдоль окружности. */
        RUNES,
        /** Звёздочка искры. */
        SPARK,
        /** Лента следа: мягче границы. */
        BEAM,
        /** Заливка круга: прозрачная середина, светлее к краю. */
        FILL;

        final ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                RpgCoreClient.MOD_ID, "textures/fx/" + name().toLowerCase(java.util.Locale.ROOT) + ".png");
        private RenderType type;

        RenderType type() {
            if (type == null) {
                type = RenderType.create("rpgcore_fx_" + name().toLowerCase(java.util.Locale.ROOT),
                        DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 262144,
                        false, false,
                        RenderType.CompositeState.builder()
                                .setShaderState(new RenderStateShard.ShaderStateShard(
                                        GameRenderer::getPositionTexColorShader))
                                .setTextureState(new RenderStateShard.TextureStateShard(
                                        location, false, false))
                                .setTransparencyState(RenderType.LIGHTNING_TRANSPARENCY)
                                .setWriteMaskState(RenderType.COLOR_WRITE)
                                .setCullState(RenderType.NO_CULL)
                                // Мишень частиц: при «Fabulous!» прозрачное
                                // сводится отдельно, и рисунок мимо неё лёг бы
                                // поверх воды и стекла.
                                .setOutputState(RenderType.PARTICLES_TARGET)
                                .createCompositeState(false));
            }
            return type;
        }
    }

    /** Вершины одной текстуры за кадр. */
    private static final class Batch {
        float[] xyzuv = new float[5 * 4 * 256];
        int[] colours = new int[4 * 256];
        int vertices;

        void add(float x, float y, float z, float u, float v, int argb) {
            if (vertices == colours.length) {
                colours = Arrays.copyOf(colours, vertices * 2);
                xyzuv = Arrays.copyOf(xyzuv, vertices * 10);
            }
            int i = vertices * 5;
            xyzuv[i] = x;
            xyzuv[i + 1] = y;
            xyzuv[i + 2] = z;
            xyzuv[i + 3] = u;
            xyzuv[i + 4] = v;
            colours[vertices++] = argb;
        }
    }

    private final Batch[] batches = new Batch[Tex.values().length];

    /** Камера: вершины пишутся относительно неё, иначе float теряет точность вдали от нуля. */
    double camX;
    double camY;
    double camZ;
    final Quaternionf camRotation = new Quaternionf();
    Level level;

    /** Подробность: 1 — всё, меньше — реже сегменты. Ставит настройка «Частицы». */
    float detail = 1f;

    private final Vector3f scratch = new Vector3f();

    FxDraw() {
        for (int i = 0; i < batches.length; i++) {
            batches[i] = new Batch();
        }
    }

    void begin(Level level, double camX, double camY, double camZ, Quaternionf rotation,
               float detail) {
        this.level = level;
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
        this.camRotation.set(rotation);
        this.detail = detail;
        for (Batch batch : batches) {
            batch.vertices = 0;
        }
    }

    /** Отдаёт накопленное: один вызов отрисовки на текстуру. */
    void flush(Matrix4f pose, MultiBufferSource.BufferSource buffers) {
        for (Tex tex : Tex.values()) {
            Batch batch = batches[tex.ordinal()];
            if (batch.vertices == 0) {
                continue;
            }
            RenderType type = tex.type();
            VertexConsumer out = buffers.getBuffer(type);
            for (int v = 0; v < batch.vertices; v++) {
                int i = v * 5;
                int c = batch.colours[v];
                out.addVertex(pose, batch.xyzuv[i], batch.xyzuv[i + 1], batch.xyzuv[i + 2])
                        .setUv(batch.xyzuv[i + 3], batch.xyzuv[i + 4])
                        .setColor((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF);
            }
            buffers.endBatch(type);
        }
    }

    // ------------------------------------------------------------------ цвет

    /** Цвет с прозрачностью: альфа от 0 до 1 поверх RGB. */
    static int withAlpha(int argb, float alpha) {
        int a = Math.round(Math.clamp(alpha, 0f, 1f) * 255f);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Смешение двух цветов: 0 — первый, 1 — второй. */
    static int mix(int from, int to, float t) {
        float k = Math.clamp(t, 0f, 1f);
        int r = Math.round(((from >> 16) & 0xFF) * (1 - k) + ((to >> 16) & 0xFF) * k);
        int g = Math.round(((from >> 8) & 0xFF) * (1 - k) + ((to >> 8) & 0xFF) * k);
        int b = Math.round((from & 0xFF) * (1 - k) + (to & 0xFF) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ вершины

    private void vertex(Tex tex, double x, double y, double z, float u, float v, int argb) {
        batches[tex.ordinal()].add((float) (x - camX), (float) (y - camY), (float) (z - camZ),
                u, v, argb);
    }

    private float groundY(double x, double z, double refY, float lift) {
        return FxGround.top(level, x, z, refY) + lift;
    }

    /** Сколько сегментов на дугу: не реже трети блока у полной подробности. */
    private int segmentsFor(double arcLength, int min, int max) {
        int n = (int) Math.ceil(arcLength / 0.33 * detail);
        return Math.clamp(n, min, max);
    }

    // ------------------------------------------------------------------ фигуры

    /**
     * Полоса по окружности, прижатая к земле.
     *
     * <p>Середина полосы лежит ровно на {@code radius}: у текстуры {@link Tex#RING}
     * яркая черта посередине, и именно она — граница области.
     *
     * @param from      начальный угол в радианах; ноль — по +X, рост — к +Z
     * @param to        конечный угол
     * @param uPerBlock сколько повторов текстуры на блок дуги
     * @param uOffset   сдвиг текстуры вдоль дуги: так руны ходят по кругу
     */
    void ring(Tex tex, double cx, double cy, double cz, double radius, double width,
              double from, double to, int argb, float alpha, float uPerBlock, float uOffset,
              float lift) {
        if (alpha <= 0.004f || radius <= 0) {
            return;
        }
        int colour = withAlpha(argb, alpha);
        double inner = Math.max(0, radius - width / 2);
        double outer = radius + width / 2;
        int segments = segmentsFor(Math.abs(to - from) * outer, 8, 220);
        double prevCos = Math.cos(from);
        double prevSin = Math.sin(from);
        float prevIn = groundY(cx + prevCos * inner, cz + prevSin * inner, cy, lift);
        float prevOut = groundY(cx + prevCos * outer, cz + prevSin * outer, cy, lift);
        for (int i = 1; i <= segments; i++) {
            double a = from + (to - from) * i / segments;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            float yIn = groundY(cx + cos * inner, cz + sin * inner, cy, lift);
            float yOut = groundY(cx + cos * outer, cz + sin * outer, cy, lift);
            float u0 = uOffset + (float) ((from + (to - from) * (i - 1) / segments) * radius) * uPerBlock;
            float u1 = uOffset + (float) (a * radius) * uPerBlock;
            vertex(tex, cx + prevCos * inner, prevIn, cz + prevSin * inner, u0, 0, colour);
            vertex(tex, cx + prevCos * outer, prevOut, cz + prevSin * outer, u0, 1, colour);
            vertex(tex, cx + cos * outer, yOut, cz + sin * outer, u1, 1, colour);
            vertex(tex, cx + cos * inner, yIn, cz + sin * inner, u1, 0, colour);
            prevCos = cos;
            prevSin = sin;
            prevIn = yIn;
            prevOut = yOut;
        }
    }

    /** Полная окружность. */
    void circle(Tex tex, double cx, double cy, double cz, double radius, double width,
                int argb, float alpha, float uPerBlock, float uOffset, float lift) {
        ring(tex, cx, cy, cz, radius, width, 0, Math.PI * 2, argb, alpha, uPerBlock, uOffset,
                lift);
    }

    /**
     * Заливка сектора по земле: от центра до {@code radius} между углами.
     *
     * <p>Текстура ложится плоско сверху, поэтому заливка круга светлеет к краю
     * одинаково на любом склоне.
     */
    void sector(Tex tex, double cx, double cy, double cz, double radius, double from, double to,
                int argb, float alpha, float lift) {
        if (alpha <= 0.004f || radius <= 0) {
            return;
        }
        int colour = withAlpha(argb, alpha);
        int rings = radius > 6 ? 4 : 3;
        int segments = segmentsFor(Math.abs(to - from) * radius, 8, 96);
        for (int k = 0; k < rings; k++) {
            double r0 = radius * k / rings;
            double r1 = radius * (k + 1) / rings;
            for (int i = 0; i < segments; i++) {
                double a0 = from + (to - from) * i / segments;
                double a1 = from + (to - from) * (i + 1) / segments;
                planar(tex, cx, cy, cz, radius, r0, a0, colour, lift);
                planar(tex, cx, cy, cz, radius, r1, a0, colour, lift);
                planar(tex, cx, cy, cz, radius, r1, a1, colour, lift);
                planar(tex, cx, cy, cz, radius, r0, a1, colour, lift);
            }
        }
    }

    private void planar(Tex tex, double cx, double cy, double cz, double radius, double r,
                        double a, int colour, float lift) {
        double x = cx + Math.cos(a) * r;
        double z = cz + Math.sin(a) * r;
        float u = (float) (0.5 + (x - cx) / (2 * radius));
        float v = (float) (0.5 + (z - cz) / (2 * radius));
        vertex(tex, x, groundY(x, z, cy, lift), z, u, v, colour);
    }

    /** Полоса по земле от точки до точки: кромка конуса. */
    void groundLine(Tex tex, double ax, double az, double bx, double bz, double refY,
                    double width, int argb, float alpha, float lift) {
        if (alpha <= 0.004f) {
            return;
        }
        int colour = withAlpha(argb, alpha);
        double dx = bx - ax;
        double dz = bz - az;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1e-4) {
            return;
        }
        double sx = -dz / length * width / 2;
        double sz = dx / length * width / 2;
        int steps = segmentsFor(length, 1, 120);
        for (int i = 0; i < steps; i++) {
            double t0 = (double) i / steps;
            double t1 = (double) (i + 1) / steps;
            double x0 = ax + dx * t0;
            double z0 = az + dz * t0;
            double x1 = ax + dx * t1;
            double z1 = az + dz * t1;
            float u0 = (float) (t0 * length);
            float u1 = (float) (t1 * length);
            vertex(tex, x0 - sx, groundY(x0 - sx, z0 - sz, refY, lift), z0 - sz, u0, 0, colour);
            vertex(tex, x0 + sx, groundY(x0 + sx, z0 + sz, refY, lift), z0 + sz, u0, 1, colour);
            vertex(tex, x1 + sx, groundY(x1 + sx, z1 + sz, refY, lift), z1 + sz, u1, 1, colour);
            vertex(tex, x1 - sx, groundY(x1 - sx, z1 - sz, refY, lift), z1 - sz, u1, 0, colour);
        }
    }

    /**
     * Квадрат лицом к камере.
     *
     * @param roll поворот в плоскости экрана, радианы: искры не должны быть
     *             одинаково ровными
     */
    void sprite(Tex tex, double x, double y, double z, float size, float roll, int argb,
                float alpha) {
        if (alpha <= 0.004f || size <= 0) {
            return;
        }
        int colour = withAlpha(argb, alpha);
        float half = size / 2;
        float cos = (float) Math.cos(roll) * half;
        float sin = (float) Math.sin(roll) * half;
        corner(tex, x, y, z, -cos + sin, -sin - cos, 0, 1, colour);
        corner(tex, x, y, z, -cos - sin, -sin + cos, 0, 0, colour);
        corner(tex, x, y, z, cos - sin, sin + cos, 1, 0, colour);
        corner(tex, x, y, z, cos + sin, sin - cos, 1, 1, colour);
    }

    private void corner(Tex tex, double x, double y, double z, float ox, float oy,
                        float u, float v, int colour) {
        scratch.set(ox, oy, 0).rotate(camRotation);
        vertex(tex, x + scratch.x(), y + scratch.y(), z + scratch.z(), u, v, colour);
    }

    /**
     * Лента через точки, развёрнутая к камере.
     *
     * @param alphaTail прозрачность у первой точки; у последней — {@code alphaHead}
     */
    void ribbon(Tex tex, double[] xs, double[] ys, double[] zs, int count, float width,
                int argb, float alphaTail, float alphaHead, float uOffset) {
        if (count < 2 || Math.max(alphaTail, alphaHead) <= 0.004f) {
            return;
        }
        double length = 0;
        for (int i = 1; i < count; i++) {
            double l = Math.sqrt(sq(xs[i] - xs[i - 1]) + sq(ys[i] - ys[i - 1]) + sq(zs[i] - zs[i - 1]));
            length += l;
        }
        if (length < 1e-4) {
            return;
        }
        double travelled = 0;
        double[] side0 = side(xs, ys, zs, count, 0, width);
        for (int i = 1; i < count; i++) {
            double[] side1 = side(xs, ys, zs, count, i, width);
            double step = Math.sqrt(sq(xs[i] - xs[i - 1]) + sq(ys[i] - ys[i - 1]) + sq(zs[i] - zs[i - 1]));
            float t0 = (float) (travelled / length);
            float t1 = (float) ((travelled + step) / length);
            int c0 = withAlpha(argb, alphaTail + (alphaHead - alphaTail) * t0);
            int c1 = withAlpha(argb, alphaTail + (alphaHead - alphaTail) * t1);
            float u0 = uOffset + (float) travelled;
            float u1 = uOffset + (float) (travelled + step);
            vertex(tex, xs[i - 1] - side0[0], ys[i - 1] - side0[1], zs[i - 1] - side0[2], u0, 0, c0);
            vertex(tex, xs[i - 1] + side0[0], ys[i - 1] + side0[1], zs[i - 1] + side0[2], u0, 1, c0);
            vertex(tex, xs[i] + side1[0], ys[i] + side1[1], zs[i] + side1[2], u1, 1, c1);
            vertex(tex, xs[i] - side1[0], ys[i] - side1[1], zs[i] - side1[2], u1, 0, c1);
            travelled += step;
            side0 = side1;
        }
    }

    /** Полуширина ленты в точке: поперёк хода и поперёк взгляда камеры. */
    private double[] side(double[] xs, double[] ys, double[] zs, int count, int i, float width) {
        int a = Math.max(0, i - 1);
        int b = Math.min(count - 1, i + 1);
        double tx = xs[b] - xs[a];
        double ty = ys[b] - ys[a];
        double tz = zs[b] - zs[a];
        double vx = camX - xs[i];
        double vy = camY - ys[i];
        double vz = camZ - zs[i];
        double sx = ty * vz - tz * vy;
        double sy = tz * vx - tx * vz;
        double sz = tx * vy - ty * vx;
        double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-6) {
            return new double[] {0, width / 2.0, 0};
        }
        double k = width / 2.0 / l;
        return new double[] {sx * k, sy * k, sz * k};
    }

    private static double sq(double v) {
        return v * v;
    }
}
