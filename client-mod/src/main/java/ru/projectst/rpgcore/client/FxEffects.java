package ru.projectst.rpgcore.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Живые эффекты навыков на клиенте.
 *
 * <p>Сервер присылает «что и где» готовыми числами — радиус выборки, срок зоны,
 * крит, — мод решает «как красиво» и больше ничего. Граница всегда ровно по
 * присланному радиусу; срок зоны мод лишь отсчитывает, а снимает зону сервер.
 *
 * <p><b>Производительность.</b> Настройка «Частицы» игрока соблюдается: «Всё» —
 * полностью, «Меньше» — меньше искр и реже сегменты, «Минимум» — без искр и
 * рун, только границы, дуги срока и ядро вспышки. Границы не убираются никогда:
 * по ним играют. Дальние эффекты рисуются без искр и рун; за пределом
 * видимости и вне кадра — не рисуются вовсе. Число эффектов и искр ограничено
 * жёстко, и при перегрузке первыми теряются украшения: двадцать магов с
 * печатями в одной точке — это двадцать колец и меньше искр, а не просадка.
 *
 * <p><b>Ни один эффект не роняет игру.</b> Ошибка в рисовании снимает этот
 * эффект с одной строкой в журнале, и только его.
 */
@EventBusSubscriber(modid = RpgCoreClient.MOD_ID, value = Dist.CLIENT)
public final class FxEffects {

    private static final Logger LOG = LoggerFactory.getLogger("rpgcore");

    /** Больше эффектов одновременно не держим: украшения сверх этого не берутся. */
    static final int MAX_EFFECTS = 320;
    /** Зоны — состояние, но и их предел есть: чужие сверх него не берутся. */
    static final int MAX_ZONES = 200;
    /** Искр одновременно. */
    static final int MAX_MOTES = 2500;

    /** Ближе — всё; дальше — без искр и рун. */
    private static final double NEAR = 24;
    /** Дальше не рисуется: сервер шлёт на сорок восемь, плюс запас на размер. */
    private static final double FAR = 72;

    private static final List<FxKinds.Effect> EFFECTS = new ArrayList<>();
    /**
     * Рождённые во время тика: сцена на тике может завести новый эффект (стрела
     * ливня — свою остановку), а в список, который сейчас перебирается,
     * добавлять нельзя — итератор упал бы и уронил игру целиком.
     */
    private static final List<FxKinds.Effect> PENDING = new ArrayList<>();
    private static boolean ticking;
    private static final Map<Integer, FxKinds.Zone> ZONES = new HashMap<>();
    private static final Map<Integer, FxKinds.Bolt> BOLTS = new HashMap<>();
    private static final FxMotes MOTES = new FxMotes(MAX_MOTES);
    private static final FxDraw DRAW = new FxDraw();

    /** Тики клиента с запуска: часы для сроков зон. */
    private static long now;
    private static boolean warned;

    private FxEffects() {
    }

    // ------------------------------------------------------------------ события

    /** События одного сообщения с сервера, в главном потоке клиента. */
    static void accept(List<FxMessage.Event> events) {
        for (FxMessage.Event event : events) {
            try {
                take(event);
            } catch (RuntimeException e) {
                warnOnce("эффект не принят", e);
            }
        }
    }

