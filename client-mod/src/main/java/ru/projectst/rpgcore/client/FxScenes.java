package ru.projectst.rpgcore.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Сцены навыков: что именно появляется в мире по событию.
 *
 * <p>Стиль ({@link FxStyle}) говорит, какого цвета и как долго кольцо. Сцена —
 * больше: корни из земли, цепи, клинки, щиты, звук и экран, собранные по
 * сценарию навыка ({@code docs/vfx/skill-visuals.md}). Сцена получает то же
 * событие с числами сервера и ничего не пересчитывает.
 *
 * <p>Сцены лежат по классам ({@code Scenes<Класс>}), здесь — только каталог и
 * разбор события. У события пять видов сцены: вспышка ({@code Burst}),
 * предупреждение ({@code Telegraph}), зона, снаряд и след перемещения.
 *
 * <p><b>Граница — правда.</b> Сцена с областью всегда кладёт обычную границу
 * тем же радиусом, а объекты держит внутри круга ({@link FxGeometry}).
 *
 * <p>Неизвестный {@code fx} сцены не имеет — его рисует роль класса, как раньше.
 */
final class FxScenes {

    private static final Logger LOG = LoggerFactory.getLogger("rpgcore");

    private FxScenes() {
    }

    interface BurstScene {
        void play(FxMessage.Burst event);
    }

    interface MarkScene {
        void play(FxMessage.Telegraph event);
    }

    /** Сцена зоны: появляется вместе с зоной и уходит вместе с ней. */
    interface ZoneScene {
        void play(FxMessage.ZoneOn event, FxKinds.Zone zone);
    }

    /**
     * Сцена снаряда: полёт ведёт {@link FxKinds.Bolt} — по нему сцена вешает
     * модель (стрелу, череп, клинок) и решает, что будет в конце.
     */
    interface BoltScene {
        void play(FxMessage.Projectile event, FxKinds.Bolt bolt);
    }

    /** Сцена следа перемещения: рывок, прыжок, телепорт. */
    interface TrailScene {
        void play(FxMessage.Trail event);
    }

    /** Сцены одного класса и облик его состояний на существах. */
    record Set(Map<String, BurstScene> bursts, Map<String, MarkScene> marks,
               Map<String, ZoneScene> zones, Map<String, BoltScene> bolts,
               Map<String, TrailScene> trails, Map<String, FxStatuses.Look> statuses) {
    }

    private static final Map<String, BurstScene> BURSTS = new HashMap<>();
    private static final Map<String, MarkScene> MARKS = new HashMap<>();
    private static final Map<String, ZoneScene> ZONES = new HashMap<>();
    private static final Map<String, BoltScene> BOLTS = new HashMap<>();
    private static final Map<String, TrailScene> TRAILS = new HashMap<>();
    private static final Map<String, FxStatuses.Look> STATUSES = new HashMap<>();

    static {
        for (Set set : List.of(ScenesDruid.scenes(), ScenesMage.scenes(), ScenesWarlock.scenes(),
                ScenesRogue.scenes(), ScenesAssassin.scenes(), ScenesTrickster.scenes(),
                ScenesHunter.scenes(), ScenesBerserker.scenes(), ScenesStriker.scenes(),
                ScenesKnight.scenes(), ScenesWarrior.scenes(), ScenesCommon.scenes())) {
            merge(BURSTS, set.bursts());
            merge(MARKS, set.marks());
            merge(ZONES, set.zones());
            merge(BOLTS, set.bolts());
            merge(TRAILS, set.trails());
            merge(STATUSES, set.statuses());
        }
    }

    private static <T> void merge(Map<String, T> into, Map<String, T> from) {
        for (Map.Entry<String, T> entry : from.entrySet()) {
            if (into.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                // Два класса заявили одну сцену — опечатка в коде мода, а не повод
                // ронять игру: остаётся первая, в журнале строка.
                LOG.warn("RpgCore: сцена {} объявлена дважды", entry.getKey());
            }
        }
    }

    /** Облик статуса на существе; {@code null} — рисует общий разбор {@link FxStatuses}. */
    static FxStatuses.Look status(String statusId) {
        return STATUSES.get(statusId);
    }

    /** Есть ли у мода своя сцена: тесту контента и отладке. */
    static boolean known(String fx) {
        return BURSTS.containsKey(fx) || MARKS.containsKey(fx) || ZONES.containsKey(fx)
                || BOLTS.containsKey(fx) || TRAILS.containsKey(fx);
    }

    /** @return {@code true}, если сцена есть и сыграна */
    static boolean burst(FxMessage.Burst event) {
        BurstScene scene = BURSTS.get(event.fx());
        if (scene == null) {
            return false;
        }
        scene.play(event);
        return true;
    }

    /** Предупреждение: своя сцена или общий круг, который заполняется к удару. */
    static void mark(FxMessage.Telegraph event) {
        MarkScene scene = MARKS.get(event.fx());
        if (scene != null) {
            scene.play(event);
        } else {
            SceneKit.mark(event, SceneKit.MarkDecor.PLAIN);
        }
    }

    static void zone(FxMessage.ZoneOn event, FxKinds.Zone zone) {
        ZoneScene scene = ZONES.get(event.fx());
        if (scene != null) {
            scene.play(event, zone);
        }
    }

    static void bolt(FxMessage.Projectile event, FxKinds.Bolt bolt) {
        BoltScene scene = BOLTS.get(event.fx());
        if (scene != null) {
            scene.play(event, bolt);
        }
    }

    /** @return {@code true}, если сцена есть и сыграна — общий след тогда не нужен */
    static boolean trail(FxMessage.Trail event) {
        TrailScene scene = TRAILS.get(event.fx());
        if (scene == null) {
            return false;
        }
        scene.play(event);
        return true;
    }
}
