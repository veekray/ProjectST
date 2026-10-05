package ru.projectst.rpgcore.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Призванные существа и их владельцы.
 *
 * <p>Знание «чей это моб» живёт здесь, а не в переменных самого моба. Из-за
 * обратного в старом стеке зверь друида то считался своим, то нет: штамп имени
 * владельца ставился в обработчике спавна и успевал не всегда, а до того любой
 * волк был «своим» для любого друида.
 *
 * <p>Служба также решает, кого навык не должен задевать. Это одно правило в
 * одном месте: свои призванные не попадают в выборку врагов владельца. Иначе
 * каждый навык с радиусом нёс бы собственный фильтр, и девятый по счёту про
 * него забыл бы.
 */
public final class MinionService {

    /** Сколько призванных может держать один игрок. */
    public static final int PER_OWNER_LIMIT = 8;

    private final LongSupplier clock;
    private final Map<UUID, Minion> byEntity = new ConcurrentHashMap<>();

    public MinionService(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Берёт существо под учёт.
     *
     * <p>При переполнении снимается самое старое существо владельца, а не
     * отклоняется новое: иначе навык срабатывал бы вполовину и молчал об этом.
     *
     * @return снятое существо, если пришлось освобождать место
     */
    public Optional<UUID> register(UUID entity, UUID owner, String tag, int durationTicks,
                                   boolean attacksEnemies) {
        expireAll();
        Optional<UUID> evicted = Optional.empty();
        List<Minion> own = ofOwner(owner);
        if (own.size() >= PER_OWNER_LIMIT) {
            Minion oldest = own.stream()
                    .min(java.util.Comparator.comparingLong(Minion::expiresAtTick))
                    .orElseThrow();
            byEntity.remove(oldest.entityId());
            evicted = Optional.of(oldest.entityId());
        }
        byEntity.put(entity, new Minion(entity, owner, tag,
                clock.getAsLong() + Math.max(1, durationTicks), attacksEnemies));
        return evicted;
    }

    public Optional<Minion> of(UUID entity) {
        Minion minion = byEntity.get(entity);
        if (minion == null) {
            return Optional.empty();
        }
        if (minion.expired(clock.getAsLong())) {
            byEntity.remove(entity);
            return Optional.empty();
        }
        return Optional.of(minion);
    }

    /** Существа этого владельца; с тегом {@code null} — все. */
    public List<Minion> ofOwner(UUID owner, String tag) {
        long now = clock.getAsLong();
        List<Minion> out = new ArrayList<>();
        for (Minion minion : byEntity.values()) {
            if (!minion.expired(now) && minion.ownedBy(owner)
                    && (tag == null || minion.tag().equals(tag))) {
                out.add(minion);
            }
        }
        return out;
    }

    public List<Minion> ofOwner(UUID owner) {
        return ofOwner(owner, null);
    }

    /**
     * Нельзя ли этому кастеру задевать эту цель своими площадными навыками.
     *
     * <p>Защищён только <b>свой</b> призванный. Чужой зверь — обычная цель, и
     * это тоже решение: иначе бой двух друидов превратился бы в бой с
     * неуязвимыми волками.
     */
    public boolean isOwnMinion(UUID caster, UUID target) {
        return of(target).filter(minion -> minion.ownedBy(caster)).isPresent();
    }

    /** Снимает с учёта: существо умерло или было снято по сроку. */
    public boolean forget(UUID entity) {
        return byEntity.remove(entity) != null;
    }

    /** Все существа владельца сняты с учёта; возвращает их, чтобы убрать из мира. */
    public List<UUID> forgetOwner(UUID owner) {
        List<UUID> out = new ArrayList<>();
        for (Minion minion : ofOwner(owner)) {
            byEntity.remove(minion.entityId());
            out.add(minion.entityId());
        }
        return out;
    }

    /** Истёкшие существа: их нужно убрать из мира, поэтому они возвращаются. */
    public List<UUID> expired() {
        long now = clock.getAsLong();
        List<UUID> out = new ArrayList<>();
        for (Minion minion : List.copyOf(byEntity.values())) {
            if (minion.expired(now)) {
                byEntity.remove(minion.entityId());
                out.add(minion.entityId());
            }
        }
        return out;
    }

    public void expireAll() {
        expired();
    }

    public List<Minion> all() {
        long now = clock.getAsLong();
        return byEntity.values().stream().filter(m -> !m.expired(now)).toList();
    }

    public int size() {
        expireAll();
        return byEntity.size();
    }
}
