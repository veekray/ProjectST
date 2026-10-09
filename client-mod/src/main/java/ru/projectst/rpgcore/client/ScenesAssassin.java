package ru.projectst.rpgcore.client;

import static ru.projectst.rpgcore.client.SceneKit.entity;
import static ru.projectst.rpgcore.client.SceneKit.live;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Убийца: клинок из ниоткуда, клеймо жнеца, растворение, артерия, удавка,
 * трупный яд, сход в тень, ответный клинок, каскад, час палача, казнь.
 *
 * <p>Сценарий — раздел 7 {@code docs/vfx/skill-visuals.md}. Облик — тень:
 * фиолетовая дымка, кровавый блик. Клинки — незеритовые мечи, клеймо —
 * незеритовая мотыга-коса, удавка — нить растяжки.
 */
final class ScenesAssassin {

    static final int SHADE = 0xFF7B3BD6;
    static final int BLOOD = 0xFFFF4D7A;
    private static final int DARK = 0xFF1A0A24;
    private static final int VENOM = 0xFF7FD957;

    /** Где был прошлый прыжок каскада у каждого убийцы: лента тени тянется оттуда. */
    private static final Map<Integer, double[]> CASCADE = new HashMap<>();

    private ScenesAssassin() {
    }

    static FxScenes.Set scenes() {
        return new FxScenes.Set(BURSTS, Map.of(), Map.of(), Map.of(), Map.of(), STATUSES);
    }

    private static final Map<String, FxScenes.BurstScene> BURSTS = Map.ofEntries(
            Map.entry("assassin_shade_step", ScenesAssassin::shadeStep),
            Map.entry("assassin_dark_arc", ScenesAssassin::darkArc),
            Map.entry("assassin_blade_fan", ScenesAssassin::bladeFan),
            Map.entry("assassin_brand_burn", ScenesAssassin::brandBurn),
            Map.entry("assassin_brand_burst", ScenesAssassin::brandBurst),
            Map.entry("assassin_dissolve", ScenesAssassin::dissolve),
            Map.entry("assassin_blood_cut", ScenesAssassin::bloodCut),
            Map.entry("assassin_bleed_drip", ScenesAssassin::bleedDrip),
            Map.entry("assassin_garrote", ScenesAssassin::garrote),
            Map.entry("assassin_venom_coat", ScenesAssassin::venomCoat),
            Map.entry("assassin_venom_splash", ScenesAssassin::venomSplash),
            Map.entry("assassin_venom_tick", ScenesAssassin::venomTick),
            Map.entry("assassin_shadow_ripple", ScenesAssassin::shadowRipple),
            Map.entry("assassin_riposte_ready", ScenesAssassin::riposteReady),
            Map.entry("assassin_riposte_strike", ScenesAssassin::riposteStrike),
            Map.entry("assassin_cascade_hit", ScenesAssassin::cascadeHit),
            Map.entry("assassin_hour_begin", ScenesAssassin::hourBegin),
            Map.entry("assassin_guillotine", ScenesAssassin::guillotine),
            Map.entry("assassin_shadow_ready", ScenesAssassin::shadowReady));

    private static final Map<String, FxStatuses.Look> STATUSES = Map.ofEntries(
            Map.entry("reaper_brand", new ReaperBrand()),
            Map.entry("veil", new ScenesRogue.Unseen(0xFF3A2A4A)),
            Map.entry("executioner", new Executioner()),
            Map.entry("shadow", new Shadow()),
            Map.entry("venom_coat", new VenomCoat()),
            Map.entry("venom", new Venom()),
            Map.entry("riposte_window", new Riposte()),
            Map.entry("cascade_hit", new CascadeHit()));

    // ------------------------------------------------------------------ клинок из ниоткуда

    /** Уход в тень: клуб тьмы там, где стоял убийца, и у спины цели. */
    private static void shadeStep(FxMessage.Burst e) {
        Entity killer = entity(e.source());
        if (killer != null) {
            puff(killer.getX(), killer.getY(), killer.getZ());
        }
        puff(e.x(), e.y(), e.z());
    }

