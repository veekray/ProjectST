package ru.projectst.rpgcore.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Геометрия сцен: ничто не выходит за радиус события.
 *
 * <p>Граница области — радиус, которым сервер выбрал цели. Корни, лозы и облако
 * — украшение внутри неё; вылезший за край корень говорил бы, что задело того,
 * кого не задело.
 */
class FxGeometryTest {

    private static final double CX = 100.5;
    private static final double CZ = -40.25;

    @Test
    @DisplayName("точки по площади всегда внутри радиуса и доходят до края")
    void insideCircleStaysInside() {
        Random random = new Random(7);
        double radius = 9;
        double farthest = 0;
        for (int i = 0; i < 5000; i++) {
            double[] p = FxGeometry.insideCircle(random, CX, 64, CZ, radius);
            double d = Math.hypot(p[0] - CX, p[2] - CZ);
            assertTrue(d <= radius - FxGeometry.EDGE_MARGIN + 1e-9, "вышла за край: " + d);
            farthest = Math.max(farthest, d);
        }
        assertTrue(farthest > radius * 0.95, "до края не доходят: украшение не покрыло бы площадь");
    }

    @Test
    @DisplayName("корни по площади, прижатые к кругу, не выходят за радиус по всей кривой")
    void spikesNeverCrossTheBorder() {
        Random random = new Random(11);
        double radius = 6;
        for (int i = 0; i < 2000; i++) {
            // Корень у самого края с наклоном наружу — худший случай.
            double a = random.nextDouble() * Math.PI * 2;
            double[][] spike = FxGeometry.rootSpike(random, CX + Math.cos(a) * (radius - 0.2), 64,
                    CZ + Math.sin(a) * (radius - 0.2), 1.6);
            for (int s = 0; s <= 24; s++) {
                double[] p = FxGeometry.clampInside(CX, CZ, radius,
                        FxGeometry.spline(spike, s / 24.0));
                assertTrue(Math.hypot(p[0] - CX, p[2] - CZ) <= radius, "корень вылез наружу");
            }
        }
    }

    @Test
    @DisplayName("лоза плюща, прижатая к кругу, не дальше границы")
    void lashStaysInside() {
        double radius = 7;
        for (int i = 0; i < 64; i++) {
            double a = Math.PI * 2 * i / 64;
            double[][] arc = FxGeometry.clampAll(CX, CZ, radius, FxGeometry.lashArc(
                    CX, 64, CZ, CX + Math.cos(a) * radius * 1.2, 64, CZ + Math.sin(a) * radius * 1.2));
            for (int s = 0; s <= 30; s++) {
                double[] p = FxGeometry.clampInside(CX, CZ, radius, FxGeometry.spline(arc, s / 30.0));
                assertTrue(Math.hypot(p[0] - CX, p[2] - CZ) <= radius, "лоза за границей");
            }
        }
    }

    @Test
    @DisplayName("прижатие не трогает точку внутри и ставит внешнюю на край")
    void clampKeepsInsideAndMovesOutside() {
        double[] inside = {CX + 1, 64, CZ};
        assertEquals(inside, FxGeometry.clampInside(CX, CZ, 5, inside));
        double[] moved = FxGeometry.clampInside(CX, CZ, 5, new double[] {CX + 20, 70, CZ});
        assertEquals(5 - FxGeometry.EDGE_MARGIN, Math.hypot(moved[0] - CX, moved[2] - CZ), 1e-9);
        assertEquals(70, moved[1], 1e-9, "высота не меняется");
    }

    @Test
    @DisplayName("корень на ногах начинается в земле и кончается у цели")
    void rootAroundLegsEndsAtTarget() {
        double[][] root = FxGeometry.rootAroundLegs(CX, 64, CZ, 0.3, 0.85, 1.2, 0.8);
        assertTrue(root[0][1] < 64, "корень пробивает землю, а не растёт из воздуха");
        double[] tip = root[root.length - 1];
        assertTrue(Math.hypot(tip[0] - CX, tip[2] - CZ) < 0.4, "кончик обвивает ноги");
        assertEquals(65.2, tip[1], 1e-9);
    }

    @Test
    @DisplayName("лиана вьётся вокруг тела, не касаясь его, из земли до высоты")
    void vineCoilKeepsOffTheBody() {
        double[][] vine = FxGeometry.vineCoil(0.5, 0.6, 1.8, 1.25);
        assertTrue(vine[0][1] < 0, "лиана выходит из земли");
        assertEquals(1.85, vine[vine.length - 1][1], 1e-9);
        for (int i = 0; i <= 40; i++) {
            double[] p = FxGeometry.spline(vine, i / 40.0);
            assertTrue(Math.hypot(p[0], p[2]) > 0.45, "не прилегает к телу: " + i);
        }
        for (int i = 1; i < vine.length; i++) {
            assertTrue(vine[i][1] > vine[i - 1][1], "растёт только вверх");
        }
    }
}
