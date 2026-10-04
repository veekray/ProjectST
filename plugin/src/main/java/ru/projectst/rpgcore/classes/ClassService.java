package ru.projectst.rpgcore.classes;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import ru.projectst.rpgcore.balance.BalanceValue;
import ru.projectst.rpgcore.data.PlayerData;
import ru.projectst.rpgcore.data.PlayerDataStore;
import ru.projectst.rpgcore.skill.SkillDef;
import ru.projectst.rpgcore.skill.SkillRegistry;
import ru.projectst.rpgcore.stat.StatService;

/**
 * Классы игроков: выбор класса, изучение навыков, привязка к слотам,
 * базовые статы от класса и уровня.
 *
 * <p>Bukkit здесь не нужен: игрок — это {@link UUID}, данные приходят из
 * хранилища, статы уходят в {@link StatService}. Поэтому правила проверяются
 * юнит-тестами.
 */
public final class ClassService {

    /** Имя источника базовых статов в {@link StatService}. */
    public static final String STAT_SOURCE = "class";

    private final ClassRegistry classes;
    private final SkillRegistry skills;
    private final PlayerDataStore data;
    private final StatService stats;

    public ClassService(ClassRegistry classes, SkillRegistry skills,
                        PlayerDataStore data, StatService stats) {
        this.classes = classes;
        this.skills = skills;
        this.data = data;
        this.stats = stats;
    }

    // ------------------------------------------------------------------ класс

    public Optional<ClassDef> classOf(UUID player) {
        String id = data.load(player).classId();
        return id == null ? Optional.empty() : classes.find(id);
    }

    /**
     * Назначает класс.
     *
     * <p>Изученные навыки и привязки сбрасываются: они принадлежат прежнему
     * классу и в новом бессмысленны. Это решение явное, а не побочный эффект —
     * иначе у игрока остались бы висеть чужие навыки в слотах.
     *
     * @return {@code false}, если такого класса нет
     */
    public boolean setClass(UUID player, String classId) {
        if (!classes.has(classId)) {
            return false;
        }
        PlayerData d = data.load(player);
        if (!classId.equals(d.classId())) {
            d.unlockedSkills().clear();
            d.slotBindings().clear();
        }
        d.setClassId(classId);
        applyBaseStats(player);
        data.saveLater(d);
        return true;
    }

    /**
     * Пересчитывает базовые статы от класса и уровня и отдаёт их в реестр
     * статов одним источником. Зовётся при входе, смене класса и повышении
     * уровня — то есть всюду, где меняется основание.
     */
    public void applyBaseStats(UUID player) {
        PlayerData d = data.load(player);
        Optional<ClassDef> def = classOf(player);
        if (def.isEmpty()) {
            stats.removeSource(player, STAT_SOURCE);
            return;
        }
        for (Map.Entry<String, BalanceValue> entry : def.get().statCurves().entrySet()) {
            stats.setBase(player, entry.getKey(), entry.getValue().at(d.level()));
        }
        // Надбавок от класса нет: он задаёт именно базу. Источник чистим,
        // чтобы при смене класса не осталось чужих значений.
        stats.removeSource(player, STAT_SOURCE);
    }

    public void setLevel(UUID player, int level) {
        PlayerData d = data.load(player);
        d.setLevel(level);
        applyBaseStats(player);
        data.saveLater(d);
    }

    public void grantPoints(UUID player, int points) {
        PlayerData d = data.load(player);
        d.setUnspentPoints(d.unspentPoints() + points);
        data.saveLater(d);
    }

    // ------------------------------------------------------------------ изучение

    public ClassOutcome.Unlock unlock(UUID player, String skillId) {
        PlayerData d = data.load(player);
        Optional<ClassDef> def = classOf(player);
        if (def.isEmpty()) {
            return new ClassOutcome.Unlock(ClassOutcome.Unlock.Kind.NO_CLASS, null);
        }
        Optional<SkillDef> skill = skills.find(skillId);
        if (skill.isEmpty()) {
            return new ClassOutcome.Unlock(ClassOutcome.Unlock.Kind.UNKNOWN_SKILL, skillId);
        }
        if (!skill.get().classId().equals(def.get().id())) {
            return new ClassOutcome.Unlock(ClassOutcome.Unlock.Kind.WRONG_CLASS,
                    "навык принадлежит классу " + skill.get().classId());
        }
        if (d.isUnlocked(skillId)) {
            return new ClassOutcome.Unlock(ClassOutcome.Unlock.Kind.ALREADY_UNLOCKED, null);
        }
        int required = def.get().levelForTier(skill.get().tier());
        if (d.level() < required) {
            return new ClassOutcome.Unlock(ClassOutcome.Unlock.Kind.LEVEL_TOO_LOW,
                    "нужен уровень " + required + ", текущий " + d.level());
        }
        if (d.unspentPoints() < 1) {
            return new ClassOutcome.Unlock(ClassOutcome.Unlock.Kind.NO_POINTS, null);
        }

        d.setUnspentPoints(d.unspentPoints() - 1);
        d.unlockedSkills().add(skillId);
        data.saveLater(d);
        return new ClassOutcome.Unlock(ClassOutcome.Unlock.Kind.UNLOCKED, null);
    }

    // ------------------------------------------------------------------ слоты

    public ClassOutcome.Bind bind(UUID player, int slot, String skillId) {
        PlayerData d = data.load(player);
        Optional<ClassDef> def = classOf(player);
        if (def.isEmpty()) {
            return new ClassOutcome.Bind(ClassOutcome.Bind.Kind.NO_CLASS, null);
        }
        if (slot < 1 || slot > def.get().slots()) {
            return new ClassOutcome.Bind(ClassOutcome.Bind.Kind.BAD_SLOT,
                    "у класса слотов: " + def.get().slots());
        }
        if (!skills.has(skillId)) {
            return new ClassOutcome.Bind(ClassOutcome.Bind.Kind.UNKNOWN_SKILL, skillId);
        }
        if (!d.isUnlocked(skillId)) {
            return new ClassOutcome.Bind(ClassOutcome.Bind.Kind.NOT_UNLOCKED, skillId);
        }

        String previous = d.slotBindings().get(slot);
        d.bind(slot, skillId);
        data.saveLater(d);
        return previous == null
                ? new ClassOutcome.Bind(ClassOutcome.Bind.Kind.BOUND, null)
                : new ClassOutcome.Bind(ClassOutcome.Bind.Kind.REPLACED, previous);
    }

    public Optional<SkillDef> skillInSlot(UUID player, int slot) {
        String id = data.load(player).slotBindings().get(slot);
        return id == null ? Optional.empty() : skills.find(id);
    }
}
