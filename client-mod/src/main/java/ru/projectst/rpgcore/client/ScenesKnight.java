package ru.projectst.rpgcore.client;

import static ru.projectst.rpgcore.client.SceneKit.entity;
import static ru.projectst.rpgcore.client.SceneKit.live;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Рыцарь: вызов, клич, ударная волна, заступник, карающий щит, глухая
 * оборона, возмездие, знамя, фортеция, правосудие.
 *
 * <p>Сценарий — раздел 12 {@code docs/vfx/skill-visuals.md}. Облик — свет и
 * бледное золото. Модели — ванильные: щит, трезубец вместо копья света,
 * золотые мечи, жёлтое знамя, кальцит по краю круга знамени, белое стекло
 * стен фортеции.
 */
final class ScenesKnight {

    static final int GOLD = 0xFFF0D58A;
    static final int LIGHT = 0xFFFFFFFF;

    private ScenesKnight() {
    }

    static FxScenes.Set scenes() {
        return new FxScenes.Set(BURSTS, Map.of(), ZONES, Map.of(), Map.of(), STATUSES);
    }

    private static final Map<String, FxScenes.BurstScene> BURSTS = Map.ofEntries(
            Map.entry("knight_challenge_spear", ScenesKnight::challengeSpear),
            Map.entry("knight_cry_mark", ScenesKnight::cryMark),
            Map.entry("knight_cry_wave", ScenesKnight::cryWave),
            Map.entry("knight_shield_slam", ScenesKnight::shieldSlam),
            Map.entry("knight_shockwave", ScenesKnight::shockwave),
            Map.entry("knight_quake_lift", ScenesKnight::quakeLift),
            Map.entry("knight_guard", ScenesKnight::guard),
            Map.entry("knight_bash", ScenesKnight::bash),
            Map.entry("knight_wall_rise", ScenesKnight::wallRise),
            Map.entry("knight_swords_ready", ScenesKnight::swordsReady),
            Map.entry("knight_swords_strike", ScenesKnight::swordsStrike),
            Map.entry("knight_banner_mark", ScenesKnight::cryMark),
            Map.entry("knight_banner_pulse", ScenesKnight::bannerPulse),
            Map.entry("knight_banner_guard", ScenesKnight::bannerGuard),
            Map.entry("knight_fortress", ScenesKnight::fortress),
            Map.entry("knight_fortress_bind", ScenesKnight::cryMark),
            Map.entry("knight_judgment_wave", ScenesKnight::judgmentWave),
            Map.entry("knight_judgment_sword", ScenesKnight::judgmentSword));

    private static final Map<String, FxScenes.ZoneScene> ZONES = Map.ofEntries(
            Map.entry("knight_banner_zone", ScenesKnight::bannerZone));

    private static final Map<String, FxStatuses.Look> STATUSES = Map.ofEntries(
            Map.entry("challenge", new Challenge()),
            Map.entry("guarded", new Guarded()),
            Map.entry("shield_wall", new ShieldWall()),
            Map.entry("retribution", new Retribution()),
            Map.entry("banner_guard", new BannerGuard()),
            Map.entry("fortress", new Fortress()));

    // ------------------------------------------------------------------ вызов

    /** Вызов: копьё света (трезубец) летит от рыцаря в цель. */
    private static void challengeSpear(FxMessage.Burst e) {
        Entity knight = entity(e.source());
        double sx = knight != null ? knight.getX() : e.x();
        double sy = knight != null ? knight.getY() + 1.3 : e.y() + 1.3;
        double sz = knight != null ? knight.getZ() : e.z();
        double dx = e.x() - sx;
        double dy = e.y() + 1.1 - sy;
        double dz = e.z() - sz;
        int flight = 5;
        FxSolids.Model spear = SceneKit.blade(Items.TRIDENT, sx, sy, sz, 1.1f, dx, dy, dz, 1, flight, 4);
        spear.vx = dx / flight;
        spear.vy = dy / flight;
        spear.vz = dz / flight;
        spear.drag = 1;
        FxSolids.add(spear);
        live(e.classId(), sx, sy, sz, 20, flight + 6, (self, draw, t, detail) -> {
            float k = Math.min(1f, t / flight);
            float fade = 1f - self.progress(t);
            draw.ribbon(FxDraw.Tex.BEAM, new double[] {sx, sx + dx * k}, new double[] {sy, sy + dy * k},
                    new double[] {sz, sz + dz * k}, 2, 0.3f, GOLD, 0f, 0.8f * fade, -t * 0.3f);
        });
    }

