package ru.projectst.rpgcore.client;

import static ru.projectst.rpgcore.client.SceneKit.entity;
import static ru.projectst.rpgcore.client.SceneKit.live;

import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Ловкач: раскол, подмена, ножевой шквал, пыль, карманник, нестабильные
 * копии, ложная смерть, перетасовка, кукловод, карнавал.
 *
 * <p>Сценарий — раздел 8 {@code docs/vfx/skill-visuals.md}. Облик — иллюзия:
 * переливчатые звёзды и конфетти. Карты — листы бумаги, монета — золотой
 * самородок, нити кукловода — нить растяжки; двойники — сами существа
 * (ванильные досаждатели), им сцена даёт облако звёзд при появлении.
 */
final class ScenesTrickster {

    static final int ILLUSION = 0xFFC86BFF;
    static final int SHIMMER = 0xFF7AF0FF;
    /** Конфетти: цвета карнавала. */
    private static final int[] CONFETTI = {0xFFFF5A7A, 0xFFFFD84A, 0xFF5AE0FF, 0xFF9AFF6A,
            0xFFC86BFF, 0xFFFFFFFF};

    private ScenesTrickster() {
    }

    static FxScenes.Set scenes() {
        return new FxScenes.Set(BURSTS, MARKS, Map.of(), Map.of(), Map.of(), STATUSES);
    }

    private static final Map<String, FxScenes.BurstScene> BURSTS = Map.ofEntries(
            Map.entry("trickster_split", ScenesTrickster::split),
            Map.entry("trickster_swap_out", ScenesTrickster::swapOut),
            Map.entry("trickster_swap", ScenesTrickster::swap),
            Map.entry("trickster_knife_ring", ScenesRogue::knifeFan),
            Map.entry("trickster_knife_hit", ScenesRogue::knifeHit),
            Map.entry("trickster_dust_cloud", ScenesTrickster::dustCloud),
            Map.entry("trickster_dust_eyes", ScenesTrickster::dustEyes),
            Map.entry("trickster_coin", ScenesTrickster::coin),
            Map.entry("trickster_confetti_blast", ScenesTrickster::confettiBlast),
            Map.entry("trickster_corpse_pop", ScenesTrickster::confettiBlast),
            Map.entry("trickster_feign", ScenesTrickster::feign),
            Map.entry("trickster_shuffle_flip", ScenesTrickster::shuffleFlip),
            Map.entry("trickster_card_flip", ScenesTrickster::cardFlip),
            Map.entry("trickster_puppet_strings", ScenesTrickster::puppetStrings),
            Map.entry("trickster_puppet_tug", ScenesTrickster::puppetTug),
            Map.entry("trickster_puppet_bind", ScenesTrickster::puppetTug),
            Map.entry("trickster_carnival", ScenesTrickster::carnival),
            Map.entry("trickster_carnival_tick", ScenesTrickster::carnivalTick));

    private static final Map<String, FxScenes.MarkScene> MARKS = Map.ofEntries(
            Map.entry("trickster_shuffle_mark", ScenesTrickster::shuffleMark));

    private static final Map<String, FxStatuses.Look> STATUSES = Map.ofEntries(
            Map.entry("dust_cover", new DustCover()),
            Map.entry("stolen_power", new StolenPower()),
            Map.entry("feigned", new ScenesRogue.Unseen(0xFF6A5A7A)),
            Map.entry("carnival", new Carnival()));

    // ------------------------------------------------------------------ общее

    /** Хлопок конфетти: разноцветные искры и звёзды во все стороны. */
    static void confetti(double x, double y, double z, int base, float speed) {
        FxMotes motes = FxEffects.motes();
        int n = (int) (base * Math.max(0.3f, FxEffects.emit()));
        for (int i = 0; i < n; i++) {
            int colour = CONFETTI[i % CONFETTI.length];
            motes.spawn(x, y, z, FxMotes.jitter(speed), FxMotes.jitter(speed) + speed * 0.4f,
                    FxMotes.jitter(speed), 0.16f, colour, 20 + (int) (FxMotes.random() * 12), 0.9f,
                    i % 3 == 0 ? FxDraw.Tex.STAR : FxDraw.Tex.SHARD);
        }
    }