    private static void take(FxMessage.Event event) {
        noteClass(event);
        switch (event) {
            case FxMessage.Burst burst -> {
                // Своя сцена навыка — корни, лозы, щепа; нет — роль класса.
                if (!FxScenes.burst(burst)) {
                    add(new FxKinds.Burst(
                            FxStyle.of(burst.fx(), kindOf(burst.shape(), burst.radius())), burst));
                }
            }
            case FxMessage.ZoneOn on -> {
                FxKinds.Zone old = ZONES.remove(on.id());
                if (old != null) {
                    old.dead = true;
                }
                if (!on.own() && ZONES.size() >= MAX_ZONES) {
                    return;
                }
                FxKinds.Zone zone = new FxKinds.Zone(FxStyle.of(on.fx(), FxStyle.Kind.ZONE), on, now);
                ZONES.put(on.id(), zone);
                EFFECTS.add(zone);
                FxScenes.zone(on, zone);
            }
            case FxMessage.ZoneOff off -> {
                FxKinds.Zone zone = ZONES.remove(off.id());
                if (zone != null) {
                    zone.end(off);
                }
            }
            case FxMessage.Projectile p -> {
                FxKinds.Bolt bolt = new FxKinds.Bolt(FxStyle.of(p.fx(), FxStyle.Kind.BOLT), p);
                BOLTS.put(p.id(), bolt);
                EFFECTS.add(bolt);
                // Своя сцена вешает на полёт модель (стрелу, череп) и решает конец.
                FxScenes.bolt(p, bolt);
            }
            case FxMessage.ProjectileEnd end -> {
                FxKinds.Bolt bolt = BOLTS.remove(end.id());
                if (bolt != null) {
                    bolt.end(end, MOTES, emitFactor());
                }
            }
            case FxMessage.Hit hit -> {
                add(new FxKinds.Hit(FxStyle.of(hit.crit() ? "crit" : "hit", FxStyle.Kind.HIT), hit));
                FxScenes.hit(hit);
                if (hit.crit()) {
                    // Свой звонкий хруст крита — поверх ванильного, его слышат все рядом.
                    Entity target = Minecraft.getInstance().level != null
                            ? Minecraft.getInstance().level.getEntity(hit.entityId()) : null;
                    if (target != null) {
                        FxSounds.play("hit.crit", target.getX(), target.getY() + 1, target.getZ(),
                                0.6f, 1f);
                    }
                }
                // Свой крит виден ещё и краями экрана: его чувствует тот, кто ударил.
                var self = Minecraft.getInstance().player;
                if (hit.crit() && self != null && hit.attacker() == self.getId()) {
                    FxScreen.flash(0xFFFFE9A8, 8);
                }
            }
            case FxMessage.Trail trail -> {
                if (!FxScenes.trail(trail)) {
                    add(new FxKinds.Trail(FxStyle.of(trail.fx(), FxStyle.Kind.TRAIL), trail));
                }
            }
            // Протокол 10: сцены, каст, статусы и звук принимаются отдельными
            // службами; здесь — только то, что рисуется видами из FxKinds.
            case FxMessage.Telegraph mark -> FxScenes.mark(mark);
            case FxMessage.CastStart start -> FxCasts.start(start);
            case FxMessage.CastEnd end -> FxCasts.end(end);
            case FxMessage.StatusOn on -> FxStatuses.on(on);
            case FxMessage.StatusOff off -> FxStatuses.off(off);
            case FxMessage.Sound sound -> FxSounds.play(sound.event(), sound.x(), sound.y(),
                    sound.z(), sound.volume(), sound.pitch());
        }
    }

    /** Запомнить, чей класс у источника события: статусам нужен облик по классу. */
    private static void noteClass(FxMessage.Event event) {
        switch (event) {
            case FxMessage.CastStart start -> SceneKit.noteClass(start.entityId(), start.classId());
            case FxMessage.Burst burst -> SceneKit.noteClass(burst.source(), burst.classId());
            case FxMessage.ZoneOn on -> SceneKit.noteClass(on.owner(), on.classId());
            case FxMessage.Telegraph mark -> SceneKit.noteClass(mark.caster(), mark.classId());
            case FxMessage.Hit hit -> SceneKit.noteClass(hit.attacker(), hit.classId());
            default -> {
            }
        }
    }

    /**
     * Общий вид для формы, если своего эффекта у мода нет.
     *
     * <p>Облако шире полутора блоков — это область, а не вспышка: рисуется
     * волной до своего радиуса, как кольцо.
     */
    private static FxStyle.Kind kindOf(FxMessage.Shape shape, float radius) {
        return switch (shape) {
            case RING -> FxStyle.Kind.WAVE;
            case CONE -> FxStyle.Kind.CONE;
            case SPHERE -> radius >= 1.5f ? FxStyle.Kind.WAVE : FxStyle.Kind.FLASH;
            case POINT, LINE -> FxStyle.Kind.FLASH;
        };
    }

    /** Для сцен: эффект с теми же пределами, что у остальных. */
    static void addEffect(FxKinds.Effect effect) {
        add(effect);
    }

    static FxMotes motes() {
        return MOTES;
    }

    static float emit() {
        return emitFactor();
    }

    /** Украшение берётся, только пока есть место. */
    private static void add(FxKinds.Effect effect) {
        if (EFFECTS.size() + PENDING.size() >= MAX_EFFECTS && effect.decor()) {
            return;
        }
        if (ticking) {
            PENDING.add(effect);
            return;
        }
        EFFECTS.add(effect);
    }

    /** Выход с сервера: всё живое забывается. */
    static void clear() {
        EFFECTS.clear();
        PENDING.clear();
        ZONES.clear();
        BOLTS.clear();
        MOTES.clear();
        FxGround.clear();
        FxSolids.clear();
        FxStatuses.clear();
        FxCasts.clear();
        FxScreen.clear();
        SceneKit.forgetClasses();
    }

