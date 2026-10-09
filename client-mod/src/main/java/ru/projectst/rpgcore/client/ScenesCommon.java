package ru.projectst.rpgcore.client;

import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Общие состояния: щит, ослабление — те, что вешают навыки разных классов.
 *
 * <p>Облик берётся по классу наложившего ({@link SceneKit#classOf}): щит
 * колдуна — кружащие души, щит рыцаря — золотая сфера.
 */
final class ScenesCommon {

    private ScenesCommon() {
    }

    static FxScenes.Set scenes() {
        return new FxScenes.Set(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), STATUSES);
    }

    private static final Map<String, FxStatuses.Look> STATUSES = Map.ofEntries(
            Map.entry("shield", new Shield()),
            Map.entry("weakened", new Weakened()));

    /**
     * Щит: тонкая сфера вокруг — три кольца в разных плоскостях медленно
     * вращаются. Щит колдуна — ещё и души по сфере.
     */
    static final class Shield implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            String owner = SceneKit.classOf(state.source);
            boolean souls = owner.equals("warlock");
            int colour = souls ? ScenesWarlock.SOUL : SceneKit.primary(owner);
            double r = Math.max(0.7, entity.getBbHeight() * 0.55);
            double cy = at.y + entity.getBbHeight() * 0.5;
            for (int i = 0; i < 3; i++) {
                double a = time * 0.02 + Math.PI * i / 3;
                draw.halo(FxDraw.Tex.RING, at.x, cy, at.z, r, 0.06, Math.cos(a), 0.6,
                        Math.sin(a), colour, 0.35f, 0.5f, 0);
            }
            draw.sprite(FxDraw.Tex.GLOW, at.x, cy, at.z, (float) r * 2.4f, 0, colour, 0.12f);
            if (souls) {
                for (int i = 0; i < 4; i++) {
                    double a = time * 0.1 + Math.PI * 2 * i / 4;
                    double b = Math.sin(time * 0.07 + i) * 0.6;
                    double x = at.x + Math.cos(a) * r * Math.cos(b);
                    double y = cy + Math.sin(b) * r;
                    double z = at.z + Math.sin(a) * r * Math.cos(b);
                    draw.sprite(FxDraw.Tex.WISP, x, y, z, 0.4f, time * 0.2f, colour, 0.7f);
                }
            }
        }
    }

    /** Ослабление: серые нити стекают с рук. */
    static final class Weakened implements FxStatuses.Look {
        @Override
        public void ambient(FxStatuses.State state, Entity entity, float emit) {
            if (FxMotes.random() > 0.3f * emit) {
                return;
            }
            double yaw = Math.toRadians(entity.getYRot());
            double side = FxMotes.random() < 0.5f ? 0.38 : -0.38;
            double x = entity.getX() + Math.cos(yaw) * side;
            double z = entity.getZ() + Math.sin(yaw) * side;
            FxEffects.motes().spawn(x, entity.getY() + entity.getBbHeight() * 0.45, z, 0, -0.03f,
                    0, 0.16f, 0xFF8A8A92, 16, 0.98f, FxDraw.Tex.WISP);
        }
    }
}
