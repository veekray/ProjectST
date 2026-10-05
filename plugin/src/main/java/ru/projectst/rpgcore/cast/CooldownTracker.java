package ru.projectst.rpgcore.cast;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Перезарядки навыков.
 *
 * <p>Время измеряется тиками из того же источника, что и статусы: два разных
 * представления о «сейчас» рано или поздно расходятся, и расхождение видно
 * игроку как навык, который вроде готов, но не срабатывает.
 */
public final class CooldownTracker {

    private final LongSupplier clock;
    private final Map<UUID, Map<String, Long>> readyAt = new HashMap<>();

    public CooldownTracker(LongSupplier clock) {
        this.clock = clock;
    }

    /** Сколько тиков осталось; ноль означает «готов». */
    public long remaining(UUID player, String skillId) {
        Long ready = readyAt.getOrDefault(player, Map.of()).get(skillId);
        if (ready == null) {
            return 0;
        }
        return Math.max(0, ready - clock.getAsLong());
    }

    public boolean ready(UUID player, String skillId) {
        return remaining(player, skillId) == 0;
    }

    public void start(UUID player, String skillId, long ticks) {
        if (ticks <= 0) {
            return;
        }
        readyAt.computeIfAbsent(player, p -> new HashMap<>())
                .put(skillId, clock.getAsLong() + ticks);
    }

    public void clear(UUID player, String skillId) {
        Map<String, Long> own = readyAt.get(player);
        if (own != null) {
            own.remove(skillId);
        }
    }

    public void forget(UUID player) {
        readyAt.remove(player);
    }
}
