package ru.projectst.rpgcore.client;

import static ru.projectst.rpgcore.client.SceneKit.entity;
import static ru.projectst.rpgcore.client.SceneKit.live;

import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Маг: печати, разряд, петля, сгон, россыпь, шаг в пустоту, коллапс.
 *
 * <p>Сценарий — раздел 4 {@code docs/vfx/skill-visuals.md}. Облик — аркана:
 * синие и фиолетовые звёзды, руны, аметист. Печать на земле рисует стиль
 * {@code mage_seal} (зона с рунами), здесь — то, что вокруг неё.
 */
final class ScenesMage {

    private static final int ARCANE = 0xFF7A86FF;
    private static final int ARCANE_LIGHT = 0xFFC9B8FF;
    private static final int VOID = 0xFFB15CFF;
    private static final int VOID_LIGHT = 0xFFF0C8FF;

    private ScenesMage() {
    }

    static FxScenes.Set scenes() {
        return new FxScenes.Set(BURSTS, Map.of(), Map.of(), BOLTS, TRAILS, Map.of());
    }

    private static final Map<String, FxScenes.BurstScene> BURSTS = Map.ofEntries(
            Map.entry("mage_flow_wave", e -> flowWave(e, false)),
            Map.entry("mage_flow_wave_strong", e -> flowWave(e, true)),
            Map.entry("mage_herd_cone", e -> herdFunnel(e, false)),
            Map.entry("mage_herd_cone_strong", e -> herdFunnel(e, true)),
            Map.entry("mage_bolt_hit", e -> boltHit(e, false)),
            Map.entry("mage_bolt_hit_strong", e -> boltHit(e, true)),
            Map.entry("mage_collapse_field", ScenesMage::collapseField),
            Map.entry("mage_collapse_blast", ScenesMage::collapseBlast));

    private static final Map<String, FxScenes.BoltScene> BOLTS = Map.ofEntries(
            Map.entry("mage_bolt", (p, bolt) -> manaBolt(p, bolt, false)),
            Map.entry("mage_bolt_strong", (p, bolt) -> manaBolt(p, bolt, true)),
            Map.entry("mage_scatter_shard", ScenesMage::scatterShard));

    private static final Map<String, FxScenes.TrailScene> TRAILS = Map.ofEntries(
            Map.entry("mage_void_trail", e -> voidStep(e, false)),
            Map.entry("mage_void_trail_strong", e -> voidStep(e, true)));

    // ------------------------------------------------------------------ разряд

    /** Сгусток маны: три руны кружат вокруг ядра, усиленный — крупнее и фиолетовый. */
    private static void manaBolt(FxMessage.Projectile p, FxKinds.Bolt bolt, boolean strong) {
        int colour = strong ? VOID_LIGHT : ARCANE_LIGHT;
        double orbit = strong ? 0.45 : 0.32;
        float size = strong ? 0.32f : 0.24f;
        live(p.classId(), p.x(), p.y(), p.z(), p.range() + 4, (int) (p.range() / p.speed()) + 80,
                (self, draw, t, detail) -> {
                    Vec3 at = bolt.at(t - (int) t);
                    if (at == null) {
                        self.dead = true;
                        return;
                    }
                    Vec3 h = bolt.heading();
                    // Руны вращаются в плоскости поперёк полёта.
                    Vec3 side = h.cross(new Vec3(0, 1, 0));
                    if (side.lengthSqr() < 1e-6) {
                        side = new Vec3(1, 0, 0);
                    }
                    side = side.normalize();
                    Vec3 up = side.cross(h).normalize();
                    for (int i = 0; i < 3; i++) {
                        double a = t * 0.5 + Math.PI * 2 * i / 3;
                        Vec3 o = side.scale(Math.cos(a) * orbit).add(up.scale(Math.sin(a) * orbit));
                        draw.sprite(FxDraw.Tex.STAR, at.x + o.x, at.y + o.y, at.z + o.z, size,
                                t * 0.2f, colour, 0.95f);
                    }
                    draw.halo(FxDraw.Tex.RUNES, at.x, at.y, at.z, orbit, 0.18, h.x, h.y, h.z,
                            strong ? VOID : ARCANE, 0.6f, 1.5f, t * 0.05f);
                }).important = true;
        Vec3 h = new Vec3(p.dx(), p.dy(), p.dz());
        // Вспышка у руки в миг выстрела.
        SceneKit.sparks(p.x() + h.x * 0.4, p.y() + h.y * 0.4, p.z() + h.z * 0.4, 10, 0.12f, 0.2f,
                strong ? VOID_LIGHT : ARCANE_LIGHT, 6, FxDraw.Tex.SPARK);
    }

