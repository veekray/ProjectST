package ru.projectst.rpgcore.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Формат канала.
 *
 * <p>Этот тест — единственная гарантия, что сервер и мод читают одно и то же.
 * Проверять согласие двух сторон вручную нельзя: сдвиг на одно поле проявляется
 * как бессмыслица во всех последующих числах, без всякого указания на причину.
 */
class StateCodecTest {

    private static final ClientState SAMPLE = new ClientState(
            "Выносливость", 73.5, 120, 17, "&8Плут",
            List.of(new ClientState.StatusLine("ambush", "Из тени", 1, 40, "BUFF", "GRAY"),
                    new ClientState.StatusLine("stun", "Оглушение", 1, 20, "CONTROL", "RED")),
            List.of(new ClientState.CooldownLine("rogue_dash", 35, 120)),
            List.of(new ClientState.SlotLine(1, "rogue_dash", "Рывок", "SUGAR"),
                    new ClientState.SlotLine(2, "", "", "")),
            List.of(new ClientState.CounterLine("mark_stacks", "Удары по метке", 7, 20, "RED")));

    @Test
    @DisplayName("состояние проходит туда и обратно без потерь")
    void stateRoundTrip() {
        ClientState back = StateCodec.readState(StateCodec.writeState(SAMPLE));

        assertEquals(SAMPLE.resourceName(), back.resourceName());
        assertEquals(SAMPLE.resource(), back.resource(), 0.01);
        assertEquals(SAMPLE.resourceMax(), back.resourceMax(), 0.01);
        assertEquals(SAMPLE.level(), back.level());
        assertEquals(SAMPLE.className(), back.className());
        assertEquals(SAMPLE.statuses(), back.statuses());
        assertEquals(SAMPLE.cooldowns(), back.cooldowns());
        assertEquals(SAMPLE.slots(), back.slots());
        assertEquals(SAMPLE.counters(), back.counters());
    }

    @Test
    @DisplayName("счётчики ядра приходят отдельно от статусов и со своим пределом")
    void countersComeSeparately() {
        ClientState back = StateCodec.readState(StateCodec.writeState(SAMPLE));

        assertEquals(1, back.counters().size());
        ClientState.CounterLine counter = back.counters().get(0);
        assertEquals("Удары по метке", counter.display(),
                "в бою игрок читает имя, а не идентификатор");
        assertEquals(7, counter.stacks());
        assertEquals(20, counter.maxStacks(), "без предела не нарисовать деления");
        assertTrue(back.statuses().stream().noneMatch(s -> s.id().equals("mark_stacks")),
                "счётчик не должен дублироваться в списке статусов");
    }

    @Test
    @DisplayName("данные меню проходят туда и обратно")
    void menuRoundTrip() {
        MenuData menu = new MenuData("rogue", 17, 120.5, 240, 3, 6,
                List.of(new MenuData.ClassLine("rogue", "&8Плут", "LEATHER_BOOTS",
                        "Выносливость", 6, 60)),
                List.of(new MenuData.SkillLine("rogue_dash", "Рывок", "SUGAR", 1, 2, 5, 1,
                        10, 4, 6.0, 1, 12.5,
                        List.of("Рывок по взгляду.", "Дёшево и быстро."))),
                List.of(new MenuData.StatLine("physical_damage", "Физический урон", 12.5),
                        new MenuData.StatLine("physical_defense", "Физическая защита", 150,
                                "режет 60% урона")),
                List.of(new MenuData.ArtifactLine(1, "ring_of_dusk", "Перстень сумерек",
                                "GOLD_INGOT", "LIGHT_PURPLE",
                                List.of("+12 physical_damage", "×1.2 critical_strike_power"),
                                ""),
                        new MenuData.ArtifactLine(2, "", "", "", "", List.of(), ""),
                        new MenuData.ArtifactLine(3, "seal_of_order", "Печать порядка",
                                "PAPER", "GOLD", List.of("+40 max_mana"),
                                "только для класса mage")));

        MenuData back = StateCodec.readMenu(StateCodec.writeMenu(menu));

        assertEquals(menu.classId(), back.classId());
        assertEquals(menu.artifacts(), back.artifacts(),
                "ячейки артефактов: и занятые, и пустые, и причина, по которой не действует");
        assertEquals(menu.level(), back.level());
        assertEquals(menu.points(), back.points());
        assertEquals(menu.slots(), back.slots());
        assertEquals(menu.classes(), back.classes());
        assertEquals(menu.skills(), back.skills());
        assertEquals(12.5, back.skills().get(0).damage(), 0.01,
                "урон считает сервер: подсказка и бой обязаны показывать одно число");
        assertEquals(2, back.skills().get(0).description().size());
        assertEquals(menu.stats(), back.stats());
    }

    @Test
    @DisplayName("действие клиента читается: что просят, какой слот, какой навык")
    void actionRoundTrip() {
        var request = StateCodec.readAction(
                StateCodec.writeAction(Protocol.Action.BIND, 3, "rogue_dash"));

        assertEquals(Protocol.Action.BIND, request.action());
        assertEquals(3, request.number());
        assertEquals("rogue_dash", request.id());
    }