    // ------------------------------------------------------------------ настройка

    /** Сколько искр рождать: от настройки «Частицы» и от тесноты. */
    private static float emitFactor() {
        ParticleStatus status = Minecraft.getInstance().options.particles().get();
        float base = switch (status) {
            case ALL -> 1f;
            case DECREASED -> 0.4f;
            case MINIMAL -> 0f;
        };
        // Когда эффектов много, каждый рождает меньше: двадцать печатей в одной
        // точке не должны соревноваться за пул искр.
        float crowd = EFFECTS.size() > 96 ? 0.4f : 1f;
        return base * crowd * (1f - MOTES.pressure() * 0.7f);
    }

    // ------------------------------------------------------------------ тик

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            if (!EFFECTS.isEmpty() || MOTES.count() > 0) {
                clear();
            }
            return;
        }
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        now++;
        FxGround.tick();
        float emit = emitFactor();
        ticking = true;
        try {
            Iterator<FxKinds.Effect> it = EFFECTS.iterator();
            while (it.hasNext()) {
                FxKinds.Effect effect = it.next();
                try {
                    effect.tick(level, MOTES, emit);
                } catch (RuntimeException e) {
                    warnOnce("эффект сломался на тике", e);
                    effect.dead = true;
                }
                if (effect.dead) {
                    it.remove();
                }
            }
        } finally {
            ticking = false;
        }
        EFFECTS.addAll(PENDING);
        PENDING.clear();
        ZONES.values().removeIf(zone -> zone.dead);
        BOLTS.values().removeIf(bolt -> bolt.dead);
        MOTES.tick();
        FxSolids.tick(level);
        FxStatuses.tick();
        FxCasts.tick();
        FxScreen.tick();
    }

    // ------------------------------------------------------------------ кадр

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            renderSolids(event);
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        ParticleStatus status = client.options.particles().get();
        float detail = switch (status) {
            case ALL -> 1f;
            case DECREASED -> 0.7f;
            case MINIMAL -> 0.5f;
        };
        boolean decorations = status != ParticleStatus.MINIMAL;
        DRAW.begin(level, eye.x, eye.y, eye.z, camera.rotation(), detail);
        Frustum frustum = event.getFrustum();
        FxKinds.Detail near = new FxKinds.Detail(decorations, 1f);
        FxKinds.Detail far = new FxKinds.Detail(false, 0f);
        ticking = true;
        try {
            for (FxKinds.Effect effect : EFFECTS) {
                try {
                    var box = effect.bounds();
                    if (!frustum.isVisible(box)) {
                        continue;
                    }
                    double distance = Math.sqrt(box.distanceToSqr(eye));
                    if (distance > FAR) {
                        continue;
                    }
                    effect.draw(DRAW, partial, distance > NEAR ? far : near);
                } catch (RuntimeException e) {
                    warnOnce("эффект сломался при рисовании", e);
                    effect.dead = true;
                }
            }
        } finally {
            ticking = false;
        }
        try {
            FxStatuses.draw(DRAW, partial);
            FxCasts.draw(DRAW, partial);
        } catch (RuntimeException e) {
            warnOnce("состояния не нарисовались", e);
        }
        if (decorations) {
            MOTES.draw(DRAW, partial, NEAR + 8);
        }
        try {
            var pose = event.getPoseStack();
            pose.pushPose();
            DRAW.flush(pose.last().pose(), client.renderBuffers().bufferSource());
            pose.popPose();
        } catch (RuntimeException e) {
            warnOnce("эффекты не нарисовались", e);
        }
    }

    /**
     * Объекты в мире — после существ, непрозрачно и с глубиной: корень
     * прячется за ногой, которую обвивает. Свечение — позже, своим проходом.
     */
    private static void renderSolids(RenderLevelStageEvent event) {
        if (FxSolids.count() == 0) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Vec3 eye = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        boolean full = client.options.particles().get() != ParticleStatus.MINIMAL;
        try {
            var pose = event.getPoseStack();
            pose.pushPose();
            var buffers = client.renderBuffers().bufferSource();
            FxSolids.render(pose, buffers, eye.x, eye.y, eye.z, partial, event.getFrustum(), full);
            buffers.endBatch();
            pose.popPose();
        } catch (RuntimeException e) {
            warnOnce("объекты не нарисовались", e);
        }
    }

    private static void warnOnce(String what, RuntimeException e) {
        if (!warned) {
            warned = true;
            LOG.warn("RpgCore: {} — {}", what, e.toString(), e);
        }
    }
}
