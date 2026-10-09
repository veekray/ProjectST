package ru.projectst.rpgcore.client;

import static ru.projectst.rpgcore.client.SceneKit.entity;
import static ru.projectst.rpgcore.client.SceneKit.live;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Берсерк: жар и перегрев, кровавый разрез, мясорубка, цепной крюк,
 * землетрясение, рёв, вихрь смерти, удар палача, игнорирование боли, голод,
 * бессмертная ярость.
 *
 * <p>Сценарий — раздел 10 {@code docs/vfx/skill-visuals.md}. Облик — кровь и
 * угли. Модели: крюк — крюк растяжки на цепи, топор палача — незеритовый
 * топор, волна земли — блоки той самой земли, по которой она бежит.
 */
final class ScenesBerserker {

    static final int BLOOD = 0xFFE0321E;
    static final int EMBER = 0xFFFFB347;
    private static final int DEEP = 0xFF8A1010;

    /** Обороты вихря смерти подряд: след каждого следующего гуще. */
    private static final Map<Integer, int[]> SPINS = new HashMap<>();

    private ScenesBerserker() {
    }

    static FxScenes.Set scenes() {
        return new FxScenes.Set(BURSTS, MARKS, Map.of(), BOLTS, Map.of(), STATUSES);
    }

    private static final Map<String, FxScenes.BurstScene> BURSTS = Map.ofEntries(
            Map.entry("berserker_heat", ScenesBerserker::heat),
            Map.entry("berserker_overheat", ScenesBerserker::overheat),
            Map.entry("berserker_blood_arc", ScenesBerserker::bloodArc),
            Map.entry("berserker_carnage_rend", ScenesBerserker::carnageRend),
            Map.entry("berserker_hook_pull", ScenesBerserker::hookPull),
            Map.entry("berserker_quake", ScenesBerserker::quake),
            Map.entry("berserker_roar", ScenesBerserker::roar),
            Map.entry("berserker_spin", ScenesBerserker::spin),
            Map.entry("berserker_axe", e -> axe(e, false)),
            Map.entry("berserker_axe_finish", e -> axe(e, true)),
            Map.entry("berserker_stone_skin", ScenesBerserker::stoneSkin),
            Map.entry("berserker_notch", ScenesBerserker::notch),
            Map.entry("berserker_debt_burst", ScenesBerserker::debtBurst),
            Map.entry("berserker_hunger", ScenesBerserker::hunger),
            Map.entry("berserker_undying", ScenesBerserker::undying),
            Map.entry("berserker_bleed_drip", e -> ScenesAssassin.drops(e.x(), e.y() + 1, e.z(), 4)));

    private static final Map<String, FxScenes.MarkScene> MARKS = Map.ofEntries(
            Map.entry("berserker_quake_mark", e -> SceneKit.mark(e, SceneKit.MarkDecor.CRACK)));

    private static final Map<String, FxScenes.BoltScene> BOLTS = Map.ofEntries(
            Map.entry("berserker_hook", ScenesBerserker::hook));

    private static final Map<String, FxStatuses.Look> STATUSES = Map.ofEntries(
            Map.entry("heat", new Heat()),
            Map.entry("overheat", new Overheat()),
            Map.entry("unshakable", new Crown()),
            Map.entry("ignore_pain", new StoneSkin()),
            Map.entry("pain_debt", new PainDebt()),
            Map.entry("undying", new Undying()));

    // ------------------------------------------------------------------ жар