    /** Карта: лист бумаги стоймя переворачивается над точкой. */
    private static void card(double x, double y, double z, int life) {
        FxSolids.Model card = SceneKit.item(Items.PAPER, x, y, z, 0.8f, 2, life, 4);
        card.yawSpeed = 360f / Math.max(4, life);
        FxSolids.add(card);
    }

    // ------------------------------------------------------------------ раскол

    /** Раскол: ловкач дробится в облаке звёзд, полупрозрачные копии разлетаются. */
    private static void split(FxMessage.Burst e) {
        Entity trickster = entity(e.source());
        if (trickster != null) {
            for (int i = -1; i <= 1; i += 2) {
                FxSolids.Ghost copy = new FxSolids.Ghost(trickster, 1, 2, 8);
                double side = SceneKit.facing(trickster) + Math.PI / 2;
                copy.vx = Math.cos(side) * 0.18 * i;
                copy.vz = Math.sin(side) * 0.18 * i;
                copy.tint = i < 0 ? ILLUSION : SHIMMER;
                copy.alpha = 0.5f;
                FxSolids.add(copy);
            }
        }
        SceneKit.sparks(e.x(), e.y() + 1, e.z(), 24, 0.18f, 0.22f, SHIMMER, 14, FxDraw.Tex.STAR);
        confetti(e.x(), e.y() + 1.2, e.z(), 14, 0.15f);
    }

    // ------------------------------------------------------------------ подмена

    /** Ловкач начинает подмену: карта у него в руке. */
    private static void swapOut(FxMessage.Burst e) {
        card(e.x(), e.y() + 1.1, e.z(), 8);
    }

