package ru.projectst.rpgcore.skill;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;

/**
 * Живые зоны на сервере.
 *
 * <p>Время берётся из того же источника, что у статусов и перезарядок: третьего
 * представления о «сейчас» в проекте быть не должно.
 *
 * <p>Истёкшие зоны отбрасываются при каждом чтении, а не только по таймеру.
 * Иначе ответ на вопрос «стою ли я в печати» зависел бы от того, успел ли
 * пройти таймер, — ровно тот сорт расхождения, из-за которого в старом стеке
 * печать то усиливала навык, то нет.
 */
public final class ZoneService {

    /** Предохранитель: зон на сервере не может быть больше. */
    private static final int LIMIT = 4000;

    private final LongSupplier clock;
    private final List<Zone> zones = new CopyOnWriteArrayList<>();

    public ZoneService(LongSupplier clock) {
        this.clock = clock;
    }

    public Zone place(String tag, UUID owner, Position center, double radius, int durationTicks) {
        return place(tag, owner, center, radius, durationTicks, null);
    }

    /**
     * Ставит зону.
     *
     * <p>При переполнении снимается самая старая. Отказ был бы хуже: навык
     * сработал бы вполовину и ничего бы об этом не сказал.
     */
    public Zone place(String tag, UUID owner, Position center, double radius,
                      int durationTicks, String particle) {
        expireAll();
        if (zones.size() >= LIMIT) {
            zones.remove(0);
        }
        Zone zone = new Zone(UUID.randomUUID(), tag, owner, center, radius,
                clock.getAsLong() + Math.max(1, durationTicks), particle);
        zones.add(zone);
        return zone;
    }

    /** Зоны с этим тегом, в чьей области лежит точка. */
    public List<Zone> at(Position point, String tag) {
        return find(zone -> zone.tag().equals(tag) && zone.contains(point));
    }

    /** Зоны с этим тегом, чей центр ближе radius к точке. */
    public List<Zone> near(Position point, double radius, String tag) {
        return find(zone -> zone.tag().equals(tag)
                && zone.center().worldId().equals(point.worldId())
                && zone.center().distanceTo(point) <= radius);
    }

    /**
     * Снимает зоны с этим тегом рядом с точкой и говорит, сколько сняла.
     *
     * <p>Возвращаемое число — это то, на что опирается расчёт: «урон за каждую
     * печать». Поэтому снятие и подсчёт — одна операция: два вызова могли бы
     * разойтись, и навык посчитал бы печати, которые уже снял кто-то другой.
     *
     * @param owner если задан, снимаются только свои зоны
     */
    public int consume(Position point, double radius, String tag, UUID owner) {
        List<Zone> hit = near(point, radius, tag);
        int count = 0;
        for (Zone zone : hit) {
            if (owner != null && !zone.ownedBy(owner)) {
                continue;
            }
            if (zones.remove(zone)) {
                count++;
            }
        }
        return count;
    }

    public boolean remove(Zone zone) {
        return zones.remove(zone);
    }

    /** Снимает все зоны этого владельца: выход игрока, смена класса. */
    public int forgetOwner(UUID owner) {
        List<Zone> own = find(zone -> zone.ownedBy(owner));
        own.forEach(zones::remove);
        return own.size();
    }

    /** Все действующие зоны: для отрисовки и для отладки. */
    public Collection<Zone> all() {
        return find(zone -> true);
    }

    public int size() {
        expireAll();
        return zones.size();
    }

    /** Убирает истёкшие. Чтения делают это сами, таймеру остаётся память. */
    public void expireAll() {
        long now = clock.getAsLong();
        zones.removeIf(zone -> zone.expired(now));
    }

    private List<Zone> find(java.util.function.Predicate<Zone> filter) {
        long now = clock.getAsLong();
        List<Zone> out = new ArrayList<>();
        for (Zone zone : zones) {
            if (!zone.expired(now) && filter.test(zone)) {
                out.add(zone);
            }
        }
        return out;
    }
}
