package ru.projectst.rpgcore.client;

import static ru.projectst.rpgcore.client.SceneKit.entity;
import static ru.projectst.rpgcore.client.SceneKit.live;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Охотник: клеймо добычи, отбойный, сигнальный, капкан, ливень стрел, гнилая
 * стрела, чутьё, выстрел на поражение, смертельная охота, азарт.
 *
 * <p>Сценарий — раздел 9 {@code docs/vfx/skill-visuals.md}. Облик — янтарь и
 * свежая зелень, наконечники. Стрелы — ванильная модель стрелы остриём по
 * ходу полёта; капкан — тяжёлая нажимная плита с зубьями из железных прутьев.
 */
final class ScenesHunter {

    static final int AMBER = 0xFFE0A83A;
    static final int FRESH = 0xFFC9F27A;
    private static final int ROT = 0xFF7A5A2A;

    /** Сколько стоит капкан: {@code trap_life} охотника в балансе. */
    private static final int TRAP_LIFE = 300;

    /** Капканы, которые стоят: срабатывание захлопывает ближайший. */
    private static final List<Trap> TRAPS = new ArrayList<>();

    private ScenesHunter() {
    }

    static FxScenes.Set scenes() {
        return new FxScenes.Set(BURSTS, Map.of(), ZONES, BOLTS, Map.of(), STATUSES);
    }

    private static final Map<String, FxScenes.BurstScene> BURSTS = Map.ofEntries(
            Map.entry("hunter_quarry_set", ScenesHunter::quarrySet),
            Map.entry("hunter_repel_impact", ScenesHunter::repelImpact),
            Map.entry("hunter_flare_pop", ScenesHunter::flarePop),
            Map.entry("hunter_trap_set", ScenesHunter::trapSet),
            Map.entry("hunter_leap_dust", ScenesHunter::leapDust),
            Map.entry("hunter_trap_snap", ScenesHunter::trapSnap),
            Map.entry("hunter_rain_hit", ScenesHunter::rainHit),
            Map.entry("hunter_rain_wave", ScenesHunter::rainWave),
            Map.entry("hunter_rot_hit", ScenesHunter::rotHit),
            Map.entry("hunter_rot_tick", ScenesHunter::rotTick),
            Map.entry("hunter_sense", ScenesHunter::sense),
            Map.entry("hunter_sense_ping", ScenesHunter::sensePing),
            Map.entry("hunter_aim_start", ScenesHunter::aimStart),
            Map.entry("hunter_kill_blast", ScenesHunter::killBlast),
            Map.entry("hunter_hunt_horn", ScenesHunter::huntHorn),
            Map.entry("hunter_rush_tick", ScenesHunter::rushTick),
            Map.entry("hunter_rush_full", ScenesHunter::rushFull));

    private static final Map<String, FxScenes.BoltScene> BOLTS = Map.ofEntries(
            Map.entry("hunter_arrow_heavy", ScenesHunter::heavyArrow),
            Map.entry("hunter_arrow_flare", ScenesHunter::flareArrow),
            Map.entry("hunter_arrow_rot", ScenesHunter::rotArrow),
            Map.entry("hunter_arrow_kill", ScenesHunter::killArrow));

    private static final Map<String, FxScenes.ZoneScene> ZONES = Map.ofEntries(
            Map.entry("hunter_downpour", ScenesHunter::downpour));

    private static final Map<String, FxStatuses.Look> STATUSES = Map.ofEntries(
            Map.entry("quarry", new Quarry()),
            Map.entry("rot", new Rot()),
            Map.entry("sense", new Sense()),
            Map.entry("aiming", new Aiming()),
            Map.entry("deadly_hunt", new DeadlyHunt()));

    // ------------------------------------------------------------------ стрелы

    /** Стрела на снаряде: модель стрелы остриём по ходу и шлейф. */
    private static FxSolids.Model arrow(FxKinds.Bolt bolt, float scale) {
        return SceneKit.riding(bolt, Items.ARROW, scale, true);
    }

