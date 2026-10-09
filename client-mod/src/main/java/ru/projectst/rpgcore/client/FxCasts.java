package ru.projectst.rpgcore.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ru.projectst.rpgcore.net.FxMessage;
import ru.projectst.rpgcore.net.MenuData;

/**
 * Подготовка каста: замах вокруг кастера и полоса у своего прицела.
 *
 * <p>Подготовку видят все рядом — её и нужно видеть, чтобы успеть сорвать или
 * уйти из-под удара. Под ногами кастера круг в облике класса заполняется дугой
 * к моменту удара, искры мотива стягиваются к рукам. Сработал — вспышка;
 * сорвали — круг раскалывается, и это видно отдельно от «сработал».
 *
 * <p>Сколько длится подготовка, решает сервер; мод только отсчитывает.
 */
final class FxCasts {

    private record Cast(String skillId, String classId, int total, long startedAt) {
    }

    private static final Map<Integer, Cast> CASTS = new HashMap<>();
    private static long now;

    private FxCasts() {
    }

    static void start(FxMessage.CastStart start) {
        CASTS.put(start.entityId(), new Cast(start.skillId(), start.classId(),
                Math.max(1, start.totalTicks()), now));
        Entity entity = entity(start.entityId());
        if (entity != null) {
            FxSounds.play(castSound(start.classId()), entity.getX(), entity.getY() + 1,
                    entity.getZ(), 0.7f, 1f);
        }
    }

    static void end(FxMessage.CastEnd end) {
        Cast cast = CASTS.remove(end.entityId());
        Entity entity = entity(end.entityId());
        if (cast == null || entity == null) {
            return;
        }
        FxStyle.Look look = FxStyle.look(cast.classId());
        int primary = 0xFF000000 | look.primary();
        int accent = 0xFF000000 | look.accent();
        FxMotes motes = FxEffects.motes();
        float emit = FxEffects.emit();
        if (end.completed()) {
            FxSounds.play("cast.release", entity.getX(), entity.getY() + 1, entity.getZ(), 0.6f, 1f);
            int n = Math.min(24, (int) (16 * emit) + 4);
            for (int i = 0; i < n; i++) {
                motes.spawn(entity.getX(), entity.getY() + 1.1, entity.getZ(), FxMotes.jitter(0.2f),
                        FxMotes.jitter(0.12f) + 0.05f, FxMotes.jitter(0.2f), 0.2f, accent, 10, 0.85f,
                        look.motif());
            }
        } else {
            // Сорван: круг раскалывается наружу тусклыми осколками, и звук свой —
            // «не сработал» должно читаться, а не теряться в общем шуме боя.
            FxSounds.play("cast.break", entity.getX(), entity.getY() + 1, entity.getZ(), 0.8f, 1f);
            for (int i = 0; i < 18; i++) {
                double a = Math.PI * 2 * i / 18;
                motes.spawn(entity.getX() + Math.cos(a) * 0.9, entity.getY() + 0.1,
                        entity.getZ() + Math.sin(a) * 0.9, (float) Math.cos(a) * 0.12f, 0.06f,
                        (float) Math.sin(a) * 0.12f, 0.2f, FxDraw.mix(primary, 0xFF555555, 0.6f),
                        12, 0.88f, FxDraw.Tex.SHARD);
            }
        }
    }

    static void clear() {
        CASTS.clear();
    }

    static void tick() {
        now++;
        FxMotes motes = FxEffects.motes();
        float emit = FxEffects.emit();
        CASTS.entrySet().removeIf(entry -> {
            Cast cast = entry.getValue();
            // Конец с сервера потерялся: подготовка не висит дольше своего срока.
            return now - cast.startedAt() > cast.total() + 40;
        });
        for (Map.Entry<Integer, Cast> entry : CASTS.entrySet()) {
            Entity entity = entity(entry.getKey());
            if (entity == null || emit <= 0) {
                continue;
            }
            Cast cast = entry.getValue();
            float k = Math.clamp((float) (now - cast.startedAt()) / cast.total(), 0f, 1f);
            gather(cast.classId(), entity, k, motes, emit);
        }
    }

    /** Звук начала подготовки: у каждого класса свой замах. */
    static String castSound(String classId) {
        return switch (classId == null ? "" : classId) {
            case "druid", "mage", "warlock", "knight", "rogue", "assassin", "trickster", "hunter",
                 "berserker", "striker", "warrior" -> classId + ".cast";
            default -> "cast.charge";
        };
    }

