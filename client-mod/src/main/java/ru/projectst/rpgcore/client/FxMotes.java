package ru.projectst.rpgcore.client;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Искры: мелочь, которая делает эффект живым.
 *
 * <p>Свой пул, а не ванильный {@code ParticleEngine}: у того бюджет общий с
 * ванилью, а настройку «Частицы» он применяет не при добавлении, так что
 * считать и ограничивать пришлось бы всё равно самим. Здесь предел один и
 * жёсткий: заполненный пул новую искру просто не берёт. Искры — украшение, и
 * по ним ничего не определяют, поэтому их и режут первыми.
 *
 * <p>Массивы вместо объектов: тысяча искр — тысяча строк в шести массивах, без
 * мусора на каждую.
 */
final class FxMotes {

    private final int capacity;
    private final double[] x;
    private final double[] y;
    private final double[] z;
    private final double[] px;
    private final double[] py;
    private final double[] pz;
    private final float[] vx;
    private final float[] vy;
    private final float[] vz;
    private final float[] size;
    private final int[] colour;
    private final short[] age;
    private final short[] life;
    private final float[] drag;
    private int count;

    FxMotes(int capacity) {
        this.capacity = capacity;
        x = new double[capacity];
        y = new double[capacity];
        z = new double[capacity];
        px = new double[capacity];
        py = new double[capacity];
        pz = new double[capacity];
        vx = new float[capacity];
        vy = new float[capacity];
        vz = new float[capacity];
        size = new float[capacity];
        colour = new int[capacity];
        age = new short[capacity];
        life = new short[capacity];
        drag = new float[capacity];
    }

    int count() {
        return count;
    }

    /** Свободна ли доля пула: при тесноте украшения скупее. */
    float pressure() {
        return (float) count / capacity;
    }

    /**
     * Новая искра.
     *
     * @param ticks сколько живёт
     * @param slow  сопротивление: 1 — летит как брошено, меньше — тормозит
     */
    void spawn(double sx, double sy, double sz, float svx, float svy, float svz, float ssize,
               int argb, int ticks, float slow) {
        if (count >= capacity) {
            return;
        }
        int i = count++;
        x[i] = px[i] = sx;
        y[i] = py[i] = sy;
        z[i] = pz[i] = sz;
        vx[i] = svx;
        vy[i] = svy;
        vz[i] = svz;
        size[i] = ssize;
        colour[i] = argb;
        age[i] = 0;
        life[i] = (short) Math.max(1, ticks);
        drag[i] = slow;
    }

    void tick() {
        int i = 0;
        while (i < count) {
            if (++age[i] >= life[i]) {
                remove(i);
                continue;
            }
            px[i] = x[i];
            py[i] = y[i];
            pz[i] = z[i];
            x[i] += vx[i];
            y[i] += vy[i];
            z[i] += vz[i];
            vx[i] *= drag[i];
            vy[i] *= drag[i];
            vz[i] *= drag[i];
            i++;
        }
    }

    private void remove(int i) {
        int last = --count;
        x[i] = x[last];
        y[i] = y[last];
        z[i] = z[last];
        px[i] = px[last];
        py[i] = py[last];
        pz[i] = pz[last];
        vx[i] = vx[last];
        vy[i] = vy[last];
        vz[i] = vz[last];
        size[i] = size[last];
        colour[i] = colour[last];
        age[i] = age[last];
        life[i] = life[last];
        drag[i] = drag[last];
    }

    void clear() {
        count = 0;
    }

    /** Рисует все искры: ярко появляются, гаснут к концу жизни. */
    void draw(FxDraw draw, float partial, double maxDistance) {
        double maxSq = maxDistance * maxDistance;
        for (int i = 0; i < count; i++) {
            double ix = px[i] + (x[i] - px[i]) * partial;
            double iy = py[i] + (y[i] - py[i]) * partial;
            double iz = pz[i] + (z[i] - pz[i]) * partial;
            double dx = ix - draw.camX;
            double dy = iy - draw.camY;
            double dz = iz - draw.camZ;
            if (dx * dx + dy * dy + dz * dz > maxSq) {
                continue;
            }
            float t = (age[i] + partial) / life[i];
            float alpha = t < 0.15f ? t / 0.15f : 1f - (t - 0.15f) / 0.85f;
            float s = size[i] * (1f - 0.4f * t);
            draw.sprite(FxDraw.Tex.SPARK, ix, iy, iz, s, i * 0.7f, colour[i], alpha);
        }
    }

    static float jitter(float spread) {
        return (ThreadLocalRandom.current().nextFloat() * 2f - 1f) * spread;
    }

    static float random() {
        return ThreadLocalRandom.current().nextFloat();
    }
}
