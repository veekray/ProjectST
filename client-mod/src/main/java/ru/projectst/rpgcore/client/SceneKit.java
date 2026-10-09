package ru.projectst.rpgcore.client;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Кирпичи сцен: граница, ком земли, модель предмета, искры, круг-предупреждение.
 *
 * <p>Общее для всех классов, чтобы сцена навыка читалась как сценарий:
 * «граница, три клинка, звон», а не как сотня строк вершин. Всё, что
 * рисуется, — ванильные модели и текстуры (решение владельца: своих
 * текстур не рисуем).
 */
final class SceneKit {

    static final Random RANDOM = new Random();

    /**
     * Чей класс у существа — по последнему его навыку: событие каста, вспышка
     * с источником, зона с хозяином. Статус не несёт класса, а облик ему
     * нужен свой: корни мага — кольца рун, а не корни друида.
     */
    private static final java.util.Map<Integer, String> CLASSES = new java.util.HashMap<>();

    private SceneKit() {
    }

    static void noteClass(int entityId, String classId) {
        if (entityId != 0 && classId != null && !classId.isEmpty()) {
            if (CLASSES.size() > 4096) {
                CLASSES.clear();
            }
            CLASSES.put(entityId, classId);
        }
    }

    /** Класс существа; пустая строка — не видели его навыков. */
    static String classOf(int entityId) {
        return CLASSES.getOrDefault(entityId, "");
    }

    static void forgetClasses() {
        CLASSES.clear();
    }

    // ------------------------------------------------------------------ мир

    static Level level() {
        return Minecraft.getInstance().level;
    }

    static Entity entity(int id) {
        Level level = level();
        return level == null || id == 0 ? null : level.getEntity(id);
    }

    static double ground(double x, double z, double y) {
        Level level = level();
        return level == null ? y : FxGround.top(level, x, z, y);
    }

    /** Это я: экранные эффекты видит только тот, кого они касаются. */
    static boolean isSelf(int entityId) {
        var player = Minecraft.getInstance().player;
        return player != null && entityId != 0 && player.getId() == entityId;
    }

    /** Я рядом с точкой: вылеченный, задетый, стоящий в круге. */
    static boolean selfNear(double x, double y, double z, double radius) {
        var player = Minecraft.getInstance().player;
        return player != null && player.distanceToSqr(x, y, z) <= radius * radius;
    }

    /** Грудь существа: откуда тянется цепь, куда бьёт клинок. */
    static Vec3 chest(Entity entity, float partial) {
        Vec3 at = entity.getPosition(partial);
        return new Vec3(at.x, at.y + entity.getBbHeight() * 0.65, at.z);
    }

    static int primary(String classId) {
        return 0xFF000000 | FxStyle.look(classId).primary();
    }

    static int accent(String classId) {
        return 0xFF000000 | FxStyle.look(classId).accent();
    }

    // ------------------------------------------------------------------ виды

    /** Граница области: обычная волна до радиуса в облике класса. */
    static void border(FxMessage.Burst e, int grow, int hold, int fade, float motes) {
        style(e, FxStyle.Kind.WAVE, 0, 0, grow, hold, fade, false, motes, 0, null);
    }

    /** Сектор конуса ровно по углу и радиусу выборки. */
    static void cone(FxMessage.Burst e, int grow, int hold, int fade, float motes) {
        style(e, FxStyle.Kind.CONE, 0, 0, grow, hold, fade, false, motes, 0, null);
    }

    /** Любой вид из {@link FxKinds} по этому событию: цвета 0 — цвета класса. */
    static void style(FxMessage.Burst e, FxStyle.Kind kind, int primary, int accent, int grow,
                      int hold, int fade, boolean runes, float motes, float size, FxDraw.Tex motif) {
        FxEffects.addEffect(new FxKinds.Burst(
                new FxStyle(kind, primary, accent, grow, hold, fade, runes, motes, size, motif), e));
    }

    /** Тот же вид в другой точке: вспышка у ног цели, волна у кастера. */
    static void styleAt(FxMessage.Burst e, double x, double y, double z, float radius,
                        FxStyle.Kind kind, int primary, int accent, int grow, int hold, int fade,
                        float motes, float size, FxDraw.Tex motif) {
        FxMessage.Burst moved = new FxMessage.Burst(e.fx(), e.classId(), e.shape(), x, y, z, radius,
                e.angle(), e.axisX(), e.axisZ(), e.source());
        style(moved, kind, primary, accent, grow, hold, fade, false, motes, size, motif);
    }

    // ------------------------------------------------------------------ объекты