    /** Подмена: лента звёзд между ловкачом и тем, с кем поменялся; обе карты переворачиваются. */
    private static void swap(FxMessage.Burst e) {
        Entity trickster = entity(e.source());
        card(e.x(), e.y() + 1.1, e.z(), 8);
        if (trickster == null) {
            return;
        }
        double fx = trickster.getX();
        double fy = trickster.getY() + 1.0;
        double fz = trickster.getZ();
        card(fx, fy + 0.1, fz, 8);
        live(e.classId(), e.x(), e.y(), e.z(), 16, 14, (self, draw, t, detail) -> {
            float fade = 1f - self.progress(t);
            int n = 9;
            double[] xs = new double[n];
            double[] ys = new double[n];
            double[] zs = new double[n];
            for (int i = 0; i < n; i++) {
                double q = (double) i / (n - 1);
                xs[i] = fx + (e.x() - fx) * q;
                ys[i] = fy + (e.y() + 1.0 - fy) * q + Math.sin(q * Math.PI) * 0.8;
                zs[i] = fz + (e.z() - fz) * q;
            }
            draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, n, 0.3f, ILLUSION, 0.7f * fade, 0.7f * fade,
                    t * 0.3f);
            for (int i = 1; i < n - 1; i += 2) {
                draw.sprite(FxDraw.Tex.STAR, xs[i], ys[i], zs[i], 0.3f, t * 0.2f + i, SHIMMER,
                        0.9f * fade);
            }
        });
    }

    // ------------------------------------------------------------------ пыль

    /** Облако блестящей пыли распускается до границы. */
    private static void dustCloud(FxMessage.Burst e) {
        SceneKit.border(e, 5, 8, 10, 0.6f);
        double r = e.radius();
        FxMotes motes = FxEffects.motes();
        int n = (int) Math.min(120, r * r * 3 * Math.max(0.3f, FxEffects.emit()));
        for (int i = 0; i < n; i++) {
            double[] p = FxGeometry.insideCircle(SceneKit.RANDOM, e.x(), e.y(), e.z(), r);
            motes.spawn(e.x(), e.y() + 1.0, e.z(), (float) (p[0] - e.x()) / 14,
                    0.01f + FxMotes.random() * 0.02f, (float) (p[2] - e.z()) / 14, 0.18f,
                    i % 4 == 0 ? 0xFFFFFFFF : (i % 2 == 0 ? SHIMMER : 0xFFFFE8A0), 30, 0.94f,
                    FxDraw.Tex.SPARK);
        }
    }

    /** Ослеплённый: блёстки в глазах; если это я — слепящие блики по экрану. */
    private static void dustEyes(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1.6, e.z(), 8, 0.05f, 0.14f, 0xFFFFFFFF, 12,
                FxDraw.Tex.SPARK);
        if (SceneKit.selfNear(e.x(), e.y(), e.z(), 0.8)) {
            FxScreen.flash(0xFFFFF4D0, 16);
        }
    }

    /** Пыль на самом ловкаче: мерцание по контуру. */
    static final class DustCover implements FxStatuses.Look {
        @Override
        public void ambient(FxStatuses.State state, Entity entity, float emit) {
            if (FxMotes.random() < 0.5f * emit) {
                FxEffects.motes().spawn(entity.getX() + FxMotes.jitter(0.45f),
                        entity.getY() + FxMotes.random() * entity.getBbHeight(),
                        entity.getZ() + FxMotes.jitter(0.45f), 0, 0.005f, 0, 0.12f,
                        FxMotes.random() < 0.5f ? 0xFFFFFFFF : SHIMMER, 8, 0.95f,
                        FxDraw.Tex.SPARK);
            }
        }
    }

    // ------------------------------------------------------------------ карманник

    /** Карманник: светящаяся монета летит от цели к ловкачу. */
    private static void coin(FxMessage.Burst e) {
        Entity trickster = entity(e.source());
        if (trickster == null) {
            return;
        }
        int id = trickster.getId();
        double fx = e.x();
        double fy = e.y() + 1.2;
        double fz = e.z();
        FxSolids.Model coin = SceneKit.item(Items.GOLD_NUGGET, fx, fy, fz, 0.5f, 1, 12, 2);
        coin.yawSpeed = 30;
        coin.anchor = new java.util.function.Supplier<>() {
            private int age;

            @Override
            public Vec3 get() {
                Entity t = entity(id);
                if (t == null) {
                    return null;
                }
                float k = Math.min(1f, ++age / 12f);
                Vec3 to = SceneKit.chest(t, 1f);
                return new Vec3(fx + (to.x - fx) * k, fy + (to.y - fy) * k + Math.sin(k * Math.PI),
                        fz + (to.z - fz) * k);
            }
        };
        FxSolids.add(coin);
        SceneKit.sparks(fx, fy, fz, 8, 0.1f, 0.14f, 0xFFFFD84A, 10, FxDraw.Tex.SPARK);
    }

    /** Украденная сила: золотые блики кружат вокруг ловкача. */
    static final class StolenPower implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            for (int i = 0; i < 3; i++) {
                double a = time * 0.15 + Math.PI * 2 * i / 3;
                draw.sprite(FxDraw.Tex.SPARK, at.x + Math.cos(a) * 0.6,
                        at.y + entity.getBbHeight() * 0.7, at.z + Math.sin(a) * 0.6, 0.25f, time * 0.2f,
                        0xFFFFD84A, 0.8f);
            }
        }
    }

    // ------------------------------------------------------------------ копии и труп

    /** Копия лопается: фейерверк конфетти. */
    private static void confettiBlast(FxMessage.Burst e) {
        confetti(e.x(), e.y() + 1, e.z(), 36, 0.3f);
        SceneKit.styleAt(e, e.x(), e.y() + 1, e.z(), 1.2f, FxStyle.Kind.FLASH, ILLUSION, SHIMMER, 2,
                2, 8, 1f, 1.6f, FxDraw.Tex.STAR);
        FxScreen.shakeIfInside(e.x(), e.y(), e.z(), 2.5, 0.35f, 5);
    }

    /**
     * Ложная смерть: на месте ловкача лежит его серый отпечаток с крестиками
     * вместо глаз; сам он невидим и уходит.
     */
    private static void feign(FxMessage.Burst e) {
        Entity trickster = entity(e.source());
        if (trickster == null) {
            return;
        }
        int id = trickster.getId();
        FxSolids.Ghost corpse = new FxSolids.Ghost(trickster, 4, 0, 10);
        corpse.lying = true;
        corpse.tint = 0xFFA0A0A8;
        corpse.alpha = 0.85f;
        corpse.holding = () -> FxStatuses.has(id, "feigned");
        corpse.decor = false;
        FxSolids.add(corpse);
        double yaw = Math.toRadians(trickster.getYRot());
        double headX = e.x() + Math.cos(yaw) * 1.4;
        double headZ = e.z() + Math.sin(yaw) * 1.4;
        double headY = e.y() + 0.3;
        SceneKit.Live eyes = live(e.classId(), e.x(), e.y(), e.z(), 3, 8,
                (self, draw, t, detail) -> {
                    float fade = 1f - self.progress(t);
                    double d = 0.1;
                    for (int i = -1; i <= 1; i += 2) {
                        double ex = headX - Math.sin(yaw) * 0.12 * i;
                        double ez = headZ + Math.cos(yaw) * 0.12 * i;
                        draw.ribbon(FxDraw.Tex.BEAM, new double[] {ex - d, ex + d},
                                new double[] {headY + 0.25 + d, headY + 0.25 - d},
                                new double[] {ez, ez}, 2, 0.05f, 0xFF202020, fade, fade, 0);
                        draw.ribbon(FxDraw.Tex.BEAM, new double[] {ex + d, ex - d},
                                new double[] {headY + 0.25 + d, headY + 0.25 - d},
                                new double[] {ez, ez}, 2, 0.05f, 0xFF202020, fade, fade, 0);
                    }
                });
        eyes.holding = () -> FxStatuses.has(id, "feigned");
        SceneKit.sparks(e.x(), e.y() + 0.5, e.z(), 14, 0.08f, 0.4f, 0xFFD0D0D8, 16, FxDraw.Tex.WISP);
    }

    // ------------------------------------------------------------------ перетасовка

    /**
     * Перетасовка: граница поля — круг из карт, стоящих ребром; заливка
     * нарастает к удару.
     */
    private static void shuffleMark(FxMessage.Telegraph e) {
        SceneKit.mark(e, SceneKit.MarkDecor.PLAIN);
        double r = e.radius();
        int n = Math.min(24, Math.max(8, (int) (r * 2.5)));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double x = e.x() + Math.cos(a) * r;
            double z = e.z() + Math.sin(a) * r;
            FxSolids.Model card = SceneKit.item(Items.PAPER, x, SceneKit.ground(x, z, e.y()) + 0.45,
                    z, 0.7f, 4, Math.max(1, e.ticks() - 4), 4);
            card.at((float) -Math.toDegrees(a) + 90, 0, 0);
            card.decor = false;
            FxSolids.add(card);
        }
    }

    /** Карты вспыхивают над полем. */
    private static void shuffleFlip(FxMessage.Burst e) {
        SceneKit.border(e, 3, 6, 8, 0.8f);
        double r = e.radius();
        for (int i = 0; i < 6; i++) {
            double[] p = FxGeometry.insideCircle(SceneKit.RANDOM, e.x(), e.y(), e.z(), r);
            card(p[0], e.y() + 2.2, p[2], 10);
        }
    }

    /** Враг накрыт картой: она переворачивается над ним на новом месте. */
    private static void cardFlip(FxMessage.Burst e) {
        card(e.x(), e.y() + 2.3, e.z(), 10);
        SceneKit.sparks(e.x(), e.y() + 1, e.z(), 10, 0.12f, 0.2f, ILLUSION, 10, FxDraw.Tex.STAR);
    }

    // ------------------------------------------------------------------ кукловод

    /** Кукловод: граница и звёзды под небом, откуда тянутся нити. */
    private static void puppetStrings(FxMessage.Burst e) {
        SceneKit.border(e, 5, 10, 8, 0.6f);
        SceneKit.sparks(e.x(), e.y() + 5, e.z(), 16, 0.2f, 0.2f, SHIMMER, 16, FxDraw.Tex.STAR);
    }

    /** Нити сверху к рукам цели: держатся две секунды и дёргаются. */
    private static void puppetTug(FxMessage.Burst e) {
        Entity target = nearestLiving(e.x(), e.y(), e.z());
        if (target == null) {
            return;
        }
        int id = target.getId();
        double topX = e.x();
        double topY = e.y() + 5.5;
        double topZ = e.z();
        for (int side = -1; side <= 1; side += 2) {
            int s = side;
            FxSolids.Strip thread = new FxSolids.Strip(partial -> {
                Entity t = entity(id);
                if (t == null) {
                    return null;
                }
                Vec3 at = t.getPosition(partial);
                double yaw = Math.toRadians(t.getYRot());
                double hx = at.x + Math.cos(yaw) * 0.38 * s;
                double hz = at.z + Math.sin(yaw) * 0.38 * s;
                double jerk = 0.15 * Math.sin((t.tickCount + partial) * 0.6 + s);
                return new double[][] {{topX + 0.3 * s, topY, topZ},
                        {hx, at.y + t.getBbHeight() * 0.55 + jerk, hz}};
            }, "block/tripwire", 0.06, 0.5, 0xFFE0D0FF, 6, 34, 6);
            FxSolids.add(thread);
        }
    }

    private static Entity nearestLiving(double x, double y, double z) {
        var level = SceneKit.level();
        if (level == null) {
            return null;
        }
        Entity best = null;
        double bestD = 4;
        for (Entity entity : level.getEntities((Entity) null,
                new net.minecraft.world.phys.AABB(x - 2, y - 2, z - 2, x + 2, y + 3, z + 2),
                c -> c instanceof net.minecraft.world.entity.LivingEntity)) {
            double d = entity.distanceToSqr(x, y, z);
            if (d < bestD) {
                bestD = d;
                best = entity;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ карнавал

    /** Карнавал: фейерверк конфетти вокруг ловкача. */
    private static void carnival(FxMessage.Burst e) {
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            confetti(e.x() + Math.cos(a) * 1.5, e.y() + 1.5 + FxMotes.random(),
                    e.z() + Math.sin(a) * 1.5, 14, 0.2f);
        }
        if (SceneKit.isSelf(e.source())) {
            FxScreen.flash(0xFFFF8AE0, 12);
        }
    }

    private static void carnivalTick(FxMessage.Burst e) {
        confetti(e.x(), e.y() + 1.6, e.z(), 6, 0.1f);
    }

    /**
     * Карнавал: над ловкачом висит гирлянда огоньков разных цветов, у него
     * самого — лёгкая радужная виньетка.
     */
    static final class Carnival implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            int n = 10;
            double r = 1.3;
            double y = at.y + entity.getBbHeight() + 0.7;
            for (int i = 0; i < n; i++) {
                double a = time * 0.03 + Math.PI * 2 * i / n;
                double sag = 0.15 * Math.abs(Math.sin(a * n / 2.0));
                float blink = 0.6f + 0.4f * (float) Math.sin(time * 0.3 + i * 1.7);
                draw.sprite(FxDraw.Tex.GLOW, at.x + Math.cos(a) * r, y - sag, at.z + Math.sin(a) * r,
                        0.35f, 0, CONFETTI[i % CONFETTI.length], blink);
            }
            draw.halo(FxDraw.Tex.BEAM, at.x, y - 0.08, at.z, r, 0.03, 0, 1, 0, 0xFF404040, 0.4f, 0.5f,
                    0);
        }

        @Override
        public void screen(FxStatuses.State state, net.minecraft.client.gui.GuiGraphics g, int w,
                           int h, float time) {
            int colour = CONFETTI[(int) (time / 10) % 5];
            FxScreen.edges(g, w, h, colour, 0.16f);
        }
    }
}
