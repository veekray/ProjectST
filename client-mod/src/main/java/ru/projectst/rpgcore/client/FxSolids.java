package ru.projectst.rpgcore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;

/**
 * Объекты в мире: корни, лозы, кора, цветы, комья земли.
 *
 * <p><b>Без новых записей в реестрах</b> — как и всё в эффектах (причина в
 * шапке {@link FxDraw}). Жёсткие объекты — готовые модели ванильных блоков:
 * {@code BlockRenderDispatcher.renderSingleBlock} рисует модель в любой матрице
 * без мира. Живые — корень, лоза — своя трубка вдоль кривой с ванильной
 * текстурой из атласа блоков: модель блока на изгибе дала бы ступеньки и стоила
 * бы в шесть раз больше граней.
 *
 * <p>Рисуются в стадии после существ, непрозрачно, с записью глубины: корень
 * прячется за стеной и за ногой, которую обвивает, как настоящий. Свечение
 * поверх — по-прежнему {@link FxDraw}.
 *
 * <p><b>У каждого объекта три фазы:</b> вырасти, держать, уйти. «Держать» —
 * либо столько-то тиков, либо пока верно условие: корни на ногах держатся,
 * пока на цели висит статус, и уходят ровно тогда, когда его сняли.
 */
final class FxSolids {

    /** Предел объектов: двадцать друидов с корнями в одной точке — не повод ронять кадр. */
    static final int MAX = 640;

    /** Дальше этого объекты не рисуются: их не разглядеть, а грани стоят как вблизи. */
    static final double FAR = 64;

    private static final List<Solid> SOLIDS = new ArrayList<>();

    private FxSolids() {
    }

    static int count() {
        return SOLIDS.size();
    }

    /** Берёт объект, если есть место; украшения при тесноте не берутся. */
    static void add(Solid solid) {
        if (SOLIDS.size() >= MAX && solid.decor) {
            return;
        }
        if (SOLIDS.size() >= MAX + 128) {
            return;
        }
        SOLIDS.add(solid);
    }

    static void clear() {
        SOLIDS.clear();
    }

    static void tick(Level level) {
        Iterator<Solid> it = SOLIDS.iterator();
        while (it.hasNext()) {
            Solid solid = it.next();
            try {
                solid.tick(level);
            } catch (RuntimeException e) {
                solid.dead = true;
            }
            if (solid.dead) {
                it.remove();
            }
        }
    }

    /** Кадр: все объекты в кадре и не дальше {@link #FAR}. */
    static void render(PoseStack pose, MultiBufferSource buffers, double camX, double camY,
                       double camZ, float partial, net.minecraft.client.renderer.culling.Frustum frustum,
                       boolean full) {
        Level level = Minecraft.getInstance().level;
        if (level == null || SOLIDS.isEmpty()) {
            return;
        }
        double farSq = FAR * FAR;
        for (Solid solid : SOLIDS) {
            if (!full && solid.decor) {
                continue;
            }
            try {
                AABB box = solid.bounds();
                if (box.distanceToSqr(new net.minecraft.world.phys.Vec3(camX, camY, camZ)) > farSq
                        || !frustum.isVisible(box)) {
                    continue;
                }
                solid.render(pose, buffers, camX, camY, camZ, partial, level);
            } catch (RuntimeException e) {
                solid.dead = true;
            }
        }
    }

