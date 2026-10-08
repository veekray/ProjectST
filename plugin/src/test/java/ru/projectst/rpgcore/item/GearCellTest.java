package ru.projectst.rpgcore.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.projectst.rpgcore.net.Protocol;

class GearCellTest {

    @Test
    @DisplayName("порядок ячеек совпадает с протоколом: иначе кольцо надевалось бы в шлем")
    void orderMatchesProtocol() {
        List<String> keys = Arrays.stream(GearCell.values()).map(GearCell::key).toList();

        assertEquals(Protocol.GEAR_CELLS, keys);
        assertTrue(Protocol.GEAR_SIZE >= keys.size(), "контейнер меньше числа ячеек");
        assertEquals(0, Protocol.GEAR_SIZE % 9, "ванильный контейнер бывает только рядами по девять");
    }

    @Test
    @DisplayName("кольцо идёт в обе ячейки колец: сначала левая, потом правая")
    void ringsGoLeftThenRight() {
        assertEquals(List.of(GearCell.RING_LEFT, GearCell.RING_RIGHT),
                GearCell.placesFor(ItemSlot.RING));
        assertEquals(List.of(GearCell.ARTIFACT_1, GearCell.ARTIFACT_2, GearCell.ARTIFACT_3),
                GearCell.placesFor(ItemSlot.ARTIFACT));
    }

    @Test
    @DisplayName("вещь «куда угодно» работает в любой ячейке, остальные — только в своей")
    void anyFitsEverywhere() {
        for (GearCell cell : GearCell.values()) {
            assertTrue(cell.fits(ItemSlot.ANY), cell.key());
        }
        assertTrue(GearCell.AMULET.fits(ItemSlot.AMULET));
        assertFalse(GearCell.AMULET.fits(ItemSlot.RING), "кольцо на шее не кольцо");
        assertFalse(GearCell.HELMET.fits(ItemSlot.HAND), "посох в шлеме не оружие");
    }

    @Test
    @DisplayName("в своих ячейках — всё, кроме брони и второй руки")
    void storedCellsAreNotVanilla() {
        assertEquals(8, GearCell.STORED.size());
        assertTrue(GearCell.STORED.stream().noneMatch(GearCell::vanilla));
        assertTrue(GearCell.byKey("gloves").isPresent());
        assertTrue(GearCell.byKey("artifact_4").isEmpty(), "четвёртой ячейки артефакта больше нет");
    }
}
