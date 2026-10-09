package ru.projectst.rpgcore.client;

import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Сцены навыков: что именно появляется в мире по событию.
 *
 * <p>Стиль ({@link FxStyle}) говорит, какого цвета и как долго кольцо. Сцена —
 * больше: корни из земли, лозы, щепа, цветок, звук и экран, собранные по
 * сценарию навыка ({@code docs/vfx/skill-visuals.md}). Сцена получает то же
 * событие с числами сервера и ничего не пересчитывает.
 *
 * <p><b>Граница — правда.</b> Сцена с областью всегда кладёт обычную границу
 * тем же радиусом, а объекты держит внутри круга ({@link FxGeometry}).
 *
 * <p>Неизвестный {@code fx} сцены не имеет — его рисует роль класса, как раньше.
 */
final class FxScenes {

    private static final Random RANDOM = new Random();

    private FxScenes() {
    }

    interface BurstScene {
        void play(FxMessage.Burst event);
    }

    interface MarkScene {
        void play(FxMessage.Telegraph event);
    }

    /** Сцена зоны: появляется вместе с зоной и уходит вместе с ней. */
    interface ZoneScene {
        void play(FxMessage.ZoneOn event, FxKinds.Zone zone);
    }

    private static final Map<String, BurstScene> BURSTS = Map.ofEntries(
            Map.entry("druid_roots_grasp", e -> rootsGrasp(e, false)),
            Map.entry("druid_roots_grasp_strong", e -> rootsGrasp(e, true)),
            Map.entry("druid_ivy_lash", e -> ivyLash(e, false)),
            Map.entry("druid_ivy_lash_strong", e -> ivyLash(e, true)),
            Map.entry("druid_ivy_strike", FxScenes::ivyStrike),
            Map.entry("druid_spores_bud", FxScenes::sporesBud),
            Map.entry("druid_spores_cloud", FxScenes::sporesCloud),
            Map.entry("druid_spores_heal", FxScenes::sporesHeal),
            Map.entry("druid_spores_corrode", FxScenes::sporesCorrode),
            Map.entry("druid_bark_rise", FxScenes::barkRise),
            Map.entry("druid_bark_splinter", FxScenes::barkSplinter),
            Map.entry("druid_beast_leaves", FxScenes::beastLeaves),
            Map.entry("druid_bloom_heal", e -> bloomPulse(e, true)),
            Map.entry("druid_bloom_harm", e -> bloomPulse(e, false)));

    private static final Map<String, MarkScene> MARKS = Map.ofEntries(
            Map.entry("druid_roots_mark", e -> mark(e, MarkLook.CRACK)),
            Map.entry("druid_ivy_mark", e -> mark(e, MarkLook.LEAVES)),
            // Остальные классы — пока общий круг их облика; землетрясение — с
            // трещиной по земле, она ему по смыслу.
            Map.entry("berserker_quake_mark", e -> mark(e, MarkLook.CRACK)),
            Map.entry("trickster_shuffle_mark", e -> mark(e, MarkLook.PLAIN)));

    private static final Map<String, ZoneScene> ZONES = Map.ofEntries(
            Map.entry("druid_abyss_flower", FxScenes::abyssFlower));

    /** Есть ли у мода своя сцена: тесту контента и отладке. */
    static boolean known(String fx) {
        return BURSTS.containsKey(fx) || MARKS.containsKey(fx) || ZONES.containsKey(fx);
    }

    /** @return {@code true}, если сцена есть и сыграна */
    static boolean burst(FxMessage.Burst event) {
        BurstScene scene = BURSTS.get(event.fx());
        if (scene == null) {
            return false;
        }
        scene.play(event);
        return true;
    }

    /** Предупреждение: своя сцена или общий круг, который заполняется к удару. */
    static void mark(FxMessage.Telegraph event) {
        MarkScene scene = MARKS.get(event.fx());
        if (scene != null) {
            scene.play(event);
        } else {
            mark(event, MarkLook.PLAIN);
        }
    }

    static void zone(FxMessage.ZoneOn event, FxKinds.Zone zone) {
        ZoneScene scene = ZONES.get(event.fx());
        if (scene != null) {
            scene.play(event, zone);
        }
    }