    /**
     * Искры подготовки: у каждого класса свой жест.
     *
     * <p>Маг, колдун, друид и рыцарь собирают силу кругом к рукам; плут и
     * убийца — тенью, без круга под ногами (их замах тихий); ловкач тасует
     * звёзды вокруг себя; охотник натягивает тетиву — наконечники
     * стягиваются вперёд по прицелу; берсерк и воин раскаляются углями от
     * земли; ударник копит грозу — искры молний пляшут по телу.
     */
    private static void gather(String classId, Entity entity, float k, FxMotes motes, float emit) {
        FxStyle.Look look = FxStyle.look(classId);
        int accent = 0xFF000000 | look.accent();
        int primary = 0xFF000000 | look.primary();
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        double h = entity.getBbHeight();
        double hands = y + h * 0.6;
        switch (classId == null ? "" : classId) {
            case "rogue", "assassin" -> {
                // Тень стекается к рукам по спирали, снизу вверх.
                if (FxMotes.random() < 0.7f * emit) {
                    double a = FxMotes.random() * Math.PI * 2;
                    double r = 0.7;
                    motes.spawn(x + Math.cos(a) * r, y + 0.1, z + Math.sin(a) * r,
                            (float) (-Math.cos(a) * r / 12 - Math.sin(a) * 0.04),
                            (float) (h * 0.6 / 12),
                            (float) (-Math.sin(a) * r / 12 + Math.cos(a) * 0.04), 0.26f,
                            FxDraw.mix(primary, 0xFF101018, 0.5f), 12, 1f, FxDraw.Tex.WISP);
                }
            }
            case "trickster" -> {
                // Звёзды кружат вокруг пояса — колода тасуется.
                double a = (entity.tickCount * 0.5) % (Math.PI * 2);
                for (int i = 0; i < 2; i++) {
                    double b = a + Math.PI * i;
                    motes.spawn(x + Math.cos(b) * 0.8, hands - 0.2, z + Math.sin(b) * 0.8,
                            (float) -Math.sin(b) * 0.1f, 0.01f, (float) Math.cos(b) * 0.1f, 0.18f,
                            i == 0 ? accent : primary, 6, 0.9f, FxDraw.Tex.STAR);
                }
            }
            case "hunter" -> {
                // Натяжение: наконечники стягиваются к луку вдоль взгляда.
                Vec3 look3 = entity.getLookAngle();
                double d = 1.6 - 1.2 * k;
                motes.spawn(x + look3.x * d + FxMotes.jitter(0.25f), hands + look3.y * d
                                + FxMotes.jitter(0.15f), z + look3.z * d + FxMotes.jitter(0.25f),
                        (float) -look3.x * 0.08f, (float) -look3.y * 0.08f,
                        (float) -look3.z * 0.08f, 0.16f, accent, 6, 0.9f, FxDraw.Tex.CHEVRON);
            }
            case "berserker", "warrior" -> {
                // Угли поднимаются от земли, к концу гуще — тело раскаляется.
                int n = FxMotes.random() < 0.4f + 0.6f * k ? 2 : 1;
                for (int i = 0; i < n; i++) {
                    double a = FxMotes.random() * Math.PI * 2;
                    double r = 0.3 + FxMotes.random() * 0.6;
                    motes.spawn(x + Math.cos(a) * r, y + 0.05, z + Math.sin(a) * r,
                            FxMotes.jitter(0.01f), 0.05f + FxMotes.random() * 0.05f,
                            FxMotes.jitter(0.01f), 0.16f, i == 0 ? primary : accent, 14, 0.96f,
                            FxDraw.Tex.EMBER);
                }
            }
            case "striker" -> {
                // Гроза по телу: молнии вспыхивают в случайных точках у рук и плеч.
                if (FxMotes.random() < 0.5f + 0.5f * k) {
                    motes.spawn(x + FxMotes.jitter(0.5f), y + h * (0.3 + FxMotes.random() * 0.6),
                            z + FxMotes.jitter(0.5f), 0, 0, 0, 0.22f + 0.1f * k, accent, 3, 1f,
                            FxDraw.Tex.ZAP);
                }
            }
            default -> {
                // Маг, колдун, друид, рыцарь: искры мотива стягиваются к рукам.
                double a = FxMotes.random() * Math.PI * 2;
                double r = 1.2 + FxMotes.random() * 0.4;
                double sx = x + Math.cos(a) * r;
                double sz = z + Math.sin(a) * r;
                double sy = y + 0.3 + FxMotes.random();
                motes.spawn(sx, sy, sz, (float) (x - sx) / 10, (float) (hands - sy) / 10,
                        (float) (z - sz) / 10, 0.18f, accent, 10, 1f, look.motif());
            }
        }
    }

