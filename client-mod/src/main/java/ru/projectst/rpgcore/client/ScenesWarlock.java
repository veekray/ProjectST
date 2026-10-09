package ru.projectst.rpgcore.client;

import static ru.projectst.rpgcore.client.SceneKit.entity;
import static ru.projectst.rpgcore.client.SceneKit.live;

import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Колдун: череп, проклятие, души, серп жатвы, цепи, пелена, переливание, кокон.
 *
 * <p>Сценарий — раздел 5 {@code docs/vfx/skill-visuals.md}. Облик — скверна:
 * лиловая дымка, ядовито-зелёные блики, души цвета пламени душ. Модели —
 * ванильные: череп иссушителя, незеритовая мотыга вместо серпа, цепь,
 * тонированное стекло кокона, око Края в пелене.
 */
final class ScenesWarlock {

    static final int FEL = 0xFFB04AE0;
    static final int FEL_LIGHT = 0xFF9CFF6A;
    static final int SOUL = 0xFF6FE8F0;
    private static final int SHADOW = 0xFF2A1238;

    private ScenesWarlock() {
    }

    static FxScenes.Set scenes() {
        return new FxScenes.Set(BURSTS, Map.of(), ZONES, BOLTS, Map.of(), STATUSES);
    }

    private static final Map<String, FxScenes.BurstScene> BURSTS = Map.ofEntries(
            Map.entry("warlock_skull_burst", ScenesWarlock::skullBurst),
            Map.entry("warlock_curse_tick", ScenesWarlock::curseTick),
            Map.entry("warlock_soul_gain", ScenesWarlock::soulGain),
            Map.entry("warlock_reap_brand", ScenesWarlock::reapBrand),
            Map.entry("warlock_chain_bind", ScenesWarlock::chainBind),
            Map.entry("warlock_chain_tug", ScenesWarlock::chainTug),
            Map.entry("warlock_veil_blink", ScenesWarlock::veilBlink),
            Map.entry("warlock_transfusion_wave", ScenesWarlock::transfusionWave),
            Map.entry("warlock_soul_drain", ScenesWarlock::soulDrain),
            Map.entry("warlock_cocoon_close", ScenesWarlock::cocoonClose));

    private static final Map<String, FxScenes.BoltScene> BOLTS = Map.ofEntries(
            Map.entry("warlock_skull", ScenesWarlock::skull));

    private static final Map<String, FxScenes.ZoneScene> ZONES = Map.ofEntries(
            Map.entry("warlock_veil", ScenesWarlock::veil));

    private static final Map<String, FxStatuses.Look> STATUSES = Map.ofEntries(
            Map.entry("curse", new Curse()),
            Map.entry("soul", new Souls()),
            Map.entry("reaper_mark", new ReaperMark()),
            Map.entry("chains", new Chains()),
            Map.entry("banish", new Cocoon()));

    // ------------------------------------------------------------------ череп

    /** Череп иссушителя в лиловом пламени летит носом вперёд и дымит. */
    private static void skull(FxMessage.Projectile p, FxKinds.Bolt bolt) {
        bolt.bare = true;
        FxSolids.Model skull = SceneKit.item(Items.WITHER_SKELETON_SKULL, p.x(), p.y(), p.z(),
                0.7f, 1, 0, 3);
        skull.anchor = () -> bolt.at(1f);
        skull.face(p.dx(), p.dy(), p.dz());
        skull.yaw += 180;
        skull.sway = 12;
        skull.decor = false;
        FxSolids.add(skull);
        SceneKit.Live flame = live(p.classId(), p.x(), p.y(), p.z(), p.range() + 4,
                (int) (p.range() / Math.max(0.05f, p.speed())) + 80, (self, draw, t, detail) -> {
                    Vec3 at = bolt.at(t - (int) t);
                    if (at == null) {
                        self.dead = true;
                        return;
                    }
                    draw.sprite(FxDraw.Tex.GLOW, at.x, at.y, at.z, 1.1f, 0, FEL, 0.6f);
                    draw.sprite(FxDraw.Tex.WISP, at.x, at.y, at.z, 0.9f, t * 0.2f, FEL_LIGHT, 0.35f);
                });
        flame.important = true;
        flame.step = (self, level, motes, emit) -> {
            Vec3 at = bolt.at(1f);
            if (at != null && emit > 0) {
                motes.spawn(at.x + FxMotes.jitter(0.15f), at.y + FxMotes.jitter(0.15f),
                        at.z + FxMotes.jitter(0.15f), 0, 0.02f, 0, 0.32f,
                        FxMotes.random() < 0.3f ? FEL_LIGHT : FEL, 14, 0.94f, FxDraw.Tex.WISP);
            }
        };
    }

