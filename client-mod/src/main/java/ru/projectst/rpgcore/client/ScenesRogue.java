package ru.projectst.rpgcore.client;

import static ru.projectst.rpgcore.client.SceneKit.entity;
import static ru.projectst.rpgcore.client.SceneKit.live;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Плут: рывок, пронзающий удар, шаг призрака, дым, удар из тени, веер
 * клинков, отражение, орлиное зрение, метка смерти, кураж.
 *
 * <p>Сценарий — раздел 6 {@code docs/vfx/skill-visuals.md}. Облик — сталь:
 * бирюзовые осколки, дым. Клинки — железные мечи, уменьшенные до кинжала;
 * двойники — полупрозрачная модель самого плута.
 */
final class ScenesRogue {

    static final int STEEL = 0xFF5FB8D0;
    static final int STEEL_LIGHT = 0xFFE6F6FF;
    private static final int SMOKE = 0xFF8A9096;

    /** Сколько ударов пришлось по метке смерти: череп трескается сильнее. */
    private static final Map<Integer, Integer> MARK_HITS = new HashMap<>();

    private ScenesRogue() {
    }

    static FxScenes.Set scenes() {
        return new FxScenes.Set(BURSTS, Map.of(), ZONES, BOLTS, Map.of(), STATUSES);
    }

    private static final Map<String, FxScenes.BurstScene> BURSTS = Map.ofEntries(
            Map.entry("rogue_dash_afterimage", ScenesRogue::dashAfterimage),
            Map.entry("rogue_pierce_lunge", ScenesRogue::pierceLunge),
            Map.entry("rogue_pierce_cut", ScenesRogue::pierceCut),
            Map.entry("rogue_ghost_fade", ScenesRogue::ghostFade),
            Map.entry("rogue_ghost_cross", ScenesRogue::ghostCross),
            Map.entry("rogue_smoke_pop", ScenesRogue::smokePop),
            Map.entry("rogue_smoke_cloud", ScenesRogue::smokeCloud),
            Map.entry("rogue_shadow_cut", ScenesRogue::shadowCut),
            Map.entry("rogue_knife_fan", ScenesRogue::knifeFan),
            Map.entry("rogue_knife_hit", ScenesRogue::knifeHit),
            Map.entry("rogue_mirror_shatter", ScenesRogue::mirrorShatter),
            Map.entry("rogue_eagle_focus", ScenesRogue::eagleFocus),
            Map.entry("rogue_death_mark", ScenesRogue::deathMark),
            Map.entry("rogue_mark_hit", ScenesRogue::markHit),
            Map.entry("rogue_mark_blast", ScenesRogue::markBlast),
            Map.entry("rogue_thrill_rise", ScenesRogue::thrillRise),
            Map.entry("rogue_thrill_chime", ScenesRogue::thrillChime));

    private static final Map<String, FxScenes.BoltScene> BOLTS = Map.ofEntries(
            Map.entry("rogue_smoke_grenade", ScenesRogue::smokeGrenade));

    private static final Map<String, FxScenes.ZoneScene> ZONES = Map.ofEntries(
            Map.entry("rogue_mirror", ScenesRogue::mirror));

    private static final Map<String, FxStatuses.Look> STATUSES = Map.ofEntries(
            Map.entry("ambush", new Unseen(0xFF5A6A70)),
            Map.entry("eagle_eye", new EagleEye()),
            Map.entry("death_mark", new DeathMark()),
            Map.entry("thrill", new Thrill()),
            Map.entry("exposed", new Exposed()));

    // ------------------------------------------------------------------ рывок

    /** Рывок: три тающих силуэта-контура вдоль пути. */
    private static void dashAfterimage(FxMessage.Burst e) {
        afterimages(e.source(), STEEL, 3, 2);
    }

    /** Силуэты существа по пути: по одному каждые {@code every} тиков. */
    static void afterimages(int entityId, int tint, int count, int every) {
        Entity who = entity(entityId);
        if (who == null) {
            return;
        }
        SceneKit.Live trail = live("rogue", who.getX(), who.getY(), who.getZ(), 12,
                count * every + 1, (self, draw, t, detail) -> {
                });
        trail.step = (self, level, motes, emit) -> {
            if (self.age % every != 1) {
                return;
            }
            Entity now = level.getEntity(entityId);
            if (now == null) {
                return;
            }
            FxSolids.Ghost ghost = new FxSolids.Ghost(now, 1, 0, 8);
            ghost.tint = tint;
            ghost.alpha = 0.35f;
            FxSolids.add(ghost);
        };
    }