    /** Кадр: круг под ногами, дуга заполнения, свечение у рук. */
    static void draw(FxDraw draw, float partial) {
        for (Map.Entry<Integer, Cast> entry : CASTS.entrySet()) {
            Entity entity = draw.level.getEntity(entry.getKey());
            if (entity == null) {
                continue;
            }
            Cast cast = entry.getValue();
            FxStyle.Look look = FxStyle.look(cast.classId());
            int primary = 0xFF000000 | look.primary();
            int accent = 0xFF000000 | look.accent();
            float t = now - cast.startedAt() + partial;
            float k = Math.clamp(t / cast.total(), 0f, 1f);
            Vec3 at = entity.getPosition(partial);
            windup(draw, cast.classId(), entity, at, k, t, primary, accent);
        }
    }

    /**
     * Знак подготовки под ногами и у рук: по нему видно, кто что готовит и
     * сколько осталось — дуга заполнения есть у всех, остальное — жест класса.
     */
    private static void windup(FxDraw draw, String classId, Entity entity, Vec3 at, float k, float t,
                               int primary, int accent) {
        double h = entity.getBbHeight();
        double from = -Math.PI / 2;
        String id = classId == null ? "" : classId;
        switch (id) {
            case "rogue", "assassin" -> {
                // Тихий замах: тусклое пятно тени и тонкая дуга, без рун.
                draw.sector(FxDraw.Tex.FILL, at.x, at.y, at.z, 0.8, 0, Math.PI * 2,
                        FxDraw.mix(primary, 0xFF000000, 0.5f), 0.25f + 0.2f * k, 0.03f);
                draw.ring(FxDraw.Tex.RING, at.x, at.y, at.z, 0.8, 0.1, from,
                        from + Math.PI * 2 * k, accent, 0.7f, 0.5f, 0, 0.06f);
                draw.sprite(FxDraw.Tex.WISP, at.x, at.y + h * 0.6, at.z, 0.5f + 0.4f * k, t * 0.05f,
                        primary, 0.5f * k);
            }
            case "trickster" -> {
                draw.ring(FxDraw.Tex.RING, at.x, at.y, at.z, 0.9, 0.14, from,
                        from + Math.PI * 2 * k, accent, 0.9f, 0.5f, 0, 0.06f);
                for (int i = 0; i < 3; i++) {
                    double a = t * 0.2 + Math.PI * 2 * i / 3;
                    draw.sprite(FxDraw.Tex.STAR, at.x + Math.cos(a) * 0.8, at.y + h * 0.55,
                            at.z + Math.sin(a) * 0.8, 0.35f, t * 0.1f, i == 1 ? accent : primary,
                            0.6f + 0.4f * k);
                }
            }
            case "hunter" -> {
                // Прицел: дуга под ногами и луч по взгляду, крепнет к выстрелу.
                draw.ring(FxDraw.Tex.RING, at.x, at.y, at.z, 0.7, 0.12, from,
                        from + Math.PI * 2 * k, accent, 0.9f, 0.5f, 0, 0.06f);
                Vec3 look = entity.getLookAngle();
                double y0 = at.y + h * 0.75;
                double reach = 1.5 + 4 * k;
                draw.ribbon(FxDraw.Tex.BEAM, new double[] {at.x + look.x * 0.6, at.x + look.x * reach},
                        new double[] {y0 + look.y * 0.6, y0 + look.y * reach},
                        new double[] {at.z + look.z * 0.6, at.z + look.z * reach}, 2, 0.06f,
                        accent, 0.5f * k, 0.05f, 0);
            }
            case "berserker", "warrior" -> {
                // Земля раскаляется кругом, к удару ярче; дуга — сколько осталось.
                draw.sector(FxDraw.Tex.FILL, at.x, at.y, at.z, 1.0, 0, Math.PI * 2, primary,
                        0.15f + 0.25f * k, 0.03f);
                draw.circle(FxDraw.Tex.BEAM, at.x, at.y, at.z, 0.4 + 0.6 * k, 0.3, accent,
                        0.5f * k, 0.3f, t * 0.05f, 0.05f);
                draw.ring(FxDraw.Tex.RING, at.x, at.y, at.z, 1.0, 0.16, from,
                        from + Math.PI * 2 * k, accent, 0.95f, 0.5f, 0, 0.06f);
                draw.sprite(FxDraw.Tex.GLOW, at.x, at.y + h * 0.6, at.z, 0.6f + 0.6f * k, 0,
                        primary, 0.35f + 0.3f * k);
            }
            case "striker" -> {
                // Вихрь ветра: кольцо крутится всё быстрее, разряд у кулаков.
                draw.circle(FxDraw.Tex.BEAM, at.x, at.y, at.z, 0.9, 0.25, accent, 0.4f + 0.3f * k,
                        0.3f, t * (0.05f + 0.25f * k), 0.05f);
                draw.ring(FxDraw.Tex.RING, at.x, at.y, at.z, 0.9, 0.14, from,
                        from + Math.PI * 2 * k, primary, 0.95f, 0.5f, 0, 0.06f);
                draw.sprite(FxDraw.Tex.ZAP, at.x, at.y + h * 0.6, at.z, 0.4f + 0.5f * k, t * 0.6f,
                        accent, 0.5f + 0.5f * k);
            }
            case "knight" -> {
                // Свет: руны кругом и столб сияния, крепнущий к удару.
                double r = 1.1;
                draw.sector(FxDraw.Tex.FILL, at.x, at.y, at.z, r, 0, Math.PI * 2, primary,
                        0.12f + 0.15f * k, 0.03f);
                draw.circle(FxDraw.Tex.RUNES, at.x, at.y, at.z, r - 0.25, 0.4, primary, 0.55f,
                        (float) (1 / (0.4 * 8)), t * 0.015f, 0.04f);
                draw.ring(FxDraw.Tex.RING, at.x, at.y, at.z, r, 0.16, from, from + Math.PI * 2 * k,
                        accent, 0.95f, 0.5f, 0, 0.06f);
                draw.ribbon(FxDraw.Tex.BEAM, new double[] {at.x, at.x},
                        new double[] {at.y, at.y + h + 0.6}, new double[] {at.z, at.z}, 2,
                        0.5f + 0.3f * k, primary, 0.25f * k, 0.25f * k, 0);
            }
            default -> {
                // Маг, колдун, друид: круг рун, заполнение дугой, свечение у рук.
                double r = 1.1;
                draw.sector(FxDraw.Tex.FILL, at.x, at.y, at.z, r, 0, Math.PI * 2, primary,
                        0.12f + 0.15f * k, 0.03f);
                draw.circle(FxDraw.Tex.RUNES, at.x, at.y, at.z, r - 0.25, 0.4, primary, 0.55f,
                        (float) (1 / (0.4 * 8)), t * 0.02f, 0.04f);
                draw.ring(FxDraw.Tex.RING, at.x, at.y, at.z, r, 0.16, from, from + Math.PI * 2 * k,
                        accent, 0.95f, 0.5f, 0, 0.06f);
            }
        }
        float glow = 0.4f + 0.6f * k;
        if (!id.equals("rogue") && !id.equals("assassin")) {
            draw.sprite(FxDraw.Tex.GLOW, at.x, at.y + h * 0.6, at.z, 0.6f + 0.8f * k, 0, primary,
                    0.5f * glow);
            draw.sprite(FxDraw.Tex.SPARK, at.x, at.y + h * 0.6, at.z, 0.4f + 0.5f * k, t * 0.2f,
                    accent, 0.8f * glow);
        }
    }

