package ru.projectst.rpgcore.item;

import java.util.List;
import java.util.Optional;

/**
 * Ячейка окна снаряжения.
 *
 * <p>Тринадцать ячеек двух родов. Броня и вторая рука — <b>ванильные</b>: окно
 * показывает и меняет настоящую экипировку игрока, поэтому надетый шлем виден
 * на модели и совпадает с обычным инвентарём. Остальные — <b>свои</b>: кольца,
 * амулет, браслет, перчатки и артефакты в ванильной броне не живут и хранятся в
 * данных игрока.
 *
 * <p>Порядок значений — это порядок ячеек в окне и в протоколе
 * ({@code Protocol.GEAR_CELLS}): мод узнаёт ячейку по её номеру. Переставить
 * значение здесь, не переставив там, значит надеть кольцо в шлем; это
 * сторожит тест.
 */
public enum GearCell {

    HELMET("helmet", "Шлем", ItemSlot.HELMET, true),
    CHEST("chest", "Нагрудник", ItemSlot.CHEST, true),
    LEGS("legs", "Поножи", ItemSlot.LEGS, true),
    BOOTS("boots", "Сапоги", ItemSlot.BOOTS, true),
    OFFHAND("offhand", "Вторая рука", ItemSlot.OFFHAND, true),
    RING_LEFT("ring_left", "Левое кольцо", ItemSlot.RING, false),
    RING_RIGHT("ring_right", "Правое кольцо", ItemSlot.RING, false),
    AMULET("amulet", "Амулет", ItemSlot.AMULET, false),
    BRACELET("bracelet", "Браслет", ItemSlot.BRACELET, false),
    GLOVES("gloves", "Перчатки", ItemSlot.GLOVES, false),
    ARTIFACT_1("artifact_1", "Артефакт 1", ItemSlot.ARTIFACT, false),
    ARTIFACT_2("artifact_2", "Артефакт 2", ItemSlot.ARTIFACT, false),
    ARTIFACT_3("artifact_3", "Артефакт 3", ItemSlot.ARTIFACT, false);

    /** Ячейки, которые хранятся в данных игрока, а не в его экипировке. */
    public static final List<GearCell> STORED =
            java.util.Arrays.stream(values()).filter(cell -> !cell.vanilla).toList();

    private final String key;
    private final String display;
    private final ItemSlot slot;
    private final boolean vanilla;

    GearCell(String key, String display, ItemSlot slot, boolean vanilla) {
        this.key = key;
        this.display = display;
        this.slot = slot;
        this.vanilla = vanilla;
    }

    /** Имя ячейки в данных игрока и в протоколе. */
    public String key() {
        return key;
    }

    public String display() {
        return display;
    }

    /** Какой слот предмета сюда идёт. */
    public ItemSlot slot() {
        return slot;
    }

    /** Ячейка — это настоящая экипировка игрока, а не запись в его данных. */
    public boolean vanilla() {
        return vanilla;
    }

    /**
     * Работает ли здесь предмет с таким объявленным слотом.
     *
     * <p>{@link ItemSlot#ANY} работает везде: так он объявлен. Остальные — только
     * в ячейке своего слота: посох в кольце не оружие, и молча давать его
     * надбавки значило бы спрятать ошибку в файле предмета.
     */
    public boolean fits(ItemSlot declared) {
        return declared == slot || declared == ItemSlot.ANY;
    }

    public static Optional<GearCell> byKey(String key) {
        for (GearCell cell : values()) {
            if (cell.key.equals(key)) {
                return Optional.of(cell);
            }
        }
        return Optional.empty();
    }

    /**
     * Куда класть предмет с таким слотом, по порядку предпочтения.
     *
     * <p>Нужен shift-щелчку: игрок не выбирает ячейку, и выбор должен быть
     * предсказуемым. Кольцо идёт сначала в левую ячейку, потом в правую;
     * артефакт — слева направо. Предмет «куда угодно» сначала ищет место
     * артефакта: это вещь, у которой своего места нет, и ровно для таких
     * артефактные ячейки и заведены.
     */
    public static List<GearCell> placesFor(ItemSlot declared) {
        if (declared == ItemSlot.ANY) {
            return List.of(ARTIFACT_1, ARTIFACT_2, ARTIFACT_3,
                    RING_LEFT, RING_RIGHT, AMULET, BRACELET, GLOVES);
        }
        return java.util.Arrays.stream(values()).filter(cell -> cell.slot == declared).toList();
    }
}