    /** Череп разлетается: костяная крошка и дымка, впитывающаяся в цель. */
    private static void skullBurst(FxMessage.Burst e) {
        double y = e.y() + 1;
        for (int i = 0; i < 4; i++) {
            SceneKit.debris(Blocks.BONE_BLOCK.defaultBlockState(), e.x(), y, e.z(), 0.1f, 0.08f);
        }
        FxMotes motes = FxEffects.motes();
        int n = (int) (18 * FxEffects.emit());
        for (int i = 0; i < n; i++) {
            double a = FxMotes.random() * Math.PI * 2;
            double r = 0.9;
            double sx = e.x() + Math.cos(a) * r;
            double sz = e.z() + Math.sin(a) * r;
            double sy = y + FxMotes.jitter(0.6f);
            motes.spawn(sx, sy, sz, (float) (e.x() - sx) / 10, (float) (y - sy) / 10,
                    (float) (e.z() - sz) / 10, 0.3f, i % 3 == 0 ? FEL_LIGHT : FEL, 10, 1f,
                    FxDraw.Tex.WISP);
        }
        SceneKit.styleAt(e, e.x(), e.y(), e.z(), 1.2f, FxStyle.Kind.SINK, FEL, FEL_LIGHT, 3, 6, 10,
                1f, 0, FxDraw.Tex.WISP);
    }

    // ------------------------------------------------------------------ проклятие