    private static void puff(double x, double y, double z) {
        SceneKit.sparks(x, y + 0.9, z, 16, 0.08f, 0.5f, DARK, 16, FxDraw.Tex.WISP);
        SceneKit.sparks(x, y + 0.9, z, 6, 0.1f, 0.3f, SHADE, 12, FxDraw.Tex.WISP);
    }

    /** Удар — тёмная дуга поперёк цели. */
    private static void darkArc(FxMessage.Burst e) {
        SceneKit.cut(e.classId(), e.x(), e.y() + 1.1, e.z(), SceneKit.yawFrom(e), 0.35, 2.0, SHADE,
                9, 0.45f);
        SceneKit.sparks(e.x(), e.y() + 1.1, e.z(), 6, 0.12f, 0.18f, BLOOD, 8, FxDraw.Tex.SPARK);
    }

    /** Со Тенью: веер из трёх тёмных лезвий по конусу. */
    private static void bladeFan(FxMessage.Burst e) {
        SceneKit.cone(e, 3, 6, 8, 0.8f);
        double axis = Math.atan2(e.axisZ(), e.axisX());
        double half = Math.toRadians(e.angle()) / 2;
        double y = e.y() + 1.0;
        for (int i = -1; i <= 1; i++) {
            double a = axis + half * 0.7 * i;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            FxSolids.Model blade = SceneKit.item(Items.NETHERITE_SWORD, e.x() + dx * 0.6, y,
                    e.z() + dz * 0.6, 0.7f, 1, 4, 6);
            blade.face(dx, 0, dz);
            blade.at(blade.yaw, blade.pitch + 90, 45);
            blade.vx = dx * e.radius() / 8;
            blade.vz = dz * e.radius() / 8;
            blade.drag = 0.9;
            FxSolids.add(blade);
        }
    }

    // ------------------------------------------------------------------ клеймо

