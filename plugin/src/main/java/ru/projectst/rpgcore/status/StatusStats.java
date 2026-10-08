package ru.projectst.rpgcore.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ru.projectst.rpgcore.stat.StatModifier;
import ru.projectst.rpgcore.stat.StatOp;
import ru.projectst.rpgcore.stat.StatService;

/**
 * Надбавки к статам, которые живут ровно столько, сколько статус.
 *
 * <p>Раньше навык накладывал статус и отдельно давал надбавку со своим
 * таймером. Связи между ними не было: развеянная «Пелена» оставляла скорость до
 * конца таймера, заблокированное оглушение всё равно обездвиживало, а показать
 * игроку «Пелена: +12% скорости» было нечем — никто не знал, что эти двое одно
 * целое.
 *
 * <p>Здесь надбавка привязана к статусу на цели и сверяется с ним: статус
 * истёк, снят, развеян, вытеснен или цель забыта — надбавка уходит; статус
 * подавлен — надбавка не действует, но вернётся, если подавление спадёт, ровно
 * как сам статус. Своего учёта времени нет: время статуса и есть время
 * надбавки, и второй учёт рядом со статусами однажды разошёлся бы с первым.
 *
 * <p>Источник надбавки — {@code status:<статус>:<стат>}, один на пару. Поэтому
 * повторное наложение заменяет надбавку, а не складывает её, — так же, как
 * было с источником навыка.
 */
public final class StatusStats {

    /** Начало имени источника: по нему видно, что надбавка от статуса. */
    public static final String SOURCE_PREFIX = "status:";

    private final StatusService statuses;
    private final StatService stats;

    /** цель → источник → привязка */
    private final Map<UUID, Map<String, Link>> links = new HashMap<>();

    public StatusStats(StatusService statuses, StatService stats) {
        this.statuses = statuses;
        this.stats = stats;
    }

    /** Имя источника надбавки статуса к стату. */
    public static String sourceOf(String statusId, String statId) {
        return SOURCE_PREFIX + statusId + ":" + statId;
    }

    /**
     * Привязывает надбавку к статусу на цели.
     *
     * @return {@code false}, если статуса на цели нет: заблокирован, не лёг. Тогда
     *         нет и надбавки — она часть статуса, а не отдельный подарок
     */
    public boolean attach(UUID target, String statusId, String statId, StatOp op, double value) {
        if (!statuses.isPresent(target, statusId)) {
            return false;
        }
        String source = sourceOf(statusId, statId);
        links.computeIfAbsent(target, key -> new LinkedHashMap<>())
                .put(source, new Link(statusId, new StatModifier(statId, op, value, source)));
        sync(target);
        return true;
    }

    /**
     * Сверяет надбавки цели с её статусами.
     *
     * <p>Зовётся каждый тик для всех, у кого привязки есть, и сразу после
     * привязки. Дёшево: статы трогаются только когда что-то изменилось.
     */
    public void sync(UUID target) {
        Map<String, Link> mine = links.get(target);
        if (mine == null) {
            return;
        }
        Set<String> present = ids(statuses.all(target));
        Set<String> acting = ids(statuses.acting(target));

        Iterator<Map.Entry<String, Link>> it = mine.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Link> entry = it.next();
            Link link = entry.getValue();
            if (!present.contains(link.statusId)) {
                stats.removeSource(target, entry.getKey());
                it.remove();
                continue;
            }
            boolean act = acting.contains(link.statusId);
            if (act && !link.applied) {
                stats.setSource(target, entry.getKey(), List.of(link.modifier));
                link.applied = true;
            } else if (!act && link.applied) {
                stats.removeSource(target, entry.getKey());
                link.applied = false;
            }
        }
        if (mine.isEmpty()) {
            links.remove(target);
        }
    }

    /** Сверка всех, у кого есть привязки. */
    public void syncAll() {
        for (UUID target : List.copyOf(links.keySet())) {
            sync(target);
        }
    }

    /** Надбавки, которые статус даёт цели прямо сейчас: для показа. */
    public List<StatModifier> of(UUID target, String statusId) {
        Map<String, Link> mine = links.get(target);
        if (mine == null) {
            return List.of();
        }
        List<StatModifier> out = new ArrayList<>();
        for (Link link : mine.values()) {
            if (link.applied && link.statusId.equals(statusId)) {
                out.add(link.modifier);
            }
        }
        return out;
    }

    /** Забыть цель: вышла или исчезла. Статы забываются отдельно. */
    public void forget(UUID target) {
        links.remove(target);
    }

    private static Set<String> ids(List<ActiveStatus> list) {
        Set<String> out = new HashSet<>();
        for (ActiveStatus status : list) {
            out.add(status.id());
        }
        return out;
    }

    private static final class Link {

        private final String statusId;
        private final StatModifier modifier;
        /** Лежит ли надбавка в статах сейчас: подавленный статус её снимает. */
        private boolean applied;

        private Link(String statusId, StatModifier modifier) {
            this.statusId = statusId;
            this.modifier = modifier;
        }
    }
}
