package ru.projectst.rpgcore.client;

import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Общее: рывок, предметы, мобы и состояния, которые вешают навыки разных
 * классов (щит, ослабление, кровь, тишина).
 *
 * <p>Облик берётся по классу наложившего ({@link SceneKit#classOf}): щит
 * колдуна — кружащие души, щит рыцаря — золотая сфера.
 */
final class ScenesCommon {

    private ScenesCommon() {
    }

    static FxScenes.Set scenes() {
        return new FxScenes.Set(BURSTS, Map.of(), Map.of(), BOLTS, Map.of(), STATUSES);
    }

    private static final int GOLD = 0xFFE8C25A;
    private static final int GOLD_LIGHT = 0xFFFFF2C0;

    private static final Map<String, FxScenes.BurstScene> BURSTS = Map.ofEntries(
            Map.entry("dash_wind", ScenesCommon::dashWind),
            Map.entry("item_arcane_hit", ScenesCommon::arcaneHit),
            Map.entry("item_shadow_nick", ScenesCommon::shadowNick),
            Map.entry("item_soul_drain", ScenesCommon::soulDrain),
            Map.entry("mob_sand_burst", ScenesCommon::sandBurst),
            Map.entry("mob_venom_sting", ScenesCommon::venomSting));

    private static final Map<String, FxScenes.BoltScene> BOLTS = Map.ofEntries(
            Map.entry("item_arcane_orb", ScenesCommon::arcaneOrb));

    // ------------------------------------------------------------------ рывок

    /** Рывок: короткий след-ветер из светлой дымки по ходу, у себя — растяжение по краям. */
    private static void dashWind(FxMessage.Burst e) {
        int id = e.source();
        Entity who = SceneKit.entity(id);
        if (who == null) {
            return;
        }
        SceneKit.live("", who.getX(), who.getY(), who.getZ(), 12, 6, (self, draw, t, detail) -> {
        }).step = (self, level, motes, emit) -> {
            Entity now = level.getEntity(id);
            if (now != null && emit > 0) {
                for (int i = 0; i < 3; i++) {
                    motes.spawn(now.getX() + FxMotes.jitter(0.3f), now.getY() + 0.3 + FxMotes.random(),
                            now.getZ() + FxMotes.jitter(0.3f), 0, 0.01f, 0, 0.3f, 0xFFF0EAD8, 12, 0.9f,
                            FxDraw.Tex.WISP);
                }
            }
        };
        if (SceneKit.isSelf(id)) {
            FxScreen.flash(0xFFF0EAD8, 6);
        }
    }

    // ------------------------------------------------------------------ предметы

    /** Импульс арканы: золотой сгусток с орбитой рун. */
    private static void arcaneOrb(FxMessage.Projectile p, FxKinds.Bolt bolt) {
        SceneKit.live(p.classId(), p.x(), p.y(), p.z(), p.range() + 4,
                (int) (p.range() / Math.max(0.05f, p.speed())) + 80, (self, draw, t, detail) -> {
                    Vec3 at = bolt.at(t - (int) t);
                    if (at == null) {
                        self.dead = true;
                        return;
                    }
                    Vec3 h = bolt.heading();
                    draw.halo(FxDraw.Tex.RUNES, at.x, at.y, at.z, 0.3, 0.16, h.x, h.y, h.z, GOLD, 0.7f,
                            1.5f, t * 0.05f);
                }).important = true;
    }

    private static void arcaneHit(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1, e.z(), 12, 0.18f, 0.2f, GOLD_LIGHT, 10, FxDraw.Tex.SPARK);
    }

    /** Укол из тени: короткий тёмный росчерк по цели. */
    private static void shadowNick(FxMessage.Burst e) {
        SceneKit.cut(e.classId(), e.x(), e.y() + 1.1, e.z(), SceneKit.yawFrom(e), 0.6, 1.0, 0xFF3A2050,
                6, 0.25f);
    }

    /** Вытяжка: из проклятого к носителю тянется золотая струйка. */
    private static void soulDrain(FxMessage.Burst e) {
        int source = e.source();
        double fx = e.x();
        double fy = e.y() + 1.1;
        double fz = e.z();
        SceneKit.live(e.classId(), fx, fy, fz, 24, 14, (self, draw, t, detail) -> {
            Entity to = SceneKit.entity(source);
            if (to == null) {
                return;
            }
            Vec3 b = SceneKit.chest(to, t - (int) t);
            float a = 1f - self.progress(t);
            draw.ribbon(FxDraw.Tex.BEAM, new double[] {fx, (fx + b.x) / 2, b.x},
                    new double[] {fy, (fy + b.y) / 2 + 0.5, b.y}, new double[] {fz, (fz + b.z) / 2, b.z},
                    3, 0.25f, GOLD, 0.7f * a, 0.3f * a, -t * 0.3f);
        });
    }

    // ------------------------------------------------------------------ мобы

    /** Песчаный выброс: из-под моба кольцо песка до границы, песок оседает. */
    private static void sandBurst(FxMessage.Burst e) {
        SceneKit.border(e, 5, 6, 10, 0.6f);
        double r = e.radius();
        int n = Math.min(16, Math.max(6, (int) (r * 3)));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            FxSolids.Model grain = new FxSolids.Model(Blocks.SAND.defaultBlockState(), e.x(), e.y() + 0.2,
                    e.z(), 0.22f, 1, 10, 6);
            grain.vx = Math.cos(a) * r / 12;
            grain.vz = Math.sin(a) * r / 12;
            grain.vy = 0.2;
            grain.gravity = 0.04;
            grain.tumble(FxMotes.jitter(20f));
            FxSolids.add(grain);
        }
        FxMotes motes = FxEffects.motes();
        int dust = (int) (r * r * 2 * Math.max(0.3f, FxEffects.emit()));
        for (int i = 0; i < dust; i++) {
            double[] p = FxGeometry.insideCircle(SceneKit.RANDOM, e.x(), e.y(), e.z(), r);
            motes.spawn(p[0], e.y() + 0.2 + FxMotes.random(), p[2], 0, -0.01f, 0, 0.4f, 0xFFE0D0A0, 24,
                    0.97f, FxDraw.Tex.WISP);
        }
    }

    /** Жало: зелёный укол, пузыри яда на цели. */
    private static void venomSting(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1, e.z(), 10, 0.12f, 0.16f, 0xFF7FD957, 12, FxDraw.Tex.GLOW);
    }

    private static final Map<String, FxStatuses.Look> STATUSES = Map.ofEntries(
            Map.entry("shield", new Shield()),
            Map.entry("weakened", new Weakened()),
            Map.entry("bleed", new Bleed()),
            Map.entry("berserk_bleed", new Bleed()),
            Map.entry("silence", new Silence()));

    /**
     * Щит: тонкая сфера вокруг — три кольца в разных плоскостях медленно
     * вращаются. Щит колдуна — ещё и души по сфере.
     */
    static final class Shield implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            String owner = SceneKit.classOf(state.source);
            boolean souls = owner.equals("warlock");
            int colour = souls ? ScenesWarlock.SOUL : SceneKit.primary(owner);
            double r = Math.max(0.7, entity.getBbHeight() * 0.55);
            double cy = at.y + entity.getBbHeight() * 0.5;
            for (int i = 0; i < 3; i++) {
                double a = time * 0.02 + Math.PI * i / 3;
                draw.halo(FxDraw.Tex.RING, at.x, cy, at.z, r, 0.06, Math.cos(a), 0.6,
                        Math.sin(a), colour, 0.35f, 0.5f, 0);
            }
            draw.sprite(FxDraw.Tex.GLOW, at.x, cy, at.z, (float) r * 2.4f, 0, colour, 0.12f);
            if (souls) {
                for (int i = 0; i < 4; i++) {
                    double a = time * 0.1 + Math.PI * 2 * i / 4;
                    double b = Math.sin(time * 0.07 + i) * 0.6;
                    double x = at.x + Math.cos(a) * r * Math.cos(b);
                    double y = cy + Math.sin(b) * r;
                    double z = at.z + Math.sin(a) * r * Math.cos(b);
                    draw.sprite(FxDraw.Tex.WISP, x, y, z, 0.4f, time * 0.2f, colour, 0.7f);
                }
            }
        }
    }

    /** Ослабление: серые нити стекают с рук. */
    static final class Weakened implements FxStatuses.Look {
        @Override
        public void ambient(FxStatuses.State state, Entity entity, float emit) {
            if (FxMotes.random() > 0.3f * emit) {
                return;
            }
            double yaw = Math.toRadians(entity.getYRot());
            double side = FxMotes.random() < 0.5f ? 0.38 : -0.38;
            double x = entity.getX() + Math.cos(yaw) * side;
            double z = entity.getZ() + Math.sin(yaw) * side;
            FxEffects.motes().spawn(x, entity.getY() + entity.getBbHeight() * 0.45, z, 0, -0.03f,
                    0, 0.16f, 0xFF8A8A92, 16, 0.98f, FxDraw.Tex.WISP);
        }
    }

    /** Кровотечение: капли падают с цели, на земле остаются пятна. */
    static final class Bleed implements FxStatuses.Look {
        @Override
        public void ambient(FxStatuses.State state, Entity entity, float emit) {
            if (FxMotes.random() > 0.25f * emit) {
                return;
            }
            FxEffects.motes().spawn(entity.getX() + FxMotes.jitter(0.3f),
                    entity.getY() + entity.getBbHeight() * (0.4 + FxMotes.random() * 0.4),
                    entity.getZ() + FxMotes.jitter(0.3f), 0, -0.04f, 0, 0.1f, 0xFFB01020, 16, 0.98f,
                    FxDraw.Tex.GLOW);
            if (FxMotes.random() < 0.15f) {
                double x = entity.getX() + FxMotes.jitter(0.3f);
                double z = entity.getZ() + FxMotes.jitter(0.3f);
                double gy = SceneKit.ground(x, z, entity.getY());
                SceneKit.live("assassin", x, gy, z, 1.5, 60, (self, draw, t, detail) -> {
                    float fade = 1f - self.progress(t);
                    draw.sector(FxDraw.Tex.GLOW, x, gy, z, 0.25, 0, Math.PI * 2, 0xFF6A0810,
                            0.6f * fade, 0.02f);
                });
            }
        }
    }

    /** Тишина: тусклая перечёркнутая метка у рта. */
    static final class Silence implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            double look = Math.toRadians(entity.getYHeadRot());
            double fx = -Math.sin(look);
            double fz = Math.cos(look);
            double sx = -fz;
            double sz = fx;
            double y = at.y + entity.getEyeHeight() - 0.22;
            double cx = at.x + fx * 0.36;
            double cz = at.z + fz * 0.36;
            double d = 0.14;
            draw.sprite(FxDraw.Tex.GLOW, cx, y, cz, 0.4f, 0, 0xFF404050, 0.5f);
            draw.ribbon(FxDraw.Tex.BEAM, new double[] {cx - sx * d, cx + sx * d},
                    new double[] {y + d, y - d}, new double[] {cz - sz * d, cz + sz * d}, 2, 0.06f,
                    0xFFB0B0C0, 0.8f, 0.8f, 0);
            draw.ribbon(FxDraw.Tex.BEAM, new double[] {cx + sx * d, cx - sx * d},
                    new double[] {y + d, y - d}, new double[] {cz + sz * d, cz - sz * d}, 2, 0.06f,
                    0xFFB0B0C0, 0.8f, 0.8f, 0);
        }
    }
}