    private static void brandBurn(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y(), e.z(), 1.2f, FxStyle.Kind.SINK, SHADE, BLOOD, 4, 8, 12,
                1f, 0, FxDraw.Tex.WISP);
    }

    /** Клеймо жнеца: знак косы над целью тлеет красным. */
    static final class ReaperBrand implements FxStatuses.Look {
        @Override
        public void appear(FxStatuses.State state, Entity entity) {
            if (!state.solids.isEmpty()) {
                return;
            }
            int id = state.entity;
            FxSolids.Model scythe = SceneKit.item(Items.NETHERITE_HOE, entity.getX(), entity.getY(),
                    entity.getZ(), 0.75f, 8, 0, 4);
            scythe.follow = id;
            scythe.faceFollow = false;
            scythe.offsetY = entity.getBbHeight() + 0.6;
            scythe.at(0, 0, 30);
            scythe.yawSpeed = 2.5f;
            scythe.holding = () -> FxStatuses.has(id, "reaper_brand");
            scythe.decor = false;
            state.solids.add(scythe);
            FxSolids.add(scythe);
        }

        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            float smoulder = 0.45f + 0.2f * (float) Math.sin(time * 0.25);
            draw.sprite(FxDraw.Tex.GLOW, at.x, at.y + entity.getBbHeight() + 0.6, at.z, 1.0f, 0,
                    BLOOD, smoulder);
            draw.sprite(FxDraw.Tex.GLOW, at.x, at.y + entity.getBbHeight() * 0.62, at.z, 0.6f, 0,
                    BLOOD, smoulder * 0.6f);
        }
    }

    /** Клеймо сорвано: знак лопается брызгами тени — тем шире, чем крупнее цель. */
    private static void brandBurst(FxMessage.Burst e) {
        Entity target = nearest(e.x(), e.y(), e.z());
        float size = target != null ? (float) Math.max(1, target.getBbHeight()) : 1f;
        SceneKit.styleAt(e, e.x(), e.y(), e.z(), 1.2f * size, FxStyle.Kind.WAVE, SHADE, BLOOD, 3, 3,
                8, 1.5f * size, 0, FxDraw.Tex.WISP);
        SceneKit.sparks(e.x(), e.y() + size, e.z(), (int) (24 * size), 0.3f * size, 0.4f, DARK, 14,
                FxDraw.Tex.WISP);
        SceneKit.sparks(e.x(), e.y() + size, e.z(), 12, 0.25f, 0.2f, BLOOD, 10, FxDraw.Tex.SPARK);
    }

    private static Entity nearest(double x, double y, double z) {
        var level = SceneKit.level();
        if (level == null) {
            return null;
        }
        Entity best = null;
        double bestD = 4;
        for (Entity entity : level.getEntities((Entity) null,
                new net.minecraft.world.phys.AABB(x - 2, y - 2, z - 2, x + 2, y + 3, z + 2))) {
            double d = entity.distanceToSqr(x, y, z);
            if (d < bestD) {
                bestD = d;
                best = entity;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ растворение

    /**
     * Убийца рассыпается в дым, на месте — силуэт из тени, который покачивается
     * и исчезает клубом.
     */
    private static void dissolve(FxMessage.Burst e) {
        Entity killer = entity(e.source());
        if (killer != null) {
            FxSolids.Ghost shade = new FxSolids.Ghost(killer, 2, 30, 10);
            shade.tint = 0xFF2A1A3A;
            shade.alpha = 0.7f;
            shade.yawSpeed = 0.6f;
            FxSolids.add(shade);
        }
        puff(e.x(), e.y(), e.z());
    }

    // ------------------------------------------------------------------ кровь

    /** Короткий кровавый росчерк по цели. */
    private static void bloodCut(FxMessage.Burst e) {
        SceneKit.cut(e.classId(), e.x(), e.y() + 1.1, e.z(), SceneKit.yawFrom(e), -0.9, 1.3, BLOOD,
                7, 0.3f);
        drops(e.x(), e.y() + 1.1, e.z(), 8);
    }

    /** Капли крови падают и оставляют пятно на земле. */
    static void drops(double x, double y, double z, int n) {
        FxMotes motes = FxEffects.motes();
        int count = (int) (n * Math.max(0.3f, FxEffects.emit()));
        for (int i = 0; i < count; i++) {
            motes.spawn(x + FxMotes.jitter(0.2f), y, z + FxMotes.jitter(0.2f), FxMotes.jitter(0.03f),
                    0.02f, FxMotes.jitter(0.03f), 0.1f, 0xFFB01020, 14, 0.97f, FxDraw.Tex.GLOW);
        }
        double gy = SceneKit.ground(x, z, y);
        live("assassin", x, gy, z, 1.5, 60, (self, draw, t, detail) -> {
            float fade = 1f - self.progress(t);
            draw.sector(FxDraw.Tex.GLOW, x, gy, z, 0.35, 0, Math.PI * 2, 0xFF6A0810, 0.7f * fade,
                    0.02f);
        });
    }

    private static void bleedDrip(FxMessage.Burst e) {
        drops(e.x(), e.y() + 1, e.z(), 4);
    }

    // ------------------------------------------------------------------ удавка

    /**
     * Удавка: вокруг шеи затягивается тёмная нить (нить растяжки), к убийце —
     * натянутый конец; держится, пока на цели {@code silence}.
     */
    private static void garrote(FxMessage.Burst e) {
        Entity target = nearest(e.x(), e.y(), e.z());
        int killerId = e.source();
        if (target == null) {
            return;
        }
        int id = target.getId();
        FxSolids.Strip wire = new FxSolids.Strip(partial -> {
            Entity t = entity(id);
            if (t == null) {
                return null;
            }
            Vec3 at = t.getPosition(partial);
            double neck = at.y + t.getBbHeight() * 0.82;
            double r = Math.max(0.2, t.getBbWidth() * 0.45);
            int n = 13;
            Entity k = entity(killerId);
            double[][] loop = new double[n + (k != null ? 1 : 0)][];
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / (n - 1);
                loop[i] = new double[] {at.x + Math.cos(a) * r, neck, at.z + Math.sin(a) * r};
            }
            if (k != null) {
                Vec3 hand = k.getPosition(partial);
                loop[n] = new double[] {hand.x, hand.y + k.getBbHeight() * 0.6, hand.z};
            }
            return loop;
        }, "block/tripwire", 0.08, 0.5, 0xFF4A3A5A, 3, 0, 4);
        wire.holding = () -> FxStatuses.has(id, "silence");
        wire.decor = false;
        FxSolids.add(wire);
        SceneKit.sparks(e.x(), e.y() + 1.5, e.z(), 8, 0.08f, 0.16f, SHADE, 8, FxDraw.Tex.SPARK);
    }

    // ------------------------------------------------------------------ трупный яд

    private static void venomCoat(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1.0, e.z(), 14, 0.08f, 0.2f, VENOM, 12, FxDraw.Tex.GLOW);
    }

    /** Клинки в зелёной слизи: капли падают с рук, по заряду на удар — реже. */
    static final class VenomCoat implements FxStatuses.Look {
        @Override
        public void ambient(FxStatuses.State state, Entity entity, float emit) {
            if (FxMotes.random() > 0.12f * emit * Math.max(1, state.stacks)) {
                return;
            }
            double yaw = Math.toRadians(entity.getYRot());
            double side = FxMotes.random() < 0.5f ? 0.4 : -0.4;
            FxEffects.motes().spawn(entity.getX() + Math.cos(yaw) * side,
                    entity.getY() + entity.getBbHeight() * 0.45, entity.getZ() + Math.sin(yaw) * side,
                    0, -0.05f, 0, 0.12f, VENOM, 14, 0.98f, FxDraw.Tex.GLOW);
        }
    }

    private static void venomSplash(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1.0, e.z(), 10, 0.15f, 0.18f, VENOM, 10, FxDraw.Tex.GLOW);
    }

    private static void venomTick(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1.2, e.z(), 4, 0.04f, 0.16f, VENOM, 12, FxDraw.Tex.GLOW);
    }

    /** Трупный яд: зелёные пузыри поднимаются с плеч, кожа будто тлеет. */
    static final class Venom implements FxStatuses.Look {
        @Override
        public void ambient(FxStatuses.State state, Entity entity, float emit) {
            if (FxMotes.random() > 0.35f * emit) {
                return;
            }
            FxEffects.motes().spawn(entity.getX() + FxMotes.jitter(0.35f),
                    entity.getY() + entity.getBbHeight() * (0.6 + FxMotes.random() * 0.3),
                    entity.getZ() + FxMotes.jitter(0.35f), FxMotes.jitter(0.004f), 0.025f,
                    FxMotes.jitter(0.004f), 0.14f, VENOM, 20, 0.97f, FxDraw.Tex.GLOW);
        }
    }

    // ------------------------------------------------------------------ сход в тень

    /** Тёмная рябь во все стороны, у самого убийцы мир на секунду уходит в серое. */
    private static void shadowRipple(FxMessage.Burst e) {
        live(e.classId(), e.x(), e.y(), e.z(), 8, 18, (self, draw, t, detail) -> {
            float k = self.progress(t);
            for (int i = 0; i < 3; i++) {
                float q = Math.clamp(k * 1.4f - i * 0.15f, 0f, 1f);
                if (q <= 0) {
                    continue;
                }
                draw.circle(FxDraw.Tex.RING, e.x(), e.y(), e.z(), 0.5 + 6 * q, 0.4, SHADE,
                        0.7f * (1f - q), 0.5f, 0, 0.05f);
            }
        });
        puff(e.x(), e.y(), e.z());
        if (SceneKit.isSelf(e.source())) {
            FxScreen.flash(0xFF707070, 20);
        }
    }

    // ------------------------------------------------------------------ ответный клинок

    private static void riposteReady(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1.2, e.z(), 6, 0.06f, 0.14f, 0xFFE0E0F0, 6,
                FxDraw.Tex.SPARK);
    }

    /** Окно ответа: тёмное лезвие висит перед грудью остриём вперёд. */
    static final class Riposte implements FxStatuses.Look {
        @Override
        public void appear(FxStatuses.State state, Entity entity) {
            if (!state.solids.isEmpty()) {
                return;
            }
            int id = state.entity;
            FxSolids.Model blade = SceneKit.item(Items.NETHERITE_SWORD, entity.getX(), entity.getY(),
                    entity.getZ(), 0.6f, 3, 0, 3);
            blade.follow = id;
            blade.offsetY = entity.getBbHeight() * 0.6;
            blade.forward = 0.55;
            blade.at(0, 90, 45);
            blade.holding = () -> FxStatuses.has(id, "riposte_window");
            blade.decor = false;
            state.solids.add(blade);
            FxSolids.add(blade);
        }
    }

    /** Ответ: лезвие бьёт в ударившего — летит от убийцы и вспыхивает. */
    private static void riposteStrike(FxMessage.Burst e) {
        Entity killer = entity(e.source());
        double sx = killer != null ? killer.getX() : e.x();
        double sy = killer != null ? killer.getY() + 1.1 : e.y() + 1.1;
        double sz = killer != null ? killer.getZ() : e.z();
        double dx = e.x() - sx;
        double dy = e.y() + 1.1 - sy;
        double dz = e.z() - sz;
        FxSolids.Model blade = SceneKit.item(Items.NETHERITE_SWORD, sx, sy, sz, 0.6f, 1, 4, 3);
        blade.face(dx, dy, dz);
        blade.at(blade.yaw, blade.pitch + 90, 45);
        blade.vx = dx / 4;
        blade.vy = dy / 4;
        blade.vz = dz / 4;
        blade.drag = 1;
        FxSolids.add(blade);
        SceneKit.cut(e.classId(), e.x(), e.y() + 1.1, e.z(), Math.atan2(dz, dx), 0.2, 1.6, SHADE, 8,
                0.4f);
    }

    // ------------------------------------------------------------------ каскад

    /**
     * Прыжок каскада: тёмная вспышка у цели и лента тени от прошлой точки к
     * новой.
     */
    private static void cascadeHit(FxMessage.Burst e) {
        int id = e.source();
        double[] last = CASCADE.get(id);
        long now = FxStatuses.now();
        double[] here = {e.x(), e.y() + 1.0, e.z(), now};
        if (last != null && now - (long) last[3] < 40) {
            live(e.classId(), e.x(), e.y(), e.z(), 16, 14, (self, draw, t, detail) -> {
                float fade = 1f - self.progress(t);
                draw.ribbon(FxDraw.Tex.BEAM, new double[] {last[0], here[0]},
                        new double[] {last[1], here[1]}, new double[] {last[2], here[2]}, 2, 0.6f,
                        SHADE, 0.2f * fade, 0.8f * fade, t * 0.3f);
            });
        }
        CASCADE.put(id, here);
        SceneKit.styleAt(e, e.x(), e.y() + 1, e.z(), 0.9f, FxStyle.Kind.FLASH, SHADE, BLOOD, 2, 2, 7,
                1f, 1f, FxDraw.Tex.WISP);
        SceneKit.cut(e.classId(), e.x(), e.y() + 1.1, e.z(), SceneKit.yawFrom(e),
                SceneKit.RANDOM.nextDouble() - 0.5, 1.6, SHADE, 8, 0.35f);
    }

    /** Уже задетые каскадом — тусклая метка: дважды он не бьёт. */
    static final class CascadeHit implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            draw.halo(FxDraw.Tex.RING, at.x, at.y + entity.getBbHeight() + 0.3, at.z, 0.3, 0.08, 0,
                    1, 0, SHADE, 0.45f, 0.5f, 0);
        }
    }

    // ------------------------------------------------------------------ тень и час палача

    private static void shadowReady(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1.0, e.z(), 8, 0.05f, 0.3f, SHADE, 12, FxDraw.Tex.WISP);
    }

    /** Тень готова: тёмный шлейф за руками. */
    static final class Shadow implements FxStatuses.Look {
        @Override
        public void ambient(FxStatuses.State state, Entity entity, float emit) {
            if (FxMotes.random() > 0.25f * emit) {
                return;
            }
            double yaw = Math.toRadians(entity.getYRot());
            double side = FxMotes.random() < 0.5f ? 0.4 : -0.4;
            FxEffects.motes().spawn(entity.getX() + Math.cos(yaw) * side,
                    entity.getY() + entity.getBbHeight() * 0.45, entity.getZ() + Math.sin(yaw) * side,
                    FxMotes.jitter(0.01f), 0.01f, FxMotes.jitter(0.01f), 0.22f, DARK, 14, 0.95f,
                    FxDraw.Tex.WISP);
        }
    }

    /** Час палача начался: тёмный ореол поднимается кольцом. */
    private static void hourBegin(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y(), e.z(), 1.5f, FxStyle.Kind.RISE, SHADE, BLOOD, 4, 10, 12,
                1.5f, 0, FxDraw.Tex.WISP);
        if (SceneKit.isSelf(e.source())) {
            FxScreen.flash(BLOOD, 12);
        }
    }

    /**
     * Час палача: сам убийца видит у себя тёмный ореол и красные глаза, мир
     * по краям темнее и краснее. Со стороны его не видно: он невидим.
     */
    static final class Executioner implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            if (!SceneKit.isSelf(state.entity)) {
                return;
            }
            double y = at.y + entity.getBbHeight() * 0.55;
            draw.halo(FxDraw.Tex.BEAM, at.x, y, at.z, 0.7, 0.3, 0, 1, 0, DARK, 0.6f, 0.5f,
                    time * 0.1f);
            draw.sprite(FxDraw.Tex.GLOW, at.x, at.y + entity.getEyeHeight(), at.z, 0.4f, 0, BLOOD,
                    0.7f);
        }

        @Override
        public void screen(FxStatuses.State state, net.minecraft.client.gui.GuiGraphics g, int w,
                           int h, float time) {
            FxScreen.edges(g, w, h, 0xFF40060E, 0.45f);
        }
    }

    /** Казнь: над добиваемым падает тёмный клинок-гильотина. */
    private static void guillotine(FxMessage.Burst e) {
        double top = e.y() + 4;
        FxSolids.Model blade = SceneKit.item(Items.NETHERITE_SWORD, e.x(), top, e.z(), 1.6f, 1, 5, 6);
        blade.at(0, 0, -135);
        blade.vy = -(top - (e.y() + 1.2)) / 4;
        blade.drag = 1;
        FxSolids.add(blade);
        SceneKit.Live stop = live(e.classId(), e.x(), e.y(), e.z(), 5, 5, (self, draw, t, detail) -> {
            float k = self.progress(t);
            draw.ribbon(FxDraw.Tex.BEAM, new double[] {e.x(), e.x()},
                    new double[] {top - 4 * k + 0.5, top}, new double[] {e.z(), e.z()}, 2, 0.4f,
                    SHADE, 0.6f * (1f - k), 0f, 0);
        });
        stop.step = (self, level, motes, emit) -> {
            if (self.age == 4) {
                blade.vy = 0;
                SceneKit.sparks(e.x(), e.y() + 1.2, e.z(), 20, 0.3f, 0.22f, BLOOD, 10,
                        FxDraw.Tex.SPARK);
                FxScreen.shakeIfInside(e.x(), e.y(), e.z(), 2.5, 0.5f, 6);
            }
        };
    }
}
