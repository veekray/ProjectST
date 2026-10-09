package ru.projectst.rpgcore.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Состояния на существах: корни на ногах, звёзды над головой, листья на плечах.
 *
 * <p>Состояние рисуется ровно столько, сколько висит статус: сервер присылает
 * «наложен» и «снят», и корни уходят в землю в тот тик, когда их сняли, а не
 * когда кончилась бы анимация. Всё, что статус значит для игры, решает сервер;
 * здесь — только как его видно.
 *
 * <p>Псевдостатусы: {@code potion:<зелье>} — ванильное зелье от навыка, о
 * котором клиент иначе не знает; {@code minion:<тег>} — чей это призванный.
 */
final class FxStatuses {

    /** Статус на существе, как его прислал сервер. */
    static final class State {
        final int entity;
        final String id;
        int source;
        int total;
        long endsAt;
        int stacks;
        final List<FxSolids.Solid> solids = new ArrayList<>();

        State(int entity, String id) {
            this.entity = entity;
            this.id = id;
        }
    }

    private static final Map<Integer, Map<String, State>> BY_ENTITY = new HashMap<>();
    private static long now;

    private FxStatuses() {
    }

    static boolean has(int entity, String statusId) {
        Map<String, State> mine = BY_ENTITY.get(entity);
        return mine != null && mine.containsKey(statusId);
    }

    static State get(int entity, String statusId) {
        Map<String, State> mine = BY_ENTITY.get(entity);
        return mine == null ? null : mine.get(statusId);
    }

    static void clear() {
        BY_ENTITY.clear();
    }

    static void on(FxMessage.StatusOn on) {
        Map<String, State> mine = BY_ENTITY.computeIfAbsent(on.entityId(), k -> new HashMap<>());
        State state = mine.get(on.statusId());
        boolean fresh = state == null;
        int stacksBefore = fresh ? 0 : state.stacks;
        if (fresh) {
            state = new State(on.entityId(), on.statusId());
            mine.put(on.statusId(), state);
        }
        state.source = on.source();
        state.total = on.totalTicks();
        state.endsAt = now + on.remainingTicks();
        state.stacks = on.stacks();
        try {
            if (fresh) {
                appear(state);
            } else if (state.stacks < stacksBefore) {
                lostStack(state);
            } else if (state.stacks > stacksBefore) {
                appear(state);
            }
        } catch (RuntimeException e) {
            // Облик статуса — украшение: сломанный не должен терять сам статус,
            // по которому рисуется экран и сцены.
        }
    }

    static void off(FxMessage.StatusOff off) {
        Map<String, State> mine = BY_ENTITY.get(off.entityId());
        if (mine == null) {
            return;
        }
        State state = mine.remove(off.statusId());
        if (mine.isEmpty()) {
            BY_ENTITY.remove(off.entityId());
        }
        if (state == null) {
            return;
        }
        state.solids.forEach(FxSolids.Solid::release);
        Entity entity = entity(off.entityId());
        if (entity != null && off.statusId().equals("root")) {
            FxSounds.play("druid.roots.snap", entity.getX(), entity.getY(), entity.getZ(), 0.9f, 1f);
        }
    }

    static void tick() {
        now++;
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        float emit = FxEffects.emit();
        for (Map<String, State> mine : BY_ENTITY.values()) {
            for (State state : mine.values()) {
                Entity entity = level.getEntity(state.entity);
                if (entity == null) {
                    continue;
                }
                ambient(state, entity, emit);
            }
        }
    }

    private static Entity entity(int id) {
        Level level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(id);
    }

    // ------------------------------------------------------------------ появление