    /** Тик иссушения: клуб лиловой дымки из груди. */
    private static void curseTick(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1.1, e.z(), 6, 0.06f, 0.34f, FEL, 16, FxDraw.Tex.WISP);
    }

    /**
     * Иссушение на цели: лиловая дымка выходит из груди и тянется к колдуну;
     * снято — огонёк души летит от цели к нему.
     */
    static final class Curse implements FxStatuses.Look {
        @Override
        public void ambient(FxStatuses.State state, Entity entity, float emit) {
            if (FxMotes.random() > 0.35f * emit) {
                return;
            }
            Entity warlock = entity(state.source);
            double y = entity.getY() + entity.getBbHeight() * 0.65;
            float vx = FxMotes.jitter(0.01f);
            float vz = FxMotes.jitter(0.01f);
            if (warlock != null) {
                Vec3 to = new Vec3(warlock.getX() - entity.getX(), 0, warlock.getZ() - entity.getZ());
                if (to.lengthSqr() > 1e-4) {
                    to = to.normalize().scale(0.05);
                    vx = (float) to.x;
                    vz = (float) to.z;
                }
            }
            FxEffects.motes().spawn(entity.getX() + FxMotes.jitter(0.2f), y, entity.getZ()
                    + FxMotes.jitter(0.2f), vx, 0.01f, vz, 0.3f, FEL, 22, 0.97f, FxDraw.Tex.WISP);
        }

        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            float pulse = 0.35f + 0.15f * (float) Math.sin(time * 0.2);
            draw.sprite(FxDraw.Tex.GLOW, at.x, at.y + entity.getBbHeight() * 0.65, at.z, 0.9f, 0,
                    FEL, pulse);
        }

        @Override
        public void gone(FxStatuses.State state, Entity entity) {
            Entity warlock = entity(state.source);
            if (warlock != null) {
                soulFlight(entity.getX(), entity.getY() + entity.getBbHeight() * 0.65,
                        entity.getZ(), warlock.getId());
            }
        }
    }

    /** Огонёк души летит дугой от точки к колдуну и гаснет в нём. */
    static void soulFlight(double fx, double fy, double fz, int warlockId) {
        live("warlock", fx, fy, fz, 24, 16, (self, draw, t, detail) -> {
            Entity warlock = entity(warlockId);
            if (warlock == null) {
                self.dead = true;
                return;
            }
            float k = (float) FxGeometry.easeOut(self.progress(t));
            Vec3 to = SceneKit.chest(warlock, t - (int) t);
            double x = fx + (to.x - fx) * k;
            double y = fy + (to.y - fy) * k + Math.sin(k * Math.PI) * 1.2;
            double z = fz + (to.z - fz) * k;
            draw.sprite(FxDraw.Tex.GLOW, x, y, z, 0.7f, 0, SOUL, 0.8f);
            draw.sprite(FxDraw.Tex.WISP, x, y, z, 0.5f, t * 0.3f, 0xFFFFFFFF, 0.6f);
        });
        SceneKit.sound("warlock.soul.return", fx, fy, fz, 0.6f, 1f);
    }

    // ------------------------------------------------------------------ души

    /** Душа добыта: огоньки душ вихрем поднимаются вокруг колдуна. */
    private static void soulGain(FxMessage.Burst e) {
        FxMotes motes = FxEffects.motes();
        int n = (int) (14 * Math.max(0.3f, FxEffects.emit()));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            motes.spawn(e.x() + Math.cos(a) * 0.7, e.y() + 0.2, e.z() + Math.sin(a) * 0.7,
                    (float) -Math.sin(a) * 0.06f, 0.07f, (float) Math.cos(a) * 0.06f, 0.24f, SOUL,
                    16, 0.93f, FxDraw.Tex.WISP);
        }
    }

    /** Души у колдуна: огоньки кружат у груди, по огоньку на душу. */
    static final class Souls implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            int n = Math.max(1, state.stacks);
            double y = at.y + entity.getBbHeight() * 0.6;
            for (int i = 0; i < n; i++) {
                double a = time * 0.08 + Math.PI * 2 * i / n;
                double r = 0.75;
                double x = at.x + Math.cos(a) * r;
                double z = at.z + Math.sin(a) * r;
                double yy = y + 0.15 * Math.sin(time * 0.15 + i);
                draw.sprite(FxDraw.Tex.GLOW, x, yy, z, 0.5f, 0, SOUL, 0.7f);
                draw.sprite(FxDraw.Tex.WISP, x, yy + 0.05, z, 0.35f, time * 0.2f + i, 0xFFE0FFFF,
                        0.6f);
            }
        }

        @Override
        public void ambient(FxStatuses.State state, Entity entity, float emit) {
            if (FxMotes.random() < 0.06f * emit * Math.max(1, state.stacks)) {
                double a = FxMotes.random() * Math.PI * 2;
                FxEffects.motes().spawn(entity.getX() + Math.cos(a) * 0.75,
                        entity.getY() + entity.getBbHeight() * 0.6, entity.getZ() + Math.sin(a) * 0.75,
                        0, 0.02f, 0, 0.14f, SOUL, 14, 0.95f, FxDraw.Tex.SPARK);
            }
        }
    }

    // ------------------------------------------------------------------ жатва

    /** Метка легла: тень опускается на цель. */
    private static void reapBrand(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y(), e.z(), 1.2f, FxStyle.Kind.SINK, FEL, SOUL, 4, 8, 10, 1f, 0,
                FxDraw.Tex.WISP);
    }

    /**
     * Метка жатвы: над головой цели — серп (незеритовая мотыга), медленно
     * покачивается в лиловом свечении. Снята — серп рассекает воздух, и к
     * колдуну летят две души.
     */
    static final class ReaperMark implements FxStatuses.Look {
        @Override
        public void appear(FxStatuses.State state, Entity entity) {
            if (!state.solids.isEmpty()) {
                return;
            }
            int id = state.entity;
            FxSolids.Model sickle = SceneKit.item(Items.NETHERITE_HOE, entity.getX(),
                    entity.getY(), entity.getZ(), 0.8f, 8, 0, 6);
            sickle.follow = id;
            sickle.faceFollow = false;
            sickle.offsetY = entity.getBbHeight() + 0.65;
            sickle.at(0, 0, -45);
            sickle.sway = 18;
            sickle.yawSpeed = 1.5f;
            sickle.holding = () -> FxStatuses.has(id, "reaper_mark");
            sickle.decor = false;
            state.solids.add(sickle);
            FxSolids.add(sickle);
        }

        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            draw.sprite(FxDraw.Tex.GLOW, at.x, at.y + entity.getBbHeight() + 0.65, at.z, 1.1f, 0,
                    FEL, 0.4f + 0.1f * (float) Math.sin(time * 0.2));
        }

        @Override
        public void gone(FxStatuses.State state, Entity entity) {
            double y = entity.getY() + entity.getBbHeight() + 0.4;
            double cx = entity.getX();
            double cz = entity.getZ();
            // Взмах серпа: дуга над головой, быстро гаснет.
            live("warlock", cx, y, cz, 3, 8, (self, draw, t, detail) -> {
                float k = self.progress(t);
                int n = 8;
                double[] xs = new double[n];
                double[] ys = new double[n];
                double[] zs = new double[n];
                double sweep = Math.PI * 1.2 * Math.min(1f, k * 2);
                for (int i = 0; i < n; i++) {
                    double a = -0.6 + sweep * i / (n - 1);
                    xs[i] = cx + Math.cos(a) * 0.9;
                    ys[i] = y - Math.sin(a) * 0.3;
                    zs[i] = cz + Math.sin(a) * 0.9;
                }
                draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, n, 0.3f, FEL_LIGHT, 0f, 1f - k, 0);
            });
            Entity warlock = entity(state.source);
            if (warlock != null) {
                soulFlight(cx - 0.3, y, cz, warlock.getId());
                soulFlight(cx + 0.3, y, cz, warlock.getId());
            }
        }
    }

    // ------------------------------------------------------------------ цепи

    /** Цепь вырывается из-под земли у колдуна: комья земли душ и дымка. */
    private static void chainBind(FxMessage.Burst e) {
        Entity warlock = entity(e.source());
        double x = warlock != null ? warlock.getX() : e.x();
        double z = warlock != null ? warlock.getZ() : e.z();
        double y = SceneKit.ground(x, z, warlock != null ? warlock.getY() : e.y());
        for (int i = 0; i < 4; i++) {
            SceneKit.debris(Blocks.SOUL_SOIL.defaultBlockState(), x + FxMotes.jitter(0.4f), y + 0.1,
                    z + FxMotes.jitter(0.4f), 0.14f, 0.07f);
        }
        SceneKit.sparks(x, y + 0.2, z, 10, 0.08f, 0.3f, SHADOW, 14, FxDraw.Tex.WISP);
    }

    /**
     * Оковы: теневая цепь от груди колдуна к груди цели, держится, пока висит
     * {@code chains}; вырастает от колдуна к цели. Близко — провисает к земле.
     */
    static final class Chains implements FxStatuses.Look {
        @Override
        public void appear(FxStatuses.State state, Entity entity) {
            if (!state.solids.isEmpty()) {
                return;
            }
            int id = state.entity;
            int source = state.source;
            FxSolids.Strip chain = FxSolids.Strip.chain(partial -> {
                Entity from = entity(source);
                Entity to = entity(id);
                if (from == null || to == null) {
                    return null;
                }
                Vec3 a = SceneKit.chest(from, partial);
                Vec3 b = SceneKit.chest(to, partial);
                return new double[][] {{a.x, a.y, a.z}, {b.x, b.y, b.z}};
            }, 1.6, 8, 0, 6);
            chain.holding = () -> FxStatuses.has(id, "chains");
            chain.decor = false;
            chain.sag = 0.4;
            state.solids.add(chain);
            FxSolids.add(chain);
        }

        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            // Тёмное свечение на звеньях у груди цели: цепь держит здесь.
            draw.sprite(FxDraw.Tex.GLOW, at.x, at.y + entity.getBbHeight() * 0.65, at.z, 0.8f, 0,
                    FEL, 0.35f);
        }
    }

    /** Подтяжка: по цепи бежит светящийся рывок от цели к колдуну, звон. */
    private static void chainTug(FxMessage.Burst e) {
        Entity warlock = entity(e.source());
        if (warlock == null) {
            return;
        }
        int warlockId = warlock.getId();
        double tx = e.x();
        double ty = e.y() + 1.1;
        double tz = e.z();
        live(e.classId(), tx, ty, tz, 24, 8, (self, draw, t, detail) -> {
            Entity w = entity(warlockId);
            if (w == null) {
                return;
            }
            Vec3 to = SceneKit.chest(w, t - (int) t);
            float k = self.progress(t);
            double a = Math.max(0, k - 0.25);
            double b = Math.min(1, k + 0.05);
            double[] xs = {tx + (to.x - tx) * a, tx + (to.x - tx) * b};
            double[] ys = {ty + (to.y - ty) * a, ty + (to.y - ty) * b};
            double[] zs = {tz + (to.z - tz) * a, tz + (to.z - tz) * b};
            draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, 2, 0.35f, FEL_LIGHT, 0.1f, 0.9f, 0);
        });
        SceneKit.sound("warlock.chains.tug", tx, ty, tz, 0.5f, 0.9f + FxMotes.random() * 0.2f);
    }

    // ------------------------------------------------------------------ пелена

    /**
     * Тёмная пелена: облако лиловой мглы до границы зоны (её рисует стиль), в
     * мгле моргают глаза — ока Края появляются и гаснут. Кто внутри — темнеет
     * по краям экрана.
     */
    private static void veil(FxMessage.ZoneOn on, FxKinds.Zone zone) {
        double r = on.radius();
        SceneKit.Live cloud = live(on.classId(), on.x(), on.y(), on.z(), r + 2, 10,
                (self, draw, t, detail) -> {
                    float fade = 1f - self.progress(t);
                    draw.sector(FxDraw.Tex.FILL, on.x(), on.y(), on.z(), r, 0, Math.PI * 2, SHADOW,
                            0.45f * fade, 0.03f);
                });
        cloud.holding = () -> !zone.ending();
        cloud.important = true;
        cloud.step = (self, level, motes, emit) -> {
            if (zone.ending()) {
                return;
            }
            int n = (int) Math.max(1, r * r * 0.25 * emit);
            for (int i = 0; i < n; i++) {
                double[] p = FxGeometry.insideCircle(SceneKit.RANDOM, on.x(), on.y(), on.z(), r);
                double gy = FxGround.top(level, p[0], p[2], on.y());
                motes.spawn(p[0], gy + 0.2 + FxMotes.random() * 2.2, p[2], FxMotes.jitter(0.01f),
                        0.004f, FxMotes.jitter(0.01f), 0.7f + FxMotes.random() * 0.4f,
                        FxMotes.random() < 0.15f ? FEL_LIGHT : FxDraw.mix(FEL, SHADOW, 0.5f), 34,
                        0.99f, FxDraw.Tex.WISP);
            }
            if (self.age % 12 == 0) {
                double[] p = FxGeometry.insideCircle(SceneKit.RANDOM, on.x(), on.y(), on.z(),
                        Math.max(0.5, r - 0.6));
                eye(p[0], FxGround.top(level, p[0], p[2], on.y()) + 0.8 + FxMotes.random() * 1.2,
                        p[2]);
            }
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) {
                double dx = player.getX() - on.x();
                double dz = player.getZ() - on.z();
                if (dx * dx + dz * dz <= r * r && Math.abs(player.getY() - on.y()) < 4) {
                    FxScreen.tint(0xFF0A0410, 0.55f);
                }
            }
        };
    }

    /** Глаз в мгле: око Края открывается, смотрит секунду и закрывается. */
    private static void eye(double x, double y, double z) {
        FxSolids.Model eye = SceneKit.item(Items.ENDER_EYE, x, y, z, 0.45f, 3, 10, 3);
        eye.yawSpeed = 2;
        FxSolids.add(eye);
    }

    /** Тик пелены по цели: рядом с ней моргает глаз, шёпот. */
    private static void veilBlink(FxMessage.Burst e) {
        eye(e.x() + FxMotes.jitter(0.6f), e.y() + 1.6, e.z() + FxMotes.jitter(0.6f));
        SceneKit.sound("warlock.veil.whisper", e.x(), e.y() + 1, e.z(), 0.5f, 1f);
    }

    // ------------------------------------------------------------------ переливание

    /** Лиловая волна до границы. */
    private static void transfusionWave(FxMessage.Burst e) {
        SceneKit.style(e, FxStyle.Kind.WAVE, FEL, SOUL, 7, 8, 9, true, 1.3f, 0, FxDraw.Tex.WISP);
    }

    /** От проклятого врага к колдуну тянется струя дымки с душой внутри. */
    private static void soulDrain(FxMessage.Burst e) {
        int source = e.source();
        double fx = e.x();
        double fy = e.y() + 1.1;
        double fz = e.z();
        live(e.classId(), fx, fy, fz, 24, 18, (self, draw, t, detail) -> {
            Entity w = entity(source);
            if (w == null) {
                return;
            }
            Vec3 to = SceneKit.chest(w, t - (int) t);
            float k = self.progress(t);
            int n = 8;
            double[] xs = new double[n];
            double[] ys = new double[n];
            double[] zs = new double[n];
            for (int i = 0; i < n; i++) {
                double q = (double) i / (n - 1);
                xs[i] = fx + (to.x - fx) * q;
                ys[i] = fy + (to.y - fy) * q + Math.sin(q * Math.PI) * 0.6
                        + 0.12 * Math.sin(t * 0.5 + q * 8);
                zs[i] = fz + (to.z - fz) * q;
            }
            float alpha = k < 0.7f ? 0.7f : 0.7f * (1f - (k - 0.7f) / 0.3f);
            draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, n, 0.4f, FEL, alpha * 0.6f, alpha, -t * 0.3f);
            int head = Math.min(n - 1, (int) (k * 1.4f * (n - 1)));
            draw.sprite(FxDraw.Tex.GLOW, xs[head], ys[head], zs[head], 0.5f, 0, SOUL, alpha);
        });
    }

    // ------------------------------------------------------------------ кокон

    /** Кокон смыкается: осколки стекла слетаются к цели. */
    private static void cocoonClose(FxMessage.Burst e) {
        FxMotes motes = FxEffects.motes();
        double y = e.y() + 1;
        int n = (int) (20 * Math.max(0.4f, FxEffects.emit()));
        for (int i = 0; i < n; i++) {
            double a = FxMotes.random() * Math.PI * 2;
            double sx = e.x() + Math.cos(a) * 1.6;
            double sz = e.z() + Math.sin(a) * 1.6;
            double sy = y + FxMotes.jitter(1f);
            motes.spawn(sx, sy, sz, (float) (e.x() - sx) / 8, (float) (y - sy) / 8,
                    (float) (e.z() - sz) / 8, 0.22f, i % 2 == 0 ? FEL : 0xFF3A2A4A, 8, 1f,
                    FxDraw.Tex.SHARD);
        }
    }

    /**
     * Кокон мучений: тонированное стекло смыкается вокруг цели, по нему
     * обвита цепь, бегут лиловые трещины. Снят — стекло разлетается.
     */
    static final class Cocoon implements FxStatuses.Look {
        @Override
        public void appear(FxStatuses.State state, Entity entity) {
            if (!state.solids.isEmpty()) {
                return;
            }
            int id = state.entity;
            double w = Math.max(0.5, entity.getBbWidth() * 0.5 + 0.3);
            double h = entity.getBbHeight();
            int rows = 3;
            for (int row = 0; row < rows; row++) {
                int around = 6;
                for (int i = 0; i < around; i++) {
                    double a = Math.PI * 2 * (i + row * 0.5) / around;
                    double rr = w * (row == rows - 1 ? 0.75 : 1);
                    FxSolids.Model pane = SceneKit.block(Blocks.TINTED_GLASS.defaultBlockState(),
                            entity.getX(), entity.getY(), entity.getZ(), 0.55f, 6 + row * 3, 0, 6);
                    pane.follow = id;
                    pane.faceFollow = false;
                    pane.offsetX = Math.cos(a) * rr;
                    pane.offsetZ = Math.sin(a) * rr;
                    pane.offsetY = h * row / rows;
                    pane.at((float) -Math.toDegrees(a) + 90, 0, 0);
                    pane.holding = () -> FxStatuses.has(id, "banish");
                    pane.decor = false;
                    state.solids.add(pane);
                    FxSolids.add(pane);
                }
            }
            FxSolids.Strip chain = FxSolids.Strip.chain(partial -> {
                Entity e = entity(id);
                if (e == null) {
                    return null;
                }
                Vec3 at = e.getPosition(partial);
                int n = 24;
                double[][] helix = new double[n][];
                for (int i = 0; i < n; i++) {
                    double q = (double) i / (n - 1);
                    double a = q * Math.PI * 5;
                    helix[i] = new double[] {at.x + Math.cos(a) * (w + 0.2), at.y + 0.1 + q * h,
                            at.z + Math.sin(a) * (w + 0.2)};
                }
                return helix;
            }, 1.2, 14, 0, 6);
            chain.holding = () -> FxStatuses.has(id, "banish");
            chain.decor = false;
            state.solids.add(chain);
            FxSolids.add(chain);
        }

        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            double h = entity.getBbHeight();
            double w = Math.max(0.5, entity.getBbWidth() * 0.5 + 0.3);
            for (int i = 0; i < 3; i++) {
                double y = at.y + h * (0.2 + 0.3 * i);
                float pulse = 0.4f + 0.3f * (float) Math.sin(time * 0.3 + i * 2);
                draw.halo(FxDraw.Tex.BEAM, at.x, y, at.z, w + 0.05, 0.08, 0.15 * i, 1, 0.1,
                        FEL, pulse, 0.5f, time * 0.1f);
            }
        }

        @Override
        public void gone(FxStatuses.State state, Entity entity) {
            for (int i = 0; i < 10; i++) {
                SceneKit.debris(Blocks.TINTED_GLASS.defaultBlockState(), entity.getX(),
                        entity.getY() + entity.getBbHeight() * FxMotes.random(), entity.getZ(),
                        0.12f, 0.12f);
            }
            SceneKit.sound("warlock.cocoon.break", entity.getX(), entity.getY() + 1, entity.getZ(),
                    1f, 0.9f);
        }

        @Override
        public void screen(FxStatuses.State state, net.minecraft.client.gui.GuiGraphics g, int w,
                           int h, float time) {
            FxScreen.edges(g, w, h, 0xFF120818, 0.65f);
            FxScreen.bottom(g, w, h, 0xFF2A1238, 0.4f);
        }
    }
}
