package ru.projectst.rpgcore.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Сверка статусов на существах с тем, что игрок уже видел.
 *
 * <p>Корни на ногах цели держатся, пока висит статус, и уходят, когда он снят.
 * Значит, мод обязан узнать и о наложении, и о снятии, и о продлении, а тот,
 * кто подошёл позже, — получить статус с остатком, а не с полным сроком.
 */
class StatusFeedTest {

    private static final UUID VIEWER = UUID.randomUUID();
    private static final int TARGET = 42;

    private final StatusFeed feed = new StatusFeed();

    private static StatusFeed.Seen root(long expiresAt) {
        return new StatusFeed.Seen("root", 7, 30, expiresAt, 1);
    }

    @Test
    @DisplayName("наложение — StatusOn с остатком, повтор без изменений — тишина")
    void onceThenQuiet() {
        List<FxMessage.Event> first = feed.diff(VIEWER, Map.of(TARGET, List.of(root(130))), 100);
        assertEquals(List.of(new FxMessage.StatusOn(TARGET, "root", 7, 30, 30, 1)), first);

        assertTrue(feed.diff(VIEWER, Map.of(TARGET, List.of(root(130))), 101).isEmpty(),
                "без изменений пакетов нет: иначе каждый тик гнал бы все статусы заново");
    }

    @Test
    @DisplayName("подошедший позже получает остаток срока, а не полный")
    void lateViewerGetsRemainder() {
        List<FxMessage.Event> late = feed.diff(VIEWER, Map.of(TARGET, List.of(root(130))), 120);

        assertEquals(List.of(new FxMessage.StatusOn(TARGET, "root", 7, 30, 10, 1)), late);
    }

    @Test
    @DisplayName("продление — новое StatusOn, снятие и истечение — StatusOff")
    void renewAndRemove() {
        feed.diff(VIEWER, Map.of(TARGET, List.of(root(130))), 100);

        List<FxMessage.Event> renewed =
                feed.diff(VIEWER, Map.of(TARGET, List.of(root(160))), 110);
        assertEquals(1, renewed.size());
        assertEquals(50, ((FxMessage.StatusOn) renewed.get(0)).remainingTicks());

        assertEquals(List.of(new FxMessage.StatusOff(TARGET, "root")),
                feed.diff(VIEWER, Map.of(TARGET, List.of()), 111), "снят навыком");

        feed.diff(VIEWER, Map.of(TARGET, List.of(root(130))), 112);
        assertEquals(List.of(new FxMessage.StatusOff(TARGET, "root")),
                feed.diff(VIEWER, Map.of(TARGET, List.of(root(130))), 130), "истёк по сроку");
    }

    @Test
    @DisplayName("существо пропало из виду — его статусы сняты")
    void goneEntityIsCleared() {
        feed.diff(VIEWER, Map.of(TARGET, List.of(root(130))), 100);

        assertEquals(List.of(new FxMessage.StatusOff(TARGET, "root")),
                feed.diff(VIEWER, Map.of(), 101));
    }
}
