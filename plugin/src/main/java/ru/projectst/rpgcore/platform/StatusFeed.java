package ru.projectst.rpgcore.platform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Какие статусы на каких существах уже показаны каждому игроку с модом.
 *
 * <p><b>Сверка, а не подписка.</b> Служба статусов не знает про мир и про мод и
 * знать не должна. Здесь раз в тик сравнивается то, что висит сейчас, с тем,
 * что игрок уже видел, и уходит только разница. Поэтому сами собой работают
 * наложение, снятие, продление, истечение и «подошёл позже»: подошедшему
 * статус уходит с остатком срока, ушедшему — снимается.
 *
 * <p>Чистая логика без Bukkit: что видно игроку, собирает платформа, а здесь
 * только «что изменилось». Ровно это и проверяется тестом.
 */
public final class StatusFeed {

    /**
     * Статус на существе, как его видно сейчас.
     *
     * @param source    сетевой номер наложившего или хозяина; 0 — неизвестен
     * @param total     полный срок, со статом длительности
     * @param expiresAt тик, когда кончится
     */
    public record Seen(String statusId, int source, int total, long expiresAt, int stacks) {
    }

    /** Что показано: игрок → (существо, статус) → как было показано. */
    private final Map<UUID, Map<Long, Map<String, Seen>>> shown = new HashMap<>();

    /**
     * Разница для одного игрока.
     *
     * @param visible существа в его радиусе: сетевой номер → статусы на нём
     * @param now     тик сейчас: от него считается остаток
     */
    public List<FxMessage.Event> diff(UUID viewer, Map<Integer, List<Seen>> visible, long now) {
        Map<Long, Map<String, Seen>> mine = shown.computeIfAbsent(viewer, k -> new HashMap<>());
        List<FxMessage.Event> out = new ArrayList<>();
        Map<Long, Map<String, Seen>> next = new HashMap<>();
        for (Map.Entry<Integer, List<Seen>> entity : visible.entrySet()) {
            int id = entity.getKey();
            Map<String, Seen> before = mine.getOrDefault((long) id, Map.of());
            Map<String, Seen> after = new HashMap<>();
            for (Seen status : entity.getValue()) {
                if (status.expiresAt() <= now) {
                    continue;
                }
                after.put(status.statusId(), status);
                Seen old = before.get(status.statusId());
                // Новый срок, новые стаки или другой наложивший — это другое
                // состояние: корни, наложенные заново, держат заново.
                if (!status.equals(old)) {
                    out.add(new FxMessage.StatusOn(id, status.statusId(), status.source(),
                            status.total(), (int) Math.min(Integer.MAX_VALUE,
                                    status.expiresAt() - now), status.stacks()));
                }
            }
            for (String gone : before.keySet()) {
                if (!after.containsKey(gone)) {
                    out.add(new FxMessage.StatusOff(id, gone));
                }
            }
            if (!after.isEmpty()) {
                next.put((long) id, after);
            }
        }
        // Существо пропало из виду целиком: умерло или игрок ушёл далеко.
        for (Map.Entry<Long, Map<String, Seen>> entity : mine.entrySet()) {
            // Видимое существо уже сверено выше, со снятием каждого статуса:
            // второй StatusOff на тот же статус был бы лишним пакетом.
            if (!visible.containsKey((int) (long) entity.getKey())) {
                for (String gone : entity.getValue().keySet()) {
                    out.add(new FxMessage.StatusOff((int) (long) entity.getKey(), gone));
                }
            }
        }
        shown.put(viewer, next);
        return out;
    }

    /** Игрок ушёл: что ему было показано, больше не нужно. */
    public void forget(UUID viewer) {
        shown.remove(viewer);
    }
}
