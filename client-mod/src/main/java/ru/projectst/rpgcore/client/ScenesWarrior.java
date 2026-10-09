package ru.projectst.rpgcore.client;

import static ru.projectst.rpgcore.client.SceneKit.entity;
import static ru.projectst.rpgcore.client.SceneKit.live;

import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Воин: рассекающий удар, натиск, таран щитом, поднять щит, круговой удар,
 * связка, вызов на бой, ярость, стойка, боевой клич.
 *
 * <p>Сценарий — раздел 13 {@code docs/vfx/skill-visuals.md}. Облик — сталь и
 * огонь, оранжевые угли. Дуги — огонь по конусу ровно по выборке; щит — щит,
 * знамёна клича — оранжевые знамёна.
 */
final class ScenesWarrior {

    static final int FIRE = 0xFFFF7A3A;
    static final int FLARE = 0xFFFFE08A;

    private ScenesWarrior() {
    }

    static FxScenes.Set scenes() {
        return new FxScenes.Set(BURSTS, Map.of(), Map.of(), Map.of(), Map.of(), STATUSES);
    }

    private static final Map<String, FxScenes.BurstScene> BURSTS = Map.ofEntries(
            Map.entry("warrior_swing", ScenesWarrior::swing),
            Map.entry("warrior_cleave_arc", e -> fireArc(e, 1f)),
            Map.entry("warrior_hit", ScenesWarrior::hit),
            Map.entry("warrior_charge_dust", ScenesWarrior::chargeDust),
            Map.entry("warrior_charge_impact", ScenesWarrior::chargeImpact),
            Map.entry("warrior_shield_rush", ScenesWarrior::shieldRush),
            Map.entry("warrior_shield_impact", ScenesWarrior::shieldImpact),
            Map.entry("warrior_shield_raise", ScenesWarrior::swing),
            Map.entry("warrior_spin_arc", ScenesWarrior::spinArc),
            Map.entry("warrior_combo_arc", e -> fireArc(e, 1f)),
            Map.entry("warrior_combo_arc_full", e -> fireArc(e, 2f)),
            Map.entry("warrior_challenge_mark", ScenesWarrior::challengeMark),
            Map.entry("warrior_rage", ScenesWarrior::rage),
            Map.entry("warrior_stance_raise", ScenesWarrior::swing),
            Map.entry("warrior_stance_notch", ScenesWarrior::stanceNotch),
            Map.entry("warrior_stance_blast", ScenesWarrior::stanceBlast),
            Map.entry("warrior_cry_banner", ScenesWarrior::cryBanner),
            Map.entry("warrior_cry_wave", ScenesWarrior::cryWave));

    private static final Map<String, FxStatuses.Look> STATUSES = Map.ofEntries(
            Map.entry("shield_up", new RaisedShield("shield_up")),
            Map.entry("warrior_stance", new RaisedShield("warrior_stance")),
            Map.entry("combo", new Combo()),
            Map.entry("rage", new Rage()));

    // ------------------------------------------------------------------ дуги