    /**
     * Вызов на цели: герб над головой и нить к вызвавшему. Герб рыцаря —
     * щит в золоте, герб воина — меч в огне.
     */
    static final class Challenge implements FxStatuses.Look {
        @Override
        public void appear(FxStatuses.State state, Entity entity) {
            if (!state.solids.isEmpty()) {
                return;
            }
            int id = state.entity;
            boolean warrior = SceneKit.classOf(state.source).equals("warrior");
            FxSolids.Model crest = SceneKit.item(warrior ? Items.GOLDEN_SWORD : Items.SHIELD,
                    entity.getX(), entity.getY(), entity.getZ(), warrior ? 0.6f : 0.55f, 6, 0, 4);
            crest.follow = id;
            crest.faceFollow = false;
            crest.offsetY = entity.getBbHeight() + 0.6;
            crest.yawSpeed = 3;
            if (warrior) {
                crest.at(0, 0, -135);
            }
            crest.holding = () -> FxStatuses.has(id, "challenge");
            crest.decor = false;
            state.solids.add(crest);
            FxSolids.add(crest);
        }

        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            boolean warrior = SceneKit.classOf(state.source).equals("warrior");
            int colour = warrior ? 0xFFFF7A3A : GOLD;
            draw.sprite(FxDraw.Tex.GLOW, at.x, at.y + entity.getBbHeight() + 0.6, at.z, 0.9f, 0,
                    colour, 0.45f);
            Entity caller = entity(state.source);
            if (caller != null) {
                float partial = time - (int) time;
                Vec3 a = SceneKit.chest(entity, partial);
                Vec3 b = SceneKit.chest(caller, partial);
                draw.ribbon(FxDraw.Tex.BEAM, new double[] {a.x, (a.x + b.x) / 2, b.x},
                        new double[] {a.y, (a.y + b.y) / 2 + 0.3, b.y},
                        new double[] {a.z, (a.z + b.z) / 2, b.z}, 3, 0.05f, colour, 0.6f, 0.3f,
                        time * 0.1f);
            }
        }
    }

    // ------------------------------------------------------------------ клич

    private static void cryMark(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1.8, e.z(), 6, 0.06f, 0.18f, GOLD, 10, FxDraw.Tex.SPARK);
    }

    /** Клич: световая волна до границы, над рыцарем вспыхивает знак. */
    private static void cryWave(FxMessage.Burst e) {
        SceneKit.style(e, FxStyle.Kind.WAVE, GOLD, LIGHT, 7, 8, 9, true, 1.3f, 0, null);
        Entity knight = entity(e.source());
        double x = knight != null ? knight.getX() : e.x();
        double y = knight != null ? knight.getY() + knight.getBbHeight() + 1.0 : e.y() + 2.8;
        double z = knight != null ? knight.getZ() : e.z();
        live(e.classId(), x, y, z, 3, 20, (self, draw, t, detail) -> {
            float k = self.progress(t);
            float a = k < 0.2f ? k / 0.2f : 1f - (k - 0.2f) / 0.8f;
            draw.sprite(FxDraw.Tex.SPARK, x, y, z, 1.6f, t * 0.05f, LIGHT, a);
            draw.sprite(FxDraw.Tex.GLOW, x, y, z, 2.4f, 0, GOLD, 0.6f * a);
            draw.halo(FxDraw.Tex.RUNES, x, y, z, 0.8, 0.3, 0, 0, 1, GOLD, 0.7f * a, 1.5f, t * 0.05f);
        });
    }

    // ------------------------------------------------------------------ ударная волна

    /** Щит бьёт о землю перед рыцарем. */
    private static void shieldSlam(FxMessage.Burst e) {
        Entity knight = entity(e.source());
        if (knight == null) {
            return;
        }
        double yaw = SceneKit.facing(knight);
        double x = knight.getX() + Math.cos(yaw) * 0.9;
        double z = knight.getZ() + Math.sin(yaw) * 0.9;
        double gy = SceneKit.ground(x, z, knight.getY());
        FxSolids.Model shield = SceneKit.item(Items.SHIELD, x, gy + 1.4, z, 1.2f, 1, 6, 5);
        shield.at((float) Math.toDegrees(Math.PI / 2 - yaw), 0, 0);
        shield.vy = -0.25;
        shield.drag = 1;
        FxSolids.add(shield);
        SceneKit.sparks(x, gy + 0.2, z, 14, 0.2f, 0.3f, 0xFFE0D8C0, 12, FxDraw.Tex.WISP);
    }

    /**
     * Ударная волна: вперёд по конусу бежит волна земли — блоки той же земли
     * поднимаются дугами до кромки — со светлой кромкой; тряска у задетых.
     */
    private static void shockwave(FxMessage.Burst e) {
        SceneKit.cone(e, 4, 8, 8, 1f);
        double r = e.radius();
        double axis = Math.atan2(e.axisZ(), e.axisX());
        double half = Math.toRadians(e.angle()) / 2;
        int steps = Math.max(3, (int) Math.ceil(r));
        SceneKit.Live wave = live(e.classId(), e.x(), e.y(), e.z(), r + 2, steps * 2 + 2,
                (self, draw, t, detail) -> {
                    float k = Math.min(1f, self.progress(t) * 1.2f);
                    double rr = r * k;
                    draw.ring(FxDraw.Tex.RING, e.x(), e.y(), e.z(), Math.max(0.3, rr), 0.3,
                            axis - half, axis + half, LIGHT, 0.8f * (1f - k * 0.5f), 0.5f, 0, 0.06f);
                });
        wave.important = true;
        wave.step = (self, level, motes, emit) -> {
            if (self.age % 2 != 1) {
                return;
            }
            int ring = self.age / 2 + 1;
            double rr = Math.min(r - 0.4, ring * (r / steps));
            if (rr <= 0.5) {
                return;
            }
            int n = (int) Math.max(3, 2 * half * rr / (emit > 0.5f ? 1.0 : 1.8));
            for (int i = 0; i <= n; i++) {
                double a = axis - half + 2 * half * i / Math.max(1, n);
                double x = e.x() + Math.cos(a) * rr;
                double z = e.z() + Math.sin(a) * rr;
                double gy = FxGround.top(level, x, z, e.y());
                BlockPos pos = BlockPos.containing(x, gy - 0.5, z);
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || !state.isSolidRender(level, pos)) {
                    continue;
                }
                FxSolids.Model chunk = new FxSolids.Model(state, x, gy - 0.9, z, 0.9f, 1, 4, 3);
                chunk.vy = 0.16;
                chunk.gravity = 0.08;
                chunk.drag = 1;
                FxSolids.add(chunk);
            }
        };
        FxScreen.shakeIfInside(e.x(), e.y(), e.z(), r, 0.7f, 8);
    }

    /** Вызванного подбрасывает выше: столб света под ним. */
    private static void quakeLift(FxMessage.Burst e) {
        live(e.classId(), e.x(), e.y(), e.z(), 3, 12, (self, draw, t, detail) -> {
            float a = 1f - self.progress(t);
            draw.ribbon(FxDraw.Tex.BEAM, new double[] {e.x(), e.x()}, new double[] {e.y(), e.y() + 3},
                    new double[] {e.z(), e.z()}, 2, 0.8f, GOLD, 0.7f * a, 0f, -t * 0.2f);
        });
    }

    // ------------------------------------------------------------------ заступник

    /** Заступник: золотая вспышка у союзника. */
    private static void guard(FxMessage.Burst e) {
        SceneKit.rise(e.x(), e.y(), e.z(), 0.8, 16, GOLD, FxDraw.Tex.SPARK);
        Entity knight = entity(e.source());
        if (knight != null) {
            ScenesRogue.afterimages(knight.getId(), GOLD, 2, 2);
        }
    }

    /** Под защитой: перед союзником висит полупрозрачный щит, вокруг — мягкий золотой свет. */
    static final class Guarded implements FxStatuses.Look {
        @Override
        public void appear(FxStatuses.State state, Entity entity) {
            if (!state.solids.isEmpty()) {
                return;
            }
            int id = state.entity;
            FxSolids.Model shield = SceneKit.item(Items.SHIELD, entity.getX(), entity.getY(),
                    entity.getZ(), 0.9f, 5, 0, 5);
            shield.follow = id;
            shield.offsetY = entity.getBbHeight() * 0.55;
            shield.forward = 0.7;
            shield.at(180, 0, 0);
            shield.holding = () -> FxStatuses.has(id, "guarded");
            shield.decor = false;
            state.solids.add(shield);
            FxSolids.add(shield);
        }

        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            draw.sprite(FxDraw.Tex.GLOW, at.x, at.y + entity.getBbHeight() * 0.55, at.z, 1.8f, 0,
                    GOLD, 0.2f);
        }
    }

    // ------------------------------------------------------------------ карающий щит

    /** Призрачный щит бьёт перед рыцарем, на цели — вспышка-печать. */
    private static void bash(FxMessage.Burst e) {
        Entity knight = entity(e.source());
        if (knight != null) {
            double dx = e.x() - knight.getX();
            double dz = e.z() - knight.getZ();
            double yaw = Math.atan2(dz, dx);
            FxSolids.Model shield = SceneKit.item(Items.SHIELD, knight.getX() + Math.cos(yaw) * 0.5,
                    knight.getY() + 1.1, knight.getZ() + Math.sin(yaw) * 0.5, 1.0f, 1, 3, 4);
            shield.at((float) Math.toDegrees(Math.PI / 2 - yaw), 0, 0);
            shield.vx = dx / 5;
            shield.vz = dz / 5;
            shield.drag = 0.6;
            FxSolids.add(shield);
        }
        double yaw = SceneKit.yawFrom(e);
        live(e.classId(), e.x(), e.y() + 1.1, e.z(), 3, 12, (self, draw, t, detail) -> {
            float k = self.progress(t);
            draw.halo(FxDraw.Tex.RUNES, e.x(), e.y() + 1.1, e.z(), 0.5 + 0.4 * k, 0.3,
                    Math.cos(yaw), 0, Math.sin(yaw), GOLD, 0.9f * (1f - k), 1.5f, t * 0.05f);
        });
        SceneKit.sparks(e.x(), e.y() + 1.1, e.z(), 14, 0.2f, 0.2f, LIGHT, 8, FxDraw.Tex.SPARK);
    }

    // ------------------------------------------------------------------ глухая оборона

    private static void wallRise(FxMessage.Burst e) {
        SceneKit.rise(e.x(), e.y(), e.z(), 0.8, 12, GOLD, FxDraw.Tex.SPARK);
    }

    /** Глухая оборона: перед рыцарем стена из призрачных щитов; клятв больше — щитов больше. */
    static final class ShieldWall implements FxStatuses.Look {
        @Override
        public void appear(FxStatuses.State state, Entity entity) {
            if (!state.solids.isEmpty()) {
                return;
            }
            int id = state.entity;
            FxStatuses.State oath = FxStatuses.get(id, "oath");
            int count = oath != null && oath.stacks >= 3 ? 5 : 3;
            for (int i = 0; i < count; i++) {
                double side = (i - (count - 1) / 2.0) * 0.55;
                FxSolids.Model shield = SceneKit.item(Items.SHIELD, entity.getX(), entity.getY(),
                        entity.getZ(), 1.0f, 4 + i, 0, 5);
                shield.follow = id;
                shield.offsetY = entity.getBbHeight() * 0.5;
                shield.forward = 0.95 - Math.abs(side) * 0.25;
                shield.side = side;
                shield.at(180, 0, 0);
                shield.holding = () -> FxStatuses.has(id, "shield_wall");
                shield.decor = false;
                state.solids.add(shield);
                FxSolids.add(shield);
            }
        }
    }

    // ------------------------------------------------------------------ возмездие

    private static void swordsReady(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1.1, e.z(), 10, 0.1f, 0.18f, LIGHT, 8, FxDraw.Tex.SPARK);
    }

    /** Возмездие: вокруг рыцаря вращаются три золотых меча остриём наружу. */
    static final class Retribution implements FxStatuses.Look {
        @Override
        public void appear(FxStatuses.State state, Entity entity) {
            if (!state.solids.isEmpty()) {
                return;
            }
            int id = state.entity;
            for (int i = 0; i < 3; i++) {
                FxSolids.Model sword = SceneKit.item(Items.GOLDEN_SWORD, entity.getX(), entity.getY(),
                        entity.getZ(), 0.7f, 3, 0, 4);
                sword.follow = id;
                sword.offsetY = entity.getBbHeight() * 0.55;
                sword.orbit = 0.95;
                sword.orbitAngle = Math.PI * 2 * i / 3;
                sword.orbitSpeed = 0.2;
                sword.holding = () -> FxStatuses.has(id, "retribution");
                sword.decor = false;
                state.solids.add(sword);
                FxSolids.add(sword);
            }
        }
    }

    /** Ответ: три меча летят в ударившего. */
    private static void swordsStrike(FxMessage.Burst e) {
        Entity knight = entity(e.source());
        if (knight == null) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            double a = Math.PI * 2 * i / 3;
            double sx = knight.getX() + Math.cos(a) * 0.9;
            double sy = knight.getY() + 1.1;
            double sz = knight.getZ() + Math.sin(a) * 0.9;
            double dx = e.x() - sx;
            double dy = e.y() + 1.0 - sy;
            double dz = e.z() - sz;
            int flight = 4 + i;
            FxSolids.Model sword = SceneKit.blade(Items.GOLDEN_SWORD, sx, sy, sz, 0.7f, dx, dy, dz, 1,
                    flight, 3);
            sword.vx = dx / flight;
            sword.vy = dy / flight;
            sword.vz = dz / flight;
            sword.drag = 1;
            FxSolids.add(sword);
        }
        SceneKit.sparks(e.x(), e.y() + 1, e.z(), 16, 0.2f, 0.2f, GOLD, 10, FxDraw.Tex.SPARK);
    }

    // ------------------------------------------------------------------ знамя

    /**
     * Знамя: жёлтое знамя вбито в центр, полотно колышется; круг размечен
     * кальцитом по краю — светлыми камнями.
     */
    private static void bannerZone(FxMessage.ZoneOn on, FxKinds.Zone zone) {
        double gy = SceneKit.ground(on.x(), on.z(), on.y());
        FxSolids.Model banner = SceneKit.item(Items.YELLOW_BANNER, on.x(), gy + 1.4, on.z(), 2.2f, 8, 0,
                8);
        banner.sway = 6;
        banner.holding = () -> !zone.ending();
        banner.decor = false;
        FxSolids.add(banner);
        double r = on.radius();
        int stones = Math.min(28, Math.max(8, (int) (r * 3)));
        for (int i = 0; i < stones; i++) {
            double a = Math.PI * 2 * i / stones;
            double x = on.x() + Math.cos(a) * r;
            double z = on.z() + Math.sin(a) * r;
            FxSolids.Model stone = SceneKit.block(Blocks.CALCITE.defaultBlockState(), x,
                    SceneKit.ground(x, z, on.y()), z, 0.3f, 6 + i % 5, 0, 6);
            stone.at((float) (i * 37 % 90), 0, 0);
            stone.holding = () -> !zone.ending();
            FxSolids.add(stone);
        }
        SceneKit.sparks(on.x(), gy + 0.2, on.z(), 12, 0.15f, 0.3f, 0xFFE0D8C0, 12, FxDraw.Tex.WISP);
    }

    private static void bannerPulse(FxMessage.Burst e) {
        SceneKit.border(e, 6, 2, 6, 0.4f);
    }

    private static void bannerGuard(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1.2, e.z(), 3, 0.04f, 0.14f, GOLD, 10, FxDraw.Tex.SPARK);
    }

    /** Под знаменем: знак знамени на груди — золотой огонёк. */
    static final class BannerGuard implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            double look = Math.toRadians(entity.getYRot());
            double y = at.y + entity.getBbHeight() * 0.65;
            draw.sprite(FxDraw.Tex.SPARK, at.x - Math.sin(look) * 0.32, y, at.z + Math.cos(look) * 0.32,
                    0.3f, time * 0.05f, GOLD, 0.8f);
        }
    }

    // ------------------------------------------------------------------ фортеция

    /**
     * Абсолютная фортеция: вокруг рыцаря до границы поднимается купол из
     * светлых стен — белое стекло рядами, держится, пока стоит фортеция.
     */
    private static void fortress(FxMessage.Burst e) {
        SceneKit.style(e, FxStyle.Kind.WAVE, GOLD, LIGHT, 8, 10, 10, true, 1.2f, 0, null);
        int knight = e.source();
        double r = e.radius();
        int around = Math.min(40, Math.max(12, (int) (Math.PI * 2 * r / 1.0)));
        for (int row = 0; row < 3; row++) {
            double rr = r * Math.cos(row * 0.45);
            for (int i = 0; i < around; i++) {
                if (row == 2 && i % 2 == 1) {
                    continue;
                }
                double a = Math.PI * 2 * (i + row * 0.5) / around;
                double x = e.x() + Math.cos(a) * rr;
                double z = e.z() + Math.sin(a) * rr;
                double gy = SceneKit.ground(x, z, e.y());
                FxSolids.Model pane = SceneKit.block(Blocks.WHITE_STAINED_GLASS.defaultBlockState(), x,
                        gy + row * 0.95, z, 0.95f, 6 + row * 4, 0, 10);
                pane.at((float) -Math.toDegrees(a) + 90, 0, 0);
                pane.holding = () -> FxStatuses.has(knight, "fortress");
                pane.decor = row == 2;
                FxSolids.add(pane);
            }
        }
    }

    /** Фортеция на рыцаре: ровная золотая рамка у него самого. */
    static final class Fortress implements FxStatuses.Look {
        @Override
        public void screen(FxStatuses.State state, net.minecraft.client.gui.GuiGraphics g, int w,
                           int h, float time) {
            FxScreen.edges(g, w, h, GOLD, 0.22f);
        }
    }

    // ------------------------------------------------------------------ правосудие

    private static void judgmentWave(FxMessage.Burst e) {
        SceneKit.style(e, FxStyle.Kind.WAVE, GOLD, LIGHT, 7, 6, 9, true, 1.4f, 0, null);
    }

    /** Правосудие: над вызванным с неба падает световой меч. */
    private static void judgmentSword(FxMessage.Burst e) {
        double top = e.y() + 7;
        FxSolids.Model sword = SceneKit.item(Items.GOLDEN_SWORD, e.x(), top, e.z(), 2.4f, 1, 6, 6);
        sword.at(0, 0, -135);
        int fall = 5;
        sword.vy = -(top - (e.y() + 1.2)) / fall;
        sword.drag = 1;
        FxSolids.add(sword);
        live(e.classId(), e.x(), e.y(), e.z(), 8, fall + 8, (self, draw, t, detail) -> {
            float k = Math.min(1f, t / fall);
            float fade = 1f - self.progress(t);
            draw.ribbon(FxDraw.Tex.BEAM, new double[] {e.x(), e.x()},
                    new double[] {top - (top - e.y()) * k + 1, top + 1}, new double[] {e.z(), e.z()},
                    2, 0.9f, GOLD, 0.7f * fade, 0f, 0);
        }).step = (self, level, motes, emit) -> {
            if (self.age == fall) {
                sword.vy = 0;
                SceneKit.sparks(e.x(), e.y() + 0.5, e.z(), 24, 0.3f, 0.22f, LIGHT, 12,
                        FxDraw.Tex.SPARK);
                if (SceneKit.selfNear(e.x(), e.y(), e.z(), 1.2)) {
                    FxScreen.flash(LIGHT, 8);
                }
            }
        };
    }
}