    // ------------------------------------------------------------------ общее

    /** Граница области: обычная волна до радиуса в облике класса. */
    private static void border(FxMessage.Burst e, int grow, int hold, int fade, float motes) {
        FxStyle style = new FxStyle(FxStyle.Kind.WAVE, 0, 0, grow, hold, fade, false, motes, 0);
        FxEffects.addEffect(new FxKinds.Burst(style, e));
    }

    private static Level level() {
        return Minecraft.getInstance().level;
    }

    private static Entity entity(int id) {
        Level level = level();
        return level == null || id == 0 ? null : level.getEntity(id);
    }

    private static double ground(double x, double z, double y) {
        Level level = level();
        return level == null ? y : FxGround.top(level, x, z, y);
    }

    /** Ком земли: вылетает из-под корня и падает. */
    private static void clod(double x, double y, double z, float scale) {
        FxSolids.Model clod = new FxSolids.Model(Blocks.ROOTED_DIRT.defaultBlockState(),
                x, y, z, scale, 1, 10, 6);
        clod.vx = FxMotes.jitter(0.06f);
        clod.vy = 0.12 + FxMotes.random() * 0.1;
        clod.vz = FxMotes.jitter(0.06f);
        clod.gravity = 0.035;
        clod.spin = FxMotes.jitter(20f);
        FxSolids.add(clod);
    }

    // ------------------------------------------------------------------ корни

    /**
     * Хватающие корни: по всей площади пробиваются корни и опадают.
     *
     * <p>Корни на пойманных — не здесь: они держатся статусом {@code root} и
     * уходят, когда его снимут ({@link FxStatuses}). Здесь — сам удар: граница,
     * короткие корни по площади, комья земли, треск, тряска у тех, кто внутри.
     */
    private static void rootsGrasp(FxMessage.Burst e, boolean strong) {
        double r = e.radius();
        border(e, 3, 10, 8, strong ? 1.6f : 1.2f);
        int spikes = (int) Math.min(strong ? 22 : 16, Math.max(6, r * r * 0.45));
        if (FxEffects.emit() <= 0) {
            spikes = Math.min(spikes, 6);
        }
        for (int i = 0; i < spikes; i++) {
            double[] p = FxGeometry.insideCircle(RANDOM, e.x(), e.y(), e.z(), r);
            double gy = ground(p[0], p[2], e.y());
            double height = 0.6 + RANDOM.nextDouble() * (strong ? 1.1 : 0.8);
            FxSolids.Tube spike = new FxSolids.Tube(
                    FxGeometry.rootSpike(RANDOM, p[0], gy, p[2], height),
                    strong ? 0.11 : 0.08, "block/mangrove_log", 0xFFD8C8B0,
                    3 + RANDOM.nextInt(4), 6 + RANDOM.nextInt(6), 8)
                    .bound(e.x(), e.z(), r);
            FxSolids.add(spike);
            if (i % 3 == 0) {
                clod(p[0], gy + 0.2, p[2], 0.18f);
            }
        }
        FxSounds.play("druid.roots.crack", e.x(), e.y(), e.z(), 1f, strong ? 0.8f : 1f);
        FxScreen.shakeIfInside(e.x(), e.y(), e.z(), r, strong ? 0.8f : 0.55f, 8);
    }

    // ------------------------------------------------------------------ плющ

