package ru.projectst.rpgcore.client;

import static ru.projectst.rpgcore.client.SceneKit.entity;
import static ru.projectst.rpgcore.client.SceneKit.live;

import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Ударник: стойки, ладонь бури, летящий кулак, подсечка, дрожь ветра, выброс
 * энергии, апперкот, касание кармы, уклонение, стойка дзэн, режим асуры.
 *
 * <p>Сценарий — раздел 11 {@code docs/vfx/skill-visuals.md}. Облик — гроза:
 * жёлтые молнии и ветер. Моделей почти нет — ударник бьёт телом, поэтому
 * его сцены — ветер, разряды и призрачные кулаки.
 */
final class ScenesStriker {

    static final int STORM = 0xFFFFD23A;
    static final int SKY = 0xFFBFF6FF;
    private static final int PRESS = 0xFFFF5040;
    private static final int CALM = 0xFF60A8FF;

    private ScenesStriker() {
    }

    static FxScenes.Set scenes() {
        return new FxScenes.Set(BURSTS, Map.of(), Map.of(), BOLTS, Map.of(), STATUSES);
    }

    private static final Map<String, FxScenes.BurstScene> BURSTS = Map.ofEntries(
            Map.entry("striker_palm_release", ScenesStriker::palmRelease),
            Map.entry("striker_gale", ScenesStriker::gale),
            Map.entry("striker_thunder_tail", ScenesStriker::thunderTail),
            Map.entry("striker_fist_hit", ScenesStriker::fistHit),
            Map.entry("striker_sweep", ScenesStriker::sweep),
            Map.entry("striker_sweep_stun", ScenesStriker::sweepStun),
            Map.entry("striker_flurry_fist", ScenesStriker::flurryFist),
            Map.entry("striker_chi_hit", ScenesStriker::chiHit),
            Map.entry("striker_uppercut", ScenesStriker::uppercut),
            Map.entry("striker_karma_set", ScenesStriker::karmaSet),
            Map.entry("striker_karma_burst", ScenesStriker::karmaBurst),
            Map.entry("striker_phantom", ScenesStriker::phantom),
            Map.entry("striker_zen", ScenesStriker::zen),
            Map.entry("striker_asura", ScenesStriker::asura),
            Map.entry("striker_asura_echo", ScenesStriker::asuraEcho),
            Map.entry("striker_asura_pulse", ScenesStriker::asuraPulse));

    private static final Map<String, FxScenes.BoltScene> BOLTS = Map.ofEntries(
            Map.entry("striker_chi_orb", ScenesStriker::chiOrb));

    private static final Map<String, FxStatuses.Look> STATUSES = Map.ofEntries(
            Map.entry("stance_press", new Stance(PRESS, Stance.Shape.SIGIL)),
            Map.entry("stance_breach", new Stance(STORM, Stance.Shape.OPEN)),
            Map.entry("stance_calm", new Stance(CALM, Stance.Shape.EVEN)),
            Map.entry("karma_mark", new Karma()),
            Map.entry("meditation", new Meditation()),
            Map.entry("asura", new Asura()));

    // ------------------------------------------------------------------ молния