    /** Модель предмета в точке: меч, стрела, щит, череп. Не добавлена — сцена донастроит. */
    static FxSolids.Model item(Item item, double x, double y, double z, float scale, int grow,
                               int hold, int leave) {
        return new FxSolids.Model(new ItemStack(item), x, y, z, scale, grow, hold, leave);
    }

    /** Модель блока в точке, низом на ней. */
    static FxSolids.Model block(BlockState state, double x, double y, double z, float scale,
                                int grow, int hold, int leave) {
        return new FxSolids.Model(state, x, y, z, scale, grow, hold, leave);
    }

    /** Ком земли: вылетает из-под удара и падает. */
    static void clod(double x, double y, double z, float scale) {
        debris(Blocks.ROOTED_DIRT.defaultBlockState(), x, y, z, scale, 0.06f);
    }

    /** Обломок блока: камень от трещины, щепа, осколок льда. */
    static void debris(BlockState state, double x, double y, double z, float scale, float spread) {
        FxSolids.Model chip = new FxSolids.Model(state, x, y, z, scale, 1, 10, 6);
        chip.vx = FxMotes.jitter(spread);
        chip.vy = 0.12 + FxMotes.random() * 0.1;
        chip.vz = FxMotes.jitter(spread);
        chip.gravity = 0.035;
        chip.tumble(FxMotes.jitter(20f));
        FxSolids.add(chip);
    }

    /** Искры разлетом из точки. */
    static void sparks(double x, double y, double z, int base, float speed, float size, int argb,
                       int life, FxDraw.Tex tex) {
        int n = (int) (base * FxEffects.emit());
        FxMotes motes = FxEffects.motes();
        for (int i = 0; i < n; i++) {
            motes.spawn(x, y, z, FxMotes.jitter(speed), FxMotes.jitter(speed) + speed * 0.3f,
                    FxMotes.jitter(speed), size, argb, life + (int) (FxMotes.random() * life * 0.5f),
                    0.88f, tex);
        }
    }

    /** Искры поднимаются кольцом у ног: бафф, лечение. */
    static void rise(double x, double y, double z, double radius, int base, int argb,
                     FxDraw.Tex tex) {
        int n = (int) (base * FxEffects.emit());
        FxMotes motes = FxEffects.motes();
        for (int i = 0; i < n; i++) {
            double a = FxMotes.random() * Math.PI * 2;
            double r = radius * (0.4 + FxMotes.random() * 0.6);
            motes.spawn(x + Math.cos(a) * r, y + 0.1 + FxMotes.random() * 0.3, z + Math.sin(a) * r,
                    0, 0.05f + FxMotes.random() * 0.05f, 0, 0.2f, argb,
                    16 + (int) (FxMotes.random() * 10), 0.95f, tex);
        }
    }

    static void sound(String event, double x, double y, double z, float volume, float pitch) {
        FxSounds.play(event, x, y, z, volume, pitch);
    }

    /** Стиль из каталога по {@code fx} этого события: сцена кладёт его и добавляет своё. */
    static void styled(FxMessage.Burst e, FxStyle.Kind fallback) {
        FxEffects.addEffect(new FxKinds.Burst(FxStyle.of(e.fx(), fallback), e));
    }

    // ------------------------------------------------------------------ клинок

    /** Направление от источника события к точке, радианы по XZ (0 — по +X). */
    static double yawFrom(FxMessage.Burst e) {
        Entity source = entity(e.source());
        if (source == null) {
            return RANDOM.nextDouble() * Math.PI * 2;
        }
        double dx = e.x() - source.getX();
        double dz = e.z() - source.getZ();
        if (dx * dx + dz * dz < 1e-4) {
            double look = Math.toRadians(source.getYRot());
            return Math.atan2(Math.cos(look), -Math.sin(look));
        }
        return Math.atan2(dz, dx);
    }

    /** Куда смотрит существо, радианы по XZ (0 — по +X). */
    static double facing(Entity entity) {
        double look = Math.toRadians(entity.getYRot());
        return Math.atan2(Math.cos(look), -Math.sin(look));
    }