    // ------------------------------------------------------------------ пронзающий

    /** Пронзающий удар: за плутом тянется лезвие-полоса по пути. */
    private static void pierceLunge(FxMessage.Burst e) {
        int id = e.source();
        Entity rogue = entity(id);
        if (rogue == null) {
            return;
        }
        double sx = rogue.getX();
        double sy = rogue.getY() + 1.0;
        double sz = rogue.getZ();
        live(e.classId(), sx, sy, sz, 10, 12, (self, draw, t, detail) -> {
            Entity now = entity(id);
            if (now == null) {
                return;
            }
            Vec3 at = now.getPosition(t - (int) t);
            float k = self.progress(t);
            float alpha = 1f - k;
            draw.ribbon(FxDraw.Tex.BEAM, new double[] {sx, at.x}, new double[] {sy, at.y + 1.0},
                    new double[] {sz, at.z}, 2, 0.5f, STEEL, 0f, 0.8f * alpha, t * 0.4f);
            draw.ribbon(FxDraw.Tex.BEAM, new double[] {sx, at.x}, new double[] {sy, at.y + 1.0},
                    new double[] {sz, at.z}, 2, 0.12f, STEEL_LIGHT, 0f, alpha, 0);
        });
    }

    /** У каждого задетого — косой разрез в воздухе. */
    private static void pierceCut(FxMessage.Burst e) {
        SceneKit.cut(e.classId(), e.x(), e.y() + 1.1, e.z(), SceneKit.yawFrom(e), 0.6, 1.6, STEEL,
                8, 0.35f);
        SceneKit.sparks(e.x(), e.y() + 1.1, e.z(), 6, 0.15f, 0.16f, STEEL_LIGHT, 6, FxDraw.Tex.SHARD);
    }

    // ------------------------------------------------------------------ шаг призрака

    /** Плут растворяется в дрожащем воздухе: его отпечаток тает, вокруг — дым. */
    private static void ghostFade(FxMessage.Burst e) {
        Entity rogue = entity(e.source());
        if (rogue != null) {
            FxSolids.Ghost ghost = new FxSolids.Ghost(rogue, 1, 2, 14);
            ghost.tint = STEEL;
            ghost.alpha = 0.5f;
            ghost.vy = 0.02;
            FxSolids.add(ghost);
        }
        SceneKit.sparks(e.x(), e.y() + 0.9, e.z(), 14, 0.06f, 0.4f, SMOKE, 18, FxDraw.Tex.WISP);
    }

    /** Удар из невидимости: проявление вспышкой и разрез крест-накрест. */
    private static void ghostCross(FxMessage.Burst e) {
        double yaw = SceneKit.yawFrom(e);
        SceneKit.cut(e.classId(), e.x(), e.y() + 1.1, e.z(), yaw, 0.8, 1.8, STEEL, 9, 0.4f);
        SceneKit.cut(e.classId(), e.x(), e.y() + 1.1, e.z(), yaw, -0.8, 1.8, STEEL, 11, 0.4f);
        SceneKit.styleAt(e, e.x(), e.y() + 1, e.z(), 0.8f, FxStyle.Kind.FLASH, STEEL, STEEL_LIGHT,
                2, 2, 6, 1f, 1.2f, FxDraw.Tex.SHARD);
    }

    /**
     * Невидимость: со стороны её не видно вовсе (на то она и невидимость), а
     * сам невидимый видит по краям экрана лёгкое обесцвечивание — помнит, что
     * спрятан.
     */
    static final class Unseen implements FxStatuses.Look {
        private final int tint;

        Unseen(int tint) {
            this.tint = tint;
        }

        @Override
        public void screen(FxStatuses.State state, net.minecraft.client.gui.GuiGraphics g, int w,
                           int h, float time) {
            FxScreen.edges(g, w, h, tint, 0.35f);
        }
    }

    // ------------------------------------------------------------------ дым