    static TextureAtlasSprite sprite(String path) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(ResourceLocation.withDefaultNamespace(path));
    }

    static int light(Level level, double x, double y, double z) {
        return LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
    }

    // ------------------------------------------------------------------ основа

    /** Объект с фазами «вырасти → держать → уйти». */
    abstract static class Solid {
        final int grow;
        final int hold;
        final int leave;
        /** Пока верно — объект держится, сколько бы ни прошло; {@code null} — по {@link #hold}. */
        BooleanSupplier holding;
        boolean decor = true;
        int age;
        int leftAt = -1;
        boolean dead;

        Solid(int grow, int hold, int leave) {
            this.grow = Math.max(1, grow);
            this.hold = Math.max(0, hold);
            this.leave = Math.max(1, leave);
        }

        /** Уйти сейчас: статус сняли, сцена кончилась. */
        void release() {
            if (leftAt < 0) {
                leftAt = age;
            }
        }

        void tick(Level level) {
            age++;
            if (leftAt < 0) {
                boolean keep = holding != null ? holding.getAsBoolean() : age < grow + hold;
                if (!keep) {
                    leftAt = age;
                }
            }
            if (leftAt >= 0 && age - leftAt > leave) {
                dead = true;
            }
        }

        /** Насколько объект «есть»: 0 → 1 при росте, 1 держит, 1 → 0 при уходе. */
        float presence(float partial) {
            float t = age + partial;
            float in = Math.min(1f, t / grow);
            if (leftAt < 0) {
                return in;
            }
            float out = 1f - Math.min(1f, (t - leftAt) / leave);
            return Math.min(in, out);
        }

        abstract AABB bounds();

        abstract void render(PoseStack pose, MultiBufferSource buffers, double camX, double camY,
                             double camZ, float partial, Level level);
    }

    // ------------------------------------------------------------------ трубка

    /**
     * Корень или лоза: сужающаяся трубка вдоль кривой.
     *
     * <p>Растёт по длине — рисуется доля кривой от начала, и кончик всегда острый,
     * — а уходит обратно в землю той же долей назад. Каждая точка, если задана
     * граница, прижимается внутрь круга: так ни один корень не вылезет за радиус,
     * даже если кривая между опорными точками выгнулась наружу.
     */
    static final class Tube extends Solid {
        private static final int SIDES = 6;

        final double[][] points;
        final double thickness;
        final TextureAtlasSprite sprite;
        final int tint;
        /** Граница: центр и радиус; радиус меньше нуля — без границы. */
        double boundX;
        double boundZ;
        double boundR = -1;
        /** Сколько сегментов вдоль: дальние и простые — меньше. */
        int segments = 12;
        /** Покачивание кончика: живой корень не стоит палкой. */
        double sway = 0.04;

        Tube(double[][] points, double thickness, String texture, int tint,
             int grow, int hold, int leave) {
            super(grow, hold, leave);
            this.points = points;
            this.thickness = thickness;
            this.sprite = sprite(texture);
            this.tint = tint;
        }

        Tube bound(double cx, double cz, double radius) {
            this.boundX = cx;
            this.boundZ = cz;
            this.boundR = radius;
            return this;
        }

        @Override
        AABB bounds() {
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            double maxZ = -Double.MAX_VALUE;
            for (double[] p : points) {
                minX = Math.min(minX, p[0]);
                minY = Math.min(minY, p[1]);
                minZ = Math.min(minZ, p[2]);
                maxX = Math.max(maxX, p[0]);
                maxY = Math.max(maxY, p[1]);
                maxZ = Math.max(maxZ, p[2]);
            }
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(thickness + 0.5);
        }

        /** Точка кривой с покачиванием и прижатая к границе. */
        private double[] at(double t, float time) {
            double[] p = FxGeometry.spline(points, t);
            double wobble = Math.sin(time * 0.15 + t * 4) * sway * t;
            p[0] += wobble;
            p[2] += wobble * 0.7;
            return boundR >= 0 ? FxGeometry.clampInside(boundX, boundZ, boundR, p) : p;
        }

        @Override
        void render(PoseStack pose, MultiBufferSource buffers, double camX, double camY,
                    double camZ, float partial, Level level) {
            float shown = presence(partial);
            if (shown <= 0.01f) {
                return;
            }
            float time = age + partial;
            double length = FxGeometry.easeOut(shown);
            int n = Math.max(2, (int) Math.ceil(segments * length));
            double[][] centre = new double[n + 1][];
            for (int i = 0; i <= n; i++) {
                centre[i] = at(length * i / n, time);
            }
            VertexConsumer out = buffers.getBuffer(
                    RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
            PoseStack.Pose last = pose.last();
            int a = 255;
            int r = (tint >> 16) & 0xFF;
            int g = (tint >> 8) & 0xFF;
            int b = tint & 0xFF;
            double[] ref = {0, 1, 0};
            double[][] prevRing = null;
            double[][] prevNormal = null;
            float prevV = 0;
            for (int i = 0; i <= n; i++) {
                double[] c = centre[i];
                double[] next = centre[Math.min(n, i + 1)];
                double[] prev = centre[Math.max(0, i - 1)];
                double[] tangent = normalize(next[0] - prev[0], next[1] - prev[1], next[2] - prev[2]);
                if (Math.abs(tangent[1]) > 0.95) {
                    ref = new double[] {1, 0, 0};
                }
                double[] side = normalize(cross(tangent, ref));
                double[] up = cross(side, tangent);
                // Сужение к кончику: острие у растущего корня — всегда на его
                // нынешнем конце, а не там, где кончится вся кривая.
                double radius = thickness * (1 - 0.88 * ((double) i / n));
                double[][] ring = new double[SIDES][];
                double[][] normals = new double[SIDES][];
                for (int s = 0; s < SIDES; s++) {
                    double ang = Math.PI * 2 * s / SIDES;
                    double nx = Math.cos(ang) * side[0] + Math.sin(ang) * up[0];
                    double ny = Math.cos(ang) * side[1] + Math.sin(ang) * up[1];
                    double nz = Math.cos(ang) * side[2] + Math.sin(ang) * up[2];
                    ring[s] = new double[] {c[0] + nx * radius, c[1] + ny * radius,
                            c[2] + nz * radius};
                    normals[s] = new double[] {nx, ny, nz};
                }
                float v = (float) (i % 2);
                if (prevRing != null) {
                    int light = light(level, c[0], c[1] + 0.2, c[2]);
                    for (int s = 0; s < SIDES; s++) {
                        int s2 = (s + 1) % SIDES;
                        float u0 = sprite.getU((float) s / SIDES);
                        float u1 = sprite.getU((float) (s + 1) / SIDES);
                        float v0 = sprite.getV(prevV);
                        float v1 = sprite.getV(v);
                        vertex(out, last, prevRing[s], camX, camY, camZ, r, g, b, a, u0, v0, light, prevNormal[s]);
                        vertex(out, last, ring[s], camX, camY, camZ, r, g, b, a, u0, v1, light, normals[s]);
                        vertex(out, last, ring[s2], camX, camY, camZ, r, g, b, a, u1, v1, light, normals[s2]);
                        vertex(out, last, prevRing[s2], camX, camY, camZ, r, g, b, a, u1, v0, light, prevNormal[s2]);
                    }
                }
                prevRing = ring;
                prevNormal = normals;
                prevV = v;
            }
        }
    }

    // ------------------------------------------------------------------ модель блока

    /**
     * Модель ванильного блока в мире: цветок, пластина коры, ком земли.
     *
     * <p>Масштаб растёт при появлении и падает при уходе; поворот и движение
     * задаёт сцена — летящая щепка, кружащийся лист.
     */
    static final class Model extends Solid {
        final BlockState state;
        double x;
        double y;
        double z;
        double prevX;
        double prevY;
        double prevZ;
        double vx;
        double vy;
        double vz;
        double gravity;
        final float scale;
        float yaw;
        float pitch;
        float spin;
        /** Привязка к существу: модель следует за ним (кора на плечах). */
        int follow = -1;
        double offsetX;
        double offsetY;
        double offsetZ;

        Model(BlockState state, double x, double y, double z, float scale,
              int grow, int hold, int leave) {
            super(grow, hold, leave);
            this.state = state;
            this.x = this.prevX = x;
            this.y = this.prevY = y;
            this.z = this.prevZ = z;
            this.scale = scale;
        }

        @Override
        void tick(Level level) {
            super.tick(level);
            prevX = x;
            prevY = y;
            prevZ = z;
            if (follow >= 0) {
                net.minecraft.world.entity.Entity entity = level.getEntity(follow);
                if (entity == null) {
                    release();
                } else {
                    x = entity.getX() + offsetX;
                    y = entity.getY() + offsetY;
                    z = entity.getZ() + offsetZ;
                    yaw = -entity.getYRot();
                }
                return;
            }
            x += vx;
            y += vy;
            z += vz;
            vy -= gravity;
            vx *= 0.96;
            vz *= 0.96;
            yaw += spin;
            pitch += spin * 0.6f;
        }

        @Override
        AABB bounds() {
            return new AABB(x - 1, y - 1, z - 1, x + 1, y + 2, z + 1).inflate(scale);
        }

        @Override
        void render(PoseStack pose, MultiBufferSource buffers, double camX, double camY,
                    double camZ, float partial, Level level) {
            float shown = presence(partial);
            if (shown <= 0.01f) {
                return;
            }
            double ix = prevX + (x - prevX) * partial;
            double iy = prevY + (y - prevY) * partial;
            double iz = prevZ + (z - prevZ) * partial;
            float s = scale * (float) FxGeometry.easeOut(shown);
            pose.pushPose();
            pose.translate(ix - camX, iy - camY, iz - camZ);
            pose.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(yaw),
                    (float) Math.toRadians(pitch), 0));
            pose.scale(s, s, s);
            pose.translate(-0.5, 0, -0.5);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers,
                    light(level, ix, iy + 0.3, iz), OverlayTexture.NO_OVERLAY, ModelData.EMPTY,
                    null);
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------ доспех

    /**
     * Доспех на теле: изогнутые пластины вокруг торса и плеч.
     *
     * <p>Не кубы блоков, а своя сетка: каждая пластина — кусок цилиндра вокруг
     * тела, с рваным верхним краем и лёгким раструбом, обтянутый текстурой из
     * атласа блоков. Следует за телом — позиция, поворот корпуса, присед. Слои
     * считаются по стакам статуса: второй заряд коры — второй, внешний слой с
     * мхом, и потерянный заряд скалывает именно его.
     */
    static final class Shell extends Solid {

        /**
         * Пластина в своей системе тела: +Z — вперёд, +X — влево.
         *
         * @param angle  куда смотрит середина пластины, радианы от +X
         * @param width  ширина дуги, радианы
         * @param side   смещение центра дуги вбок: плечи — не на оси тела
         * @param flare  насколько верх шире низа: пластина расходится раструбом
         * @param layer  слой: 0 — первый заряд, 1 — второй
         */
        record Plate(double angle, double width, double y0, double y1, double radius,
                     double side, double flare, int layer, boolean moss, long seed,
                     boolean cap, double capY) {

            /** Пластина пояса: кусок овала вокруг корпуса. */
            Plate(double angle, double width, double y0, double y1, double radius,
                  double side, double flare, int layer, boolean moss, long seed) {
                this(angle, width, y0, y1, radius, side, flare, layer, moss, seed, false, 0);
            }
        }

        /** Корпус плоский: спереди-сзади тоньше, чем в плечах. */
        private static final double DEPTH = 0.6;

        final int follow;
        final List<Plate> plates;
        final TextureAtlasSprite bark;
        final TextureAtlasSprite moss;
        /** Сколько слоёв сейчас держится: по стакам статуса. */
        java.util.function.IntSupplier layers = () -> 1;

        Shell(int follow, List<Plate> plates, int grow, int leave) {
            super(grow, 0, leave);
            this.follow = follow;
            this.plates = plates;
            this.bark = sprite("block/oak_log");
            this.moss = sprite("block/moss_block");
        }

        @Override
        AABB bounds() {
            Level level = Minecraft.getInstance().level;
            net.minecraft.world.entity.Entity entity = level == null ? null : level.getEntity(follow);
            if (entity == null) {
                return new AABB(0, -1000, 0, 0, -1000, 0);
            }
            return entity.getBoundingBox().inflate(0.6);
        }

        @Override
        void tick(Level level) {
            super.tick(level);
            if (level.getEntity(follow) == null) {
                release();
            }
        }

        @Override
        void render(PoseStack pose, MultiBufferSource buffers, double camX, double camY,
                    double camZ, float partial, Level level) {
            net.minecraft.world.entity.Entity entity = level.getEntity(follow);
            if (entity == null) {
                return;
            }
            float shown = presence(partial);
            if (shown <= 0.01f) {
                return;
            }
            double grown = FxGeometry.easeOut(shown);
            net.minecraft.world.phys.Vec3 at = entity.getPosition(partial);
            float yaw = entity instanceof net.minecraft.world.entity.LivingEntity living
                    ? net.minecraft.util.Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot)
                    : entity.getYRot();
            double yawRad = Math.toRadians(yaw);
            double cos = Math.cos(yawRad);
            double sin = Math.sin(yawRad);
            // Присел — доспех ниже и чуть вперёд, как и торс.
            double crouch = entity.isCrouching() ? -0.18 : 0;
            double scaleY = entity.getBbHeight() / 1.8;
            int light = light(level, at.x, at.y + 1, at.z);
            VertexConsumer out = buffers.getBuffer(
                    RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
            PoseStack.Pose last = pose.last();
            int shownLayers = layers.getAsInt();
            for (Plate plate : plates) {
                if (plate.layer() >= shownLayers) {
                    continue;
                }
                TextureAtlasSprite tex = plate.moss() ? moss : bark;
                // Появление: пластина поднимается от земли и смыкается к телу.
                double lift = (1 - grown) * -0.9;
                double spread = (1 - grown) * 0.3;
                int cols = 4;
                int rows = 3;
                double[][][] grid = new double[cols + 1][rows + 1][];
                double[][][] normal = new double[cols + 1][rows + 1][];
                for (int c = 0; c <= cols; c++) {
                    double a = plate.angle() + ((double) c / cols - 0.5) * plate.width();
                    for (int r = 0; r <= rows; r++) {
                        double k = (double) r / rows;
                        double bump = jag(plate.seed(), c, r) * 0.025;
                        double lx;
                        double ly;
                        double lz;
                        double nx;
                        double ny;
                        double nz;
                        if (plate.cap()) {
                            // Наплечник — купол над плечом: дуга поперёк плеча,
                            // вытянутая вдоль него спереди назад.
                            double rr = plate.radius() + bump + spread;
                            lx = Math.cos(a) * rr + plate.side();
                            ly = (plate.capY() + Math.sin(a) * rr) * scaleY + lift + crouch;
                            lz = plate.y0() + (plate.y1() - plate.y0()) * k;
                            nx = Math.cos(a);
                            ny = Math.sin(a);
                            nz = 0;
                        } else {
                            // Рваный верхний край: верхний ряд гуляет по высоте.
                            double top = r == rows ? jag(plate.seed() + 7, c, r) * 0.07 : 0;
                            double rr = plate.radius() + plate.flare() * k + bump + spread;
                            lx = Math.cos(a) * rr + plate.side();
                            lz = Math.sin(a) * rr * DEPTH;
                            ly = (plate.y0() + (plate.y1() - plate.y0()) * k + top) * scaleY
                                    + lift + crouch;
                            nx = Math.cos(a);
                            ny = 0.15;
                            nz = Math.sin(a);
                        }
                        grid[c][r] = new double[] {at.x + lx * cos - lz * sin, at.y + ly,
                                at.z + lx * sin + lz * cos};
                        normal[c][r] = new double[] {nx * cos - nz * sin, ny, nx * sin + nz * cos};
                    }
                }
                for (int c = 0; c < cols; c++) {
                    for (int r = 0; r < rows; r++) {
                        float u0 = tex.getU((float) c / cols);
                        float u1 = tex.getU((float) (c + 1) / cols);
                        float v0 = tex.getV(1f - (float) r / rows);
                        float v1 = tex.getV(1f - (float) (r + 1) / rows);
                        vertex(out, last, grid[c][r], camX, camY, camZ, 255, 255, 255, 255,
                                u0, v0, light, normal[c][r]);
                        vertex(out, last, grid[c + 1][r], camX, camY, camZ, 255, 255, 255, 255,
                                u1, v0, light, normal[c + 1][r]);
                        vertex(out, last, grid[c + 1][r + 1], camX, camY, camZ, 255, 255, 255,
                                255, u1, v1, light, normal[c + 1][r + 1]);
                        vertex(out, last, grid[c][r + 1], camX, camY, camZ, 255, 255, 255, 255,
                                u0, v1, light, normal[c][r + 1]);
                    }
                }
            }
        }

        /** Неровность коры: одна и та же у пластины каждый кадр, без дрожи. */
        private static double jag(long seed, int c, int r) {
            long h = seed * 31 + c * 977L + r * 7919L;
            h ^= h >>> 13;
            h *= 0x5bd1e995L;
            h ^= h >>> 15;
            return ((h & 0xFFFF) / 65535.0) * 2 - 1;
        }

        /**
         * Кора друида: пояс пластин на торсе и наплечники; второй слой — внешний,
         * ниже и с мхом.
         */
        static List<Plate> bark(long seed) {
            List<Plate> out = new ArrayList<>();
            // Два ряда пластин внахлёст, как чешуя: верхний сдвинут на полшага,
            // чтобы стыки нижнего прятались под ним.
            for (int i = 0; i < 7; i++) {
                double a = Math.PI * 2 * i / 7;
                out.add(new Plate(a, 1.05, 0.72, 1.06, 0.29, 0, 0.02, 0, false, seed + i));
                out.add(new Plate(a + Math.PI / 7, 1.05, 0.98, 1.4, 0.3, 0, 0.04, 0, false,
                        seed + 10 + i));
            }
            // Наплечники — купола над плечами.
            out.add(new Plate(1.2, 2.6, -0.17, 0.17, 0.21, 0.37, 0, 0, false, seed + 20, true, 1.37));
            out.add(new Plate(Math.PI - 1.2, 2.6, -0.17, 0.17, 0.21, -0.37, 0, 0, false, seed + 21,
                    true, 1.37));
            // Второй заряд: внешний пояс ниже, шире, местами в мху.
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2 * i / 6 + 0.25;
                out.add(new Plate(a, 1.2, 0.5, 0.95, 0.34, 0, 0.05, 1, i % 2 == 0, seed + 40 + i));
            }
            out.add(new Plate(1.2, 2.6, -0.2, 0.2, 0.27, 0.37, 0, 1, true, seed + 60, true, 1.39));
            out.add(new Plate(Math.PI - 1.2, 2.6, -0.2, 0.2, 0.27, -0.37, 0, 1, true, seed + 61,
                    true, 1.39));
            return out;
        }
    }

    // ------------------------------------------------------------------ вершины

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, double[] p, double camX,
                               double camY, double camZ, int r, int g, int b, int a, float u,
                               float v, int light, double[] n) {
        out.addVertex(pose, (float) (p[0] - camX), (float) (p[1] - camY), (float) (p[2] - camZ))
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) n[0], (float) n[1], (float) n[2]);
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] normalize(double[] v) {
        return normalize(v[0], v[1], v[2]);
    }

    private static double[] normalize(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        if (l < 1.0E-9) {
            return new double[] {0, 1, 0};
        }
        return new double[] {x / l, y / l, z / l};
    }
}