    /** Отравленный плющ: лозы хлещут от друида во все стороны до границы. */
    private static void ivyLash(FxMessage.Burst e, boolean strong) {
        double r = e.radius();
        border(e, 3, 6, 10, strong ? 1.5f : 1.1f);
        int lashes = strong ? 16 : 9;
        double start = RANDOM.nextDouble() * Math.PI * 2;
        for (int i = 0; i < lashes; i++) {
            double a = start + Math.PI * 2 * i / lashes + RANDOM.nextDouble() * 0.3;
            double reach = r * (0.75 + RANDOM.nextDouble() * 0.25);
            double tx = e.x() + Math.cos(a) * reach;
            double tz = e.z() + Math.sin(a) * reach;
            double[][] arc = FxGeometry.clampAll(e.x(), e.z(), r, FxGeometry.lashArc(
                    e.x() + Math.cos(a) * 0.5, ground(e.x(), e.z(), e.y()), e.z() + Math.sin(a) * 0.5,
                    tx, ground(tx, tz, e.y()), tz));
            int tint = i % 3 == 0 ? 0xFFA070C0 : 0xFF7FB050;
            FxSolids.add(new FxSolids.Tube(arc, 0.07, "block/vine", tint, 3, 5, 10)
                    .bound(e.x(), e.z(), r));
        }
        if (strong) {
            // Изгородь по краю: живая черта плюща ровно внутри границы.
            int posts = (int) Math.max(10, r * 4);
            for (int i = 0; i < posts; i++) {
                double a = Math.PI * 2 * i / posts;
                double px = e.x() + Math.cos(a) * (r - 0.35);
                double pz = e.z() + Math.sin(a) * (r - 0.35);
                FxSolids.add(new FxSolids.Tube(FxGeometry.rootSpike(RANDOM, px,
                        ground(px, pz, e.y()), pz, 0.9), 0.06, "block/vine", 0xFF7FB050,
                        4, 20, 10).bound(e.x(), e.z(), r));
            }
        }
        FxSounds.play("druid.ivy.lash", e.x(), e.y(), e.z(), 1f, strong ? 0.85f : 1f);
    }

    /** Лоза к каждой задетой цели — от друида, дугой. */
    private static void ivyStrike(FxMessage.Burst e) {
        Entity source = entity(e.source());
        double fx = source != null ? source.getX() : e.x() + 1;
        double fz = source != null ? source.getZ() : e.z();
        double fy = ground(fx, fz, source != null ? source.getY() : e.y());
        double[][] arc = FxGeometry.lashArc(fx, fy, fz, e.x(), e.y() + 1.0, e.z());
        FxSolids.add(new FxSolids.Tube(arc, 0.075, "block/vine", 0xFF8FC060, 3, 4, 10));
        FxSounds.play("druid.ivy.splat", e.x(), e.y() + 1, e.z(), 0.8f, 1f);
    }

    // ------------------------------------------------------------------ споры

    /** Бутон у ног раскрывается. */
    private static void sporesBud(FxMessage.Burst e) {
        double gy = ground(e.x(), e.z(), e.y());
        FxSolids.add(new FxSolids.Model(Blocks.FLOWERING_AZALEA.defaultBlockState(),
                e.x(), gy, e.z(), 0.9f, 6, 8, 8));
        FxSounds.play("druid.spores.pop", e.x(), e.y(), e.z(), 1f, 1f);
    }

    /** Облако спор до границы: золото, медленно тает. */
    private static void sporesCloud(FxMessage.Burst e) {
        FxStyle cloud = new FxStyle(FxStyle.Kind.WAVE, 0xE8D36A, 0xFFF4B0, 8, 14, 12, false,
                2.4f, 0, FxDraw.Tex.GLOW);
        FxEffects.addEffect(new FxKinds.Burst(cloud, e));
        FxMotes motes = FxEffects.motes();
        int n = (int) Math.min(90, e.radius() * e.radius() * 2 * FxEffects.emit());
        for (int i = 0; i < n; i++) {
            double[] p = FxGeometry.insideCircle(RANDOM, e.x(), e.y(), e.z(), e.radius());
            motes.spawn(p[0], ground(p[0], p[2], e.y()) + 0.2 + FxMotes.random() * 1.6, p[2],
                    FxMotes.jitter(0.01f), 0.006f, FxMotes.jitter(0.01f), 0.22f, 0xFFF4E08A,
                    30 + (int) (FxMotes.random() * 20), 0.99f, FxDraw.Tex.GLOW);
        }
        FxSounds.play("druid.spores.hiss", e.x(), e.y(), e.z(), 0.9f, 1f);
    }

