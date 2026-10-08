package ru.projectst.rpgcore.client;

import java.util.Map;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ru.projectst.rpgcore.net.Protocol;

/**
 * Окно снаряжения на стороне клиента: те же ячейки, что у сервера, но на своих
 * местах.
 *
 * <p><b>Почему своё меню, а не переставленные ячейки сундука.</b> Сервер
 * открывает обычный контейнер на два ряда, и ванильный клиент строит под него
 * сундук, ячейки которого стоят сеткой. Координаты ячейки в ванилле
 * неизменяемы, и переставить их можно только правкой чужого класса — через
 * access transformer или миксин. Вместо этого мод строит своё меню с тем же
 * номером окна и тем же порядком ячеек (снаряжение, затем сумка, затем хотбар,
 * ровно как у сундука) — и сервер с клиентом говорят об одних и тех же номерах,
 * а ячейки при этом стоят там, где их ждёт раскладка. Чужой код не тронут.
 *
 * <p><b>Клиент ничего не решает.</b> Shift-щелчок здесь не делает ничего: куда
 * надеть вещь, решает сервер, и ответ приходит обычным обновлением ячеек.
 * Предсказание клиента осталось только у простого щелчка — чтобы вещь на
 * курсоре не замирала на время ответа; если сервер откажет, он сам вернёт всё
 * на место.
 */
public final class GearMenu extends AbstractContainerMenu {

    /** Сторона гнезда ячейки снаряжения: значок 16 и по три пикселя воздуха. */
    public static final int SOCKET = 22;
    /** Шаг гнёзд: два пикселя между ними, чтобы рамки не слипались. */
    private static final int STEP = 24;

    // Раскладка в координатах окна (угол окна — ноль). Окно того же размера,
    // что и книга героя: переход по закладке не должен двигать края.

    /** Столбцы колец, брони и перчаток. */
    private static final int COL_RINGS = 32;
    private static final int COL_HAND = 66;
    private static final int COL_ARMOR = COL_HAND + STEP;
    private static final int COL_GLOVES = COL_ARMOR + STEP;
    /** Ряды: шлем стоит над нагрудником, кольца начинаются с нагрудника. */
    private static final int ROW_0 = 66;

    /** Ряд артефактов под куклой. */
    public static final int ARTIFACT_ROW = 186;
    private static final int ARTIFACT_LEFT = 83;

    /** Ниша куклы: справа от столбца перчаток, высотой с четыре ряда. */
    public static final int DOLL_X = COL_GLOVES + SOCKET + 10;
    public static final int DOLL_Y = ROW_0;
    public static final int DOLL_WIDTH = 60;
    public static final int DOLL_HEIGHT = STEP * 4 - 2;

    /** Сумка: правая колонка, хотбар прижат к нижнему краю поля. */
    public static final int BAG_X = 232;
    public static final int BAG_Y = 148;
    public static final int HOTBAR_Y = 206;

    /**
     * Что за ячейка, как её назвать и что в неё класть.
     *
     * @param accepts что сюда идёт, словами — для подсказки пустой ячейки
     */
    public record Cell(String key, String display, String accepts, int x, int y) {
    }