    /**
     * Косой разрез в воздухе поперёк цели: росчерк пробегает от одного конца к
     * другому и гаснет.
     *
     * @param yaw    откуда смотрит удар (от бьющего к цели), радианы
     * @param tilt   наклон росчерка: 0 — горизонтально, π/2 — сверху вниз
     */
    static void cut(String classId, double x, double y, double z, double yaw, double tilt,
                    double length, int colour, int life, float width) {
        double sx = -Math.sin(yaw);
        double sz = Math.cos(yaw);
        double fx = Math.cos(yaw);
        double fz = Math.sin(yaw);
        double dx = sx * Math.cos(tilt);
        double dy = Math.sin(tilt);
        double dz = sz * Math.cos(tilt);
        int n = 7;
        live(classId, x, y, z, length + 1, life, (self, draw, t, detail) -> {
            float k = self.progress(t);
            double head = Math.min(1, k * 3);
            double tail = Math.max(0, k * 1.6 - 0.3);
            if (head <= tail) {
                return;
            }
            double[] xs = new double[n];
            double[] ys = new double[n];
            double[] zs = new double[n];
            for (int i = 0; i < n; i++) {
                double q = tail + (head - tail) * i / (n - 1);
                double along = (q - 0.5) * length;
                // Чуть выгнут к бьющему: росчерк, а не палка.
                double bulge = -Math.sin(q * Math.PI) * 0.15 * length;
                xs[i] = x + dx * along + fx * bulge;
                ys[i] = y + dy * along;
                zs[i] = z + dz * along + fz * bulge;
            }
            float alpha = 1f - k * k;
            draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, n, width, colour, 0.2f * alpha, alpha, 0);
            draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, n, width * 0.35f, 0xFFFFFFFF, 0.1f * alpha,
                    0.8f * alpha, 0);
        });
    }

    /**
     * Дуга взмаха вокруг бьющего: голова дуги пробегает от края к краю, хвост
     * гаснет. Рассекающий удар, круговой, веер.
     *
     * @param yaw    середина дуги, радианы
     * @param spread полный угол дуги, радианы
     */
    static void arc(String classId, double x, double y, double z, double yaw, double spread,
                    double radius, int colour, int life, float width) {
        int n = Math.max(6, (int) (spread * radius * 3));
        live(classId, x, y, z, radius + 1, life, (self, draw, t, detail) -> {
            float k = self.progress(t);
            double head = Math.min(1, k * 2.2);
            double tail = Math.max(0, k * 1.5 - 0.4);
            if (head <= tail) {
                return;
            }
            double[] xs = new double[n];
            double[] ys = new double[n];
            double[] zs = new double[n];
            for (int i = 0; i < n; i++) {
                double q = tail + (head - tail) * i / (n - 1);
                double a = yaw - spread / 2 + spread * q;
                xs[i] = x + Math.cos(a) * radius;
                ys[i] = y + Math.sin(q * Math.PI) * 0.15;
                zs[i] = z + Math.sin(a) * radius;
            }
            float alpha = 1f - k * k;
            draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, n, width, colour, 0.15f * alpha, alpha, 0);
            draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, n, width * 0.3f, 0xFFFFFFFF, 0.1f * alpha,
                    0.7f * alpha, 0);
        });
    }

    // ------------------------------------------------------------------ живое

    /**
     * Короткий эффект с рисованием лямбдой: сфера печатей, струя дымки, разрыв.
     *
     * <p>Чтобы сцена навыка не заводила класс на каждую мелочь: срок, рамка
     * для отсечения и кадр. Цвета класса — в {@link #primary} и {@link #accent}.
     */
    static final class Live extends FxKinds.Effect {
        interface Frame {
            void draw(Live self, FxDraw draw, float t, FxKinds.Detail detail);
        }

        interface Step {
            void tick(Live self, Level level, FxMotes motes, float emit);
        }

        final int life;
        final double x;
        final double y;
        final double z;
        final double reach;
        private final Frame frame;
        Step step;
        /** Держаться, пока верно, сколько бы ни прошло; срок — потом на угасание. */
        java.util.function.BooleanSupplier holding;
        private int releasedAt = -1;
        boolean important;

        Live(String classId, double x, double y, double z, double reach, int life, Frame frame) {
            super(new FxStyle(FxStyle.Kind.FLASH, 0, 0, 0, 0, life, false, 1f, 0), classId);
            this.x = x;
            this.y = y;
            this.z = z;
            this.reach = reach;
            this.life = Math.max(1, life);
            this.frame = frame;
        }

        int primary() {
            return primary;
        }

        int accent() {
            return accent;
        }

        /** Доля пройденного срока 0..1 (у держащегося — с отпуска). */
        float progress(float t) {
            if (holding != null) {
                return releasedAt < 0 ? 0f : Math.clamp((t - releasedAt) / life, 0f, 1f);
            }
            return Math.clamp(t / life, 0f, 1f);
        }

        @Override
        boolean decor() {
            return !important;
        }

        @Override
        void tick(Level level, FxMotes motes, float emit) {
            super.tick(level, motes, emit);
            if (holding != null) {
                if (releasedAt < 0 && !holding.getAsBoolean()) {
                    releasedAt = age;
                }
                if (releasedAt >= 0 && age - releasedAt >= life) {
                    dead = true;
                    return;
                }
            } else if (age >= life) {
                dead = true;
                return;
            }
            if (step != null) {
                step.tick(this, level, motes, emit);
            }
        }

        @Override
        AABB bounds() {
            return new AABB(x - reach, y - reach, z - reach, x + reach, y + reach, z + reach);
        }

        @Override
        void draw(FxDraw draw, float partial, FxKinds.Detail detail) {
            frame.draw(this, draw, age + partial, detail);
        }
    }

    /** Добавить живой эффект и вернуть его — сцена донастроит шаг или удержание. */
    static Live live(String classId, double x, double y, double z, double reach, int life,
                     Live.Frame frame) {
        Live live = new Live(classId, x, y, z, reach, life, frame);
        FxEffects.addEffect(live);
        return live;
    }

    // ------------------------------------------------------------------ предупреждение

    /**
     * Чем круг-предупреждение отличается у навыка: трещина, листья, руны.
     * Тик — для того, что рождается по ходу (комья, искры), кадр — для того,
     * что рисуется поверх общего круга.
     */
    interface MarkDecor {
        default void tick(Mark mark, Level level, float emit) {
        }

        default void draw(Mark mark, FxDraw draw, float t, float k, float fade,
                          FxKinds.Detail detail) {
        }

        /** Просто круг, который заполняется к удару. */
        MarkDecor PLAIN = new MarkDecor() {
        };

        /** Светящаяся трещина бежит от центра, комья выбиваются там, куда добежала. */
        MarkDecor CRACK = new MarkDecor() {
            @Override
            public void tick(Mark m, Level level, float emit) {
                if (m.age >= m.e.ticks() || emit <= 0) {
                    return;
                }
                double k = FxGeometry.easeOut((double) m.age / m.e.ticks());
                double a = FxMotes.random() * Math.PI * 2;
                double d = m.e.radius() * k;
                double x = m.e.x() + Math.cos(a) * d;
                double z = m.e.z() + Math.sin(a) * d;
                if (FxMotes.random() < 0.5f) {
                    clod(x, FxGround.top(level, x, z, m.e.y()) + 0.1, z, 0.12f);
                }
            }

            @Override
            public void draw(Mark m, FxDraw draw, float t, float k, float fade,
                             FxKinds.Detail detail) {
                draw.circle(FxDraw.Tex.BEAM, m.e.x(), m.e.y(), m.e.z(),
                        Math.max(0.2, m.e.radius() * FxGeometry.easeOut(k)), 0.4, m.accent(),
                        0.7f * fade, 0.3f, t * 0.05f, 0.07f);
            }
        };

        /** Пояс рун или листьев внутри границы. */
        MarkDecor RUNES = new MarkDecor() {
            @Override
            public void draw(Mark m, FxDraw draw, float t, float k, float fade,
                             FxKinds.Detail detail) {
                if (!detail.full()) {
                    return;
                }
                double r = m.e.radius();
                double band = Math.min(0.7, r * 0.25);
                draw.circle(FxDraw.Tex.RUNES, m.e.x(), m.e.y(), m.e.z(), r - band, band,
                        m.accent(), 0.5f * fade, (float) (1 / (band * 8)), t * 0.02f, 0.04f);
            }
        };
    }

    /** Круг, который заполняется к удару: из него нужно успеть выйти. */
    static void mark(FxMessage.Telegraph e, MarkDecor decor) {
        FxEffects.addEffect(new Mark(e, decor));
    }

    /**
     * Круг-предупреждение.
     *
     * <p>Граница стоит на радиусе с первого тика — она и есть «отсюда уйти».
     * Заливка нарастает к удару, внутреннее кольцо догоняет границу: когда
     * сомкнулись — бьёт.
     */
    static final class Mark extends FxKinds.Effect {
        final FxMessage.Telegraph e;
        private final MarkDecor decor;
        private final int life;

        Mark(FxMessage.Telegraph e, MarkDecor decor) {
            super(new FxStyle(FxStyle.Kind.TELEGRAPH, 0, 0, 0, e.ticks(), 4, false, 1f, 0),
                    FxStyle.owner(e.classId(), e.fx()));
            this.e = e;
            this.decor = decor;
            this.life = Math.max(1, e.ticks()) + 4;
        }

        int accent() {
            return accent;
        }

        int primary() {
            return primary;
        }

        int age() {
            return age;
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
            decor.tick(this, level, emit);
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
            draw.circle(FxDraw.Tex.RING, e.x(), e.y(), e.z(), Math.max(0.2, r * k), 0.2, accent,
                    0.8f * fade, 0.5f, 0, 0.06f);
            decor.draw(this, draw, t, k, fade, detail);
        }
    }
}
