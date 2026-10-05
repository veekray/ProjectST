package ru.projectst.rpgcore.data;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Сохраняемые данные игрока.
 *
 * <p>Это единственное место в проекте, где данные изменяемы, а не {@code record}.
 * Причина простая: они меняются постоянно — каждый уровень, каждая
 * разблокировка, каждая перепривязка слота. Пересобирать record на каждое
 * изменение значило бы плодить мусор и путаться, какая копия актуальна.
 *
 * <p>Статусы здесь не хранятся намеренно: они транзиентны по смыслу. Стан,
 * переживший перезапуск сервера, — это баг, а не фича.
 */
public final class PlayerData {

    /**
     * Версия схемы. Увеличивается при несовместимом изменении полей; старые
     * файлы поднимаются в {@link PlayerDataCodec}.
     */
    public static final int SCHEMA_VERSION = 2;

    private final UUID uuid;
    private String classId;
    private int level;
    private double xp;
    private int unspentPoints;
    private final Set<String> unlockedSkills = new LinkedHashSet<>();
    private final Map<Integer, String> slotBindings = new LinkedHashMap<>();
    private final Map<String, Integer> skillLevels = new LinkedHashMap<>();

    public PlayerData(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid обязателен");
        }
        this.uuid = uuid;
        this.level = 1;
    }

    public UUID uuid() {
        return uuid;
    }

    public String classId() {
        return classId;
    }

    public void setClassId(String classId) {
        this.classId = classId;
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        if (level < 1) {
            throw new IllegalArgumentException("уровень не меньше 1");
        }
        this.level = level;
    }

    public double xp() {
        return xp;
    }

    public void setXp(double xp) {
        this.xp = Math.max(0, xp);
    }

    public int unspentPoints() {
        return unspentPoints;
    }

    public void setUnspentPoints(int points) {
        this.unspentPoints = Math.max(0, points);
    }

    public Set<String> unlockedSkills() {
        return unlockedSkills;
    }

    public boolean isUnlocked(String skillId) {
        return unlockedSkills.contains(skillId);
    }

    /**
     * Уровни изученных навыков. Запись есть только у поднятых выше первого:
     * изученный навык без записи — это первый уровень. Так отсутствие записи
     * нигде не означает «ноль», и забытая миграция не обнулила бы вложенные
     * очки молча.
     */
    public Map<String, Integer> skillLevels() {
        return skillLevels;
    }

    /** Уровень навыка; ноль означает «не изучен». */
    public int skillLevel(String skillId) {
        if (!isUnlocked(skillId)) {
            return 0;
        }
        return skillLevels.getOrDefault(skillId, 1);
    }

    public void setSkillLevel(String skillId, int level) {
        if (level < 1) {
            throw new IllegalArgumentException("уровень навыка не меньше 1");
        }
        if (level == 1) {
            skillLevels.remove(skillId);
        } else {
            skillLevels.put(skillId, level);
        }
    }

    /** Привязки слотов: номер слота → id навыка. */
    public Map<Integer, String> slotBindings() {
        return slotBindings;
    }

    /**
     * Привязывает навык к слоту. Разблокировка не проверяется здесь: это
     * правило класса, а не хранилища, и живёт в M8.
     */
    public void bind(int slot, String skillId) {
        if (slot < 1) {
            throw new IllegalArgumentException("номер слота не меньше 1");
        }
        slotBindings.put(slot, skillId);
    }

    public void unbind(int slot) {
        slotBindings.remove(slot);
    }
}