    /** Отбойный: тяжёлая стрела, на наконечнике дрожит кольцо ударной волны. */
    private static void heavyArrow(FxMessage.Projectile p, FxKinds.Bolt bolt) {
        arrow(bolt, 1.1f);
        tipEffect(p, bolt, (draw, at, h, t) -> draw.halo(FxDraw.Tex.RING, at.x + h.x * 0.6,
                at.y + h.y * 0.6, at.z + h.z * 0.6, 0.35 + 0.08 * Math.sin(t), 0.12, h.x, h.y, h.z,
                0xFFFFFFFF, 0.6f, 0.5f, 0));
    }

    /** Сигнальный: горящий наконечник, искры сыплются следом. */
    private static void flareArrow(FxMessage.Projectile p, FxKinds.Bolt bolt) {
        arrow(bolt, 1f);
        tipEffect(p, bolt, (draw, at, h, t) -> {
            draw.sprite(FxDraw.Tex.GLOW, at.x + h.x * 0.5, at.y + h.y * 0.5, at.z + h.z * 0.5, 0.7f,
                    0, 0xFFFF9030, 0.9f);
            draw.sprite(FxDraw.Tex.EMBER, at.x + h.x * 0.5, at.y + h.y * 0.5 + 0.1,
                    at.z + h.z * 0.5, 0.4f, t * 0.3f, 0xFFFFE080, 0.9f);
        }).step = (self, level, motes, emit) -> {
            Vec3 at = bolt.at(1f);
            if (at != null && emit > 0) {
                motes.spawn(at.x, at.y, at.z, FxMotes.jitter(0.03f), -0.02f, FxMotes.jitter(0.03f),
                        0.14f, 0xFFFFC060, 10, 0.9f, FxDraw.Tex.EMBER);
            }
        };
    }

    /** Гнилая: стрела в бурой слизи, за ней тянется гнилой шлейф. */
    private static void rotArrow(FxMessage.Projectile p, FxKinds.Bolt bolt) {
        arrow(bolt, 1f);
        tipEffect(p, bolt, (draw, at, h, t) -> draw.sprite(FxDraw.Tex.WISP, at.x, at.y, at.z, 0.6f,
                t * 0.2f, ROT, 0.6f)).step = (self, level, motes, emit) -> {
                    Vec3 at = bolt.at(1f);
                    if (at != null && emit > 0) {
                        motes.spawn(at.x, at.y, at.z, 0, -0.01f, 0, 0.3f, ROT, 16, 0.95f,
                                FxDraw.Tex.WISP);
                    }
                };
    }

    /** На поражение: крупная стрела с длинным хвостом света. */
    private static void killArrow(FxMessage.Projectile p, FxKinds.Bolt bolt) {
        arrow(bolt, 1.3f);
        tipEffect(p, bolt, (draw, at, h, t) -> {
            draw.ribbon(FxDraw.Tex.BEAM, new double[] {at.x - h.x * 4, at.x},
                    new double[] {at.y - h.y * 4, at.y}, new double[] {at.z - h.z * 4, at.z}, 2,
                    0.35f, AMBER, 0f, 0.9f, -t * 0.4f);
            draw.sprite(FxDraw.Tex.GLOW, at.x, at.y, at.z, 0.9f, 0, 0xFFFFE0A0, 0.8f);
        });
    }

    private interface Tip {
        void draw(FxDraw draw, Vec3 at, Vec3 heading, float t);
    }

    /** Кадр у летящей стрелы, пока она летит. */
    private static SceneKit.Live tipEffect(FxMessage.Projectile p, FxKinds.Bolt bolt, Tip tip) {
        SceneKit.Live live = live(p.classId(), p.x(), p.y(), p.z(), p.range() + 4,
                (int) (p.range() / Math.max(0.05f, p.speed())) + 80, (self, draw, t, detail) -> {
                    Vec3 at = bolt.at(t - (int) t);
                    if (at == null) {
                        self.dead = true;
                        return;
                    }
                    tip.draw(draw, at, bolt.heading(), t);
                });
        live.important = true;
        return live;
    }