    /** Замах: угли у руки. */
    private static void swing(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1.1, e.z(), 8, 0.08f, 0.16f, FLARE, 8, FxDraw.Tex.EMBER);
    }

    /**
     * Огненная дуга ровно по конусу выборки: сектор рисует стиль, поверх —
     * взмах огня у кромки.
     *
     * @param power 1 — обычная, 2 — вдвое шире и ярче (третий удар связки)
     */
    private static void fireArc(FxMessage.Burst e, float power) {
        SceneKit.cone(e, 3, 4, 6, power);
        double axis = Math.atan2(e.axisZ(), e.axisX());
        double spread = Math.toRadians(e.angle());
        SceneKit.arc(e.classId(), e.x(), e.y() + 1.0, e.z(), axis, spread,
                Math.max(1.2, e.radius() * 0.75), power > 1 ? FLARE : FIRE, 8, 0.6f * power);
        SceneKit.arc(e.classId(), e.x(), e.y() + 0.9, e.z(), axis, spread * 0.9,
                Math.max(1.0, e.radius() * 0.5), FIRE, 10, 0.4f * power);
        if (power > 1) {
            FxScreen.shakeIfInside(e.x(), e.y(), e.z(), e.radius(), 0.4f, 6);
        }
    }

    /** Попадание: угли и косой росчерк. */
    private static void hit(FxMessage.Burst e) {
        SceneKit.cut(e.classId(), e.x(), e.y() + 1.1, e.z(), SceneKit.yawFrom(e),
                SceneKit.RANDOM.nextDouble() - 0.5, 1.4, FIRE, 7, 0.3f);
        SceneKit.sparks(e.x(), e.y() + 1.1, e.z(), 8, 0.15f, 0.16f, FLARE, 8, FxDraw.Tex.EMBER);
    }

    /** Круговой удар: огненная дуга на полный круг до границы. */
    private static void spinArc(FxMessage.Burst e) {
        SceneKit.border(e, 3, 4, 6, 1f);
        Entity warrior = entity(e.source());
        double x = warrior != null ? warrior.getX() : e.x();
        double y = warrior != null ? warrior.getY() + 1.0 : e.y() + 1;
        double z = warrior != null ? warrior.getZ() : e.z();
        SceneKit.arc(e.classId(), x, y, z, SceneKit.RANDOM.nextDouble() * Math.PI * 2, Math.PI * 2,
                Math.max(1.2, e.radius() - 0.3), FIRE, 9, 0.7f);
        SceneKit.sparks(x, y, z, 20, 0.3f, 0.16f, FLARE, 10, FxDraw.Tex.EMBER);
    }

    // ------------------------------------------------------------------ натиск и таран

    /** Натиск: пыль из-под ног всю дорогу разбега. */
    private static void chargeDust(FxMessage.Burst e) {
        dustTrail(e.source(), 8);
    }

    private static void dustTrail(int id, int ticks) {
        Entity who = entity(id);
        if (who == null) {
            return;
        }
        live("warrior", who.getX(), who.getY(), who.getZ(), 12, ticks, (self, draw, t, detail) -> {
        }).step = (self, level, motes, emit) -> {
            Entity now = level.getEntity(id);
            if (now != null && emit > 0) {
                for (int i = 0; i < 2; i++) {
                    motes.spawn(now.getX() + FxMotes.jitter(0.3f), now.getY() + 0.1,
                            now.getZ() + FxMotes.jitter(0.3f), FxMotes.jitter(0.03f), 0.03f,
                            FxMotes.jitter(0.03f), 0.35f, 0xFFD8C8A8, 18, 0.94f, FxDraw.Tex.WISP);
                }
            }
        };
    }

    /** Столкновение: ударная вспышка, цель отлетает. */
    private static void chargeImpact(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y() + 1, e.z(), 1.2f, FxStyle.Kind.FLASH, FIRE, FLARE, 2, 2, 7,
                1.4f, 1.6f, FxDraw.Tex.EMBER);
        FxScreen.shakeIfInside(e.x(), e.y(), e.z(), 2, 0.45f, 6);
    }

    /** Таран: разбег с выставленным щитом. */
    private static void shieldRush(FxMessage.Burst e) {
        int id = e.source();
        Entity warrior = entity(id);
        if (warrior == null) {
            return;
        }
        FxSolids.Model shield = SceneKit.item(Items.SHIELD, warrior.getX(), warrior.getY(),
                warrior.getZ(), 1.1f, 2, 6, 4);
        shield.follow = id;
        shield.offsetY = warrior.getBbHeight() * 0.55;
        shield.forward = 0.8;
        shield.at(180, 0, 0);
        FxSolids.add(shield);
        dustTrail(id, 6);
    }

    /** Таран попал: щит вспыхивает, гулкий удар. */
    private static void shieldImpact(FxMessage.Burst e) {
        live(e.classId(), e.x(), e.y() + 1.1, e.z(), 3, 10, (self, draw, t, detail) -> {
            float k = self.progress(t);
            draw.sprite(FxDraw.Tex.GLOW, e.x(), e.y() + 1.1, e.z(), 1.2f + 1.5f * k, 0, FLARE, 1f - k);
        });
        SceneKit.sparks(e.x(), e.y() + 1.1, e.z(), 18, 0.25f, 0.18f, FLARE, 8, FxDraw.Tex.SPARK);
        FxScreen.shakeIfInside(e.x(), e.y(), e.z(), 2, 0.5f, 6);
    }

    /**
     * Поднятый щит и стойка: перед воином большой щит; у стойки на нём горят
     * засечки — по одной на принятый удар.
     */
    static final class RaisedShield implements FxStatuses.Look {
        private final String status;

        RaisedShield(String status) {
            this.status = status;
        }

        @Override
        public void appear(FxStatuses.State state, Entity entity) {
            if (!state.solids.isEmpty()) {
                return;
            }
            int id = state.entity;
            FxSolids.Model shield = SceneKit.item(Items.SHIELD, entity.getX(), entity.getY(),
                    entity.getZ(), 1.4f, 3, 0, 4);
            shield.follow = id;
            shield.offsetY = entity.getBbHeight() * 0.5;
            shield.forward = 0.75;
            shield.at(180, 0, 0);
            shield.holding = () -> FxStatuses.has(id, status);
            shield.decor = false;
            state.solids.add(shield);
            FxSolids.add(shield);
        }

        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            if (!status.equals("warrior_stance")) {
                return;
            }
            FxStatuses.State taken = FxStatuses.get(state.entity, "stance_taken");
            int n = taken == null ? 0 : Math.min(10, taken.stacks);
            double look = Math.toRadians(entity.getYRot());
            double fx = -Math.sin(look);
            double fz = Math.cos(look);
            for (int i = 0; i < n; i++) {
                double side = (i % 5 - 2) * 0.12;
                double y = at.y + entity.getBbHeight() * 0.5 + 0.2 - (i / 5) * 0.25;
                draw.sprite(FxDraw.Tex.EMBER, at.x + fx * 0.85 - fz * side, y,
                        at.z + fz * 0.85 + fx * side, 0.16f, 0, FLARE, 0.9f);
            }
        }
    }

    private static void stanceNotch(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1.1, e.z(), 6, 0.12f, 0.16f, FLARE, 6, FxDraw.Tex.SPARK);
    }

    /** Стойка кончилась: взрыв огня по радиусу, тряска у задетых. */
    private static void stanceBlast(FxMessage.Burst e) {
        SceneKit.style(e, FxStyle.Kind.WAVE, FIRE, FLARE, 4, 4, 10, false, 2.2f, 0, FxDraw.Tex.EMBER);
        SceneKit.sparks(e.x(), e.y() + 1, e.z(), 40, 0.4f, 0.24f, FIRE, 14, FxDraw.Tex.EMBER);
        FxScreen.shakeIfInside(e.x(), e.y(), e.z(), e.radius(), 0.8f, 10);
    }

    // ------------------------------------------------------------------ связка

    /** Связка: над воином три засечки, загораются по попаданиям. */
    static final class Combo implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            double y = at.y + entity.getBbHeight() + 0.45;
            double look = Math.toRadians(entity.getYRot());
            double sx = Math.cos(look);
            double sz = Math.sin(look);
            for (int i = 0; i < 3; i++) {
                boolean lit = i < state.stacks;
                double d = (i - 1) * 0.25;
                draw.sprite(lit ? FxDraw.Tex.EMBER : FxDraw.Tex.GLOW, at.x + sx * d, y, at.z + sz * d,
                        lit ? 0.3f : 0.18f, 0, lit ? FLARE : 0xFF606060, lit ? 1f : 0.5f);
            }
        }
    }

    // ------------------------------------------------------------------ вызов, ярость, клич

    private static void challengeMark(FxMessage.Burst e) {
        SceneKit.sparks(e.x(), e.y() + 1.6, e.z(), 10, 0.1f, 0.18f, FIRE, 10, FxDraw.Tex.EMBER);
    }

    private static void rage(FxMessage.Burst e) {
        SceneKit.styleAt(e, e.x(), e.y(), e.z(), 1.4f, FxStyle.Kind.WAVE, FIRE, FLARE, 4, 4, 8, 1.6f, 0,
                FxDraw.Tex.EMBER);
        ScenesAssassin.drops(e.x(), e.y() + 1.0, e.z(), 3);
    }

    /** Ярость: огненная аура, редкая струйка крови с руки. */
    static final class Rage implements FxStatuses.Look {
        @Override
        public void draw(FxStatuses.State state, FxDraw draw, Entity entity, Vec3 at, float time) {
            draw.sprite(FxDraw.Tex.GLOW, at.x, at.y + entity.getBbHeight() * 0.5, at.z, 2.0f, 0, FIRE,
                    0.25f + 0.08f * (float) Math.sin(time * 0.3));
        }

        @Override
        public void ambient(FxStatuses.State state, Entity entity, float emit) {
            FxMotes motes = FxEffects.motes();
            if (FxMotes.random() < 0.6f * emit) {
                double a = FxMotes.random() * Math.PI * 2;
                motes.spawn(entity.getX() + Math.cos(a) * 0.4, entity.getY() + FxMotes.random() * 0.6,
                        entity.getZ() + Math.sin(a) * 0.4, 0, 0.08f, 0, 0.2f,
                        FxMotes.random() < 0.5f ? FIRE : FLARE, 12, 0.95f, FxDraw.Tex.EMBER);
            }
            if (FxMotes.random() < 0.03f) {
                ScenesAssassin.drops(entity.getX(), entity.getY() + entity.getBbHeight() * 0.45,
                        entity.getZ(), 2);
            }
        }
    }

    /** Боевой клич: над союзником вспыхивает оранжевое знамя. */
    private static void cryBanner(FxMessage.Burst e) {
        FxSolids.Model banner = SceneKit.item(Items.ORANGE_BANNER, e.x(), e.y() + 3.0, e.z(), 1.2f, 4,
                24, 8);
        banner.sway = 8;
        FxSolids.add(banner);
        SceneKit.sparks(e.x(), e.y() + 2.4, e.z(), 8, 0.08f, 0.16f, FLARE, 10, FxDraw.Tex.EMBER);
    }

    /** Клич: волна от воина до границы. */
    private static void cryWave(FxMessage.Burst e) {
        SceneKit.style(e, FxStyle.Kind.WAVE, FIRE, FLARE, 5, 6, 9, false, 1.6f, 0, FxDraw.Tex.EMBER);
        FxScreen.shakeIfInside(e.x(), e.y(), e.z(), e.radius(), 0.3f, 6);
    }
}
