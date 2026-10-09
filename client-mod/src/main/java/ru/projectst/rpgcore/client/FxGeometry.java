package ru.projectst.rpgcore.client;

import java.util.Random;

/**
 * Геометрия сцен: где растут корни, как гнётся лоза, куда ложатся лепестки.
 *
 * <p>Чистая математика без Minecraft, поэтому её проверяет обычный тест. Главное
 * правило проверяется именно здесь: <b>ничто не выходит за радиус события</b>.
 * Корни, лозы и облако — украшение внутри области; граница стоит ровно на
 * радиусе, и картинка, вылезшая за неё, врала бы о том, кого задело.
 *
 * <p>Точки — массивы {@code {x, y, z}} в мировых координатах.
 */
final class FxGeometry {

    /** Насколько внутрь от границы держатся украшения: черта границы — своя. */
    static final double EDGE_MARGIN = 0.15;

    private FxGeometry() {
    }

    /**
     * Точка внутри круга: прижимает к радиусу, если вылезла.
     *
     * @return новая точка; высота не меняется
     */
    static double[] clampInside(double cx, double cz, double radius, double[] p) {
        double limit = Math.max(0, radius - EDGE_MARGIN);
        double dx = p[0] - cx;
        double dz = p[2] - cz;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d <= limit || d == 0) {
            return p;
        }
        double k = limit / d;
        return new double[] {cx + dx * k, p[1], cz + dz * k};
    }

    /**
     * Случайная точка внутри круга, равномерно по площади.
     *
     * <p>Корень из случайного радиуса: без него точки сбивались бы к центру, и
     * край области выглядел бы пустым — как будто корни туда не достают.
     */
    static double[] insideCircle(Random random, double cx, double y, double cz, double radius) {
        double a = random.nextDouble() * Math.PI * 2;
        double r = Math.sqrt(random.nextDouble()) * Math.max(0, radius - EDGE_MARGIN);
        return new double[] {cx + Math.cos(a) * r, y, cz + Math.sin(a) * r};
    }

    /**
     * Опорные точки корня: из земли рядом с целью — вверх и вокруг ног.
     *
     * @param tx     где стоит цель
     * @param angle  с какой стороны корень выходит из земли
     * @param height до какой высоты обвивает
     * @param turns  сколько витков вокруг ног
     */
    static double[][] rootAroundLegs(double tx, double ty, double tz, double angle,
                                     double reach, double height, double turns) {
        int n = 7;
        double[][] out = new double[n][];
        // Выход из земли — на шаг в сторону от цели, чуть ниже поверхности: корень
        // пробивает землю, а не вырастает из воздуха.
        out[0] = new double[] {tx + Math.cos(angle) * reach, ty - 0.35,
                tz + Math.sin(angle) * reach};
        for (int i = 1; i < n; i++) {
            double k = (double) i / (n - 1);
            double a = angle + k * turns * Math.PI * 2;
            double r = reach * (1 - k) + 0.32 * k;
            out[i] = new double[] {tx + Math.cos(a) * r, ty + height * k * k,
                    tz + Math.sin(a) * r};
        }
        return out;
    }

    /**
     * Лиана вокруг существа: из земли — спиралью вверх, не касаясь тела.
     *
     * <p>Точки — смещения от ног существа: трубка с привязкой переносит их за
     * ним. Радиус держится почти ровным (лиана чуть дышит, но не ближе
     * {@code radius * 0.94}), кончик отгибается наружу, как у живого побега.
     *
     * @param angle  откуда лиана выходит из земли, радианы
     * @param radius на каком расстоянии от оси тела вьётся
     * @param height до какой высоты поднимается
     * @param turns  сколько витков; знак — направление
     */
    static double[][] vineCoil(double angle, double radius, double height, double turns) {
        int n = Math.max(5, (int) Math.ceil(Math.abs(turns) * 6)) + 1;
        double[][] out = new double[n][];
        for (int i = 0; i < n; i++) {
            double k = (double) i / (n - 1);
            double a = angle + k * turns * Math.PI * 2;
            double r = radius * (1 + 0.06 * Math.sin(k * Math.PI * 3));
            double y = -0.3 + (height + 0.3) * k;
            if (i == n - 1) {
                r += 0.06;
                y += 0.05;
            }
            out[i] = new double[] {Math.cos(a) * r, y, Math.sin(a) * r};
        }
        return out;
    }

    /**
     * Короткий корень, который пробивает землю и опадает: украшение по площади.
     */
    static double[][] rootSpike(Random random, double x, double y, double z, double height) {
        double lean = random.nextDouble() * Math.PI * 2;
        double tilt = 0.15 + random.nextDouble() * 0.35;
        return new double[][] {
                {x, y - 0.3, z},
                {x + Math.cos(lean) * tilt * 0.3, y + height * 0.45, z + Math.sin(lean) * tilt * 0.3},
                {x + Math.cos(lean) * tilt, y + height, z + Math.sin(lean) * tilt},
        };
    }

    /**
     * Лоза дугой: из земли у источника — к точке удара.
     *
     * <p>Дуга поднимается над землёй на треть длины: хлёст должен читаться как
     * взмах, а не как ползущая по земле верёвка.
     */
    static double[][] lashArc(double fx, double fy, double fz, double tx, double ty, double tz) {
        double dx = tx - fx;
        double dz = tz - fz;
        double length = Math.sqrt(dx * dx + dz * dz);
        double lift = Math.max(0.8, length * 0.35);
        int n = 6;
        double[][] out = new double[n][];
        for (int i = 0; i < n; i++) {
            double k = (double) i / (n - 1);
            double y = fy + (ty - fy) * k + Math.sin(k * Math.PI) * lift;
            out[i] = new double[] {fx + dx * k, y - (i == 0 ? 0.3 : 0), fz + dz * k};
        }
        return out;
    }

    /**
     * Точка на кривой Катмулла-Рома через опорные точки.
     *
     * @param t от 0 (первая опорная) до 1 (последняя)
     */
    static double[] spline(double[][] points, double t) {
        int segments = points.length - 1;
        double scaled = Math.clamp(t, 0, 1) * segments;
        int i = Math.min(segments - 1, (int) Math.floor(scaled));
        double u = scaled - i;
        double[] p0 = points[Math.max(0, i - 1)];
        double[] p1 = points[i];
        double[] p2 = points[i + 1];
        double[] p3 = points[Math.min(points.length - 1, i + 2)];
        double[] out = new double[3];
        double u2 = u * u;
        double u3 = u2 * u;
        for (int c = 0; c < 3; c++) {
            out[c] = 0.5 * ((2 * p1[c]) + (-p0[c] + p2[c]) * u
                    + (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * u2
                    + (-p0[c] + 3 * p1[c] - 3 * p2[c] + p3[c]) * u3);
        }
        return out;
    }

    /** Самая дальняя по горизонтали точка кривой от центра: для проверки границы. */
    static double farthest(double[][] points, double cx, double cz, int samples) {
        double best = 0;
        for (int i = 0; i <= samples; i++) {
            double[] p = spline(points, (double) i / samples);
            best = Math.max(best, Math.hypot(p[0] - cx, p[2] - cz));
        }
        return best;
    }

    /** Прижимает все опорные точки внутрь круга. */
    static double[][] clampAll(double cx, double cz, double radius, double[][] points) {
        double[][] out = new double[points.length][];
        for (int i = 0; i < points.length; i++) {
            out[i] = clampInside(cx, cz, radius, points[i]);
        }
        return out;
    }

    /** Плавное нарастание: быстро вначале, мягко к концу. */
    static double easeOut(double t) {
        double k = Math.clamp(t, 0, 1);
        return 1 - (1 - k) * (1 - k) * (1 - k);
    }
}