    /** Дымовая граната: шарик пороха кувыркается по дуге. */
    private static void smokeGrenade(FxMessage.Projectile p, FxKinds.Bolt bolt) {
        bolt.bare = true;
        FxSolids.Model ball = SceneKit.item(Items.FIREWORK_STAR, p.x(), p.y(), p.z(), 0.5f, 1, 0, 2);
        ball.anchor = () -> bolt.at(1f);
        ball.tumble(25);
        ball.decor = false;
        FxSolids.add(ball);
    }

    /** Хлопок гранаты у цели. */
    private static void smokePop(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 0.5, e.z(), 12, 0.2f, 0.3f, SMOKE, 12, FxDraw.Tex.WISP);
    }

    /**
     * Облако серого дыма до границы: клубится и медленно оседает; кто внутри —
     * у того темнеет экран, пока облако держится.
     */
    private static void smokeCloud(FxMessage.Burst e) {
        SceneKit.border(e, 4, 20, 12, 0.6f);
        double r = e.radius();
        SceneKit.Live cloud = live(e.classId(), e.x(), e.y(), e.z(), r + 2, 60,
                (self, draw, t, detail) -> {
                    float k = self.progress(t);
                    draw.sector(FxDraw.Tex.FILL, e.x(), e.y(), e.z(), r, 0, Math.PI * 2, 0xFF505458,
                            0.4f * (1f - k), 0.03f);
                });
        cloud.important = true;
        cloud.step = (self, level, motes, emit) -> {
            float k = self.progress(self.age);
            if (self.age < 40) {
                int n = (int) Math.max(1, r * r * 0.35 * Math.max(0.3f, emit) * (1f - k));
                for (int i = 0; i < n; i++) {
                    double[] p = FxGeometry.insideCircle(SceneKit.RANDOM, e.x(), e.y(), e.z(), r);
                    double gy = FxGround.top(level, p[0], p[2], e.y());
                    motes.spawn(p[0], gy + 0.2 + FxMotes.random() * 1.8 * (1f - k), p[2],
                            FxMotes.jitter(0.01f), -0.004f, FxMotes.jitter(0.01f),
                            0.8f + FxMotes.random() * 0.5f, i % 3 == 0 ? 0xFFB0B4B8 : SMOKE, 30,
                            0.99f, FxDraw.Tex.WISP);
                }
            }
            if (SceneKit.selfNear(e.x(), e.y(), e.z(), r)) {
                FxScreen.tint(0xFF101214, 0.6f * (1f - k));
            }
        };
    }

    // ------------------------------------------------------------------ удар из тени

    /** Плут выходит из клуба дыма за спиной цели; удар — косой бирюзовый разрез. */
    private static void shadowCut(FxMessage.Burst e) {
        Entity rogue = entity(e.source());
        if (rogue != null) {
            SceneKit.sparks(rogue.getX(), rogue.getY() + 0.9, rogue.getZ(), 14, 0.08f, 0.45f, SMOKE,
                    16, FxDraw.Tex.WISP);
        }
        SceneKit.cut(e.classId(), e.x(), e.y() + 1.1, e.z(), SceneKit.yawFrom(e) + Math.PI, -0.7,
                1.8, STEEL, 9, 0.4f);
    }

    // ------------------------------------------------------------------ веер

    /**
     * Веер клинков: кинжалы разлетаются во все стороны до границы, втыкаются в
     * землю у края и тают.
     */
    static void knifeFan(FxMessage.Burst e) {
        SceneKit.border(e, 4, 8, 6, 0.8f);
        double r = e.radius();
        int n = Math.min(16, Math.max(8, (int) (r * 2.5)));
        double start = SceneKit.RANDOM.nextDouble() * Math.PI * 2;
        double y = e.y() + 1.0;
        for (int i = 0; i < n; i++) {
            double a = start + Math.PI * 2 * i / n;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            int flight = 5;
            double reach = r - 0.2;
            FxSolids.Model knife = SceneKit.item(Items.IRON_SWORD, e.x() + dx * 0.5, y,
                    e.z() + dz * 0.5, 0.45f, 1, flight + 10, 8);
            knife.face(dx, -0.15, dz);
            knife.at(knife.yaw, knife.pitch + 90, 45);
            knife.vx = dx * (reach - 0.5) / flight;
            knife.vz = dz * (reach - 0.5) / flight;
            knife.vy = (SceneKit.ground(e.x() + dx * reach, e.z() + dz * reach, e.y()) + 0.25 - y)
                    / flight;
            knife.drag = 1;
            // Долетел — встал: сопротивление гасит скорость после полёта.
            SceneKit.Live stop = live(e.classId(), e.x(), e.y(), e.z(), r + 1, flight + 1,
                    (self, draw, t, detail) -> {
                    });
            stop.step = (self, level, motes, emit) -> {
                if (self.age == flight) {
                    knife.vx = 0;
                    knife.vy = 0;
                    knife.vz = 0;
                }
            };
            FxSolids.add(knife);
        }
    }

    /** Попавший кинжал торчит в цели и дрожит, у плута — искра выносливости. */
    static void knifeHit(FxMessage.Burst e) {
        double yaw = SceneKit.yawFrom(e);
        FxSolids.Model knife = SceneKit.item(Items.IRON_SWORD, e.x() - Math.cos(yaw) * 0.35,
                e.y() + 1.0, e.z() - Math.sin(yaw) * 0.35, 0.4f, 1, 12, 6);
        knife.face(Math.cos(yaw), 0, Math.sin(yaw));
        knife.at(knife.yaw, knife.pitch + 90, 45);
        knife.sway = 6;
        FxSolids.add(knife);
        Entity rogue = entity(e.source());
        if (rogue != null) {
            SceneKit.sparks(rogue.getX(), rogue.getY() + 1.2, rogue.getZ(), 5, 0.08f, 0.18f,
                    STEEL_LIGHT, 8, FxDraw.Tex.SPARK);
        }
    }

    // ------------------------------------------------------------------ отражение

    /**
     * Отражение: на месте плута стоит его полупрозрачный двойник с бирюзовыми
     * трещинами, вокруг — круг срабатывания (граница зоны рисует стиль).
     */
    private static void mirror(FxMessage.ZoneOn on, FxKinds.Zone zone) {
        Entity rogue = entity(on.owner());
        if (rogue == null) {
            return;
        }
        FxSolids.Ghost ghost = new FxSolids.Ghost(rogue, 4, 0, 6);
        ghost.x = ghost.prevX = on.x();
        ghost.y = ghost.prevY = on.y();
        ghost.z = ghost.prevZ = on.z();
        ghost.tint = 0xFFBFE8F0;
        ghost.alpha = 0.6f;
        ghost.holding = () -> !zone.ending();
        ghost.decor = false;
        FxSolids.add(ghost);
        SceneKit.Live cracks = live(on.classId(), on.x(), on.y() + 1, on.z(), 3, 6,
                (self, draw, t, detail) -> {
                    float fade = 1f - self.progress(t);
                    for (int i = 0; i < 3; i++) {
                        double a = i * 2.1 + 0.4;
                        double y0 = on.y() + 0.4 + i * 0.5;
                        draw.ribbon(FxDraw.Tex.BEAM,
                                new double[] {on.x() + Math.cos(a) * 0.25, on.x() - Math.cos(a) * 0.15},
                                new double[] {y0, y0 + 0.5},
                                new double[] {on.z() + Math.sin(a) * 0.25, on.z() - Math.sin(a) * 0.15},
                                2, 0.06f, STEEL, 0.8f * fade, 0.8f * fade, 0);
                    }
                    draw.sprite(FxDraw.Tex.GLOW, on.x(), on.y() + 1, on.z(), 1.6f, 0, STEEL,
                            0.15f * fade);
                });
        cracks.holding = () -> !zone.ending();
    }

    /** Двойник разбит: осколки стекла веером по радиусу, звон. */
    private static void mirrorShatter(FxMessage.Burst e) {
        for (int i = 0; i < 10; i++) {
            SceneKit.debris(Blocks.GLASS.defaultBlockState(), e.x(), e.y() + 0.4 + FxMotes.random(),
                    e.z(), 0.12f, 0.2f);
        }
        SceneKit.sparks(e.x(), e.y() + 1, e.z(), 30, 0.35f, 0.24f, STEEL_LIGHT, 12,
                FxDraw.Tex.SHARD);
        SceneKit.styled(e, FxStyle.Kind.WAVE);
    }

    /** Уязвимость: треснувший полупрозрачный щит мигает на груди. */
    static final class Exposed implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            float blink = 0.25f + 0.25f * (float) Math.max(0, Math.sin(time * 0.35));
            double y = at.y + entity.getBbHeight() * 0.62;
            draw.sprite(FxDraw.Tex.SHARD, at.x, y, at.z, 0.7f, 0.3f, 0xFFFFC060, blink);
            draw.sprite(FxDraw.Tex.SHARD, at.x, y, at.z, 0.5f, -0.5f, 0xFFFFE0A0, blink * 0.8f);
        }
    }

    // ------------------------------------------------------------------ орлиное зрение

    private static void eagleFocus(FxMessage.Burst e) {
        SceneKit.rise(e.x(), e.y(), e.z(), 0.6, 14, STEEL_LIGHT, FxDraw.Tex.SPARK);
    }

    /** Глаза плута светятся бирюзой; у него самого — тонкая рамка прицела. */
    static final class EagleEye implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            double look = Math.toRadians(entity.getYHeadRot());
            double fx = -Math.sin(look);
            double fz = Math.cos(look);
            double sx = -fz;
            double sz = fx;
            double y = at.y + entity.getEyeHeight() + 0.02;
            for (int i = -1; i <= 1; i += 2) {
                draw.sprite(FxDraw.Tex.GLOW, at.x + fx * 0.3 + sx * 0.12 * i, y,
                        at.z + fz * 0.3 + sz * 0.12 * i, 0.22f, 0, STEEL, 0.9f);
            }
        }

        @Override
        public void screen(FxStatuses.State state, net.minecraft.client.gui.GuiGraphics g, int w,
                           int h, float time) {
            FxScreen.edges(g, w, h, STEEL, 0.12f);
            int cx = w / 2;
            int cy = h / 2;
            int c = FxDraw.withAlpha(STEEL, 0.5f);
            // Уголки прицела вокруг перекрестья: тонко, середина свободна.
            int d = 18;
            int l = 6;
            g.fill(cx - d, cy - d, cx - d + l, cy - d + 1, c);
            g.fill(cx - d, cy - d, cx - d + 1, cy - d + l, c);
            g.fill(cx + d - l, cy - d, cx + d, cy - d + 1, c);
            g.fill(cx + d - 1, cy - d, cx + d, cy - d + l, c);
            g.fill(cx - d, cy + d - 1, cx - d + l, cy + d, c);
            g.fill(cx - d, cy + d - l, cx - d + 1, cy + d, c);
            g.fill(cx + d - l, cy + d - 1, cx + d, cy + d, c);
            g.fill(cx + d - 1, cy + d - l, cx + d, cy + d, c);
        }
    }

    // ------------------------------------------------------------------ метка смерти

    private static void deathMark(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y(), e.z(), 1.2f, FxStyle.Kind.SINK, 0xFFD04040, STEEL_LIGHT,
                4, 8, 10, 1f, 0, FxDraw.Tex.SHARD);
    }

    /** Череп над целью: чем больше ударов по метке, тем сильнее дрожит и краснее. */
    static final class DeathMark implements FxStatuses.Look {
        @Override
        public void appear(FxStatuses.State state, Entity entity) {
            if (!state.solids.isEmpty()) {
                return;
            }
            int id = state.entity;
            MARK_HITS.put(id, 0);
            FxSolids.Model skull = SceneKit.item(Items.SKELETON_SKULL, entity.getX(), entity.getY(),
                    entity.getZ(), 0.6f, 6, 0, 4);
            skull.follow = id;
            skull.offsetY = entity.getBbHeight() + 0.55;
            skull.holding = () -> FxStatuses.has(id, "death_mark");
            skull.decor = false;
            state.solids.add(skull);
            FxSolids.add(skull);
        }

        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            int hits = MARK_HITS.getOrDefault(state.entity, 0);
            float heat = Math.min(1f, hits / 8f);
            double y = at.y + entity.getBbHeight() + 0.55;
            double shake = 0.02 + 0.05 * heat;
            draw.sprite(FxDraw.Tex.GLOW, at.x + Math.sin(time * 1.7) * shake, y,
                    at.z + Math.cos(time * 2.3) * shake, 0.8f + 0.6f * heat, 0,
                    FxDraw.mix(0xFF808080, 0xFFFF3030, heat), 0.35f + 0.4f * heat);
            for (int i = 0; i < Math.min(6, hits); i++) {
                double a = i * 1.1;
                draw.sprite(FxDraw.Tex.SHARD, at.x + Math.cos(a) * 0.2, y + Math.sin(a) * 0.15,
                        at.z + Math.sin(a) * 0.2, 0.18f, (float) a, 0xFFFF5050, 0.7f);
            }
        }

        @Override
        public void gone(FxStatuses.State state, Entity entity) {
            MARK_HITS.remove(state.entity);
        }
    }

    /** Удар по метке: трещина на черепе, тиканье выше с каждым ударом. */
    private static void markHit(FxMessage.Burst e) {
        int count = 0;
        Integer best = null;
        double bestD = 4;
        for (Map.Entry<Integer, Integer> hit : MARK_HITS.entrySet()) {
            Entity target = entity(hit.getKey());
            if (target != null) {
                double d = target.distanceToSqr(e.x(), e.y(), e.z());
                if (d < bestD) {
                    bestD = d;
                    best = hit.getKey();
                }
            }
        }
        if (best != null) {
            count = MARK_HITS.merge(best, 1, Integer::sum);
        }
        SceneKit.sparks(e.x(), e.y() + 1.2, e.z(), 4, 0.1f, 0.16f, 0xFFFF6060, 6, FxDraw.Tex.SHARD);
        SceneKit.sound("rogue.mark.tick", e.x(), e.y() + 1, e.z(), 0.6f,
                Math.min(2f, 0.9f + count * 0.08f));
    }

    /** Время вышло: череп взрывается вспышкой, тряска у тех, кто рядом. */
    private static void markBlast(FxMessage.Burst e) {
        for (int i = 0; i < 5; i++) {
            SceneKit.debris(Blocks.BONE_BLOCK.defaultBlockState(), e.x(), e.y() + 2.2, e.z(), 0.1f,
                    0.12f);
        }
        SceneKit.styleAt(e, e.x(), e.y() + 1, e.z(), 2.5f, FxStyle.Kind.WAVE, 0xFFD04040,
                STEEL_LIGHT, 4, 3, 8, 1.5f, 0, FxDraw.Tex.SHARD);
        SceneKit.sparks(e.x(), e.y() + 1.6, e.z(), 30, 0.35f, 0.26f, 0xFFFF6060, 12,
                FxDraw.Tex.SHARD);
        FxScreen.shakeIfInside(e.x(), e.y(), e.z(), 3, 0.6f, 8);
    }

    // ------------------------------------------------------------------ кураж

    private static void thrillRise(FxMessage.Burst e) {
        SceneKit.rise(e.x(), e.y(), e.z(), 0.8, 18, STEEL, FxDraw.Tex.SHARD);
    }

    /** Попадание в кураже: бирюзовая искра у плута. */
    private static void thrillChime(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1.1, e.z(), 6, 0.1f, 0.18f, STEEL_LIGHT, 8,
                FxDraw.Tex.SPARK);
    }

    /** Кураж: вихрь бирюзовых искр у ног и короткий след за спиной. */
    static final class Thrill implements FxStatuses.Look {
        @Override
        public void ambient(FxStatuses.State state, Entity entity, float emit) {
            FxMotes motes = FxEffects.motes();
            if (FxMotes.random() < 0.6f * emit) {
                double a = entity.tickCount * 0.6;
                motes.spawn(entity.getX() + Math.cos(a) * 0.5, entity.getY() + 0.1,
                        entity.getZ() + Math.sin(a) * 0.5, (float) -Math.sin(a) * 0.06f, 0.03f,
                        (float) Math.cos(a) * 0.06f, 0.16f, STEEL, 10, 0.9f, FxDraw.Tex.SHARD);
            }
            Vec3 move = entity.getDeltaMovement();
            if (move.horizontalDistanceSqr() > 0.01 && FxMotes.random() < 0.8f * emit) {
                motes.spawn(entity.getX() - move.x * 2, entity.getY() + 0.9 + FxMotes.jitter(0.4f),
                        entity.getZ() - move.z * 2, 0, 0, 0, 0.18f, STEEL_LIGHT, 6, 0.9f,
                        FxDraw.Tex.SPARK);
            }
        }
    }
}
