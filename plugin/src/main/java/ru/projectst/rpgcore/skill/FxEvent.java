package ru.projectst.rpgcore.skill;

import java.util.UUID;

/**
 * Видимое событие навыка для клиентского мода.
 *
 * <p>Все числа уже посчитаны исполнителем: радиус — тот, которым выбирались
 * цели, со статом радиуса; срок зоны — тот, с которым она поставлена. Мод их не
 * выводит, а рисует, — иначе граница на экране и граница урона разошлись бы на
 * первом же предмете со статом, как разошлись ванильные кольца.
 *
 * <p>У каждого события есть ванильный запасной вид — частица из навыка. Кто его
 * увидит, решает платформа: игрок с модом получает событие, без мода — частицы.
 * Одно и то же никто не видит дважды.
 *
 * <p>{@code classId} — чей класс: по нему мод красит общий эффект, если
 * своего для {@code fx} у него нет. Пусто — у навыка нет класса (предмет, моб).
 */
public sealed interface FxEvent {

    /**
     * Вспышка в точке: кольцо, облако, конус или точка.
     *
     * @param fx       эффект мода
     * @param radius   радиус границы; для {@code size: radius} — радиус выборки
     * @param angle    полная ширина конуса; для других форм ноль
     * @param axis     ось конуса; для других форм {@code null}
     * @param particle ванильная частица для игроков без мода
     * @param count    сколько ванильных частиц
     */
    record Burst(String fx, String classId, Position at, Action.Particles.Shape shape,
                 double radius, double angle, Heading axis, String particle, int count)
            implements FxEvent {
    }

    /** Поставлена зона с эффектом: срок, радиус и место — в самой зоне. */
    record ZonePlaced(Zone zone) implements FxEvent {
    }

    /**
     * Зону сняли навыком, а не сроком: печать съедена или стянута Коллапсом.
     *
     * @param pulledTo куда её стянуло: точка действия снявшего шага
     */
    record ZoneConsumed(Zone zone, Position pulledTo) implements FxEvent {
    }

    /**
     * Урон навыка дошёл до цели.
     *
     * <p>Только урон навыков: обычный удар мечом сюда не попадает, его крит
     * показывает ванилла.
     *
     * @param crit был ли крит — из того же результата конвейера, что урон
     */
    record Hit(UUID target, String classId, boolean crit) implements FxEvent {
    }

    /**
     * След перемещения: откуда и куда перенесло.
     *
     * @param fx       эффект мода; {@code null} — у всех ванильный след
     * @param particle ванильная частица следа
     */
    record Trail(String fx, String classId, Position from, Position to, String particle)
            implements FxEvent {
    }
}
