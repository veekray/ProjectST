package ru.projectst.rpgcore.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Живые эффекты по видам.
 *
 * <p>Граница области — радиус из события, и рисуется она всегда одинаково:
 * тонкая яркая черта посередине полосы {@link FxDraw.Tex#RING} ровно на
 * радиусе плюс мягкий ореол. Всё прочее — волна, заливка, руны, искры —
 * украшение, по которому радиус не определяют. Поэтому волна дорастает до
 * радиуса и останавливается, а не уходит дальше: остановившаяся граница и есть
 * то, что игрок должен прочитать.
 *
 * <p>Время считается в тиках клиента с долей кадра: эффект плавный при любом
 * числе кадров и останавливается вместе с игрой на паузе.
 */
final class FxKinds {

    /** Насколько граница приподнята над землёй: чтобы не мерцала в ней. */
    private static final float LIFT = 0.045f;

    /** Подробность кадра: что рисовать и сколько искр рождать. */
    record Detail(boolean full, float emit) {
    }

    private FxKinds() {
    }

    /** Общее у всех эффектов: стиль, цвета, возраст. */
    abstract static class Effect {
        final FxStyle style;
        final int primary;
        final int accent;
        /** Форма искр: мотив класса или своя у стиля. */
        final FxDraw.Tex motif;
        int age;
        boolean dead;

        Effect(FxStyle style, String classId) {
            this.style = style;
            this.primary = style.primaryFor(classId);
            this.accent = style.accentFor(classId);
            this.motif = style.motifFor(classId);
        }

        /** Искра в мотиве эффекта: у берсерка — уголь, у друида — лист. */
        void mote(FxMotes motes, double x, double y, double z, float vx, float vy, float vz,
                  float size, int argb, int ticks, float slow) {
            motes.spawn(x, y, z, vx, vy, vz, size, argb, ticks, slow, motif);
        }

        /** Можно ли выбросить при перегрузке: украшения — да, зоны и снаряды — нет. */
        boolean decor() {
            return true;
        }

        void tick(Level level, FxMotes motes, float emit) {
            age++;
        }

        abstract AABB bounds();

        abstract void draw(FxDraw draw, float partial, Detail detail);
    }

    private static float ease(float t) {
        float k = Math.clamp(t, 0f, 1f);
        return 1f - (1f - k) * (1f - k) * (1f - k);
    }

    private static int emitCount(float base, float emit) {
        float n = base * emit;
        int whole = (int) n;
        return whole + (FxMotes.random() < n - whole ? 1 : 0);
    }

    /** Граница: ореол и чёткая черта ровно на радиусе. */
    private static void border(FxDraw draw, double x, double y, double z, double radius,
                               double from, double to, int glow, int core, float alpha) {
        draw.ring(FxDraw.Tex.RING, x, y, z, radius, Math.min(1.0, 0.35 + radius * 0.08),
                from, to, glow, 0.38f * alpha, 0.5f, 0, LIFT);
        draw.ring(FxDraw.Tex.RING, x, y, z, radius, 0.24, from, to, core, alpha, 0.5f, 0,
                LIFT + 0.01f);
    }

    // ------------------------------------------------------------------ зона

    /**
     * Зона на земле: печать, ловушка, поле.
     *
     * <p>Дуга внутри границы показывает остаток срока: полная при постановке,
     * пустая к концу, последние три секунды пульсирует. Срок — из события: мод
     * лишь отсчитывает присланный остаток, а снимает зону всё равно сервер.
     */
    static final class Zone extends Effect {
        final double x;
        final double y;
        final double z;
        final double radius;
        final int total;
        final boolean own;
        private final long endsAt;
        private final long startedAt;

        private FxMessage.ZoneEnd ending;
        private int endAge;
        private double toX;
        private double toY;
        private double toZ;

        Zone(FxStyle style, FxMessage.ZoneOn on, long now) {
            super(style, FxStyle.owner(on.classId(), on.fx()));
            this.x = on.x();
            this.y = on.y();
            this.z = on.z();
            this.radius = on.radius();
            this.total = Math.max(1, on.totalTicks());
            this.own = on.own();
            this.endsAt = now + on.remainingTicks();
            this.startedAt = now;
        }

        @Override
        boolean decor() {
            return false;
        }

        /** Кончается ли зона: сцена поверх неё уходит вместе с ней. */
        boolean ending() {
            return ending != null || dead;
        }

        /** Зону сняли: истекла, съедена или игрок ушёл далеко. */
        void end(FxMessage.ZoneOff off) {
            if (ending != null) {
                return;
            }
            ending = off.reason();
            endAge = age;
            toX = off.toX();
            toY = off.toY();
            toZ = off.toZ();
        }

        @Override
        void tick(Level level, FxMotes motes, float emit) {
            super.tick(level, motes, emit);
            long now = startedAt + age;
            if (ending == null && now > endsAt + 20) {
                // Снятие с сервера потерялось вместе с пакетом: срок вышел, и
                // висеть дальше зона не должна.
                ending = FxMessage.ZoneEnd.EXPIRED;
                endAge = age;
            }
            if (ending != null && age - endAge > Math.max(4, style.fade())) {
                dead = true;
                return;
            }
            if (ending == null && emit > 0) {
                int n = emitCount(style.motes() * (own ? 0.35f : 0.12f) * (float) Math.max(1, radius / 2.5), emit);
                for (int i = 0; i < n; i++) {
                    double a = FxMotes.random() * Math.PI * 2;
                    double r = Math.sqrt(FxMotes.random()) * radius;
                    double mx = x + Math.cos(a) * r;
                    double mz = z + Math.sin(a) * r;
                    mote(motes, mx, FxGround.top(level, mx, mz, y) + 0.1, mz,
                            FxMotes.jitter(0.004f), 0.03f + FxMotes.random() * 0.02f,
                            FxMotes.jitter(0.004f), 0.18f, accent, 26 + (int) (FxMotes.random() * 14),
                            0.99f);
                }
            }
        }

        @Override
        AABB bounds() {
            return new AABB(x - radius, y - 4, z - radius, x + radius, y + 3, z + radius);
        }

        @Override
        void draw(FxDraw draw, float partial, Detail detail) {
            float t = age + partial;
            float alpha = Math.min(1f, t / 6f) * (own ? 1f : 0.55f);
            double cx = x;
            double cy = y;
            double cz = z;
            double r = radius;
            if (ending != null) {
                float k = Math.clamp((t - endAge) / Math.max(4, style.fade()), 0f, 1f);
                if (ending == FxMessage.ZoneEnd.CONSUMED) {
                    // Съеденная или стянутая печать уходит туда, куда её забрали.
                    float e = ease(k);
                    cx = x + (toX - x) * e;
                    cy = y + (toY - y) * e;
                    cz = z + (toZ - z) * e;
                    r = radius * (1 - e);
                    draw.sprite(FxDraw.Tex.GLOW, cx, cy + 0.6, cz, (float) (0.6 + radius * 0.4),
                            0, accent, (1 - k) * 0.8f);
                }
                alpha *= 1 - k;
            }
            if (alpha <= 0.004f) {
                return;
            }
            // Заливка: по ней видно, что это область, но не её край.
            draw.sector(FxDraw.Tex.FILL, cx, cy, cz, r, 0, Math.PI * 2, primary, 0.22f * alpha,
                    LIFT - 0.01f);
            if (style.runes() && detail.full() && r > 1.2) {
                double band = Math.min(0.75, r * 0.28);
                draw.circle(FxDraw.Tex.RUNES, cx, cy, cz, r - band * 0.9, band, primary,
                        0.55f * alpha, (float) (1 / (band * 8)), t * 0.004f, LIFT);
            }
            // Черта границы ближе к основному цвету, дуга срока — к блику:
            // иначе на краю печати их было бы не различить.
            border(draw, cx, cy, cz, r, 0, Math.PI * 2, primary, FxDraw.mix(primary, accent, 0.35f),
                    alpha);
            // Дуга срока внутри границы, от севера по часовой.
            if (ending == null && r > 0.8) {
                long now = startedAt + age;
                float left = Math.clamp((endsAt - now - partial) / total, 0f, 1f);
                float pulse = endsAt - now < 60 ? 0.55f + 0.45f * (float) Math.cos(t * 0.6) : 1f;
                double from = -Math.PI / 2;
                double to = from + Math.PI * 2 * left;
                draw.ring(FxDraw.Tex.RING, cx, cy, cz, r - 0.24, 0.14, from, to, accent,
                        0.9f * alpha * pulse, 0.5f, 0, LIFT + 0.02f);
                double hx = cx + Math.cos(to) * (r - 0.24);
                double hz = cz + Math.sin(to) * (r - 0.24);
                draw.sprite(FxDraw.Tex.GLOW, hx, FxGround.top(draw.level, hx, hz, cy) + 0.15, hz,
                        0.45f, 0, accent, 0.8f * alpha * pulse);
            }
            // Капля при постановке: короткая рябь наружу, сама граница стоит.
            if (t < 10 && ending == null) {
                float k = t / 10f;
                draw.circle(FxDraw.Tex.RING, cx, cy, cz, r * (1 + 0.3 * k), 0.3, accent,
                        (1 - k) * 0.5f * alpha, 0.5f, 0, LIFT);
            }
        }
    }

    // ------------------------------------------------------------------ вспышки

    /** Вспышка по событию: волна, конус, стягивание, круг удара или точка. */
    static final class Burst extends Effect {
        final FxMessage.Burst event;
        final FxStyle.Kind kind;
        final int life;
        final double heading;
        final double half;
        /** Высота вспышки: у точки на земле поднята к груди, см. {@link #settle}. */
        private double y;
        /** Земля под центром: от неё растут столбы подъёма и оседания. */
        private double ground;

        Burst(FxStyle style, FxMessage.Burst event) {
            super(style, FxStyle.owner(event.classId(), event.fx()));
            this.event = event;
            // Форма решает геометрию: конус без оси не нарисовать кольцом и
            // наоборот, какой бы вид ни стоял в каталоге.
            if (event.shape() == FxMessage.Shape.CONE) {
                this.kind = FxStyle.Kind.CONE;
            } else if (style.kind() == FxStyle.Kind.CONE) {
                this.kind = FxStyle.Kind.WAVE;
            } else {
                this.kind = style.kind();
            }
            this.life = Math.max(2, style.grow() + style.hold() + style.fade());
            this.heading = Math.atan2(event.axisZ(), event.axisX());
            this.half = Math.toRadians(event.angle()) / 2;
            this.y = event.y();
            this.ground = event.y();
        }

        /**
         * Привязка к земле в первый тик: тогда уже есть мир.
         *
         * <p>Сервер шлёт точку цели — это ноги. Вспышка у ног прячется в траве и
         * читается как грязь на земле, поэтому точка у самой земли поднимается к
         * груди. Точка в воздухе — попадание снаряда, конец луча — остаётся где
         * была.
         */
        private void settle(Level level) {
            ground = FxGround.top(level, event.x(), event.z(), event.y());
            if (kind == FxStyle.Kind.FLASH && event.y() - ground < 0.35) {
                y = ground + 0.9;
            }
        }

        /** Радиус подъёма и оседания: не меньше, чем человек в полный рост. */
        private double around() {
            return Math.clamp(event.radius(), 0.7, 8.0);
        }

        @Override
        AABB bounds() {
            double r = Math.max(1, event.radius()) + 1;
            return new AABB(event.x() - r, event.y() - 4, event.z() - r,
                    event.x() + r, event.y() + 4, event.z() + r);
        }

        /** Прозрачность границы: держится, потом гаснет. */
        private float holdAlpha(float t) {
            float fadeFrom = style.grow() + style.hold();
            if (t <= fadeFrom) {
                return 1f;
            }
            return 1f - Math.clamp((t - fadeFrom) / Math.max(1, style.fade()), 0f, 1f);
        }

        @Override
        void tick(Level level, FxMotes motes, float emit) {
            super.tick(level, motes, emit);
            if (age >= life) {
                dead = true;
                return;
            }
            if (age == 1) {
                settle(level);
            }
            double x = event.x();
            double y = this.y;
            double z = event.z();
            double r = event.radius();
            switch (kind) {
                case RISE -> {
                    if (age < style.grow() + style.hold()) {
                        // Искры поднимаются от земли по всему кругу и чуть
                        // закручиваются: сила собирается вокруг цели.
                        double rr = around();
                        int n = emitCount((float) (0.5 + rr * 0.45) * style.motes(), emit);
                        for (int i = 0; i < n; i++) {
                            double a = FxMotes.random() * Math.PI * 2;
                            double d = Math.sqrt(FxMotes.random()) * rr;
                            double sx = x + Math.cos(a) * d;
                            double sz = z + Math.sin(a) * d;
                            float swirl = 0.02f;
                            mote(motes, sx, FxGround.top(level, sx, sz, ground) + 0.1, sz,
                                    (float) -Math.sin(a) * swirl, 0.05f + FxMotes.random() * 0.05f,
                                    (float) Math.cos(a) * swirl, 0.2f, FxMotes.random() < 0.3f
                                            ? primary : accent,
                                    18 + (int) (FxMotes.random() * 12), 0.97f);
                        }
                    }
                }
                case SINK -> {
                    if (age < style.grow() + style.hold()) {
                        // Искры падают на цель сверху: на неё что-то легло.
                        double rr = around() * 0.8;
                        int n = emitCount((float) (0.5 + rr * 0.45) * style.motes(), emit);
                        for (int i = 0; i < n; i++) {
                            double a = FxMotes.random() * Math.PI * 2;
                            double d = Math.sqrt(FxMotes.random()) * rr;
                            mote(motes, x + Math.cos(a) * d, ground + 2.3 + FxMotes.random() * 0.6,
                                    z + Math.sin(a) * d, FxMotes.jitter(0.008f),
                                    -0.06f - FxMotes.random() * 0.05f, FxMotes.jitter(0.008f),
                                    0.2f, FxMotes.random() < 0.5f ? primary : accent,
                                    20 + (int) (FxMotes.random() * 8), 0.99f);
                        }
                    }
                }
                case WAVE -> {
                    if (age == 1) {
                        int n = Math.min(70, emitCount((float) (r * 6) * style.motes(), emit));
                        float speed = (float) (r / Math.max(2, style.grow()) * 0.55);
                        for (int i = 0; i < n; i++) {
                            double a = FxMotes.random() * Math.PI * 2;
                            double sx = x + Math.cos(a) * r * 0.2;
                            double sz = z + Math.sin(a) * r * 0.2;
                            mote(motes, sx, FxGround.top(level, sx, sz, y) + 0.25, sz,
                                    (float) Math.cos(a) * speed, 0.02f + FxMotes.random() * 0.03f,
                                    (float) Math.sin(a) * speed, 0.22f, accent,
                                    style.grow() + 6, 0.82f);
                        }
                    }
                }
                case CONE -> {
                    if (age > style.grow() && age < style.grow() + style.hold()) {
                        // Искры текут к вершине: туда, куда сгоняют.
                        int n = emitCount((float) (r * 0.6) * style.motes(), emit);
                        for (int i = 0; i < n; i++) {
                            double a = heading + (FxMotes.random() * 2 - 1) * half;
                            double d = r * (0.35 + 0.65 * FxMotes.random());
                            double sx = x + Math.cos(a) * d;
                            double sz = z + Math.sin(a) * d;
                            float speed = 0.45f;
                            mote(motes, sx, FxGround.top(level, sx, sz, y) + 0.2, sz,
                                    (float) -Math.cos(a) * speed, 0.005f,
                                    (float) -Math.sin(a) * speed, 0.2f, accent,
                                    (int) Math.min(22, d / speed), 1f);
                        }
                    }
                }
                case TELEGRAPH -> {
                    if (age < style.hold()) {
                        int n = emitCount((float) (r * 0.8) * style.motes(), emit);
                        for (int i = 0; i < n; i++) {
                            double a = FxMotes.random() * Math.PI * 2;
                            double sx = x + Math.cos(a) * r;
                            double sz = z + Math.sin(a) * r;
                            float speed = (float) (r / 14);
                            mote(motes, sx, FxGround.top(level, sx, sz, y) + 0.3, sz,
                                    (float) -Math.cos(a) * speed, 0.01f,
                                    (float) -Math.sin(a) * speed, 0.22f, accent, 14, 1f);
                        }
                    }
                }
                case IMPLODE -> {
                    if (age == style.grow()) {
                        int n = Math.min(40, emitCount(16 * style.motes(), emit));
                        for (int i = 0; i < n; i++) {
                            mote(motes, x, y + 0.2, z, FxMotes.jitter(0.06f),
                                    0.12f + FxMotes.random() * 0.12f, FxMotes.jitter(0.06f),
                                    0.25f, accent, 16, 0.9f);
                        }
                    }
                }
                default -> {
                    if (age == 1) {
                        int n = Math.min(30, emitCount(10 * style.motes(), emit));
                        for (int i = 0; i < n; i++) {
                            mote(motes, x, y, z, FxMotes.jitter(0.15f), FxMotes.jitter(0.15f),
                                    FxMotes.jitter(0.15f), 0.2f, accent, 10, 0.85f);
                        }
                    }
                }
            }
        }

        @Override
        void draw(FxDraw draw, float partial, Detail detail) {
            float t = age + partial;
            double x = event.x();
            double y = this.y;
            double z = event.z();
            double r = event.radius();
            float hold = holdAlpha(t);
            int core = FxDraw.mix(primary, accent, 0.6f);
            switch (kind) {
                case RISE -> {
                    double rr = around();
                    float g = style.grow() <= 0 ? 1f : Math.clamp(t / style.grow(), 0f, 1f);
                    float alpha = Math.clamp(t / 3f, 0f, 1f) * hold;
                    // Круг у ног разворачивается, над ним встаёт мягкий столб света.
                    draw.sector(FxDraw.Tex.FILL, x, ground, z, rr, 0, Math.PI * 2, primary,
                            0.2f * alpha, LIFT - 0.01f);
                    draw.circle(FxDraw.Tex.RING, x, ground, z, rr * (0.6 + 0.4 * ease(g)), 0.3,
                            core, 0.9f * alpha, 0.5f, 0, LIFT);
                    double top = ground + 0.2 + 2.3 * ease(g);
                    draw.ribbon(FxDraw.Tex.BEAM, new double[] {x, x},
                            new double[] {ground + 0.05, top}, new double[] {z, z}, 2,
                            (float) (rr * 1.3), primary, 0.4f * alpha, 0f, -t * 0.05f);
                    draw.ribbon(FxDraw.Tex.BEAM, new double[] {x, x},
                            new double[] {ground + 0.05, top * 0.8 + ground * 0.2},
                            new double[] {z, z}, 2, (float) (rr * 0.45), accent, 0.5f * alpha,
                            0f, -t * 0.08f);
                    if (style.runes() && detail.full() && rr > 1.2) {
                        double band = Math.min(0.7, rr * 0.3);
                        draw.circle(FxDraw.Tex.RUNES, x, ground, z, rr - band, band, primary,
                                0.5f * alpha, (float) (1 / (band * 8)), t * 0.01f, LIFT);
                    }
                }
                case SINK -> {
                    double rr = around();
                    float g = style.grow() <= 0 ? 1f : Math.clamp(t / style.grow(), 0f, 1f);
                    float alpha = Math.clamp(t / 3f, 0f, 1f) * hold;
                    // Кольцо сжимается на цель и ложится на землю; над головой —
                    // знак того, что на ней теперь висит.
                    draw.sector(FxDraw.Tex.FILL, x, ground, z, rr, 0, Math.PI * 2, primary,
                            0.22f * alpha, LIFT - 0.01f);
                    draw.circle(FxDraw.Tex.RING, x, ground, z, rr * (1.7 - 0.7 * ease(g)), 0.34,
                            primary, 0.85f * alpha, 0.5f, 0, LIFT);
                    draw.circle(FxDraw.Tex.RING, x, ground, z, rr * 0.55, 0.2, accent,
                            0.6f * alpha * g, 0.5f, 0, LIFT + 0.01f);
                    double mark = ground + 2.5 - 0.25 * ease(g);
                    draw.sprite(FxDraw.Tex.GLOW, x, mark, z, 1.1f, 0, primary, 0.6f * alpha);
                    draw.sprite(motif, x, mark, z, 0.7f, t * 0.03f, accent, 0.9f * alpha);
                }
                case WAVE -> {
                    float g = style.grow() <= 0 ? 1f : Math.clamp(t / style.grow(), 0f, 1f);
                    double wave = r * ease(g);
                    if (g < 1f) {
                        draw.circle(FxDraw.Tex.RING, x, y, z, wave, 0.5 + 0.6 * (1 - g), accent,
                                0.9f, 0.5f, 0, LIFT);
                        draw.sector(FxDraw.Tex.FILL, x, y, z, wave, 0, Math.PI * 2, primary,
                                0.3f * (1 - g * 0.5f), LIFT - 0.01f);
                    } else {
                        // Волна встала на радиус: дальше это просто граница.
                        border(draw, x, y, z, r, 0, Math.PI * 2, primary, core, hold);
                        draw.sector(FxDraw.Tex.FILL, x, y, z, r, 0, Math.PI * 2, primary,
                                0.15f * hold, LIFT - 0.01f);
                        if (style.runes() && detail.full() && r > 1.2) {
                            double band = Math.min(0.75, r * 0.25);
                            draw.circle(FxDraw.Tex.RUNES, x, y, z, r - band, band, primary,
                                    0.5f * hold, (float) (1 / (band * 8)), -t * 0.01f, LIFT);
                        }
                    }
                }
                case CONE -> {
                    float in = style.grow() <= 0 ? 1f : Math.clamp(t / style.grow(), 0f, 1f);
                    float alpha = in * hold;
                    double from = heading - half;
                    double to = heading + half;
                    draw.sector(FxDraw.Tex.FILL, x, y, z, r, from, to, primary, 0.2f * alpha,
                            LIFT - 0.01f);
                    border(draw, x, y, z, r, from, to, primary, core, alpha);
                    for (double edge : new double[] {from, to}) {
                        double ex = x + Math.cos(edge) * r;
                        double ez = z + Math.sin(edge) * r;
                        draw.groundLine(FxDraw.Tex.RING, x, z, ex, ez, y, 0.22, core,
                                0.85f * alpha, LIFT + 0.01f);
                    }
                    // Вершина: туда сгоняют, и её видно отдельно.
                    draw.sprite(FxDraw.Tex.GLOW, x, FxGround.top(draw.level, x, z, y) + 0.3, z,
                            1.1f, 0, accent, 0.7f * alpha);
                }
                case IMPLODE -> {
                    float g = style.grow() <= 0 ? 1f : Math.clamp(t / style.grow(), 0f, 1f);
                    if (g < 1f) {
                        double shrink = r * (1 - ease(g));
                        draw.circle(FxDraw.Tex.RING, x, y, z, shrink, 0.3, accent, 0.4f + 0.6f * g,
                                0.5f, 0, LIFT);
                    }
                    float flash = g < 1f ? 0f
                            : 1f - Math.clamp((t - style.grow()) / Math.max(1, style.fade()), 0f, 1f);
                    if (flash > 0) {
                        double[] xs = {x, x};
                        double[] ys = {y, y + 2.6};
                        double[] zs = {z, z};
                        draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, 2, style.size() * (0.6f + flash),
                                accent, flash, 0f, 0);
                        draw.sprite(FxDraw.Tex.GLOW, x, y + 0.5, z, style.size() * 2.2f, 0,
                                primary, flash * 0.8f);
                    }
                }
                case TELEGRAPH -> {
                    float in = Math.clamp(t / 4f, 0f, 1f);
                    float alpha = in * hold;
                    float charge = Math.clamp(t / Math.max(1, style.hold()), 0f, 1f);
                    float pulse = 0.75f + 0.25f * (float) Math.sin(t * (0.4 + charge));
                    draw.sector(FxDraw.Tex.FILL, x, y, z, r, 0, Math.PI * 2, primary,
                            (0.1f + 0.3f * charge) * alpha, LIFT - 0.01f);
                    border(draw, x, y, z, r, 0, Math.PI * 2, primary, core, alpha * pulse);
                    if (style.runes() && detail.full() && r > 1.2) {
                        double band = Math.min(0.8, r * 0.25);
                        draw.circle(FxDraw.Tex.RUNES, x, y, z, r - band, band, accent,
                                0.6f * alpha, (float) (1 / (band * 8)), t * (0.01f + 0.05f * charge),
                                LIFT);
                    }
                }
                default -> {
                    float g = Math.clamp(t / Math.max(1, style.grow()), 0f, 1f);
                    // Облако в шесть блоков вспышкой не рисуется: это была бы
                    // стена света во весь экран. Крупнее двух блоков — уже волна.
                    float size = (float) Math.min(2.4, Math.max(style.size(), r * 2))
                            * (0.5f + 0.5f * ease(g));
                    draw.sprite(FxDraw.Tex.GLOW, x, y, z, size * 1.3f, 0, primary, 0.7f * hold);
                    draw.sprite(FxDraw.Tex.GLOW, x, y, z, size * 0.5f, 0, accent, 0.9f * hold);
                    draw.sprite(FxDraw.Tex.SPARK, x, y, z, size * 0.8f, t * 0.08f, accent, hold);
                }
            }
        }
    }

    // ------------------------------------------------------------------ снаряд

    /**
     * Снаряд: светящееся ядро и шлейф.
     *
     * <p>Полёт ведётся тем же расчётом, что на сервере: отрезки не длиннее
     * полублока, снижение делится между ними. Где снаряд кончился на самом деле,
     * говорит сервер — по его точке ядро и вспыхивает, а не по догадке клиента.
     */
    static final class Bolt extends Effect {
        private static final int HISTORY = 9;
        private static final double MAX_SEGMENT = 0.5;

        final float speed;
        final float range;
        final float gravity;
        private double x;
        private double y;
        private double z;
        private double prevX;
        private double prevY;
        private double prevZ;
        private double dx;
        private double dy;
        private double dz;
        private double travelled;
        private final double[] hx = new double[HISTORY];
        private final double[] hy = new double[HISTORY];
        private final double[] hz = new double[HISTORY];
        private int recorded;
        private boolean ended;
        private int endAge;
        /**
         * Без светящегося ядра: полёт рисует модель сцены (стрела, череп), а
         * от снаряда остаётся только шлейф.
         */
        boolean bare;
        /** Что сделать, когда сервер скажет, где снаряд кончился: сцена удара. */
        java.util.function.Consumer<FxMessage.ProjectileEnd> onEnd;

        Bolt(FxStyle style, FxMessage.Projectile p) {
            super(style, FxStyle.owner(p.classId(), p.fx()));
            this.speed = Math.max(0.05f, p.speed());
            this.range = p.range();
            this.gravity = p.gravity();
            this.x = this.prevX = p.x();
            this.y = this.prevY = p.y();
            this.z = this.prevZ = p.z();
            this.dx = p.dx();
            this.dy = p.dy();
            this.dz = p.dz();
            record();
        }

        @Override
        boolean decor() {
            return false;
        }

        private void record() {
            for (int i = HISTORY - 1; i > 0; i--) {
                hx[i] = hx[i - 1];
                hy[i] = hy[i - 1];
                hz[i] = hz[i - 1];
            }
            hx[0] = x;
            hy[0] = y;
            hz[0] = z;
            recorded = Math.min(HISTORY, recorded + 1);
        }

        /** Где снаряд в этом кадре; {@code null} — кончился: модель на нём уходит. */
        Vec3 at(float partial) {
            if (ended || dead) {
                return null;
            }
            return new Vec3(prevX + (x - prevX) * partial, prevY + (y - prevY) * partial,
                    prevZ + (z - prevZ) * partial);
        }

        /** Куда летит сейчас, единичный вектор. */
        Vec3 heading() {
            return new Vec3(dx, dy, dz).normalize();
        }

        boolean ended() {
            return ended;
        }

        /** Сервер сказал, где снаряд кончился. */
        void end(FxMessage.ProjectileEnd end, FxMotes motes, float emit) {
            if (ended) {
                return;
            }
            ended = true;
            if (onEnd != null) {
                try {
                    onEnd.accept(end);
                } catch (RuntimeException e) {
                    // Сцена удара — украшение: снаряд всё равно кончается.
                }
            }
            endAge = age;
            prevX = x;
            prevY = y;
            prevZ = z;
            x = end.x();
            y = end.y();
            z = end.z();
            record();
            int n = Math.min(40, emitCount((end.hit() ? 18 : 10) * style.motes(), emit));
            for (int i = 0; i < n; i++) {
                mote(motes, x, y, z, FxMotes.jitter(0.18f), FxMotes.jitter(0.18f),
                        FxMotes.jitter(0.18f), 0.2f, accent, 10 + (int) (FxMotes.random() * 6),
                        0.84f);
            }
        }

        @Override
        void tick(Level level, FxMotes motes, float emit) {
            super.tick(level, motes, emit);
            if (ended) {
                if (age - endAge > style.fade()) {
                    dead = true;
                }
                return;
            }
            if (age > range / speed + 60) {
                // Конец с сервера так и не пришёл: снаряд не висит вечно.
                dead = true;
                return;
            }
            prevX = x;
            prevY = y;
            prevZ = z;
            double remaining = Math.min(speed, range - travelled);
            if (remaining > 0) {
                int segments = (int) Math.ceil(remaining / MAX_SEGMENT);
                double step = remaining / Math.max(1, segments);
                for (int i = 0; i < segments; i++) {
                    x += dx * step;
                    y += dy * step;
                    z += dz * step;
                    travelled += step;
                    if (gravity > 0) {
                        dy -= gravity / segments;
                    }
                }
            }
            record();
            int n = emitCount(1.4f * style.motes(), emit);
            for (int i = 0; i < n; i++) {
                mote(motes, x, y, z, FxMotes.jitter(0.03f), FxMotes.jitter(0.03f),
                        FxMotes.jitter(0.03f), style.size() * 0.4f, accent, 9, 0.9f);
            }
        }

        @Override
        AABB bounds() {
            return new AABB(x - 3, y - 3, z - 3, x + 3, y + 3, z + 3).expandTowards(
                    hx[recorded - 1] - x, hy[recorded - 1] - y, hz[recorded - 1] - z);
        }

        @Override
        void draw(FxDraw draw, float partial, Detail detail) {
            float t = age + partial;
            float alpha = ended ? 1f - Math.clamp((t - endAge) / Math.max(1, style.fade()), 0f, 1f) : 1f;
            if (alpha <= 0) {
                return;
            }
            double ix = prevX + (x - prevX) * partial;
            double iy = prevY + (y - prevY) * partial;
            double iz = prevZ + (z - prevZ) * partial;
            // Шлейф: от головы назад по прошлым точкам, гаснет к хвосту.
            int n = Math.min(recorded, detail.full() ? HISTORY : 5);
            double[] xs = new double[n];
            double[] ys = new double[n];
            double[] zs = new double[n];
            xs[0] = ix;
            ys[0] = iy;
            zs[0] = iz;
            for (int i = 1; i < n; i++) {
                xs[i] = hx[i];
                ys[i] = hy[i];
                zs[i] = hz[i];
            }
            draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, n, style.size() * 1.4f, primary,
                    0.85f * alpha, 0f, -t * 0.3f);
            draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, Math.min(n, 4), style.size() * 0.5f, accent,
                    alpha, 0f, -t * 0.3f);
            if (ended) {
                float k = Math.clamp((t - endAge) / Math.max(1, style.fade()), 0f, 1f);
                draw.sprite(FxDraw.Tex.GLOW, ix, iy, iz, style.size() * (2.5f + 3f * k), 0,
                        primary, alpha);
                draw.sprite(FxDraw.Tex.SPARK, ix, iy, iz, style.size() * (2f + 2f * k), t * 0.1f,
                        accent, alpha);
                return;
            }
            if (bare) {
                return;
            }
            draw.sprite(FxDraw.Tex.GLOW, ix, iy, iz, style.size() * 2.4f, 0, primary, 0.7f);
            draw.sprite(FxDraw.Tex.GLOW, ix, iy, iz, style.size() * 1.1f, 0, accent, 1f);
            draw.sprite(FxDraw.Tex.SPARK, ix, iy, iz, style.size() * 1.5f, t * 0.25f, accent, 0.9f);
        }
    }

    // ------------------------------------------------------------------ след

    /** След перемещения: лента от старта до прибытия на высоте груди. */
    static final class Trail extends Effect {
        final FxMessage.Trail event;
        final int life;

        Trail(FxStyle style, FxMessage.Trail event) {
            super(style, FxStyle.owner(event.classId(), event.fx()));
            this.event = event;
            this.life = Math.max(2, style.hold() + style.fade());
        }

        @Override
        void tick(Level level, FxMotes motes, float emit) {
            super.tick(level, motes, emit);
            if (age >= life) {
                dead = true;
                return;
            }
            if (age == 1) {
                double ddx = event.toX() - event.fromX();
                double ddy = event.toY() - event.fromY();
                double ddz = event.toZ() - event.fromZ();
                double length = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                int n = Math.min(60, emitCount((float) (length * 2.5) * style.motes(), emit));
                for (int i = 0; i < n; i++) {
                    double k = FxMotes.random();
                    mote(motes, event.fromX() + ddx * k, event.fromY() + ddy * k + 0.4 + FxMotes.random() * 1.2,
                            event.fromZ() + ddz * k, FxMotes.jitter(0.02f),
                            0.015f + FxMotes.random() * 0.02f, FxMotes.jitter(0.02f), 0.2f, accent,
                            14 + (int) (FxMotes.random() * 10), 0.96f);
                }
            }
        }

        @Override
        AABB bounds() {
            return new AABB(event.fromX(), event.fromY(), event.fromZ(),
                    event.toX(), event.toY() + 2, event.toZ()).inflate(2);
        }

        @Override
        void draw(FxDraw draw, float partial, Detail detail) {
            float t = age + partial;
            float alpha = t <= style.hold() ? 1f
                    : 1f - Math.clamp((t - style.hold()) / Math.max(1, style.fade()), 0f, 1f);
            int steps = 10;
            double[] xs = new double[steps + 1];
            double[] ys = new double[steps + 1];
            double[] zs = new double[steps + 1];
            for (int i = 0; i <= steps; i++) {
                double k = (double) i / steps;
                xs[i] = event.fromX() + (event.toX() - event.fromX()) * k;
                ys[i] = event.fromY() + (event.toY() - event.fromY()) * k + 1.0
                        + Math.sin(k * Math.PI) * 0.15;
                zs[i] = event.fromZ() + (event.toZ() - event.fromZ()) * k;
            }
            draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, steps + 1, style.size() * 2.2f, primary,
                    0.45f * alpha, 0.45f * alpha, t * 0.2f);
            draw.ribbon(FxDraw.Tex.BEAM, xs, ys, zs, steps + 1, style.size() * 0.6f, accent,
                    0.4f * alpha, alpha, t * 0.2f);
            draw.sprite(FxDraw.Tex.GLOW, event.fromX(), event.fromY() + 1, event.fromZ(),
                    1.8f, 0, primary, 0.6f * alpha);
            draw.sprite(FxDraw.Tex.GLOW, event.toX(), event.toY() + 1, event.toZ(),
                    1.4f, 0, accent, 0.8f * alpha);
        }
    }

    // ------------------------------------------------------------------ попадание

    /**
     * Попадание по цели; крит заметно ярче и крупнее.
     *
     * <p>Цель ищется по сетевому номеру каждый кадр: вспышка держится на цели,
     * даже если её отбросило ударом.
     */
    static final class Hit extends Effect {
        final int entityId;
        final boolean crit;
        final int life;
        private final float[] streaks;
        private double lastX;
        private double lastY;
        private double lastZ;
        private double footY;
        private boolean located;

        Hit(FxStyle style, FxMessage.Hit hit) {
            super(style, hit.classId());
            this.entityId = hit.entityId();
            this.crit = hit.crit();
            this.life = Math.max(2, style.fade());
            int n = crit ? 7 : 0;
            this.streaks = new float[n * 2];
            for (int i = 0; i < n; i++) {
                streaks[i * 2] = (float) (FxMotes.random() * Math.PI * 2);
                streaks[i * 2 + 1] = (float) (FxMotes.random() * Math.PI - Math.PI / 2);
            }
        }

        private boolean locate(Level level, float partial) {
            Entity target = level.getEntity(entityId);
            if (target != null) {
                Vec3 at = target.getPosition(partial);
                lastX = at.x;
                lastY = at.y + target.getBbHeight() * 0.6;
                lastZ = at.z;
                footY = at.y;
                located = true;
            }
            return located;
        }

        @Override
        void tick(Level level, FxMotes motes, float emit) {
            super.tick(level, motes, emit);
            if (age >= life) {
                dead = true;
                return;
            }
            if (age == 1 && locate(level, 1f)) {
                int n = Math.min(30, emitCount((crit ? 10 : 3) * style.motes(), emit));
                float spread = crit ? 0.22f : 0.12f;
                for (int i = 0; i < n; i++) {
                    mote(motes, lastX, lastY, lastZ, FxMotes.jitter(spread),
                            FxMotes.jitter(spread) + 0.05f, FxMotes.jitter(spread),
                            crit ? 0.28f : 0.18f, accent, 10 + (int) (FxMotes.random() * 8), 0.85f);
                }
            }
        }

        @Override
        AABB bounds() {
            return new AABB(lastX - 3, lastY - 3, lastZ - 3, lastX + 3, lastY + 3, lastZ + 3);
        }

        @Override
        void draw(FxDraw draw, float partial, Detail detail) {
            if (!locate(draw.level, partial)) {
                return;
            }
            float t = age + partial;
            float k = Math.clamp(t / life, 0f, 1f);
            float alpha = 1f - k * k;
            float size = style.size() * (0.7f + 0.5f * ease(Math.min(1f, t / 3f)));
            draw.sprite(FxDraw.Tex.GLOW, lastX, lastY, lastZ, size * 1.6f, 0, primary, 0.7f * alpha);
            draw.sprite(FxDraw.Tex.SPARK, lastX, lastY, lastZ, size, t * 0.15f, accent, alpha);
            if (!crit) {
                return;
            }
            // Крит: белое ядро, лучи и волна у ног — его видно сквозь толпу.
            draw.sprite(FxDraw.Tex.GLOW, lastX, lastY, lastZ, size * 0.8f, 0, 0xFFFFFFFF, alpha);
            draw.sprite(FxDraw.Tex.SPARK, lastX, lastY, lastZ, size * 1.7f, -t * 0.1f + 0.4f,
                    accent, 0.8f * alpha);
            float length = 0.4f + 1.3f * ease(Math.min(1f, t / 4f));
            for (int i = 0; i < streaks.length / 2; i++) {
                double yaw = streaks[i * 2];
                double pitch = streaks[i * 2 + 1] * 0.6;
                double ex = lastX + Math.cos(yaw) * Math.cos(pitch) * length;
                double ey = lastY + Math.sin(pitch) * length;
                double ez = lastZ + Math.sin(yaw) * Math.cos(pitch) * length;
                draw.ribbon(FxDraw.Tex.BEAM, new double[] {lastX, ex}, new double[] {lastY, ey},
                        new double[] {lastZ, ez}, 2, 0.14f, accent, alpha, 0f, 0);
            }
            double wave = 0.5 + 1.5 * ease(Math.min(1f, t / 6f));
            draw.circle(FxDraw.Tex.RING, lastX, footY, lastZ, wave, 0.3, accent, 0.7f * alpha,
                    0.5f, 0, LIFT);
        }
    }
}