    /**
     * Ячейки по имени из протокола.
     *
     * <p>По имени, а не по номеру: номер берётся из {@link Protocol#GEAR_CELLS},
     * и если сервер однажды пришлёт ячейку, которой мод не знает, она просто не
     * будет нарисована — а не встанет на место соседней.
     */
    private static final Map<String, Cell> CELLS = Map.ofEntries(
            cell("helmet", "Шлем", "шлем", COL_ARMOR, 0),
            cell("chest", "Нагрудник", "нагрудник", COL_ARMOR, 1),
            cell("legs", "Поножи", "поножи", COL_ARMOR, 2),
            cell("boots", "Сапоги", "сапоги", COL_ARMOR, 3),
            cell("offhand", "Вторая рука", "щит, факел — что угодно", COL_HAND, 1),
            cell("bracelet", "Браслет", "браслет", COL_HAND, 2),
            cell("gloves", "Перчатки", "перчатки", COL_GLOVES, 1),
            cell("ring_left", "Левое кольцо", "кольцо", COL_RINGS, 1),
            cell("amulet", "Амулет", "амулет", COL_RINGS, 2),
            cell("ring_right", "Правое кольцо", "кольцо", COL_RINGS, 3),
            Map.entry("artifact_1", new Cell("artifact_1", "Артефакт 1", "артефакт",
                    ARTIFACT_LEFT, ARTIFACT_ROW)),
            Map.entry("artifact_2", new Cell("artifact_2", "Артефакт 2", "артефакт",
                    ARTIFACT_LEFT + STEP, ARTIFACT_ROW)),
            Map.entry("artifact_3", new Cell("artifact_3", "Артефакт 3", "артефакт",
                    ARTIFACT_LEFT + STEP * 2, ARTIFACT_ROW)));

    private final Container cells = new SimpleContainer(Protocol.GEAR_SIZE);

    public GearMenu(int containerId, Inventory inventory) {
        // Тип — тот, что прислал сервер: два ряда по девять. Для клиента он
        // только имя, а держать его тем же честнее, чем выдумывать свой.
        super(MenuType.GENERIC_9x2, containerId);

        for (int i = 0; i < Protocol.GEAR_SIZE; i++) {
            Cell cell = i < Protocol.GEAR_CELLS.size() ? CELLS.get(Protocol.GEAR_CELLS.get(i)) : null;
            addSlot(cell == null ? new HiddenSlot(cells, i) : new CellSlot(cells, i, cell));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9,
                        BAG_X + col * 18 + 1, BAG_Y + row * 18 + 1));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, BAG_X + col * 18 + 1, HOTBAR_Y + 1));
        }
    }

    private static Map.Entry<String, Cell> cell(String key, String display, String accepts,
                                                int x, int row) {
        return Map.entry(key, new Cell(key, display, accepts, x, ROW_0 + row * STEP));
    }

    /** Куда надеть — решает сервер; клиент ничего не предсказывает. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /**
     * Протяжка по ячейкам снаряжения выключена.
     *
     * <p>Курсор, задевший ячейку при нажатии, превращает щелчок в протяжку по
     * одной ячейке, а протяжку сервер раскладывает как по сундуку — стопкой.
     * Без ячеек в протяжке такое нажатие остаётся обычным щелчком, и вещь
     * надевается как надо. По сумке тянуть можно, как всегда.
     */
    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof CellSlot) && slot.isActive();
    }

    /** Двойной щелчок не собирает вещи со снаряжения: снятие — отдельное дело. */
    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != cells;
    }

    /** Ячейка снаряжения на своём месте раскладки. */
    public static final class CellSlot extends Slot {

        private final Cell cell;

        CellSlot(Container container, int index, Cell cell) {
            super(container, index, cell.x() + (SOCKET - 16) / 2, cell.y() + (SOCKET - 16) / 2);
            this.cell = cell;
        }

        public Cell cell() {
            return cell;
        }

        /**
         * Одна вещь на ячейку, стопка — только во второй руке.
         *
         * <p>То же правило, что у сервера. Здесь оно только для того, чтобы
         * предсказание щелчка совпало с ответом и вещь не прыгала.
         */
        @Override
        public int getMaxStackSize() {
            return cell.key().equals("offhand") ? super.getMaxStackSize() : 1;
        }
    }

    /**
     * Ячейка-заполнитель: контейнер бывает только рядами по девять, а ячеек
     * тринадцать. Не рисуется, не принимает и не отдаёт.
     */
    private static final class HiddenSlot extends Slot {

        HiddenSlot(Container container, int index) {
            super(container, index, -10000, -10000);
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