    @Test
    @DisplayName("действие чужой версии отвергается, а не исполняется наугад")
    void actionOfForeignVersionIsRefused() {
        byte[] action = StateCodec.writeAction(Protocol.Action.UNLOCK, 0, "mage_collapse");
        action[0] = (byte) (Protocol.VERSION + 1);

        assertThrows(IllegalArgumentException.class, () -> StateCodec.readAction(action));
    }

    @Test
    @DisplayName("кириллица и цветовые коды не портятся")
    void cyrillicSurvives() {
        ClientState back = StateCodec.readState(StateCodec.writeState(SAMPLE));

        assertEquals("Выносливость", back.resourceName());
        assertEquals("&8Плут", back.className());
        assertEquals("Рывок", back.slots().get(0).display());
    }

    @Test
    @DisplayName("пустое состояние тоже читается: класс может быть не выбран")
    void emptyStateRoundTrip() {
        ClientState empty = new ClientState("", 0, 0, 1, "", List.of(), List.of(),
                List.of(), List.of());

        ClientState back = StateCodec.readState(StateCodec.writeState(empty));

        assertEquals(1, back.level());
        assertTrue(back.statuses().isEmpty());
        assertTrue(back.slots().isEmpty());
    }

    @Test
    @DisplayName("версия стоит в первом байте каждого сообщения")
    void versionIsTheFirstByte() {
        byte[] state = StateCodec.writeState(SAMPLE);
        byte[] welcome = StateCodec.writeWelcome(Protocol.Handshake.ACCEPTED);

        assertEquals(Protocol.VERSION, state[0]);
        assertEquals(Protocol.VERSION, welcome[0]);
    }

    @Test
    @DisplayName("чужая версия отвергается словами, а не читается как своя")
    void foreignVersionIsRefused() {
        byte[] state = StateCodec.writeState(SAMPLE);
        state[0] = (byte) (Protocol.VERSION + 1);

        var error = assertThrows(IllegalArgumentException.class,
                () -> StateCodec.readState(state));

        assertTrue(error.getMessage().contains("версия"), error.getMessage());
    }

    @Test
    @DisplayName("обрезанное сообщение не читается молча")
    void truncatedMessageFails() {
        byte[] full = StateCodec.writeState(SAMPLE);
        byte[] cut = java.util.Arrays.copyOf(full, full.length / 2);

        assertThrows(IllegalArgumentException.class, () -> StateCodec.readState(cut));
    }

    @Test
    @DisplayName("рукопожатие переносит версию и строку версии мода")
    void helloRoundTrip() {
        StateCodec.Hello hello = StateCodec.readHello(
                StateCodec.writeHello(Protocol.VERSION, "1.0.0-beta"));

        assertEquals(Protocol.VERSION, hello.version());
        assertEquals("1.0.0-beta", hello.modVersion());
    }

    @Test
    @DisplayName("исход рукопожатия называет, кого обновлять")
    void handshakeNamesWhoToUpdate() {
        assertEquals(Protocol.Handshake.ACCEPTED, Protocol.decide(Protocol.VERSION));
        assertEquals(Protocol.Handshake.MOD_TOO_OLD, Protocol.decide(Protocol.VERSION - 1));
        assertEquals(Protocol.Handshake.MOD_TOO_NEW, Protocol.decide(Protocol.VERSION + 1));

        var welcome = StateCodec.readWelcome(
                StateCodec.writeWelcome(Protocol.Handshake.MOD_TOO_NEW));
        assertEquals(Protocol.Handshake.MOD_TOO_NEW, welcome.outcome());
        assertEquals(Protocol.VERSION, welcome.version());
    }

    @Test
    @DisplayName("одинаковые состояния равны: на этом держится отправка только изменений")
    void equalStatesAreEqual() {
        ClientState copy = new ClientState("Выносливость", 73.5, 120, 17, "&8Плут",
                SAMPLE.statuses(), SAMPLE.cooldowns(), SAMPLE.slots(), SAMPLE.counters());

        assertEquals(SAMPLE, copy);

        ClientState other = new ClientState("Выносливость", 73.4, 120, 17, "&8Плут",
                copy.statuses(), copy.cooldowns(), copy.slots(), copy.counters());
        assertNotEquals(SAMPLE, other, "иначе изменение запаса не ушло бы моду");
    }

    @Test
    @DisplayName("слишком длинная строка обрезается, а не роняет отправку")
    void longStringsAreTrimmed() {
        String huge = "я".repeat(5000);
        ClientState state = new ClientState(huge, 1, 1, 1, huge,
                List.of(), List.of(), List.of(), List.of());

        ClientState back = StateCodec.readState(StateCodec.writeState(state));

        assertTrue(back.resourceName().length() < huge.length(),
                "обрезано, но сообщение прочиталось целиком");
    }
}