    /** Полоса подготовки у прицела — только своя. */
    static void drawBar(GuiGraphics g, int w, int h, float partial) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        Cast cast = CASTS.get(client.player.getId());
        if (cast == null) {
            return;
        }
        float k = Math.clamp((now - cast.startedAt() + partial) / cast.total(), 0f, 1f);
        int width = 120;
        int x = (w - width) / 2;
        int y = h / 2 + 22;
        FxStyle.Look look = FxStyle.look(cast.classId());
        g.fill(x - 1, y - 1, x + width + 1, y + 5, 0xC0000000);
        g.fill(x, y, x + Math.round(width * k), y + 4, 0xFF000000 | look.primary());
        g.fill(x, y, x + Math.round(width * k), y + 1, 0xFF000000 | look.accent());
        String name = displayOf(cast.skillId());
        g.drawCenteredString(client.font, Component.literal(name), w / 2, y + 7, 0xFFE8D9A0);
    }

    /** Имя навыка из данных меню; нет данных — идентификатор. */
    private static String displayOf(String skillId) {
        return ClientNetwork.menu().flatMap(menu -> menu.skills().stream()
                        .filter(skill -> skill.id().equals(skillId))
                        .map(MenuData.SkillLine::display).findFirst())
                .map(name -> name.replaceAll("[&§][0-9a-fk-or]", ""))
                .orElse(skillId);
    }

    private static Entity entity(int id) {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(id);
    }
}