    /** Попадание: звёздный всплеск; усиленный оставляет на цели разрыв пустоты. */
    private static void boltHit(FxMessage.Burst e, boolean strong) {
        SceneKit.styled(e, FxStyle.Kind.WAVE);
        SceneKit.sparks(e.x(), e.y() + 1, e.z(), strong ? 22 : 14, 0.2f, 0.22f,
                strong ? VOID_LIGHT : ARCANE_LIGHT, 10, FxDraw.Tex.STAR);
        if (!strong) {
            return;
        }
        // Разрыв: вертикальная щель пустоты, расходится и затягивается.
        live(e.classId(), e.x(), e.y() + 1, e.z(), 2, 16, (self, draw, t, detail) -> {
            float k = self.progress(t);
            float open = (float) Math.sin(Math.min(1f, k * 1.4f) * Math.PI);
            double half = 0.9 * open;
            double[] xs = {e.x(), e.x(), e.x()};
            double[] ys = {e.y() + 1 - half, e.y() + 1, e.y() + 1 + half};
            double[] zs = {e.z(), e.z(), e.z()};
            draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, 3, 0.5f * open, VOID, 0.9f, 0.9f, t * 0.1f);
            draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, 3, 0.16f * open, 0xFF1A0830, 1f, 1f, 0);
        });
    }

    // ------------------------------------------------------------------ россыпь

    /** Осколок аметиста летит, кувыркаясь; где упал — встанет печать (её ставит сервер). */
    private static void scatterShard(FxMessage.Projectile p, FxKinds.Bolt bolt) {
        bolt.bare = true;
        FxSolids.Model shard = SceneKit.item(Items.AMETHYST_SHARD, p.x(), p.y(), p.z(), 0.55f, 1,
                0, 4);
        shard.anchor = () -> bolt.at(1f);
        shard.face(p.dx(), p.dy(), p.dz());
        shard.rollSpeed = 35;
        shard.decor = false;
        FxSolids.add(shard);
        bolt.onEnd = end -> {
            SceneKit.sparks(end.x(), end.y() + 0.2, end.z(), 10, 0.1f, 0.18f, ARCANE_LIGHT, 8,
                    FxDraw.Tex.SHARD);
        };
    }

    // ------------------------------------------------------------------ петля

    /** Кольцо маны до границы; с печатью — двойное, и печать у ног раскалывается. */
    private static void flowWave(FxMessage.Burst e, boolean strong) {
        SceneKit.styled(e, FxStyle.Kind.WAVE);
        double r = e.radius();
        int colour = strong ? VOID_LIGHT : ARCANE_LIGHT;
        // Кольцо в воздухе на высоте груди догоняет границу на земле.
        live(e.classId(), e.x(), e.y(), e.z(), r + 1, 14, (self, draw, t, detail) -> {
            float k = self.progress(t);
            double rr = Math.max(0.3, r * FxGeometry.easeOut(Math.min(1f, k * 1.6f)));
            float a = 1f - k;
            draw.halo(FxDraw.Tex.RUNES, e.x(), e.y() + 1.0, e.z(), rr, 0.35, 0, 1, 0, colour,
                    0.7f * a, 1.2f, t * 0.05f);
            if (strong) {
                double r2 = Math.max(0.3, r * FxGeometry.easeOut(Math.min(1f, k * 1.3f)));
                draw.halo(FxDraw.Tex.RING, e.x(), e.y() + 0.5, e.z(), r2, 0.3, 0, 1, 0, VOID,
                        0.8f * a, 0.5f, 0);
            }
        });
        if (strong) {
            SceneKit.sparks(e.x(), e.y() + 0.1, e.z(), 20, 0.25f, 0.24f, VOID_LIGHT, 10,
                    FxDraw.Tex.SHARD);
            if (SceneKit.selfNear(e.x(), e.y(), e.z(), 1.5)) {
                FxScreen.shake(0.35f, 6);
            }
        }
    }

    // ------------------------------------------------------------------ сгон

    /**
     * Воронка: от дуги конуса к его вершине текут светящиеся ленты — туда, куда
     * тянет. Сектор ровно по углу и радиусу выборки рисует стиль.
     */
    private static void herdFunnel(FxMessage.Burst e, boolean strong) {
        SceneKit.styled(e, FxStyle.Kind.CONE);
        double r = e.radius();
        double axis = Math.atan2(e.axisZ(), e.axisX());
        double half = Math.toRadians(e.angle()) / 2;
        int lanes = strong ? 9 : 6;
        int colour = strong ? VOID_LIGHT : ARCANE_LIGHT;
        live(e.classId(), e.x(), e.y(), e.z(), r + 1, 18, (self, draw, t, detail) -> {
            float k = self.progress(t);
            float alpha = k < 0.7f ? 1f : 1f - (k - 0.7f) / 0.3f;
            for (int i = 0; i < lanes; i++) {
                double a = axis - half + 2 * half * (i + 0.5) / lanes;
                double y = e.y() + 0.6 + 0.4 * Math.sin(i * 1.7);
                // Лента от края к вершине: голова бежит к магу, хвост гаснет.
                double head = Math.max(0, 1 - (k * 1.6 + i * 0.05) % 1.0);
                double tail = Math.min(1, head + 0.35);
                double[] xs = {e.x() + Math.cos(a) * r * tail, e.x() + Math.cos(a) * r * head};
                double[] ys = {y, y};
                double[] zs = {e.z() + Math.sin(a) * r * tail, e.z() + Math.sin(a) * r * head};
                draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, 2, 0.22f, colour, 0f, 0.8f * alpha,
                        t * 0.2f);
            }
        });
    }

    /**
     * Рунные оковы вместо корней: статус {@code root} от мага — кольца рун
     * вокруг голеней, держатся, пока висит статус.
     */
    static void runeShackles(FxStatuses.State state, Entity entity) {
        int id = state.entity;
        SceneKit.Live shackle = live("mage", entity.getX(), entity.getY(), entity.getZ(), 3, 6,
                (self, draw, t, detail) -> {
                    Entity target = entity(id);
                    if (target == null) {
                        return;
                    }
                    Vec3 at = target.position();
                    float fade = 1f - self.progress(t);
                    float grow = Math.min(1f, t / 5f);
                    for (int i = 0; i < 2; i++) {
                        double y = at.y + 0.2 + i * 0.35;
                        double tilt = 0.25 * Math.sin(t * 0.1 + i * 2);
                        draw.halo(FxDraw.Tex.RUNES, at.x, y, at.z, 0.45 * grow, 0.16, tilt, 1,
                                0.1, i == 0 ? VOID : ARCANE, 0.8f * fade, 2f, t * 0.04f * (i * 2 - 1));
                    }
                    draw.circle(FxDraw.Tex.RING, at.x, at.y, at.z, 0.6, 0.12, VOID, 0.6f * fade,
                            0.5f, 0, 0.05f);
                });
        shackle.holding = () -> FxStatuses.has(id, "root");
        shackle.important = true;
        SceneKit.sound("mage.herd.chains", entity.getX(), entity.getY(), entity.getZ(), 0.8f, 1.2f);
    }

    // ------------------------------------------------------------------ шаг в пустоту

    /**
     * Шаг: в точке старта маг рассыпается звёздами (его же полупрозрачный
     * отпечаток), лента пустоты до прибытия, у себя — растяжение по краям экрана.
     */
    private static void voidStep(FxMessage.Trail e, boolean strong) {
        FxEffects.addEffect(new FxKinds.Trail(FxStyle.of(e.fx(), FxStyle.Kind.TRAIL), e));
        Entity mage = nearestPlayer(e.toX(), e.toY(), e.toZ());
        if (mage != null) {
            FxSolids.Ghost ghost = new FxSolids.Ghost(mage, 1, 2, 10);
            ghost.x = ghost.prevX = e.fromX();
            ghost.y = ghost.prevY = e.fromY();
            ghost.z = ghost.prevZ = e.fromZ();
            ghost.tint = strong ? VOID : ARCANE;
            ghost.alpha = 0.5f;
            ghost.vy = 0.03;
            FxSolids.add(ghost);
            if (SceneKit.isSelf(mage.getId())) {
                FxScreen.flash(strong ? VOID : ARCANE, 8);
            }
        }
        FxMotes motes = FxEffects.motes();
        int n = (int) (30 * Math.max(0.3f, FxEffects.emit()));
        for (int i = 0; i < n; i++) {
            motes.spawn(e.fromX() + FxMotes.jitter(0.4f), e.fromY() + FxMotes.random() * 1.9,
                    e.fromZ() + FxMotes.jitter(0.4f), FxMotes.jitter(0.04f), 0.02f,
                    FxMotes.jitter(0.04f), 0.2f, i % 2 == 0 ? VOID_LIGHT : ARCANE_LIGHT, 18, 0.93f,
                    FxDraw.Tex.STAR);
        }
    }

    /** Кто прибыл в точку: шаг — свой, ближайший игрок в полблоке. */
    private static Entity nearestPlayer(double x, double y, double z) {
        var level = SceneKit.level();
        if (level == null) {
            return null;
        }
        Entity best = null;
        double bestD = 2.5;
        for (var player : level.players()) {
            double d = player.distanceToSqr(x, y, z);
            if (d < bestD) {
                bestD = d;
                best = player;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ коллапс

    /**
     * Сбор: над точкой собирается сфера из печатей — кольца рун в разных
     * плоскостях сжимаются и пульсируют секунду; граница удара на земле видна
     * всё это время (стиль {@code mage_collapse_field}).
     */
    private static void collapseField(FxMessage.Burst e) {
        SceneKit.styled(e, FxStyle.Kind.TELEGRAPH);
        double r = e.radius();
        live(e.classId(), e.x(), e.y() + 2, e.z(), r + 3, 22, (self, draw, t, detail) -> {
            float k = self.progress(t);
            double size = 1.8 - 0.9 * k + 0.12 * Math.sin(t * (0.6 + k));
            double cy = e.y() + 2.2;
            int rings = detail.full() ? 6 : 3;
            for (int i = 0; i < rings; i++) {
                double a = Math.PI * i / rings + t * 0.04;
                draw.halo(FxDraw.Tex.RUNES, e.x(), cy, e.z(), size, 0.3, Math.cos(a), 0.4,
                        Math.sin(a), i % 2 == 0 ? VOID : ARCANE, 0.75f, 1.5f, t * 0.05f);
            }
            draw.sprite(FxDraw.Tex.GLOW, e.x(), cy, e.z(), (float) size * 2.2f, 0, VOID, 0.5f + 0.4f * k);
            draw.sprite(FxDraw.Tex.SPARK, e.x(), cy, e.z(), (float) size * 1.4f, t * 0.1f,
                    VOID_LIGHT, 0.8f);
            // Нити от земли к сфере: она тянет.
            for (int i = 0; i < 5; i++) {
                double a = Math.PI * 2 * i / 5 + t * 0.03;
                double gx = e.x() + Math.cos(a) * r * 0.8;
                double gz = e.z() + Math.sin(a) * r * 0.8;
                draw.ribbon(FxDraw.Tex.BEAM, new double[] {gx, e.x()},
                        new double[] {SceneKit.ground(gx, gz, e.y()) + 0.1, cy},
                        new double[] {gz, e.z()}, 2, 0.12f, ARCANE_LIGHT, 0.1f, 0.6f, -t * 0.2f);
            }
        }).important = true;
        if (SceneKit.selfNear(e.x(), e.y(), e.z(), r)) {
            FxScreen.flash(VOID, 20);
        }
    }

    /** Схлопывание: волна до границы, вспышка, тряска у тех, кто внутри. */
    private static void collapseBlast(FxMessage.Burst e) {
        SceneKit.styled(e, FxStyle.Kind.WAVE);
        SceneKit.sparks(e.x(), e.y() + 1.5, e.z(), 40, 0.4f, 0.3f, VOID_LIGHT, 14, FxDraw.Tex.STAR);
        live(e.classId(), e.x(), e.y() + 2, e.z(), e.radius() + 2, 10, (self, draw, t, detail) -> {
            float k = self.progress(t);
            draw.sprite(FxDraw.Tex.GLOW, e.x(), e.y() + 2, e.z(), 2f + 6f * k, 0, VOID_LIGHT,
                    1f - k);
        });
        FxScreen.shakeIfInside(e.x(), e.y(), e.z(), e.radius(), 0.9f, 10);
        if (SceneKit.selfNear(e.x(), e.y(), e.z(), e.radius())) {
            FxScreen.flash(0xFFFFFFFF, 6);
        }
    }
}
