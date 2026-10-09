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
            FxSounds.play(start.classId().equals("druid") ? "druid.cast" : "cast.charge",
                    entity.getX(), entity.getY() + 1, entity.getZ(), 0.7f, 1f);
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
            FxStyle.Look look = FxStyle.look(entry.getValue().classId());
            // Искры мотива стягиваются к рукам: сила собирается.
            double a = FxMotes.random() * Math.PI * 2;
            double r = 1.2 + FxMotes.random() * 0.4;
            double sx = entity.getX() + Math.cos(a) * r;
            double sz = entity.getZ() + Math.sin(a) * r;
            double sy = entity.getY() + 0.3 + FxMotes.random();
            double tx = entity.getX();
            double ty = entity.getY() + 1.2;
            double tz = entity.getZ();
            motes.spawn(sx, sy, sz, (float) (tx - sx) / 10, (float) (ty - sy) / 10,
                    (float) (tz - sz) / 10, 0.18f, 0xFF000000 | look.accent(), 10, 1f,
                    look.motif());
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
            double r = 1.1;
            draw.sector(FxDraw.Tex.FILL, at.x, at.y, at.z, r, 0, Math.PI * 2, primary,
                    0.12f + 0.15f * k, 0.03f);
            draw.circle(FxDraw.Tex.RUNES, at.x, at.y, at.z, r - 0.25, 0.4, primary, 0.55f,
                    (float) (1 / (0.4 * 8)), t * 0.02f, 0.04f);
            double from = -Math.PI / 2;
            draw.ring(FxDraw.Tex.RING, at.x, at.y, at.z, r, 0.16, from, from + Math.PI * 2 * k,
                    accent, 0.95f, 0.5f, 0, 0.06f);
            float glow = 0.4f + 0.6f * k;
            draw.sprite(FxDraw.Tex.GLOW, at.x, at.y + entity.getBbHeight() * 0.6, at.z,
                    0.6f + 0.8f * k, 0, primary, 0.5f * glow);
            draw.sprite(FxDraw.Tex.SPARK, at.x, at.y + entity.getBbHeight() * 0.6, at.z,
                    0.4f + 0.5f * k, t * 0.2f, accent, 0.8f * glow);
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