    /** Объекты, которые держатся, пока висит статус. */
    private static void appear(State state) {
        Entity entity = entity(state.entity);
        if (entity == null) {
            return;
        }
        int id = state.entity;
        String status = state.id;
        switch (status) {
            case "root" -> {
                // Корни на Росте держат дольше — и толще, с шипами. Мод видит это
                // по сроку статуса: решение владельца, отдельной пометки нет.
                boolean thick = state.total >= 50;
                int count = thick ? 4 : 3;
                double base = Math.random() * Math.PI * 2;
                for (int i = 0; i < count; i++) {
                    double angle = base + Math.PI * 2 * i / count;
                    FxSolids.Tube root = new FxSolids.Tube(FxGeometry.rootAroundLegs(
                            entity.getX(), entity.getY(), entity.getZ(), angle, 0.85,
                            thick ? 1.25 : 0.95, 0.6 + 0.2 * (i % 2)),
                            thick ? 0.13 : 0.085, "block/mangrove_log", 0xFFD8C8B0,
                            8, 0, 6);
                    root.holding = () -> has(id, "root");
                    root.decor = false;
                    state.solids.add(root);
                    FxSolids.add(root);
                    if (thick) {
                        FxSolids.Tube thorn = new FxSolids.Tube(FxGeometry.rootSpike(
                                new java.util.Random(), entity.getX() + Math.cos(angle) * 0.6,
                                entity.getY(), entity.getZ() + Math.sin(angle) * 0.6, 0.7),
                                0.05, "block/mangrove_roots_side", 0xFFB0D890, 6, 0, 6);
                        thorn.holding = () -> has(id, "root");
                        state.solids.add(thorn);
                        FxSolids.add(thorn);
                    }
                }
                FxSounds.play("druid.roots.creak", entity.getX(), entity.getY(), entity.getZ(),
                        0.9f, thick ? 0.8f : 1f);
            }
            case "bark_guard" -> {
                // Пластины коры смыкаются на плечах и груди. Каждый заряд — свой
                // слой; сорванный заряд раскалывает один слой, а не всю кору.
                int layer = Math.max(0, state.stacks - 1);
                for (int i = 0; i < 4; i++) {
                    double angle = Math.PI / 2 * i + layer * 0.4;
                    FxSolids.Model plate = new FxSolids.Model(Blocks.OAK_WOOD.defaultBlockState(),
                            entity.getX(), entity.getY(), entity.getZ(), 0.3f + layer * 0.04f,
                            10, 0, 8);
                    plate.follow = id;
                    plate.offsetX = Math.cos(angle) * (0.32 + layer * 0.06);
                    plate.offsetZ = Math.sin(angle) * (0.32 + layer * 0.06);
                    plate.offsetY = 0.75 + (i % 2) * 0.35;
                    plate.holding = () -> has(id, "bark_guard");
                    plate.decor = false;
                    state.solids.add(plate);
                    FxSolids.add(plate);
                }
                FxSounds.play("druid.bark.grow", entity.getX(), entity.getY(), entity.getZ(),
                        0.9f, 1f);
            }
            case "minion:beast_ward" -> {
                FxSolids.Model bloom = new FxSolids.Model(
                        Blocks.FLOWERING_AZALEA.defaultBlockState(),
                        entity.getX(), entity.getY(), entity.getZ(), 0.35f, 12, 0, 10);
                bloom.follow = id;
                bloom.offsetY = entity.getBbHeight() * 0.85;
                bloom.holding = () -> has(id, "minion:beast_ward");
                state.solids.add(bloom);
                FxSolids.add(bloom);
            }
            case "minion:beast_fury" -> {
                for (int i = 0; i < 3; i++) {
                    FxSolids.Model thorn = new FxSolids.Model(
                            Blocks.SWEET_BERRY_BUSH.defaultBlockState(),
                            entity.getX(), entity.getY(), entity.getZ(), 0.28f, 12, 0, 10);
                    thorn.follow = id;
                    thorn.offsetY = entity.getBbHeight() * 0.8;
                    thorn.offsetX = (i - 1) * 0.18;
                    thorn.holding = () -> has(id, "minion:beast_fury");
                    state.solids.add(thorn);
                    FxSolids.add(thorn);
                }
            }
            default -> {
            }
        }
    }

    /** Стака стало меньше: у коры — раскололся слой. */
    private static void lostStack(State state) {
        if (!state.id.equals("bark_guard")) {
            return;
        }
        int keep = state.stacks * 4;
        for (int i = state.solids.size() - 1; i >= keep && i >= 0; i--) {
            FxSolids.Solid plate = state.solids.remove(i);
            plate.release();
            if (plate instanceof FxSolids.Model model) {
                splinters(model.x, model.y, model.z, 3);
            }
        }
        Entity entity = entity(state.entity);
        if (entity != null) {
            FxSounds.play("druid.bark.crack", entity.getX(), entity.getY(), entity.getZ(), 1f, 1f);
        }
    }

    /** Щепа: несколько кусочков коры разлетаются и падают. */
    static void splinters(double x, double y, double z, int count) {
        for (int i = 0; i < count; i++) {
            FxSolids.Model chip = new FxSolids.Model(Blocks.OAK_WOOD.defaultBlockState(),
                    x, y, z, 0.12f, 1, 14, 6);
            chip.vx = FxMotes.jitter(0.15f);
            chip.vy = 0.15 + FxMotes.random() * 0.12;
            chip.vz = FxMotes.jitter(0.15f);
            chip.gravity = 0.04;
            chip.spin = FxMotes.jitter(25f);
            FxSolids.add(chip);
        }
    }

    // ------------------------------------------------------------------ живое