    // ------------------------------------------------------------------ попадания

    /** Отбойный попал: кольцо толчка и пыль. */
    private static void repelImpact(FxMessage.Burst e) {
        double yaw = SceneKit.yawFrom(e);
        live(e.classId(), e.x(), e.y() + 1, e.z(), 3, 10, (self, draw, t, detail) -> {
            float k = self.progress(t);
            draw.halo(FxDraw.Tex.RING, e.x(), e.y() + 1, e.z(), 0.4 + 1.6 * k, 0.3, Math.cos(yaw), 0,
                    Math.sin(yaw), 0xFFFFFFFF, 0.8f * (1f - k), 0.5f, 0);
        });
        SceneKit.sparks(e.x(), e.y() + 0.5, e.z(), 12, 0.12f, 0.35f, 0xFFE0E0D0, 14,
                FxDraw.Tex.WISP);
    }

    /**
     * Сигнальный попал: ракета-вспышка повисает в воздухе и светит; у
     * задетого игрока — белая вспышка по экрану.
     */
    private static void flarePop(FxMessage.Burst e) {
        double y0 = e.y() + 2.5;
        live(e.classId(), e.x(), y0, e.z(), 6, 60, (self, draw, t, detail) -> {
            float k = self.progress(t);
            double y = y0 - 0.6 * k;
            float flicker = 0.85f + 0.15f * (float) Math.sin(t * 1.3);
            draw.sprite(FxDraw.Tex.GLOW, e.x(), y, e.z(), 3.5f * (1f - 0.4f * k), 0, 0xFFFFF0C0,
                    0.7f * flicker * (1f - k));
            draw.sprite(FxDraw.Tex.SPARK, e.x(), y, e.z(), 1.2f, t * 0.1f, 0xFFFFFFFF,
                    0.9f * (1f - k));
        }).step = (self, level, motes, emit) -> {
            if (emit > 0 && self.age % 2 == 0) {
                motes.spawn(e.x(), y0 - 0.6 * self.progress(self.age), e.z(), FxMotes.jitter(0.04f),
                        -0.03f, FxMotes.jitter(0.04f), 0.14f, 0xFFFFE0A0, 14, 0.95f,
                        FxDraw.Tex.SPARK);
            }
        };
        if (SceneKit.selfNear(e.x(), e.y(), e.z(), 1.2)) {
            FxScreen.flash(0xFFFFFFFF, 12);
        }
    }

