package ru.projectst.rpgcore.cast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Перезарядки навыков.
 *
 * <p>Время измеряется тиками из того же источника, что и статусы: два разных
 * представления о «сейчас» рано или поздно расходятся, и расхождение видно
 * игроку как навык, который вроде готов, но не срабатывает.
 *
 * <p><b>Заряды.</b> Навык может иметь их несколько, и тогда хранится не «когда
 * будет готов», а список сроков возврата потраченных зарядов. Каждый заряд
 * возвращается сам, через свою перезарядку, считая от момента, когда его
 * потратили: три рывка подряд вернутся по одному, а не все вместе. Обратная
 * схема — один таймер на всё — означала бы, что второй заряд, потраченный сразу
 * после первого, продлевает ожидание первому.
 *
 * <p>Навык без зарядов — тот же механизм с одним зарядом, и ни одной отдельной
 * ветки кода: вторая ветка однажды разошлась бы с первой.
 */
public final class CooldownTracker {

    private final LongSupplier clock;

    /** Сроки возврата потраченных зарядов по игроку и навыку. */
    private final Map<UUID, Map<String, List<Long>>> pending = new HashMap<>();

    public CooldownTracker(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Сколько тиков осталось до возможности применить навык; ноль — «готов».
     *
     * <p>Для навыка с одним зарядом — ровно прежнее поведение.
     *
     * @param charges сколько зарядов у навыка
     */
    public long remaining(UUID player, String skillId, int charges) {
        List<Long> own = prune(player, skillId);
        if (own.size() < Math.max(1, charges)) {
            return 0;
        }
        return untilNextCharge(player, skillId);
    }

    /** Сколько тиков осталось у навыка с одним зарядом. */
    public long remaining(UUID player, String skillId) {
        return remaining(player, skillId, 1);
    }

    /**
     * Сколько тиков до возврата ближайшего заряда; ноль — ничего не тратилось.
     *
     * <p>Отдельно от {@link #remaining}: полоса заряда рисуется и тогда, когда
     * свободные заряды ещё есть, иначе третий заряд возвращался бы на экране
     * мгновенно и без предупреждения.
     */
    public long untilNextCharge(UUID player, String skillId) {
        List<Long> own = prune(player, skillId);
        long soonest = Long.MAX_VALUE;
        for (long at : own) {
            soonest = Math.min(soonest, at);
        }
        return soonest == Long.MAX_VALUE ? 0 : Math.max(0, soonest - clock.getAsLong());
    }

    /** Сколько зарядов сейчас доступно из {@code charges}. */
    public int freeCharges(UUID player, String skillId, int charges) {
        int total = Math.max(1, charges);
        return Math.max(0, total - prune(player, skillId).size());
    }

    public boolean ready(UUID player, String skillId) {
        return remaining(player, skillId) == 0;
    }

    public void start(UUID player, String skillId, long ticks) {
        start(player, skillId, ticks, 1);
    }

    /**
     * Тратит один заряд: он вернётся через {@code ticks}.
     *
     * <p>Больше {@code charges} сроков не хранится. Лишний срок означал бы, что
     * навык применили, когда зарядов не было, и тогда честнее потерять запись,
     * чем копить ожидание, которого игрок не заслужил.
     */
    public void start(UUID player, String skillId, long ticks, int charges) {
        if (ticks <= 0) {
            return;
        }
        prune(player, skillId);
        List<Long> own = pending.computeIfAbsent(player, p -> new HashMap<>())
                .computeIfAbsent(skillId, s -> new ArrayList<>());
        own.add(clock.getAsLong() + ticks);
        int total = Math.max(1, charges);
        while (own.size() > total) {
            own.remove(0);
        }
    }

    /** Снимает перезарядку целиком: все заряды возвращаются сразу. */
    public void clear(UUID player, String skillId) {
        Map<String, List<Long>> own = pending.get(player);
        if (own != null) {
            own.remove(skillId);
        }
    }

    public void forget(UUID player) {
        pending.remove(player);
    }

    /**
     * Сроки по навыку без тех, что уже прошли.
     *
     * <p>Записи не создаёт: чтение состояния не должно оставлять за собой
     * пустые списки по каждому навыку, о котором спросили.
     */
    private List<Long> prune(UUID player, String skillId) {
        Map<String, List<Long>> own = pending.get(player);
        List<Long> times = own == null ? null : own.get(skillId);
        if (times == null) {
            return List.of();
        }
        long now = clock.getAsLong();
        times.removeIf(at -> at <= now);
        return times;
    }
}