    /** Ломаная молнии между двумя точками: каждый кадр новая, как настоящий разряд. */
    static void bolt(FxDraw draw, double ax, double ay, double az, double bx, double by, double bz,
                     float width, int colour, float alpha, int seed) {
        int n = 6;
        double[] xs = new double[n];
        double[] ys = new double[n];
        double[] zs = new double[n];
        java.util.Random rnd = new java.util.Random(seed);
        double len = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
        double jag = Math.min(0.5, len * 0.15);
        for (int i = 0; i < n; i++) {
            double q = (double) i / (n - 1);
            double off = i == 0 || i == n - 1 ? 0 : jag;
            xs[i] = ax + (bx - ax) * q + (rnd.nextDouble() - 0.5) * off;
            ys[i] = ay + (by - ay) * q + (rnd.nextDouble() - 0.5) * off;
            zs[i] = az + (bz - az) * q + (rnd.nextDouble() - 0.5) * off;
        }
        draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, n, width * 2.5f, colour, alpha * 0.5f, alpha * 0.5f,
                0);
        draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, n, width, 0xFFFFFFFF, alpha, alpha, 0);
    }

    // ------------------------------------------------------------------ ладонь бури

    private static void palmRelease(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1.2, e.z(), 8, 0.12f, 0.2f, SKY, 6, FxDraw.Tex.ZAP);
    }

    /** Порыв: видимый конус ветра — ленты бегут наружу, по кромкам молнии. */
    private static void gale(FxMessage.Burst e) {
        SceneKit.cone(e, 3, 6, 8, 0.8f);
        double r = e.radius();
        double axis = Math.atan2(e.axisZ(), e.axisX());
        double half = Math.toRadians(e.angle()) / 2;
        live(e.classId(), e.x(), e.y(), e.z(), r + 1, 12, (self, draw, t, detail) -> {
            float k = self.progress(t);
            float alpha = 1f - k;
            int lanes = 6;
            for (int i = 0; i < lanes; i++) {
                double a = axis - half + 2 * half * (i + 0.5) / lanes;
                double head = Math.min(1, k * 2.2 + (i % 2) * 0.1);
                double tail = Math.max(0, head - 0.45);
                double y = e.y() + 0.7 + 0.5 * Math.sin(i * 1.3);
                draw.ribbon(FxDraw.Tex.BEAM,
                        new double[] {e.x() + Math.cos(a) * r * tail, e.x() + Math.cos(a) * r * head},
                        new double[] {y, y},
                        new double[] {e.z() + Math.sin(a) * r * tail, e.z() + Math.sin(a) * r * head},
                        2, 0.25f, SKY, 0f, 0.7f * alpha, 0);
            }
            for (int side = -1; side <= 1; side += 2) {
                double a = axis + half * side;
                bolt(draw, e.x(), e.y() + 1, e.z(), e.x() + Math.cos(a) * r, e.y() + 0.8,
                        e.z() + Math.sin(a) * r, 0.06f, STORM, 0.9f * alpha, (int) t + side);
            }
        });
    }

    // ------------------------------------------------------------------ летящий кулак

    /** Хвост молнии за кулаком: от старта до ударника, пока он летит. */
    private static void thunderTail(FxMessage.Burst e) {
        int id = e.source();
        double sx = e.x();
        double sy = e.y() + 1.0;
        double sz = e.z();
        live(e.classId(), sx, sy, sz, 10, 10, (self, draw, t, detail) -> {
            Entity striker = entity(id);
            if (striker == null) {
                return;
            }
            Vec3 at = SceneKit.chest(striker, t - (int) t);
            bolt(draw, sx, sy, sz, at.x, at.y, at.z, 0.08f, STORM, 1f - self.progress(t), (int) t);
        });
    }

    /** Удар кулака: громовой хлопок — вспышка, кольцо, лёгкая тряска рядом. */
    private static void fistHit(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y() + 1, e.z(), 1f, FxStyle.Kind.FLASH, STORM, SKY, 2, 2, 6, 1.2f,
                1.4f, FxDraw.Tex.ZAP);
        live(e.classId(), e.x(), e.y() + 1, e.z(), 3, 8, (self, draw, t, detail) -> {
            float k = self.progress(t);
            draw.halo(FxDraw.Tex.RING, e.x(), e.y() + 1, e.z(), 0.3 + 1.5 * k, 0.2,
                    Math.cos(SceneKit.yawFrom(e)), 0, Math.sin(SceneKit.yawFrom(e)), SKY,
                    0.8f * (1f - k), 0.5f, 0);
        });
        FxScreen.shakeIfInside(e.x(), e.y(), e.z(), 2, 0.4f, 5);
    }

    // ------------------------------------------------------------------ подсечка

    /** Подсечка: круговая дуга от ноги по земле до границы. */
    private static void sweep(FxMessage.Burst e) {
        SceneKit.border(e, 3, 4, 6, 0.6f);
        double r = e.radius();
        double start = SceneKit.RANDOM.nextDouble() * Math.PI * 2;
        live(e.classId(), e.x(), e.y(), e.z(), r + 1, 10, (self, draw, t, detail) -> {
            float k = self.progress(t);
            double head = Math.min(1, k * 2);
            double tail = Math.max(0, k * 1.6 - 0.5);
            if (head > tail) {
                draw.ring(FxDraw.Tex.BEAM, e.x(), e.y(), e.z(), r * 0.75, r * 0.5,
                        start + Math.PI * 2 * tail, start + Math.PI * 2 * head, SKY, 0.7f * (1f - k),
                        0.3f, 0, 0.06f);
            }
        });
        SceneKit.sparks(e.x(), e.y() + 0.2, e.z(), 14, 0.2f, 0.3f, 0xFFD8D0C0, 12, FxDraw.Tex.WISP);
    }

    private static void sweepStun(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 0.3, e.z(), 6, 0.08f, 0.2f, STORM, 8, FxDraw.Tex.ZAP);
    }

    // ------------------------------------------------------------------ дрожь ветра

    /** Призрачный кулак бьёт по цели: светящийся сгусток летит от ударника и вспыхивает. */
    private static void flurryFist(FxMessage.Burst e) {
        Entity striker = entity(e.source());
        double sx = striker != null ? striker.getX() : e.x();
        double sy = striker != null ? striker.getY() + 1.2 : e.y() + 1.2;
        double sz = striker != null ? striker.getZ() : e.z();
        double side = (SceneKit.RANDOM.nextDouble() - 0.5) * 0.8;
        live(e.classId(), e.x(), e.y(), e.z(), 6, 6, (self, draw, t, detail) -> {
            float k = Math.min(1f, self.progress(t) * 2f);
            double x = sx + (e.x() - sx) * k;
            double y = sy + (e.y() + 1.1 - sy) * k + side * (1 - k);
            double z = sz + (e.z() - sz) * k;
            draw.sprite(FxDraw.Tex.GLOW, x, y, z, 0.6f, 0, STORM, 0.8f * (1f - self.progress(t)));
            draw.sprite(FxDraw.Tex.ZAP, x, y, z, 0.4f, t, SKY, 0.9f * (1f - self.progress(t)));
        });
        SceneKit.sparks(e.x(), e.y() + 1.1, e.z(), 5, 0.12f, 0.14f, SKY, 6, FxDraw.Tex.SPARK);
    }

    // ------------------------------------------------------------------ выброс энергии

    /** Шар энергии: ядро класса и молнии, бьющие из него во все стороны. */
    private static void chiOrb(FxMessage.Projectile p, FxKinds.Bolt bolt) {
        live(p.classId(), p.x(), p.y(), p.z(), p.range() + 4,
                (int) (p.range() / Math.max(0.05f, p.speed())) + 80, (self, draw, t, detail) -> {
                    Vec3 at = bolt.at(t - (int) t);
                    if (at == null) {
                        self.dead = true;
                        return;
                    }
                    for (int i = 0; i < 3; i++) {
                        java.util.Random rnd = new java.util.Random((long) t * 7 + i);
                        bolt(draw, at.x, at.y, at.z, at.x + (rnd.nextDouble() - 0.5) * 1.2,
                                at.y + (rnd.nextDouble() - 0.5) * 1.2,
                                at.z + (rnd.nextDouble() - 0.5) * 1.2, 0.04f, STORM, 0.9f, (int) t + i);
                    }
                }).important = true;
    }

    /** Попадание шара: разряд; треснувший щит вспыхивает на цели. */
    private static void chiHit(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y() + 1, e.z(), 1f, FxStyle.Kind.FLASH, STORM, SKY, 2, 2, 7, 1.2f,
                1.2f, FxDraw.Tex.ZAP);
        live(e.classId(), e.x(), e.y() + 1.1, e.z(), 2, 10, (self, draw, t, detail) -> {
            float a = 1f - self.progress(t);
            draw.sprite(FxDraw.Tex.SHARD, e.x(), e.y() + 1.1, e.z(), 0.8f, 0.3f, SKY, a);
            draw.sprite(FxDraw.Tex.SHARD, e.x(), e.y() + 1.1, e.z(), 0.6f, -0.6f, 0xFFFFFFFF, a);
        });
    }

    // ------------------------------------------------------------------ апперкот

    /** Апперкот: снизу вверх поднимается вихрь-дракон из ветра. */
    private static void uppercut(FxMessage.Burst e) {
        double x = e.x();
        double z = e.z();
        double y0 = e.y();
        live(e.classId(), x, y0 + 2, z, 4, 14, (self, draw, t, detail) -> {
            float k = self.progress(t);
            int n = 16;
            double[] xs = new double[n];
            double[] ys = new double[n];
            double[] zs = new double[n];
            double head = Math.min(1, k * 2);
            for (int i = 0; i < n; i++) {
                double q = head * i / (n - 1);
                double a = q * Math.PI * 5 + t * 0.4;
                double r = 0.7 * (1 - q * 0.6);
                xs[i] = x + Math.cos(a) * r;
                ys[i] = y0 + q * 4;
                zs[i] = z + Math.sin(a) * r;
            }
            draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, n, 0.35f, SKY, 0.1f * (1f - k),
                    0.8f * (1f - k), -t * 0.3f);
            draw.sprite(FxDraw.Tex.GLOW, xs[n - 1], ys[n - 1], zs[n - 1], 0.8f, 0, STORM,
                    0.8f * (1f - k));
        });
    }

    // ------------------------------------------------------------------ карма

    private static void karmaSet(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y(), e.z(), 1f, FxStyle.Kind.SINK, 0xFFFFFFFF, 0xFF202020, 3, 6, 8,
                1f, 0, FxDraw.Tex.STAR);
    }

    /**
     * Касание кармы: над целью инь-ян — белый и чёрный огонь кружат друг за
     * другом, всё быстрее к моменту взрыва.
     */
    static final class Karma implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            double y = at.y + entity.getBbHeight() + 0.6;
            float left = state.total <= 0 ? 0f
                    : Math.clamp((float) FxStatuses.remaining(state) / state.total, 0f, 1f);
            double spin = time * (0.15 + 0.6 * (1 - left));
            draw.halo(FxDraw.Tex.RING, at.x, y, at.z, 0.36, 0.08, 0, 1, 0, 0xFF808080, 0.8f, 0.5f, 0);
            for (int i = 0; i < 2; i++) {
                double a = spin + Math.PI * i;
                int colour = i == 0 ? 0xFFFFFFFF : 0xFF181818;
                draw.sprite(FxDraw.Tex.GLOW, at.x + Math.cos(a) * 0.18, y, at.z + Math.sin(a) * 0.18,
                        0.42f, 0, colour, i == 0 ? 0.9f : 0.6f);
                draw.sprite(FxDraw.Tex.SPARK, at.x + Math.cos(a) * 0.18, y + 0.01,
                        at.z + Math.sin(a) * 0.18, 0.14f, 0, i == 0 ? 0xFF181818 : 0xFFFFFFFF, 0.9f);
            }
        }
    }

    /** Карма взорвалась: белая вспышка, у самой цели — белый экран. */
    private static void karmaBurst(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y() + 1, e.z(), 2f, FxStyle.Kind.WAVE, 0xFFFFFFFF, STORM, 3, 3, 8,
                2f, 0, FxDraw.Tex.STAR);
        live(e.classId(), e.x(), e.y() + 1, e.z(), 4, 8, (self, draw, t, detail) -> {
            float k = self.progress(t);
            draw.sprite(FxDraw.Tex.GLOW, e.x(), e.y() + 1, e.z(), 1.5f + 4f * k, 0, 0xFFFFFFFF,
                    1f - k);
        });
        if (SceneKit.selfNear(e.x(), e.y(), e.z(), 1.2)) {
            FxScreen.flash(0xFFFFFFFF, 10);
        }
        FxScreen.shakeIfInside(e.x(), e.y(), e.z(), 3, 0.6f, 8);
    }

    // ------------------------------------------------------------------ уклонение и дзэн

    /** Уклонение: на месте — призрачный силуэт, сквозь который проходят удары. */
    private static void phantom(FxMessage.Burst e) {
        Entity striker = entity(e.source());
        if (striker != null) {
            FxSolids.Ghost ghost = new FxSolids.Ghost(striker, 1, 6, 8);
            ghost.tint = SKY;
            ghost.alpha = 0.45f;
            FxSolids.add(ghost);
        }
        SceneKit.sparks(e.x(), e.y() + 0.4, e.z(), 10, 0.1f, 0.3f, 0xFFE8F0F8, 12, FxDraw.Tex.WISP);
    }

    private static void zen(FxMessage.Burst e) {
        SceneKit.rise(e.x(), e.y(), e.z(), 0.8, 14, CALM, FxDraw.Tex.LEAF);
    }

    /**
     * Стойка дзэн: вокруг ударника медленно вращается круг рун, листья
     * зависают в воздухе; у самого — мягкая белая дымка по краям.
     */
    static final class Meditation implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            draw.halo(FxDraw.Tex.RUNES, at.x, at.y + 0.9, at.z, 1.0, 0.35, 0, 1, 0, CALM, 0.6f, 1.2f,
                    time * 0.01f);
            for (int i = 0; i < 5; i++) {
                double a = i * 1.26 + 0.3;
                double y = at.y + 0.6 + (i % 3) * 0.5 + 0.03 * Math.sin(time * 0.05 + i);
                draw.sprite(FxDraw.Tex.LEAF, at.x + Math.cos(a) * 1.3, y, at.z + Math.sin(a) * 1.3,
                        0.35f, i, 0xFF9AD070, 0.8f);
            }
        }

        @Override
        public void screen(FxStatuses.State state, net.minecraft.client.gui.GuiGraphics g, int w,
                           int h, float time) {
            FxScreen.edges(g, w, h, 0xFFE8F0FF, 0.25f);
        }
    }

    // ------------------------------------------------------------------ стойки

    /**
     * Стойка под ногами: Напор — красный знак, Брешь — разомкнутое кольцо,
     * Покой — ровный синий круг. Смена — короткий всплеск.
     */
    static final class Stance implements FxStatuses.Look {
        enum Shape { SIGIL, OPEN, EVEN }

        private final int colour;
        private final Shape shape;

        Stance(int colour, Shape shape) {
            this.colour = colour;
            this.shape = shape;
        }

        @Override
        public void appear(FxStatuses.State state, Entity entity) {
            SceneKit.sparks(entity.getX(), entity.getY() + 0.2, entity.getZ(), 10, 0.12f, 0.18f,
                    colour, 8, FxDraw.Tex.SPARK);
        }

        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            switch (shape) {
                case SIGIL -> {
                    draw.circle(FxDraw.Tex.RING, at.x, at.y, at.z, 0.7, 0.1, colour, 0.7f, 0.5f, 0,
                            0.04f);
                    for (int i = 0; i < 3; i++) {
                        double a = time * 0.02 + Math.PI * 2 * i / 3;
                        draw.groundLine(FxDraw.Tex.BEAM, at.x, at.z, at.x + Math.cos(a) * 0.65,
                                at.z + Math.sin(a) * 0.65, at.y, 0.12, colour, 0.7f, 0.045f);
                    }
                }
                case OPEN -> draw.ring(FxDraw.Tex.RING, at.x, at.y, at.z, 0.7, 0.12, time * 0.03,
                        time * 0.03 + Math.PI * 1.5, colour, 0.75f, 0.5f, 0, 0.04f);
                case EVEN -> draw.circle(FxDraw.Tex.RING, at.x, at.y, at.z, 0.7, 0.14, colour, 0.7f,
                        0.5f, 0, 0.04f);
            }
        }
    }

    // ------------------------------------------------------------------ асура

    private static void asura(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y(), e.z(), 1.6f, FxStyle.Kind.RISE, STORM, 0xFFFFE8A0, 4, 10, 12,
                1.6f, 0, FxDraw.Tex.ZAP);
        if (SceneKit.isSelf(e.source())) {
            FxScreen.flash(STORM, 14);
        }
    }

    /**
     * Режим асуры: за спиной две призрачные пары рук — светящиеся дуги от плеч,
     * повторяющие удары; у самого — золотистая рамка.
     */
    static final class Asura implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            double look = Math.toRadians(entity.getYRot());
            double fx = -Math.sin(look);
            double fz = Math.cos(look);
            double sx = -fz;
            double sz = fx;
            double shoulder = at.y + entity.getBbHeight() * 0.75;
            for (int pair = 0; pair < 2; pair++) {
                for (int side = -1; side <= 1; side += 2) {
                    double swing = Math.sin(time * 0.25 + pair * 1.5 + side) * 0.3;
                    double bx = at.x - fx * 0.3 + sx * 0.25 * side;
                    double bz = at.z - fz * 0.3 + sz * 0.25 * side;
                    double ex = bx + sx * (0.7 + pair * 0.2) * side + fx * swing;
                    double ez = bz + sz * (0.7 + pair * 0.2) * side + fz * swing;
                    double ey = shoulder + 0.2 - pair * 0.45;
                    draw.ribbon(FxDraw.Tex.BEAM, new double[] {bx, (bx + ex) / 2, ex},
                            new double[] {shoulder, shoulder + 0.25 - pair * 0.2, ey},
                            new double[] {bz, (bz + ez) / 2, ez}, 3, 0.18f, STORM, 0.2f, 0.7f, 0);
                    draw.sprite(FxDraw.Tex.GLOW, ex, ey, ez, 0.35f, 0, 0xFFFFE8A0, 0.8f);
                }
            }
        }

        @Override
        public void screen(FxStatuses.State state, net.minecraft.client.gui.GuiGraphics g, int w,
                           int h, float time) {
            FxScreen.edges(g, w, h, STORM, 0.2f);
        }
    }

    /** Всплеск асуры по ближнему: золотое кольцо у цели. */
    private static void asuraEcho(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y() + 0.1, e.z(), 1.2f, FxStyle.Kind.WAVE, STORM, 0xFFFFE8A0, 3,
                2, 5, 0.8f, 0, FxDraw.Tex.ZAP);
    }

    private static void asuraPulse(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1.2, e.z(), 4, 0.06f, 0.16f, STORM, 8, FxDraw.Tex.ZAP);
    }

    /** Удар в режиме асуры: призрачная рука повторяет его — эхо-вспышка у цели. */
    static void onHit(FxMessage.Hit hit) {
        if (hit.attacker() == 0 || !FxStatuses.has(hit.attacker(), "asura")) {
            return;
        }
        Entity target = entity(hit.entityId());
        if (target != null) {
            SceneKit.sparks(target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    6, 0.15f, 0.2f, 0xFFFFE8A0, 6, FxDraw.Tex.ZAP);
        }
    }
}