    private static void rotHit(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1, e.z(), 10, 0.12f, 0.24f, ROT, 14, FxDraw.Tex.WISP);
    }

    private static void rotTick(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1, e.z(), 4, 0.05f, 0.22f, ROT, 16, FxDraw.Tex.WISP);
    }

    /** Гниющая рана: стрела торчит из груди, вокруг — бурые споры. */
    static final class Rot implements FxStatuses.Look {
        @Override
        public void appear(FxStatuses.State state, Entity entity) {
            if (!state.solids.isEmpty()) {
                return;
            }
            int id = state.entity;
            FxSolids.Model stuck = SceneKit.item(Items.ARROW, entity.getX(), entity.getY(),
                    entity.getZ(), 0.7f, 2, 0, 4);
            stuck.follow = id;
            stuck.offsetY = entity.getBbHeight() * 0.62;
            stuck.forward = 0.35;
            stuck.at(0, 90 + 25, 45);
            stuck.holding = () -> FxStatuses.has(id, "rot");
            state.solids.add(stuck);
            FxSolids.add(stuck);
        }

        @Override
        public void ambient(FxStatuses.State state, Entity entity, float emit) {
            if (FxMotes.random() < 0.3f * emit) {
                FxEffects.motes().spawn(entity.getX() + FxMotes.jitter(0.3f),
                        entity.getY() + entity.getBbHeight() * 0.6, entity.getZ() + FxMotes.jitter(0.3f),
                        FxMotes.jitter(0.01f), 0.01f, FxMotes.jitter(0.01f), 0.2f, ROT, 18, 0.97f,
                        FxDraw.Tex.WISP);
            }
        }
    }

    // ------------------------------------------------------------------ клеймо добычи

    private static void quarrySet(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y(), e.z(), 1f, FxStyle.Kind.SINK, AMBER, FRESH, 4, 6, 8, 1f, 0,
                FxDraw.Tex.CHEVRON);
    }

    /** Добыча: над головой — янтарное перекрестье, медленно поворачивается. */
    static final class Quarry implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            crosshair(draw, at.x, at.y + entity.getBbHeight() + 0.6, at.z, 0.35, time * 0.02, AMBER,
                    0.9f);
        }
    }

    /** Перекрестье в воздухе: кольцо и четыре риски. */
    static void crosshair(FxDraw draw, double x, double y, double z, double r, double spin,
                          int colour, float alpha) {
        draw.halo(FxDraw.Tex.RING, x, y, z, r, 0.08, 0, 1, 0, colour, alpha, 0.5f, 0);
        for (int i = 0; i < 4; i++) {
            double a = spin + Math.PI / 2 * i;
            double c = Math.cos(a);
            double s = Math.sin(a);
            draw.ribbon(FxDraw.Tex.BEAM, new double[] {x + c * r * 0.6, x + c * r * 1.5},
                    new double[] {y, y}, new double[] {z + s * r * 0.6, z + s * r * 1.5}, 2, 0.07f,
                    colour, alpha, alpha, 0);
        }
        draw.sprite(FxDraw.Tex.GLOW, x, y, z, 0.25f, 0, colour, alpha * 0.8f);
    }

    // ------------------------------------------------------------------ капкан

    /** Капкан: плита и зубья; захлопывается, когда сработает. */
    private static final class Trap {
        final double x;
        final double y;
        final double z;
        final List<FxSolids.Model> jaws = new ArrayList<>();
        FxSolids.Model plate;
        boolean shut;

        Trap(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** Отход: на месте охотника раскрытый капкан со стальными зубьями. */
    private static void trapSet(FxMessage.Burst e) {
        double gy = SceneKit.ground(e.x(), e.z(), e.y());
        Trap trap = new Trap(e.x(), gy, e.z());
        trap.plate = SceneKit.block(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState(), e.x(),
                gy, e.z(), 1.1f, 4, 0, 8);
        trap.plate.decor = false;
        // Капкан живёт столько же, сколько его страж на сервере (trap_life).
        trap.plate.holding = () -> !trap.shut && trap.plate.age < TRAP_LIFE;
        FxSolids.add(trap.plate);
        int teeth = 8;
        for (int i = 0; i < teeth; i++) {
            double a = Math.PI * 2 * i / teeth;
            FxSolids.Model tooth = SceneKit.block(Blocks.IRON_BARS.defaultBlockState(),
                    e.x() + Math.cos(a) * 0.5, gy, e.z() + Math.sin(a) * 0.5, 0.35f, 4, 0, 8);
            tooth.at((float) -Math.toDegrees(a), 0, 0);
            tooth.holding = () -> !trap.shut && trap.plate.age < TRAP_LIFE;
            tooth.decor = false;
            trap.jaws.add(tooth);
            FxSolids.add(tooth);
        }
        TRAPS.removeIf(t -> t.shut || t.plate.dead);
        TRAPS.add(trap);
    }

    private static void leapDust(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 0.2, e.z(), 12, 0.1f, 0.35f, 0xFFD8D0C0, 14, FxDraw.Tex.WISP);
    }

    /** Сработал: зубья захлопываются на ногах цели, лязг и искры. */
    private static void trapSnap(FxMessage.Burst e) {
        Trap best = null;
        double bestD = 9;
        for (Trap trap : TRAPS) {
            double d = (trap.x - e.x()) * (trap.x - e.x()) + (trap.z - e.z()) * (trap.z - e.z());
            if (!trap.shut && d < bestD) {
                bestD = d;
                best = trap;
            }
        }
        if (best != null) {
            Trap trap = best;
            // Зубья сходятся к центру и встают торчком — пасть сомкнулась.
            for (FxSolids.Model tooth : trap.jaws) {
                tooth.vx = (trap.x - tooth.x) / 3;
                tooth.vz = (trap.z - tooth.z) / 3;
                tooth.drag = 0.5;
                tooth.pitchSpeed = 25;
            }
            trap.shut = true;
            TRAPS.remove(trap);
        }
        SceneKit.sparks(e.x(), e.y() + 0.3, e.z(), 14, 0.15f, 0.16f, 0xFFFFE0A0, 8,
                FxDraw.Tex.SPARK);
        FxScreen.shakeIfInside(e.x(), e.y(), e.z(), 1.2, 0.5f, 6);
    }

    // ------------------------------------------------------------------ ливень

    /**
     * Ливень стрел: над местом — облако стрел, по кругу у границы воткнутые
     * стрелы, внутри стрелы падают дождём и втыкаются в землю.
     */
    private static void downpour(FxMessage.ZoneOn on, FxKinds.Zone zone) {
        double r = on.radius();
        int ring = Math.min(28, Math.max(10, (int) (r * 3)));
        for (int i = 0; i < ring; i++) {
            double a = Math.PI * 2 * i / ring;
            double x = on.x() + Math.cos(a) * r;
            double z = on.z() + Math.sin(a) * r;
            FxSolids.Model stake = SceneKit.blade(Items.ARROW, x, SceneKit.ground(x, z, on.y()) + 0.3,
                    z, 0.7f, Math.cos(a) * 0.2, -1, Math.sin(a) * 0.2, 6 + i % 4, 0, 8);
            stake.holding = () -> !zone.ending();
            stake.decor = false;
            FxSolids.add(stake);
        }
        double sky = on.y() + 9;
        SceneKit.Live cloud = live(on.classId(), on.x(), sky, on.z(), r + 2, 10,
                (self, draw, t, detail) -> {
                    float fade = 1f - self.progress(t);
                    draw.sprite(FxDraw.Tex.GLOW, on.x(), sky, on.z(), (float) r * 2.2f, 0,
                            0xFF6A6050, 0.35f * fade);
                    draw.halo(FxDraw.Tex.BEAM, on.x(), sky, on.z(), r * 0.8, 0.6, 0, 1, 0, AMBER,
                            0.35f * fade, 0.3f, t * 0.05f);
                });
        cloud.holding = () -> !zone.ending();
        cloud.important = true;
        cloud.step = (self, level, motes, emit) -> {
            if (zone.ending() || self.age % 3 != 0) {
                return;
            }
            double[] p = FxGeometry.insideCircle(SceneKit.RANDOM, on.x(), on.y(), on.z(), r);
            double gy = FxGround.top(level, p[0], p[2], on.y());
            int flight = 6;
            FxSolids.Model drop = SceneKit.blade(Items.ARROW, p[0], sky, p[2], 0.7f, 0.05, -1, 0.05, 1,
                    flight + 14, 6);
            drop.vy = -(sky - gy - 0.3) / flight;
            drop.drag = 1;
            FxSolids.add(drop);
            SceneKit.Live stop = live(on.classId(), p[0], gy, p[2], 2, flight + 1,
                    (s, d, t, detail) -> {
                    });
            stop.step = (s, l, m, e) -> {
                if (s.age == flight) {
                    drop.vy = 0;
                    SceneKit.debris(l.getBlockState(net.minecraft.core.BlockPos.containing(p[0],
                            gy - 0.5, p[2])), p[0], gy + 0.1, p[2], 0.08f, 0.04f);
                }
            };
        };
    }

    private static void rainHit(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1, e.z(), 5, 0.1f, 0.16f, AMBER, 8, FxDraw.Tex.CHEVRON);
    }

    /** Залп ливня: тонкая волна по границе. */
    private static void rainWave(FxMessage.Burst e) {
        SceneKit.border(e, 3, 4, 6, 0.4f);
    }

    // ------------------------------------------------------------------ чутьё

    private static void sense(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y(), e.z(), 1.2f, FxStyle.Kind.RISE, AMBER, FRESH, 4, 6, 8, 1f,
                0, FxDraw.Tex.CHEVRON);
    }

    private static void sensePing(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1.8, e.z(), 3, 0.03f, 0.18f, AMBER, 10, FxDraw.Tex.CHEVRON);
    }

    /**
     * Чутьё: глаза охотника светятся янтарём; у него — сепия по краям и
     * приглушённое сердцебиение. Контуры живого подсвечивает сервер свечением.
     */
    static final class Sense implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            eyes(draw, entity, at, AMBER);
        }

        @Override
        public void ambient(FxStatuses.State state, Entity entity, float emit) {
            if (SceneKit.isSelf(state.entity) && FxStatuses.now() % 20 == 0) {
                SceneKit.sound("hunter.heartbeat", entity.getX(), entity.getY() + 1, entity.getZ(),
                        0.5f, 1f);
            }
        }

        @Override
        public void screen(FxStatuses.State state, net.minecraft.client.gui.GuiGraphics g, int w,
                           int h, float time) {
            FxScreen.edges(g, w, h, 0xFF704A1A, 0.3f);
        }
    }

    /** Светящиеся глаза: две точки перед лицом. */
    static void eyes(FxDraw draw, Entity entity, Vec3 at, int colour) {
        double look = Math.toRadians(entity.getYHeadRot());
        double fx = -Math.sin(look);
        double fz = Math.cos(look);
        double y = at.y + entity.getEyeHeight() + 0.02;
        for (int i = -1; i <= 1; i += 2) {
            draw.sprite(FxDraw.Tex.GLOW, at.x + fx * 0.3 - fz * 0.12 * i, y,
                    at.z + fz * 0.3 + fx * 0.12 * i, 0.22f, 0, colour, 0.9f);
        }
    }

    // ------------------------------------------------------------------ на поражение

    private static void aimStart(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1.3, e.z(), 8, 0.05f, 0.16f, AMBER, 10, FxDraw.Tex.CHEVRON);
    }

    /**
     * Прицеливание: от глаз охотника по взгляду — тонкий луч-прицел до
     * первого блока (видят все), у самого — сужение по краям.
     */
    static final class Aiming implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            Vec3 eye = entity.getEyePosition(time - (int) time);
            Vec3 look = entity.getLookAngle();
            Vec3 end = eye.add(look.scale(40));
            HitResult hit = entity.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, entity));
            Vec3 to = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
            Vec3 from = eye.add(look.scale(0.6)).add(0, -0.15, 0);
            draw.ribbon(FxDraw.Tex.BEAM, new double[] {from.x, to.x}, new double[] {from.y, to.y},
                    new double[] {from.z, to.z}, 2, 0.05f, 0xFFFF4020, 0.9f, 0.5f, 0);
            draw.sprite(FxDraw.Tex.GLOW, to.x, to.y, to.z, 0.35f, 0, 0xFFFF4020, 0.9f);
            // Натянутая тетива: светящаяся черта у груди.
            double side = Math.toRadians(entity.getYRot());
            double cx = at.x - Math.sin(side) * 0.4;
            double cz = at.z + Math.cos(side) * 0.4;
            double y = at.y + entity.getBbHeight() * 0.7;
            draw.ribbon(FxDraw.Tex.BEAM, new double[] {cx, cx}, new double[] {y - 0.5, y + 0.5},
                    new double[] {cz, cz}, 2, 0.05f, FRESH, 0.8f, 0.8f, 0);
        }

        @Override
        public void screen(FxStatuses.State state, net.minecraft.client.gui.GuiGraphics g, int w,
                           int h, float time) {
            FxScreen.edges(g, w, h, 0xFF000000, 0.5f);
        }
    }

    /** Попадание на поражение: взрыв янтаря и осколков, тряска рядом. */
    private static void killBlast(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y() + 1, e.z(), 1.8f, FxStyle.Kind.WAVE, AMBER, FRESH, 3, 3, 8,
                1.5f, 0, FxDraw.Tex.CHEVRON);
        SceneKit.sparks(e.x(), e.y() + 1.2, e.z(), 30, 0.35f, 0.22f, 0xFFFFE0A0, 12,
                FxDraw.Tex.CHEVRON);
        FxScreen.shakeIfInside(e.x(), e.y(), e.z(), 3, 0.6f, 8);
    }

    // ------------------------------------------------------------------ охота и азарт

    private static void huntHorn(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y(), e.z(), 1.5f, FxStyle.Kind.WAVE, AMBER, FRESH, 6, 4, 10,
                1.5f, 0, FxDraw.Tex.CHEVRON);
        if (SceneKit.isSelf(e.source())) {
            FxScreen.flash(AMBER, 14);
        }
    }

    /**
     * Смертельная охота: янтарная аура вокруг охотника, на земле за ним —
     * следы-наконечники; у самого — янтарная виньетка.
     */
    static final class DeadlyHunt implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            for (int i = 0; i < 2; i++) {
                double y = at.y + 0.3 + i * 0.8 + 0.1 * Math.sin(time * 0.1 + i);
                draw.halo(FxDraw.Tex.BEAM, at.x, y, at.z, 0.65, 0.18, 0, 1, 0, AMBER, 0.45f, 0.5f,
                        time * 0.08f * (i * 2 - 1));
            }
        }

        @Override
        public void ambient(FxStatuses.State state, Entity entity, float emit) {
            Vec3 move = entity.getDeltaMovement();
            if (move.horizontalDistanceSqr() > 0.004 && entity.tickCount % 4 == 0) {
                double gy = SceneKit.ground(entity.getX(), entity.getZ(), entity.getY());
                double x = entity.getX();
                double z = entity.getZ();
                double yaw = Math.atan2(move.z, move.x);
                live("hunter", x, gy, z, 1.5, 40, (self, draw, t, detail) -> {
                    float fade = 1f - self.progress(t);
                    double c = Math.cos(yaw);
                    double s = Math.sin(yaw);
                    draw.groundLine(FxDraw.Tex.BEAM, x - c * 0.2 + s * 0.15, z - s * 0.2 - c * 0.15,
                            x + c * 0.1, z + s * 0.1, gy, 0.08, AMBER, 0.7f * fade, 0.03f);
                    draw.groundLine(FxDraw.Tex.BEAM, x - c * 0.2 - s * 0.15, z - s * 0.2 + c * 0.15,
                            x + c * 0.1, z + s * 0.1, gy, 0.08, AMBER, 0.7f * fade, 0.03f);
                });
            }
        }

        @Override
        public void screen(FxStatuses.State state, net.minecraft.client.gui.GuiGraphics g, int w,
                           int h, float time) {
            FxScreen.edges(g, w, h, AMBER, 0.18f);
        }
    }

    /** Азарт: язычки пламени у охотника, по одному на стак. */
    private static void rushTick(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1, e.z(), 6, 0.06f, 0.16f, 0xFFFFA040, 10, FxDraw.Tex.EMBER);
    }

    /** Полный разгон: кольцо огня вокруг охотника. */
    private static void rushFull(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y(), e.z(), 1.4f, FxStyle.Kind.WAVE, 0xFFFF8030, 0xFFFFE080, 4,
                4, 8, 1.6f, 0, FxDraw.Tex.EMBER);
    }
}