    /** Вылеченный: подъём золотых искр; если это я — тёплое свечение по краям. */
    private static void sporesHeal(FxMessage.Burst e) {
        FxStyle rise = new FxStyle(FxStyle.Kind.RISE, 0xE8D36A, 0xFFF4B0, 4, 8, 10, false, 1.2f, 0,
                FxDraw.Tex.LEAF);
        FxEffects.addEffect(new FxKinds.Burst(rise, e));
        var player = Minecraft.getInstance().player;
        if (player != null && player.distanceToSqr(e.x(), e.y(), e.z()) < 1.0) {
            FxScreen.flash(0xFFFFE08A, 12);
        }
    }

    /** Разъеденный враг: бурые споры оседают на нём. */
    private static void sporesCorrode(FxMessage.Burst e) {
        FxStyle sink = new FxStyle(FxStyle.Kind.SINK, 0x9A6A2A, 0xC0903A, 4, 8, 10, false, 1.2f, 0,
                FxDraw.Tex.WISP);
        FxEffects.addEffect(new FxKinds.Burst(sink, e));
    }

    // ------------------------------------------------------------------ кора

    /** Кора поднимается от земли: пластины держит статус, здесь — земля и звук. */
    private static void barkRise(FxMessage.Burst e) {
        double gy = ground(e.x(), e.z(), e.y());
        for (int i = 0; i < 4; i++) {
            clod(e.x() + FxMotes.jitter(0.5f), gy + 0.1, e.z() + FxMotes.jitter(0.5f), 0.14f);
        }
    }

    /** Кора погасила удар: щепка летит от друида в ударившего. */
    private static void barkSplinter(FxMessage.Burst e) {
        Entity source = entity(e.source());
        double sx = source != null ? source.getX() : e.x();
        double sy = source != null ? source.getY() + 1.1 : e.y() + 1;
        double sz = source != null ? source.getZ() : e.z();
        for (int i = 0; i < 3; i++) {
            FxSolids.Model chip = new FxSolids.Model(Blocks.OAK_WOOD.defaultBlockState(),
                    sx, sy, sz, 0.16f, 1, 8, 4);
            chip.vx = (e.x() - sx) / 8 + FxMotes.jitter(0.03f);
            chip.vy = (e.y() + 1.2 - sy) / 8 + 0.05;
            chip.vz = (e.z() - sz) / 8 + FxMotes.jitter(0.03f);
            chip.gravity = 0.01;
            chip.spin = 30;
            FxSolids.add(chip);
        }
        FxSounds.play("druid.bark.crack", sx, sy, sz, 1f, 1.1f);
    }

    // ------------------------------------------------------------------ зверь