    /** Каждый тик: искры, пузыри, листья — то, что делает состояние живым. */
    private static void ambient(State state, Entity entity, float emit) {
        FxMotes motes = FxEffects.motes();
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        double h = entity.getBbHeight();
        switch (state.id) {
            case "stun" -> {
                // Три звезды кружат над головой: рисуются кадром, здесь — искорка.
                if (FxMotes.random() < 0.3f * emit) {
                    motes.spawn(x, y + h + 0.3, z, FxMotes.jitter(0.02f), 0.01f,
                            FxMotes.jitter(0.02f), 0.16f, 0xFFFFE9A8, 10, 0.9f, FxDraw.Tex.STAR);
                }
            }
            case "potion:poison" -> {
                if (FxMotes.random() < 0.5f * emit) {
                    motes.spawn(x + FxMotes.jitter(0.3f), y + h * (0.4 + FxMotes.random() * 0.5),
                            z + FxMotes.jitter(0.3f), FxMotes.jitter(0.005f), 0.025f,
                            FxMotes.jitter(0.005f), 0.14f, 0xFF7FD957, 20, 0.97f,
                            FxDraw.Tex.GLOW);
                }
            }
            case "slowed" -> {
                if (FxMotes.random() < 0.25f * emit) {
                    motes.spawn(x + FxMotes.jitter(0.4f), y + 0.05, z + FxMotes.jitter(0.4f),
                            0, 0.004f, 0, 0.22f, 0xFF7A5A33, 18, 0.9f, FxDraw.Tex.LEAF);
                }
            }
            case "growth", "wither" -> {
                if (FxMotes.random() < 0.08f * emit * Math.max(1, state.stacks)) {
                    int colour = state.id.equals("growth") ? 0xFF7FE05A : 0xFFB0763A;
                    double a = FxMotes.random() * Math.PI * 2;
                    motes.spawn(x + Math.cos(a) * 0.45, y + h * 0.85, z + Math.sin(a) * 0.45,
                            (float) -Math.sin(a) * 0.02f, -0.006f, (float) Math.cos(a) * 0.02f,
                            0.16f, colour, 26, 0.98f, FxDraw.Tex.LEAF);
                }
            }
            case "minion:beast_fury" -> {
                if (FxMotes.random() < 0.2f * emit) {
                    motes.spawn(x + FxMotes.jitter(0.3f), y + h * 0.7, z + FxMotes.jitter(0.3f),
                            0, 0.01f, 0, 0.14f, 0xFFB0763A, 16, 0.95f, FxDraw.Tex.LEAF);
                }
            }
            default -> {
            }
        }
    }

    /** Кадр: то, что светится и тянется — звёзды, нити к хозяину. */
    static void draw(FxDraw draw, float partial) {
        Level level = draw.level;
        for (Map<String, State> mine : BY_ENTITY.values()) {
            for (State state : mine.values()) {
                Entity entity = level.getEntity(state.entity);
                if (entity == null) {
                    continue;
                }
                Vec3 at = entity.getPosition(partial);
                double h = entity.getBbHeight();
                float t = now + partial;
                switch (state.id) {
                    case "stun" -> {
                        for (int i = 0; i < 3; i++) {
                            double a = t * 0.25 + i * Math.PI * 2 / 3;
                            draw.sprite(FxDraw.Tex.STAR, at.x + Math.cos(a) * 0.35, at.y + h + 0.25,
                                    at.z + Math.sin(a) * 0.35, 0.3f, t * 0.1f, 0xFFFFE9A8, 0.95f);
                        }
                    }
                    case "slowed" -> draw.circle(FxDraw.Tex.RING, at.x, at.y, at.z, 0.55, 0.35,
                            0xFF7A5A33, 0.45f, 0.5f, 0, 0.05f);
                    case "minion:beast_ward" -> {
                        Entity owner = level.getEntity(state.source);
                        if (owner != null) {
                            Vec3 o = owner.getPosition(partial);
                            double[] xs = {at.x, (at.x + o.x) / 2, o.x};
                            double[] ys = {at.y + h * 0.6, (at.y + o.y) / 2 + 1.4, o.y + 1.0};
                            double[] zs = {at.z, (at.z + o.z) / 2, o.z};
                            draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, 3, 0.12f, 0xFF7FE05A, 0.5f,
                                    0.5f, t * 0.1f);
                        }
                    }
                    case "minion:beast_fury" -> {
                        draw.sprite(FxDraw.Tex.GLOW, at.x, at.y + h * 0.75, at.z, 0.5f, 0,
                                0xFFB0402A, 0.6f);
                    }
                    default -> {
                    }
                }
            }
        }
    }
}
