package ru.projectst.rpgcore.skill;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<UUID, java.util.Set<UUID>> occupants = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTicks = new ConcurrentHashMap<>();

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
        return place(tag, owner, center, radius, durationTicks, particle, 0, null, null, 0)
                .orElseThrow();
    }

    /**
     * Ставит зону, если ближе {@code minGap} нет своей зоны с тем же тегом.
     *
     * @return поставленная зона либо пусто, если место уже занято своей
     */
    public Optional<Zone> place(String tag, UUID owner, Position center, double radius,
                                int durationTicks, String particle, double minGap,
                                String onEnter, String onTick, int tickInterval) {
        expireAll();
        if (minGap > 0 && !near(center, minGap, tag).stream()
                .filter(zone -> owner == null || zone.ownedBy(owner)).toList().isEmpty()) {
            return Optional.empty();
        }
        if (zones.size() >= LIMIT) {
            zones.remove(0);
        }
        Zone zone = new Zone(UUID.randomUUID(), tag, owner, center, radius,
                clock.getAsLong() + Math.max(1, durationTicks), particle,
                onEnter, onTick, tickInterval);
        zones.add(zone);
        return Optional.of(zone);
    }

    /**
     * Кто уже внутри этой зоны по учёту службы.
     *
     * <p>Вход определяется сравнением с прошлым разом, поэтому «вошёл»
     * срабатывает один раз, а не каждый тик, пока цель стоит в круге.
     */
    public boolean markInside(UUID zoneId, UUID entity, boolean inside) {
        java.util.Set<UUID> known = occupants.computeIfAbsent(zoneId,
                id -> java.util.concurrent.ConcurrentHashMap.newKeySet());
        return inside ? known.add(entity) : known.remove(entity);
    }

    /** Когда зоны не стало, её учёт вошедших тоже не нужен. */
    public void forgetOccupants(UUID zoneId) {
        occupants.remove(zoneId);
    }

    /** Когда зона в последний раз тикала. */
    public long lastTick(UUID zoneId) {
        return lastTicks.getOrDefault(zoneId, 0L);
    }

    public void markTicked(UUID zoneId) {
        lastTicks.put(zoneId, clock.getAsLong());
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

    /** Свои зоны этого владельца с этим тегом. */
    public List<Zone> ofOwner(UUID owner, String tag) {
        return find(zone -> zone.ownedBy(owner) && (tag == null || zone.tag().equals(tag)));
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
        for (Zone zone : List.copyOf(zones)) {
            if (zone.expired(now)) {
                zones.remove(zone);
                occupants.remove(zone.id());
                lastTicks.remove(zone.id());
            }
        }
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
