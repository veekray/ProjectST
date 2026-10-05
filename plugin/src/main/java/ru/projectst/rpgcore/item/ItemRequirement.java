package ru.projectst.rpgcore.item;

/**
 * Кому предмет разрешён.
 *
 * <p>Отказ всегда назван: предмет, который надет и не даёт надбавок, без
 * объяснения отличить от сломанного нельзя.
 *
 * @param classId требуемый класс; {@code null} — любой
 * @param level   требуемый уровень; ноль — без требования
 */
public record ItemRequirement(String classId, int level) {

    public static final ItemRequirement NONE = new ItemRequirement(null, 0);

    public ItemRequirement {
        if (level < 0) {
            throw new IllegalArgumentException("уровень требования не может быть отрицательным");
        }
    }

    public boolean any() {
        return classId == null && level == 0;
    }

    /**
     * Почему предмет не работает у этого игрока.
     *
     * @return пустая строка, если всё в порядке; иначе причина для показа
     */
    public String refusal(String playerClass, int playerLevel) {
        if (classId != null && !classId.equals(playerClass)) {
            return "только для класса " + classId;
        }
        if (level > 0 && playerLevel < level) {
            return "нужен уровень " + level;
        }
        return "";
    }
}
