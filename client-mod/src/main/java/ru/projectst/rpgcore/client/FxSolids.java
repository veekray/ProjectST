package ru.projectst.rpgcore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;

/**
 * Объекты в мире: корни, лозы, лианы коры, цветы, комья земли.
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

    /** Текстура из атласа блоков: {@code block/vine}, {@code item/iron_sword}; без пространства — ванильная. */
    static TextureAtlasSprite sprite(String path) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(ResourceLocation.parse(path));
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
        /**
         * Привязка к существу: точки кривой — смещения от его ног, и трубка
         * ходит вместе с ним (лианы коры). Меньше нуля — стоит на месте.
         */
        int follow = -1;
        /** Насколько сужается к кончику: 0.88 — корень острием, лиана — тупее. */
        double taper = 0.88;
        /** Поворот вокруг существа, радиан за тик: лианы медленно вьются. */
        double spin;
        private double atX;
        private double atY;
        private double atZ;
        private double prevAtX;
        private double prevAtY;
        private double prevAtZ;

        Tube(double[][] points, double thickness, String texture, int tint,
             int grow, int hold, int leave) {
            super(grow, hold, leave);
            this.points = points;
            this.thickness = thickness;
            this.sprite = sprite(texture);
            this.tint = tint;
        }

        /** Следовать за существом; точки кривой — смещения от его ног. */
        Tube follow(net.minecraft.world.entity.Entity entity) {
            this.follow = entity.getId();
            this.atX = this.prevAtX = entity.getX();
            this.atY = this.prevAtY = entity.getY();
            this.atZ = this.prevAtZ = entity.getZ();
            return this;
        }

        @Override
        void tick(Level level) {
            super.tick(level);
            if (follow < 0) {
                return;
            }
            prevAtX = atX;
            prevAtY = atY;
            prevAtZ = atZ;
            net.minecraft.world.entity.Entity entity = level.getEntity(follow);
            if (entity == null) {
                release();
                return;
            }
            atX = entity.getX();
            atY = entity.getY();
            atZ = entity.getZ();
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
            AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
            if (follow >= 0) {
                // Вращение уводит точки по кругу: берём весь круг их радиуса.
                double reach = Math.max(Math.max(Math.abs(minX), Math.abs(maxX)),
                        Math.max(Math.abs(minZ), Math.abs(maxZ)));
                box = new AABB(atX - reach, atY + minY, atZ - reach,
                        atX + reach, atY + maxY, atZ + reach);
            }
            return box.inflate(thickness + 0.5);
        }

        /** Точка кривой с покачиванием, прижатая к границе или перенесённая к существу. */
        private double[] at(double t, float time, float partial) {
            double[] p = FxGeometry.spline(points, t);
            double wobble = Math.sin(time * 0.15 + t * 4) * sway * t;
            p[0] += wobble;
            p[2] += wobble * 0.7;
            if (follow >= 0) {
                double turn = spin * time;
                double cos = Math.cos(turn);
                double sin = Math.sin(turn);
                double x = p[0] * cos - p[2] * sin;
                double z = p[0] * sin + p[2] * cos;
                p[0] = prevAtX + (atX - prevAtX) * partial + x;
                p[1] += prevAtY + (atY - prevAtY) * partial;
                p[2] = prevAtZ + (atZ - prevAtZ) * partial + z;
                return p;
            }
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
                centre[i] = at(length * i / n, time, partial);
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
                double radius = thickness * (1 - taper * ((double) i / n));
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

    // ------------------------------------------------------------------ модель

    /**
     * Модель ванильного блока или предмета в мире: цветок, ком земли, меч,
     * стрела, щит, череп, знамя.
     *
     * <p>Масштаб растёт при появлении и падает при уходе; поворот и движение
     * задаёт сцена — летящая щепка, вращающийся клинок. Модель блока стоит
     * низом на точке, предмет — серединой: так меч, воткнутый в землю, и ком
     * земли ставятся одинаково просто.
     *
     * <p>Привязка: к существу ({@link #follow}) — модель ходит за ним со
     * смещением; к точке ({@link #anchor}) — за чем угодно, например за
     * летящим снарядом. Привязка пропала — модель уходит.
     */
    static final class Model extends Solid {
        final BlockState state;
        final ItemStack item;
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
        /** Сопротивление воздуха: скорость за тик умножается на него. */
        double drag = 0.96;
        final float scale;
        float yaw;
        float pitch;
        float roll;
        float yawSpeed;
        float pitchSpeed;
        float rollSpeed;
        /** Покачивание вокруг своего крена, градусы: серп над головой, знамя на ветру. */
        float sway;
        private float prevYaw;
        private float prevPitch;
        private float prevRoll;
        /** Привязка к существу: модель следует за ним (кора на плечах). */
        int follow = -1;
        /** Поворачиваться вслед за существом, к которому привязана. */
        boolean faceFollow = true;
        /** Привязка к точке: {@code null} из поставщика — модель уходит. */
        Supplier<Vec3> anchor;
        double offsetX;
        double offsetY;
        double offsetZ;
        /** Смещение вперёд по взгляду существа, к которому привязана: клинок перед грудью. */
        double forward;
        /**
         * Куда смотрит остриё, каждый тик: стрела в полёте клонится вслед за
         * снижением. {@code null} — поворот задают скорости.
         */
        Supplier<Vec3> aim;

        Model(BlockState state, double x, double y, double z, float scale,
              int grow, int hold, int leave) {
            this(state, null, x, y, z, scale, grow, hold, leave);
        }

        Model(ItemStack item, double x, double y, double z, float scale,
              int grow, int hold, int leave) {
            this(null, item, x, y, z, scale, grow, hold, leave);
        }

        private Model(BlockState state, ItemStack item, double x, double y, double z, float scale,
                      int grow, int hold, int leave) {
            super(grow, hold, leave);
            this.state = state;
            this.item = item;
            this.x = this.prevX = x;
            this.y = this.prevY = y;
            this.z = this.prevZ = z;
            this.scale = scale;
        }

        /** Кувырок: рыскание и тангаж вместе, как у летящей щепки. */
        Model tumble(float speed) {
            this.yawSpeed = speed;
            this.pitchSpeed = speed * 0.6f;
            return this;
        }

        /**
         * Остриём по направлению — для мечей, стрел и мотыг: их модель лежит
         * по диагонали, остриё вверх-вправо, поэтому крен 45° и тангаж +90°
         * разворачивают её остриём вперёд.
         */
        Model point(double dx, double dy, double dz) {
            face(dx, dy, dz);
            pitch += 90;
            roll = 45;
            prevPitch = pitch;
            prevRoll = roll;
            return this;
        }

        /** Повернуть носом по направлению (для стрелы и клинка в полёте). */
        Model face(double dx, double dy, double dz) {
            double flat = Math.sqrt(dx * dx + dz * dz);
            this.yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
            this.pitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
            this.prevYaw = yaw;
            this.prevPitch = pitch;
            return this;
        }

        Model at(float yaw, float pitch, float roll) {
            this.yaw = this.prevYaw = yaw;
            this.pitch = this.prevPitch = pitch;
            this.roll = this.prevRoll = roll;
            return this;
        }

        @Override
        void tick(Level level) {
            super.tick(level);
            prevX = x;
            prevY = y;
            prevZ = z;
            prevYaw = yaw;
            prevPitch = pitch;
            prevRoll = roll;
            yaw += yawSpeed;
            pitch += pitchSpeed;
            roll += rollSpeed;
            if (sway != 0) {
                roll += sway * (float) (Math.sin(age * 0.12) - Math.sin((age - 1) * 0.12));
            }
            if (follow >= 0) {
                net.minecraft.world.entity.Entity entity = level.getEntity(follow);
                if (entity == null) {
                    release();
                } else {
                    double look = Math.toRadians(entity.getYRot());
                    x = entity.getX() + offsetX - Math.sin(look) * forward;
                    y = entity.getY() + offsetY;
                    z = entity.getZ() + offsetZ + Math.cos(look) * forward;
                    if (faceFollow) {
                        yaw = -entity.getYRot();
                        prevYaw = yaw;
                    }
                }
                return;
            }
            if (aim != null) {
                Vec3 d = aim.get();
                if (d != null && d.lengthSqr() > 1e-8) {
                    double flat = Math.sqrt(d.x * d.x + d.z * d.z);
                    yaw = (float) Math.toDegrees(Math.atan2(d.x, d.z));
                    pitch = (float) -Math.toDegrees(Math.atan2(d.y, flat)) + 90;
                    roll = 45;
                    if (age <= 1) {
                        prevYaw = yaw;
                        prevPitch = pitch;
                        prevRoll = roll;
                    }
                }
            }
            if (anchor != null) {
                Vec3 at = anchor.get();
                if (at == null) {
                    release();
                } else {
                    x = at.x + offsetX;
                    y = at.y + offsetY;
                    z = at.z + offsetZ;
                }
                return;
            }
            x += vx;
            y += vy;
            z += vz;
            vy -= gravity;
            vx *= drag;
            vz *= drag;
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
            float ry = prevYaw + (yaw - prevYaw) * partial;
            float rp = prevPitch + (pitch - prevPitch) * partial;
            float rr = prevRoll + (roll - prevRoll) * partial;
            int light = light(level, ix, iy + 0.3, iz);
            pose.pushPose();
            pose.translate(ix - camX, iy - camY, iz - camZ);
            pose.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(ry),
                    (float) Math.toRadians(rp), (float) Math.toRadians(rr)));
            pose.scale(s, s, s);
            if (item != null) {
                Minecraft.getInstance().getItemRenderer().renderStatic(item,
                        ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                        level, 0);
            } else {
                pose.translate(-0.5, 0, -0.5);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers,
                        light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            }
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------ полоса

    /**
     * Полоса вдоль кривой из двух скрещённых плоскостей: цепь, леска, нить.
     *
     * <p>Так нарисована ванильная цепь: две плоскости крестом с текстурой
     * звеньев. Кривая берётся заново каждый кадр — цепь между колдуном и целью
     * тянется за обоими. Текстура повторяется по длине: кривая режется на
     * куски ровно в одно повторение, иначе спрайт атласа пришлось бы
     * заворачивать, а он не заворачивается.
     */
    static final class Strip extends Solid {
        /** Кривая в мире на этот кадр; {@code null} — концов больше нет, полоса уходит. */
        final java.util.function.Function<Float, double[][]> path;
        final TextureAtlasSprite sprite;
        final double width;
        /** Сколько блоков длины на одно повторение текстуры. */
        final double repeat;
        final int tint;
        /** Доли ширины текстуры для первой и второй плоскости. */
        float u0 = 0;
        float u1 = 1;
        float u2 = 0;
        float u3 = 1;
        /** Провисание посередине, блоков. */
        double sag;
        private double[][] last;

        Strip(java.util.function.Function<Float, double[][]> path, String texture, double width,
              double repeat, int tint, int grow, int hold, int leave) {
            super(grow, hold, leave);
            this.path = path;
            this.sprite = sprite(texture);
            this.width = width;
            this.repeat = Math.max(0.05, repeat);
            this.tint = tint;
        }

        /** Цепь ванильной текстурой: звенья в двух плоскостях, как у блока цепи. */
        static Strip chain(java.util.function.Function<Float, double[][]> path, double scale,
                           int grow, int hold, int leave) {
            Strip strip = new Strip(path, "block/chain", 3 / 16.0 * scale, scale, 0xFFFFFFFF,
                    grow, hold, leave);
            strip.u0 = 0;
            strip.u1 = 3 / 16f;
            strip.u2 = 3 / 16f;
            strip.u3 = 6 / 16f;
            return strip;
        }

        @Override
        void tick(Level level) {
            super.tick(level);
            if (leftAt < 0 && path.apply(1f) == null) {
                release();
            }
        }

        @Override
        AABB bounds() {
            double[][] p = last != null ? last : path.apply(1f);
            if (p == null || p.length == 0) {
                return new AABB(0, -1000, 0, 0, -1000, 0);
            }
            AABB box = new AABB(p[0][0], p[0][1], p[0][2], p[0][0], p[0][1], p[0][2]);
            for (double[] q : p) {
                box = box.minmax(new AABB(q[0], q[1], q[2], q[0], q[1], q[2]));
            }
            return box.inflate(width + sag + 0.5);
        }

        @Override
        void render(PoseStack pose, MultiBufferSource buffers, double camX, double camY,
                    double camZ, float partial, Level level) {
            float shown = presence(partial);
            double[][] p = path.apply(partial);
            if (p == null) {
                p = last;
            }
            if (p == null || p.length < 2 || shown <= 0.01f) {
                return;
            }
            last = p;
            // Длина по кривой и точка на доле длины: полоса растёт от начала.
            double[] cum = new double[p.length];
            for (int i = 1; i < p.length; i++) {
                cum[i] = cum[i - 1] + Math.sqrt(sq(p[i][0] - p[i - 1][0]) + sq(p[i][1] - p[i - 1][1])
                        + sq(p[i][2] - p[i - 1][2]));
            }
            double total = cum[p.length - 1] * FxGeometry.easeOut(shown);
            if (total < 0.01) {
                return;
            }
            VertexConsumer out = buffers.getBuffer(
                    RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
            PoseStack.Pose last = pose.last();
            int r = (tint >> 16) & 0xFF;
            int g = (tint >> 8) & 0xFF;
            int b = tint & 0xFF;
            double full = cum[p.length - 1];
            for (double from = 0; from < total; from += repeat) {
                double to = Math.min(total, from + repeat);
                double[] a = along(p, cum, from, full);
                double[] c = along(p, cum, to, full);
                double[] dir = normalize(c[0] - a[0], c[1] - a[1], c[2] - a[2]);
                double[] ref = Math.abs(dir[1]) > 0.95 ? new double[] {1, 0, 0} : new double[] {0, 1, 0};
                double[] side = normalize(cross(dir, ref));
                double[] up = cross(side, dir);
                float v1 = (float) ((to - from) / repeat);
                int light = light(level, a[0], a[1] + 0.2, a[2]);
                plane(out, last, a, c, side, up, camX, camY, camZ, r, g, b, u0, u1, v1, light);
                plane(out, last, a, c, up, side, camX, camY, camZ, r, g, b, u2, u3, v1, light);
            }
        }

        /** Точка на расстоянии {@code d} по кривой, с провисанием посередине. */
        private double[] along(double[][] p, double[] cum, double d, double full) {
            int i = 1;
            while (i < p.length - 1 && cum[i] < d) {
                i++;
            }
            double span = cum[i] - cum[i - 1];
            double k = span < 1.0E-9 ? 0 : (d - cum[i - 1]) / span;
            double[] q = {p[i - 1][0] + (p[i][0] - p[i - 1][0]) * k,
                    p[i - 1][1] + (p[i][1] - p[i - 1][1]) * k,
                    p[i - 1][2] + (p[i][2] - p[i - 1][2]) * k};
            if (sag != 0 && full > 0) {
                double t = d / full;
                q[1] -= sag * 4 * t * (1 - t);
            }
            return q;
        }

        private void plane(VertexConsumer out, PoseStack.Pose pose, double[] a, double[] c,
                           double[] side, double[] normal, double camX, double camY, double camZ,
                           int r, int g, int b, float ua, float ub, float v1, int light) {
            double h = width / 2;
            double[] a0 = {a[0] - side[0] * h, a[1] - side[1] * h, a[2] - side[2] * h};
            double[] a1 = {a[0] + side[0] * h, a[1] + side[1] * h, a[2] + side[2] * h};
            double[] c0 = {c[0] - side[0] * h, c[1] - side[1] * h, c[2] - side[2] * h};
            double[] c1 = {c[0] + side[0] * h, c[1] + side[1] * h, c[2] + side[2] * h};
            float su0 = sprite.getU(ua);
            float su1 = sprite.getU(ub);
            float sv0 = sprite.getV(0);
            float sv1 = sprite.getV(v1);
            vertex(out, pose, a0, camX, camY, camZ, r, g, b, 255, su0, sv0, light, normal);
            vertex(out, pose, c0, camX, camY, camZ, r, g, b, 255, su0, sv1, light, normal);
            vertex(out, pose, c1, camX, camY, camZ, r, g, b, 255, su1, sv1, light, normal);
            vertex(out, pose, a1, camX, camY, camZ, r, g, b, 255, su1, sv0, light, normal);
        }
    }

    // ------------------------------------------------------------------ двойник

    /**
     * Полупрозрачный двойник существа: его собственная модель и скин, без
     * новых сущностей.
     *
     * <p>Берётся модель из рендерера существа и рисуется полупрозрачным
     * проходом, с цветом класса поверх: отражение плута, копии ловкача,
     * силуэт растворившегося убийцы. Поза — та, что была в миг появления;
     * двойник может стоять, плыть или кружить вокруг хозяина.
     */
    static final class Ghost extends Solid {
        final int entityId;
        double x;
        double y;
        double z;
        double prevX;
        double prevY;
        double prevZ;
        double vx;
        double vy;
        double vz;
        float yaw;
        float yawSpeed;
        /** Насколько видно: доля непрозрачности на пике. */
        float alpha = 0.45f;
        int tint = 0xFFFFFFFF;
        /** Лежит на боку, как павший: ложная смерть ловкача. */
        boolean lying;
        /** Кружить вокруг существа: радиус, угол, скорость; радиус 0 — стоять. */
        double orbit;
        double orbitAngle;
        double orbitSpeed;

        Ghost(net.minecraft.world.entity.Entity of, int grow, int hold, int leave) {
            super(grow, hold, leave);
            this.entityId = of.getId();
            this.x = this.prevX = of.getX();
            this.y = this.prevY = of.getY();
            this.z = this.prevZ = of.getZ();
            this.yaw = of instanceof LivingEntity living ? living.yBodyRot : of.getYRot();
        }

        @Override
        void tick(Level level) {
            super.tick(level);
            prevX = x;
            prevY = y;
            prevZ = z;
            if (orbit > 0) {
                net.minecraft.world.entity.Entity owner = level.getEntity(entityId);
                if (owner == null) {
                    release();
                    return;
                }
                orbitAngle += orbitSpeed;
                x = owner.getX() + Math.cos(orbitAngle) * orbit;
                y = owner.getY();
                z = owner.getZ() + Math.sin(orbitAngle) * orbit;
                yaw = (float) Math.toDegrees(orbitAngle) - 90;
                return;
            }
            x += vx;
            y += vy;
            z += vz;
            yaw += yawSpeed;
        }

        @Override
        AABB bounds() {
            return new AABB(x - 1, y, z - 1, x + 1, y + 2.2, z + 1);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        void render(PoseStack pose, MultiBufferSource buffers, double camX, double camY,
                    double camZ, float partial, Level level) {
            float shown = presence(partial);
            if (shown <= 0.01f) {
                return;
            }
            net.minecraft.world.entity.Entity entity = level.getEntity(entityId);
            if (!(entity instanceof LivingEntity living)) {
                return;
            }
            var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(living);
            if (!(renderer instanceof LivingEntityRenderer lr)) {
                return;
            }
            EntityModel model = lr.getModel();
            ResourceLocation skin = lr.getTextureLocation(living);
            double ix = prevX + (x - prevX) * partial;
            double iy = prevY + (y - prevY) * partial;
            double iz = prevZ + (z - prevZ) * partial;
            float age = this.age + partial;
            pose.pushPose();
            pose.translate(ix - camX, iy - camY, iz - camZ);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f - yaw));
            if (lying) {
                // Как у ванильной смерти: набок вокруг оси вдоль взгляда.
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90f));
            }
            pose.scale(-1f, -1f, 1f);
            if (living instanceof net.minecraft.world.entity.player.Player) {
                pose.scale(0.9375f, 0.9375f, 0.9375f);
            }
            pose.translate(0, -1.501f, 0);
            // Поза покоя: без шага и поворота головы — двойник стоит, как отпечаток.
            model.attackTime = 0;
            model.riding = false;
            model.young = living.isBaby();
            model.prepareMobModel(living, 0, 0, partial);
            model.setupAnim(living, 0, 0, age, 0, 0);
            int a = Math.round(255 * alpha * shown);
            int colour = (a << 24) | (tint & 0xFFFFFF);
            VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(skin));
            model.renderToBuffer(pose, out, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    colour);
            pose.popPose();
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

    private static double sq(double v) {
        return v * v;
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
