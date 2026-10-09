// КОПИЯ из плагина: plugin/src/main/java/ru/projectst/rpgcore/net/FxMessage.java
// Менять только там и копировать сюда — см. client-mod/README.md.
package ru.projectst.rpgcore.net;

/**
 * Видимые события навыков, как их получает мод.
 *
 * <p>Все числа посчитаны сервером. Радиус — тот, которым выбирались цели, срок
 * зоны — тот, с которым она поставлена. Мод их рисует и не выводит: граница,
 * посчитанная на клиенте второй раз, разошлась бы с уроном на первом же
 * предмете со статом радиуса.
 *
 * <p>Координаты — {@code double}: на краю мира у {@code float} шаг в два блока,
 * и кольцо в двух с половиной блоках там превратилось бы в квадрат.
 *
 * <p>{@code classId} — чей класс: им мод красит общий эффект, если своего для
 * {@code fx} у него нет. Пусто — ничей.
 */
public final class FxMessage {

    private FxMessage() {
    }

    /** Форма вспышки: те же, что у частиц навыка. */
    public enum Shape {
        POINT, SPHERE, RING, LINE, CONE;

        public static Shape of(int code) {
            Shape[] values = values();
            return code >= 0 && code < values.length ? values[code] : POINT;
        }
    }

    /** Почему зоны не стало. */
    public enum ZoneEnd {
        /** Истёк срок. */
        EXPIRED,
        /** Сняли навыком: съели или стянули. Тянется к точке в сообщении. */
        CONSUMED,
        /** Игрок отошёл далеко: зона есть, но рисовать её больше незачем. */
        OUT_OF_RANGE;

        public static ZoneEnd of(int code) {
            ZoneEnd[] values = values();
            return code >= 0 && code < values.length ? values[code] : EXPIRED;
        }
    }

    /** Одно событие. Набор закрытый: новый вид — новая версия протокола. */
    public sealed interface Event {
    }

    /**
     * Вспышка: кольцо, облако, конус или точка.
     *
     * @param radius радиус границы, уже со статом радиуса
     * @param angle  полная ширина конуса в градусах; для других форм ноль
     * @param axisX  ось конуса по X; для других форм ноль
     * @param axisZ  ось конуса по Z
     */
    public record Burst(String fx, String classId, Shape shape, double x, double y, double z,
                        float radius, float angle, float axisX, float axisZ) implements Event {
    }

    /**
     * Зона на земле.
     *
     * <p>Приходит при постановке и ещё раз тому, кто подошёл позже: поэтому
     * остаток срока отдельно от полного — дуга таймера должна начаться не с
     * полной, если зоне уже полминуты.
     *
     * @param id             номер зоны в этом соединении; по нему её снимают
     * @param totalTicks     полный срок
     * @param remainingTicks сколько осталось
     * @param own            поставил сам игрок: свою печать мод рисует ярче
     */
    public record ZoneOn(int id, String fx, String classId, double x, double y, double z,
                         float radius, int totalTicks, int remainingTicks, boolean own)
            implements Event {
    }

    /**
     * Зоны не стало.
     *
     * @param toX куда её стянуло, для {@link ZoneEnd#CONSUMED}; иначе её центр
     */
    public record ZoneOff(int id, ZoneEnd reason, double toX, double toY, double toZ)
            implements Event {
    }

    /**
     * Снаряд вылетел.
     *
     * <p>Полёт мод ведёт сам тем же расчётом, что сервер: отрезки не длиннее
     * полублока, снижение делится между ними. Где снаряд кончился на самом
     * деле, говорит {@link ProjectileEnd}: оно и решает, а не догадка клиента.
     *
     * @param dx       направление вылета, единичное
     * @param speed    блоков за тик
     * @param range    сколько всего пролетит
     * @param gravity  снижение направления за тик
     */
    public record Projectile(int id, String fx, String classId, double x, double y, double z,
                             float dx, float dy, float dz, float speed, float range,
                             float gravity) implements Event {
    }

    /**
     * Снаряд кончился.
     *
     * @param hit задел цель, а не упёрся или долетел
     */
    public record ProjectileEnd(int id, double x, double y, double z, boolean hit)
            implements Event {
    }

    /**
     * Урон навыка дошёл до цели.
     *
     * @param entityId сетевой номер существа: по нему мод находит, на ком вспыхнуть
     * @param crit     был ли крит — из результата конвейера урона
     */
    public record Hit(int entityId, String classId, boolean crit) implements Event {
    }

    /** След перемещения от точки до точки. */
    public record Trail(String fx, String classId, double fromX, double fromY, double fromZ,
                        double toX, double toY, double toZ) implements Event {
    }
}