    /** Вихрь листьев собирается в волка. */
    private static void beastLeaves(FxMessage.Burst e) {
        FxMotes motes = FxEffects.motes();
        int n = (int) (40 * Math.max(0.3f, FxEffects.emit()));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double r = 1.4 + FxMotes.random() * 0.6;
            motes.spawn(e.x() + Math.cos(a) * r, e.y() + 0.2 + FxMotes.random() * 1.2,
                    e.z() + Math.sin(a) * r, (float) -Math.sin(a) * 0.12f - (float) Math.cos(a) * 0.05f,
                    0.02f, (float) Math.cos(a) * 0.12f - (float) Math.sin(a) * 0.05f, 0.22f,
                    i % 2 == 0 ? 0xFF7FE05A : 0xFFB0763A, 22, 0.93f, FxDraw.Tex.LEAF);
        }
        FxSounds.play("druid.beast.call", e.x(), e.y(), e.z(), 1f, 1f);
    }

    // ------------------------------------------------------------------ бездна

    /** Пульс фазы цветка: волна от центра и звук. */
    private static void bloomPulse(FxMessage.Burst e, boolean heal) {
        FxStyle wave = new FxStyle(FxStyle.Kind.WAVE, heal ? 0x6FD45A : 0x9A5A2A,
                heal ? 0xE8F59A : 0xC0703A, 6, 4, 8, false, heal ? 1.4f : 1.0f, 0,
                heal ? FxDraw.Tex.LEAF : FxDraw.Tex.WISP);
        FxEffects.addEffect(new FxKinds.Burst(wave, e));
        FxSounds.play("druid.bloom.pulse", e.x(), e.y(), e.z(), 0.8f, heal ? 1.2f : 0.8f);
    }

    /**
     * Цвет бездны: цветок с лепестками на всю зону.
     *
     * <p>Фаза — по статусу {@code bloom_phase} владельца, то есть так, как навык
     * работает на самом деле: статус висит — последний тик лечил, лепестки
     * раскрыты и зелены; нет — жёг, лепестки сомкнуты и бурые, по земле шипы.
     */
    private static void abyssFlower(FxMessage.ZoneOn on, FxKinds.Zone zone) {
        FxEffects.addEffect(new Bloom(on, zone));
        double gy = ground(on.x(), on.z(), on.y());
        FxSolids.Model core = new FxSolids.Model(Blocks.FLOWERING_AZALEA.defaultBlockState(),
                on.x(), gy, on.z(), 1.6f, 15, 0, 10);
        core.holding = () -> !zone.ending();
        core.decor = false;
        FxSolids.add(core);
        FxSounds.play("druid.bloom.open", on.x(), on.y(), on.z(), 1f, 1f);
    }

    /** Лепестки цветка бездны: живут, пока жива зона. */
    private static final class Bloom extends FxKinds.Effect {
        private final FxMessage.ZoneOn on;
        private final FxKinds.Zone zone;
        private float open;
        private boolean lastHeal;

        Bloom(FxMessage.ZoneOn on, FxKinds.Zone zone) {
            super(new FxStyle(FxStyle.Kind.ZONE, 0, 0, 15, 0, 10, false, 1f, 0), on.classId());
            this.on = on;
            this.zone = zone;
            this.lastHeal = heal();
        }

        private boolean heal() {
            return on.owner() != 0 && FxStatuses.has(on.owner(), "bloom_phase");
        }

        @Override
        boolean decor() {
            return false;
        }

        @Override
        void tick(Level level, FxMotes motes, float emit) {
            super.tick(level, motes, emit);
            if (zone.ending()) {
                open = Math.max(0, open - 0.1f);
                if (open <= 0) {
                    dead = true;
                }
                return;
            }
            boolean heal = heal();
            float target = heal ? 1f : 0.55f;
            open += (target - open) * 0.15f;
            if (heal != lastHeal) {
                lastHeal = heal;
                if (!heal) {
                    // Фаза урона: по полю пробиваются шипы.
                    for (int i = 0; i < 10; i++) {
                        double[] p = FxGeometry.insideCircle(RANDOM, on.x(), on.y(), on.z(),
                                on.radius());
                        FxSolids.add(new FxSolids.Tube(FxGeometry.rootSpike(RANDOM, p[0],
                                FxGround.top(level, p[0], p[2], on.y()), p[2], 0.8), 0.07,
                                "block/mangrove_log", 0xFF9A6A4A, 3, 8, 8)
                                .bound(on.x(), on.z(), on.radius()));
                    }
                }
            }
        }

        @Override
        AABB bounds() {
            double r = on.radius();
            return new AABB(on.x() - r, on.y() - 3, on.z() - r, on.x() + r, on.y() + 3, on.z() + r);
        }

        @Override
        void draw(FxDraw draw, float partial, FxKinds.Detail detail) {
            double r = on.radius();
            boolean heal = lastHeal;
            int colour = heal ? 0xFF6FD45A : 0xFF9A5A2A;
            int edge = heal ? 0xFFE8F59A : 0xFFC0703A;
            int petals = 8;
            float spin = (age + partial) * 0.003f;
            for (int i = 0; i < petals; i++) {
                double mid = spin + Math.PI * 2 * i / petals;
                double half = Math.PI / petals * 0.8;
                // Лепесток — сектор от сердцевины до края, раскрытый на долю фазы.
                double reach = Math.max(0.6, (r - 0.2) * open);
                draw.sector(FxDraw.Tex.FILL, on.x(), on.y(), on.z(), reach, mid - half, mid + half,
                        colour, 0.32f, 0.02f);
                draw.ring(FxDraw.Tex.RING, on.x(), on.y(), on.z(), reach, 0.18, mid - half,
                        mid + half, edge, 0.7f, 0.5f, 0, 0.04f);
            }
        }
    }

    // ------------------------------------------------------------------ предупреждение

    private enum MarkLook { PLAIN, CRACK, LEAVES }

    /**
     * Круг, который заполняется к удару: из него нужно успеть выйти.
     *
     * <p>Граница стоит на радиусе с первого тика — она и есть «отсюда уйти».
     * Заливка нарастает к удару, у корней по земле бежит светящаяся трещина, у
     * плюща — пояс листьев.
     */
    private static void mark(FxMessage.Telegraph e, MarkLook look) {
        FxEffects.addEffect(new Mark(e, look));
        // Скрип корней — только у корней: у землетрясения свой ванильный звук удара.
        if (look == MarkLook.CRACK && e.fx().startsWith("druid_")) {
            FxSounds.play("druid.roots.creak", e.x(), e.y(), e.z(), 0.7f, 0.6f);
        }
    }

    private static final class Mark extends FxKinds.Effect {
        private final FxMessage.Telegraph e;
        private final MarkLook look;
        private final int life;

        Mark(FxMessage.Telegraph e, MarkLook look) {
            super(new FxStyle(FxStyle.Kind.TELEGRAPH, 0, 0, 0, e.ticks(), 4, false, 1f, 0),
                    FxStyle.owner(e.classId(), e.fx()));
            this.e = e;
            this.look = look;
            this.life = Math.max(1, e.ticks()) + 4;
        }

        @Override
        boolean decor() {
            return false;
        }

        @Override
        void tick(Level level, FxMotes motes, float emit) {
            super.tick(level, motes, emit);
            if (age >= life) {
                dead = true;
                return;
            }
            if (look == MarkLook.CRACK && age < e.ticks() && emit > 0) {
                // Комья земли выбиваются там, куда добежала трещина.
                double k = FxGeometry.easeOut((double) age / e.ticks());
                double a = FxMotes.random() * Math.PI * 2;
                double d = e.radius() * k;
                double x = e.x() + Math.cos(a) * d;
                double z = e.z() + Math.sin(a) * d;
                if (FxMotes.random() < 0.5f) {
                    clod(x, FxGround.top(level, x, z, e.y()) + 0.1, z, 0.12f);
                }
            }
        }

        @Override
        AABB bounds() {
            double r = e.radius() + 1;
            return new AABB(e.x() - r, e.y() - 4, e.z() - r, e.x() + r, e.y() + 4, e.z() + r);
        }

        @Override
        void draw(FxDraw draw, float partial, FxKinds.Detail detail) {
            float t = age + partial;
            float k = Math.clamp(t / Math.max(1, e.ticks()), 0f, 1f);
            float fade = t > e.ticks() ? 1f - Math.clamp((t - e.ticks()) / 4f, 0f, 1f) : 1f;
            double r = e.radius();
            draw.sector(FxDraw.Tex.FILL, e.x(), e.y(), e.z(), r, 0, Math.PI * 2, primary,
                    (0.08f + 0.3f * k) * fade, 0.02f);
            draw.circle(FxDraw.Tex.RING, e.x(), e.y(), e.z(), r, 0.26, primary, 0.95f * fade,
                    0.5f, 0, 0.05f);
            // Внутреннее кольцо догоняет границу к моменту удара: когда сомкнулись —
            // бьёт. По нему видно, сколько осталось.
            draw.circle(FxDraw.Tex.RING, e.x(), e.y(), e.z(), Math.max(0.2, r * k), 0.2, accent,
                    0.8f * fade, 0.5f, 0, 0.06f);
            switch (look) {
                case CRACK -> draw.circle(FxDraw.Tex.BEAM, e.x(), e.y(), e.z(),
                        Math.max(0.2, r * FxGeometry.easeOut(k)), 0.4, 0xFFA8E070,
                        0.7f * fade, 0.3f, t * 0.05f, 0.07f);
                case LEAVES -> {
                    if (detail.full()) {
                        double band = Math.min(0.7, r * 0.25);
                        draw.circle(FxDraw.Tex.RUNES, e.x(), e.y(), e.z(), r - band, band, accent,
                                0.5f * fade, (float) (1 / (band * 8)), t * 0.02f, 0.04f);
                    }
                }
                case PLAIN -> {
                }
            }
        }
    }
}