    private static void heat(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1, e.z(), 6, 0.06f, 0.16f, EMBER, 10, FxDraw.Tex.EMBER);
    }

    /** Жар: угли тлеют на плечах, больше стаков — больше углей. */
    static final class Heat implements FxStatuses.Look {
        @Override
        public void ambient(FxStatuses.State state, Entity entity, float emit) {
            if (FxMotes.random() > 0.05f * emit * Math.max(1, state.stacks)) {
                return;
            }
            double yaw = Math.toRadians(entity.getYRot());
            double side = FxMotes.random() < 0.5f ? 0.3 : -0.3;
            FxEffects.motes().spawn(entity.getX() + Math.cos(yaw) * side,
                    entity.getY() + entity.getBbHeight() * 0.8, entity.getZ() + Math.sin(yaw) * side,
                    FxMotes.jitter(0.01f), 0.03f, FxMotes.jitter(0.01f), 0.14f, EMBER, 14, 0.96f,
                    FxDraw.Tex.EMBER);
        }
    }

    /** Перегрев: кольцо лавы вспыхивает у ног. */
    private static void overheat(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y(), e.z(), 1.6f, FxStyle.Kind.WAVE, BLOOD, EMBER, 4, 4, 10,
                2f, 0, FxDraw.Tex.EMBER);
        if (SceneKit.isSelf(e.source())) {
            FxScreen.flash(BLOOD, 12);
        }
    }

    /** Перегрев: пламя по контуру; у самого — пульсирующая красная рамка. */
    static final class Overheat implements FxStatuses.Look {
        @Override
        public void ambient(FxStatuses.State state, Entity entity, float emit) {
            FxMotes motes = FxEffects.motes();
            for (int i = 0; i < 2; i++) {
                if (FxMotes.random() > 0.7f * emit) {
                    continue;
                }
                double a = FxMotes.random() * Math.PI * 2;
                double r = entity.getBbWidth() * 0.6;
                motes.spawn(entity.getX() + Math.cos(a) * r,
                        entity.getY() + FxMotes.random() * entity.getBbHeight(),
                        entity.getZ() + Math.sin(a) * r, 0, 0.06f, 0, 0.22f,
                        FxMotes.random() < 0.5f ? BLOOD : EMBER, 10, 0.95f, FxDraw.Tex.EMBER);
            }
        }

        @Override
        public void screen(FxStatuses.State state, net.minecraft.client.gui.GuiGraphics g, int w,
                           int h, float time) {
            FxScreen.edges(g, w, h, BLOOD, 0.22f + 0.1f * (float) Math.sin(time * 0.3));
        }
    }

    // ------------------------------------------------------------------ разрез и мясорубка

    /** Кровавый разрез: широкая красная дуга от берсерка, брызги крови на цели. */
    private static void bloodArc(FxMessage.Burst e) {
        Entity berserker = entity(e.source());
        if (berserker != null) {
            double yaw = SceneKit.yawFrom(e);
            double dist = Math.sqrt(berserker.distanceToSqr(e.x(), berserker.getY(), e.z()));
            SceneKit.arc(e.classId(), berserker.getX(), berserker.getY() + 1.0, berserker.getZ(), yaw,
                    Math.PI * 0.8, Math.max(1.5, dist), BLOOD, 9, 0.6f);
            // Плата кровью: струйка с руки.
            ScenesAssassin.drops(berserker.getX(), berserker.getY() + 1.0, berserker.getZ(), 3);
        }
        ScenesAssassin.drops(e.x(), e.y() + 1.1, e.z(), 8);
    }

    /** Мясорубка: рваные красные полосы по задетому и вихрь углей. */
    private static void carnageRend(FxMessage.Burst e) {
        double yaw = SceneKit.yawFrom(e);
        SceneKit.cut(e.classId(), e.x(), e.y() + 1.1, e.z(), yaw, 0.9, 1.6, BLOOD, 8, 0.35f);
        SceneKit.cut(e.classId(), e.x(), e.y() + 1.0, e.z(), yaw, -0.4, 1.6, DEEP, 10, 0.3f);
        SceneKit.sparks(e.x(), e.y() + 1, e.z(), 14, 0.2f, 0.16f, EMBER, 10, FxDraw.Tex.EMBER);
    }

    // ------------------------------------------------------------------ крюк

    /** Цепной крюк: крюк растяжки летит, цепь разматывается от руки берсерка. */
    private static void hook(FxMessage.Projectile p, FxKinds.Bolt bolt) {
        SceneKit.riding(bolt, Items.TRIPWIRE_HOOK, 0.8f, true);
        Entity thrower = nearestPlayer(p.x(), p.y(), p.z());
        if (thrower == null) {
            return;
        }
        int id = thrower.getId();
        FxSolids.Strip chain = FxSolids.Strip.chain(partial -> {
            Entity t = entity(id);
            Vec3 at = bolt.at(partial);
            if (t == null || at == null) {
                return null;
            }
            Vec3 hand = SceneKit.chest(t, partial);
            return new double[][] {{hand.x, hand.y, hand.z}, {at.x, at.y, at.z}};
        }, 1.2, 1, 0, 3);
        chain.decor = false;
        FxSolids.add(chain);
    }

    private static Entity nearestPlayer(double x, double y, double z) {
        var level = SceneKit.level();
        if (level == null) {
            return null;
        }
        Entity best = null;
        double bestD = 9;
        for (var player : level.players()) {
            double d = player.distanceToSqr(x, y, z);
            if (d < bestD) {
                bestD = d;
                best = player;
            }
        }
        return best;
    }

    /** Попал: цепь натягивается и тащит цель к берсерку. */
    private static void hookPull(FxMessage.Burst e) {
        int berserker = e.source();
        Entity target = nearestLiving(e.x(), e.y(), e.z(), berserker);
        if (target == null) {
            return;
        }
        int id = target.getId();
        FxSolids.Strip chain = FxSolids.Strip.chain(partial -> {
            Entity from = entity(berserker);
            Entity to = entity(id);
            if (from == null || to == null) {
                return null;
            }
            Vec3 a = SceneKit.chest(from, partial);
            Vec3 b = SceneKit.chest(to, partial);
            return new double[][] {{a.x, a.y, a.z}, {b.x, b.y, b.z}};
        }, 1.2, 1, 12, 4);
        FxSolids.add(chain);
        FxSolids.Model barb = SceneKit.item(Items.TRIPWIRE_HOOK, e.x(), e.y() + 1, e.z(), 0.8f, 1, 12,
                4);
        barb.follow = id;
        barb.offsetY = target.getBbHeight() * 0.65;
        FxSolids.add(barb);
        SceneKit.sparks(e.x(), e.y() + 1, e.z(), 10, 0.15f, 0.16f, EMBER, 8, FxDraw.Tex.SPARK);
    }

    private static Entity nearestLiving(double x, double y, double z, int except) {
        var level = SceneKit.level();
        if (level == null) {
            return null;
        }
        Entity best = null;
        double bestD = 6;
        for (Entity entity : level.getEntities((Entity) null,
                new net.minecraft.world.phys.AABB(x - 2, y - 2, z - 2, x + 2, y + 3, z + 2),
                c -> c instanceof net.minecraft.world.entity.LivingEntity && c.getId() != except)) {
            double d = entity.distanceToSqr(x, y, z);
            if (d < bestD) {
                bestD = d;
                best = entity;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ землетрясение

    /**
     * Землетрясение: от точки удара бежит волна земли — кольцо приподнятых
     * блоков той же земли до границы, — и остаются раскалённые трещины.
     * Тряска у всех в радиусе, сильнее ближе к центру.
     */
    private static void quake(FxMessage.Burst e) {
        double r = e.radius();
        SceneKit.border(e, 8, 6, 10, 1f);
        int steps = Math.max(4, (int) Math.ceil(r));
        SceneKit.Live wave = live(e.classId(), e.x(), e.y(), e.z(), r + 2, steps * 2 + 2,
                (self, draw, t, detail) -> {
                });
        wave.important = true;
        wave.step = (self, level, motes, emit) -> {
            if (self.age % 2 != 1) {
                return;
            }
            int ring = self.age / 2 + 1;
            double rr = Math.min(r - 0.4, ring * (r / steps));
            if (rr <= 0.3) {
                return;
            }
            int n = (int) Math.max(6, Math.PI * 2 * rr / (emit > 0.5f ? 1.0 : 1.8));
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * (i + (ring % 2) * 0.5) / n;
                double x = e.x() + Math.cos(a) * rr;
                double z = e.z() + Math.sin(a) * rr;
                double gy = FxGround.top(level, x, z, e.y());
                BlockState state = groundBlock(level, x, gy, z);
                if (state == null) {
                    continue;
                }
                FxSolids.Model chunk = new FxSolids.Model(state, x, gy - 0.9, z, 0.9f, 1, 4, 3);
                chunk.vy = 0.18;
                chunk.gravity = 0.09;
                chunk.drag = 1;
                FxSolids.add(chunk);
            }
        };
        // Раскалённые трещины: лучи от центра, гаснут за три секунды.
        int cracks = 7;
        double[] angles = new double[cracks];
        for (int i = 0; i < cracks; i++) {
            angles[i] = Math.PI * 2 * i / cracks + SceneKit.RANDOM.nextDouble() * 0.4;
        }
        live(e.classId(), e.x(), e.y(), e.z(), r + 1, 60, (self, draw, t, detail) -> {
            float k = self.progress(t);
            float grow = Math.min(1f, t / 8f);
            for (double a : angles) {
                double len = (r - 0.3) * grow;
                double mx = e.x() + Math.cos(a + 0.15) * len * 0.5;
                double mz = e.z() + Math.sin(a + 0.15) * len * 0.5;
                draw.groundLine(FxDraw.Tex.BEAM, e.x(), e.z(), mx, mz, e.y(), 0.25, EMBER,
                        0.9f * (1f - k), 0.03f);
                draw.groundLine(FxDraw.Tex.BEAM, mx, mz, e.x() + Math.cos(a) * len,
                        e.z() + Math.sin(a) * len, e.y(), 0.18, BLOOD, 0.8f * (1f - k), 0.03f);
            }
        });
        FxScreen.shakeIfInside(e.x(), e.y(), e.z(), r, 1f, 12);
    }

    /** Блок земли под точкой: из него и сложена волна. */
    private static BlockState groundBlock(Level level, double x, double y, double z) {
        BlockState state = level.getBlockState(BlockPos.containing(x, y - 0.5, z));
        if (state.isAir() || !state.isSolidRender(level, BlockPos.containing(x, y - 0.5, z))) {
            return null;
        }
        return state;
    }

    // ------------------------------------------------------------------ рёв

    /** Дикий рёв: звуковая волна — кольца искажения до границы; тряска у тех, кто в радиусе. */
    private static void roar(FxMessage.Burst e) {
        double r = e.radius();
        SceneKit.border(e, 5, 6, 8, 0.8f);
        live(e.classId(), e.x(), e.y() + 1.4, e.z(), r + 2, 16, (self, draw, t, detail) -> {
            float k = self.progress(t);
            for (int i = 0; i < 3; i++) {
                float q = Math.clamp(k * 1.5f - i * 0.18f, 0f, 1f);
                if (q <= 0 || q >= 1) {
                    continue;
                }
                double rr = 0.5 + (r - 0.5) * q;
                draw.halo(FxDraw.Tex.BEAM, e.x(), e.y() + 1.4 - q * 0.6, e.z(), rr, 0.6, 0, 1, 0,
                        0xFFFFE0C0, 0.35f * (1f - q), 0.3f, t * 0.2f);
            }
        });
        FxScreen.shakeIfInside(e.x(), e.y(), e.z(), r, 0.7f, 10);
    }

    /** Непоколебимость: над берсерком тлеющая корона из углей. */
    static final class Crown implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            double y = at.y + entity.getBbHeight() + 0.25;
            int n = 7;
            for (int i = 0; i < n; i++) {
                double a = time * 0.04 + Math.PI * 2 * i / n;
                float flicker = 0.6f + 0.4f * (float) Math.sin(time * 0.5 + i * 2);
                draw.sprite(FxDraw.Tex.EMBER, at.x + Math.cos(a) * 0.32, y + 0.08 * (i % 2),
                        at.z + Math.sin(a) * 0.32, 0.28f, 0, EMBER, flicker);
            }
            draw.halo(FxDraw.Tex.RING, at.x, y, at.z, 0.32, 0.08, 0, 1, 0, BLOOD, 0.7f, 0.5f, 0);
        }
    }

    // ------------------------------------------------------------------ вихрь смерти

    /** Оборот вихря: круговая кровавая дуга по радиусу, угли по кругу; каждый гуще. */
    private static void spin(FxMessage.Burst e) {
        long now = FxStatuses.now();
        int[] count = SPINS.computeIfAbsent(e.source(), k -> new int[] {0, 0});
        if (now - count[1] > 20) {
            count[0] = 0;
        }
        count[0]++;
        count[1] = (int) now;
        int turn = Math.min(4, count[0]);
        Entity berserker = entity(e.source());
        double cx = berserker != null ? berserker.getX() : e.x();
        double cy = berserker != null ? berserker.getY() + 1.0 : e.y() + 1;
        double cz = berserker != null ? berserker.getZ() : e.z();
        double start = SceneKit.RANDOM.nextDouble() * Math.PI * 2;
        SceneKit.arc(e.classId(), cx, cy, cz, start, Math.PI * 2, Math.max(1.2, e.radius() - 0.3),
                turn >= 3 ? DEEP : BLOOD, 8, 0.5f + 0.15f * turn);
        FxMotes motes = FxEffects.motes();
        int n = (int) (10 * turn * Math.max(0.3f, FxEffects.emit()));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            motes.spawn(cx, cy, cz, (float) Math.cos(a) * 0.3f, 0.05f, (float) Math.sin(a) * 0.3f,
                    0.16f, EMBER, 10, 0.88f, FxDraw.Tex.EMBER);
        }
    }

    // ------------------------------------------------------------------ удар палача

    /**
     * Удар палача: огромный топор-призрак опускается на цель; по раненому —
     * втрое крупнее и в пламени душ.
     */
    private static void axe(FxMessage.Burst e, boolean finish) {
        float size = finish ? 3.2f : 1.4f;
        double top = e.y() + (finish ? 6 : 3.5);
        double yaw = SceneKit.yawFrom(e);
        FxSolids.Model axe = SceneKit.item(Items.NETHERITE_AXE, e.x(), top, e.z(), size, 1, 6, 6);
        axe.at((float) Math.toDegrees(Math.PI / 2 - yaw), 0, -135);
        int fall = 4;
        axe.vy = -(top - (e.y() + size * 0.5)) / fall;
        axe.drag = 1;
        FxSolids.add(axe);
        SceneKit.Live stop = live(e.classId(), e.x(), e.y(), e.z(), 7, fall + 1,
                (self, draw, t, detail) -> {
                });
        stop.step = (self, level, motes, emit) -> {
            if (self.age == fall) {
                axe.vy = 0;
                SceneKit.sparks(e.x(), e.y() + 0.6, e.z(), finish ? 40 : 18, finish ? 0.4f : 0.25f,
                        0.22f, finish ? 0xFF6FE8F0 : EMBER, 12, FxDraw.Tex.EMBER);
                FxScreen.shakeIfInside(e.x(), e.y(), e.z(), finish ? 4 : 2.5, finish ? 0.9f : 0.5f,
                        8);
            }
        };
        if (finish) {
            SceneKit.Live flame = live(e.classId(), e.x(), e.y(), e.z(), 7, 18,
                    (self, draw, t, detail) -> {
                    });
            flame.step = (self, level, motes, emit) -> {
                if (emit > 0) {
                    motes.spawn(axe.x + FxMotes.jitter(0.8f), axe.y + FxMotes.random() * 2,
                            axe.z + FxMotes.jitter(0.8f), 0, 0.05f, 0, 0.3f, 0xFF6FE8F0, 10, 0.95f,
                            FxDraw.Tex.EMBER);
                }
            };
        }
    }

    // ------------------------------------------------------------------ боль

    private static void stoneSkin(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1, e.z(), 14, 0.08f, 0.3f, 0xFF8A8580, 16, FxDraw.Tex.WISP);
    }

    /** Игнорирование боли: кожа будто каменеет — серое свечение и пепел. */
    static final class StoneSkin implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            draw.sprite(FxDraw.Tex.GLOW, at.x, at.y + entity.getBbHeight() * 0.55, at.z, 1.6f, 0,
                    0xFF8A8580, 0.3f);
        }

        @Override
        public void ambient(FxStatuses.State state, Entity entity, float emit) {
            if (FxMotes.random() < 0.2f * emit) {
                FxEffects.motes().spawn(entity.getX() + FxMotes.jitter(0.35f),
                        entity.getY() + FxMotes.random() * entity.getBbHeight(),
                        entity.getZ() + FxMotes.jitter(0.35f), 0, -0.01f, 0, 0.14f, 0xFF6A6560, 16,
                        0.97f, FxDraw.Tex.WISP);
            }
        }
    }

    private static void notch(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1.2, e.z(), 4, 0.06f, 0.14f, BLOOD, 8, FxDraw.Tex.SPARK);
    }

    /** Долг боли: на теле светятся зарубки — по одной на пропущенный удар. */
    static final class PainDebt implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            int n = Math.min(12, Math.max(1, state.stacks));
            double h = entity.getBbHeight();
            for (int i = 0; i < n; i++) {
                double a = i * 2.39996;
                double y = at.y + h * (0.35 + 0.45 * ((i * 0.618) % 1.0));
                double r = entity.getBbWidth() * 0.55;
                draw.sprite(FxDraw.Tex.SHARD, at.x + Math.cos(a) * r, y, at.z + Math.sin(a) * r,
                        0.16f, (float) a, BLOOD, 0.85f);
            }
        }
    }

    /** Стойка кончилась: зарубки разом вспыхивают кровью. */
    private static void debtBurst(FxMessage.Burst e) {
        ScenesAssassin.drops(e.x(), e.y() + 1.1, e.z(), 16);
        SceneKit.sparks(e.x(), e.y() + 1.1, e.z(), 20, 0.2f, 0.18f, BLOOD, 10, FxDraw.Tex.SHARD);
    }

    // ------------------------------------------------------------------ голод и ярость

    private static void hunger(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y(), e.z(), 1.2f, FxStyle.Kind.RISE, DEEP, BLOOD, 4, 8, 10, 1f,
                0, FxDraw.Tex.EMBER);
    }

    /**
     * Голод: от каждой цели, по которой бьёт берсерк в голоде, к нему тянется
     * красная струйка. Зовётся из общего попадания ({@link FxScenes#hit}).
     */
    static void onHit(FxMessage.Hit hit) {
        if (hit.attacker() == 0 || !FxStatuses.has(hit.attacker(), "hunger")) {
            return;
        }
        Entity target = entity(hit.entityId());
        Entity berserker = entity(hit.attacker());
        if (target == null || berserker == null) {
            return;
        }
        int from = target.getId();
        int to = berserker.getId();
        live("berserker", target.getX(), target.getY(), target.getZ(), 12, 14,
                (self, draw, t, detail) -> {
                    Entity a = entity(from);
                    Entity b = entity(to);
                    if (a == null || b == null) {
                        return;
                    }
                    float partial = t - (int) t;
                    Vec3 pa = SceneKit.chest(a, partial);
                    Vec3 pb = SceneKit.chest(b, partial);
                    float k = self.progress(t);
                    int n = 6;
                    double[] xs = new double[n];
                    double[] ys = new double[n];
                    double[] zs = new double[n];
                    for (int i = 0; i < n; i++) {
                        double q = (double) i / (n - 1);
                        xs[i] = pa.x + (pb.x - pa.x) * q;
                        ys[i] = pa.y + (pb.y - pa.y) * q + Math.sin(q * Math.PI) * 0.4;
                        zs[i] = pa.z + (pb.z - pa.z) * q;
                    }
                    draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, n, 0.18f, BLOOD, 0.8f * (1f - k),
                            0.4f * (1f - k), -t * 0.3f);
                });
    }

    private static void undying(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y(), e.z(), 1.6f, FxStyle.Kind.WAVE, BLOOD, EMBER, 4, 4, 10, 2f,
                0, FxDraw.Tex.EMBER);
        FxScreen.shakeIfInside(e.x(), e.y(), e.z(), 3, 0.5f, 8);
    }

    /**
     * Бессмертная ярость: берсерк в столбе пламени, глаза белые; у самого —
     * красная пульсирующая рамка.
     */
    static final class Undying implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            double h = entity.getBbHeight();
            draw.ribbon(FxDraw.Tex.BEAM, new double[] {at.x, at.x}, new double[] {at.y, at.y + h + 1.2},
                    new double[] {at.z, at.z}, 2, 1.4f, BLOOD, 0.45f, 0f, -time * 0.2f);
            ScenesHunter.eyes(draw, entity, at, 0xFFFFFFFF);
        }

        @Override
        public void ambient(FxStatuses.State state, Entity entity, float emit) {
            FxMotes motes = FxEffects.motes();
            for (int i = 0; i < 3; i++) {
                if (FxMotes.random() > 0.8f * emit) {
                    continue;
                }
                double a = FxMotes.random() * Math.PI * 2;
                double r = 0.3 + FxMotes.random() * 0.3;
                motes.spawn(entity.getX() + Math.cos(a) * r, entity.getY() + FxMotes.random() * 0.5,
                        entity.getZ() + Math.sin(a) * r, 0, 0.12f, 0, 0.26f,
                        i == 0 ? EMBER : BLOOD, 14, 0.96f, FxDraw.Tex.EMBER);
            }
        }

        @Override
        public void screen(FxStatuses.State state, net.minecraft.client.gui.GuiGraphics g, int w,
                           int h, float time) {
            FxScreen.edges(g, w, h, BLOOD, 0.3f + 0.15f * (float) Math.sin(time * 0.4));
        }
    }
}
