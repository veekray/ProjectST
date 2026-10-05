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

    /** Выше этого уровня навык не поднять. */
    public static final int MAX_SKILL_LEVEL = 5;

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
            d.skillLevels().clear();
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

    /**
     * Начисляет опыт и поднимает уровни, пока его хватает.
     *
     * <p>Цикл, а не один уровень за вызов: крупная награда обязана поднять
     * сразу на столько, на сколько её хватило. Иначе излишек терялся бы молча,
     * и объяснить игроку, куда он делся, было бы нечем.
     *
     * <p>На пределе уровня опыт не копится совсем. Копить его «в запас» значило
     * бы, что поднятый позже предел выдаёт пачку уровней сразу, и отличить это
     * от ошибки будет невозможно.
     */
    public ClassOutcome.Experience addExperience(UUID player, double amount) {
        PlayerData d = data.load(player);
        Optional<ClassDef> def = classOf(player);
        if (def.isEmpty()) {
            return new ClassOutcome.Experience(0, 0, d.level(), d.xp(), false);
        }
        ClassDef c = def.get();
        if (d.level() >= c.maxLevel()) {
            return new ClassOutcome.Experience(0, 0, d.level(), 0, true);
        }
        if (amount <= 0) {
            return new ClassOutcome.Experience(0, 0, d.level(), d.xp(), false);
        }

        double pool = d.xp() + amount;
        int levels = 0;
        int level = d.level();
        while (level < c.maxLevel() && pool >= c.xpToNext(level)) {
            pool -= c.xpToNext(level);
            level++;
            levels++;
        }
        boolean atMax = level >= c.maxLevel();
        if (atMax) {
            pool = 0;
        }

        int points = levels * c.pointsPerLevel();
        d.setXp(pool);
        if (levels > 0) {
            d.setLevel(level);
            d.setUnspentPoints(d.unspentPoints() + points);
            applyBaseStats(player);
        }
        data.saveLater(d);
        return new ClassOutcome.Experience(levels, points, level, pool, atMax);
    }

    /**
     * Данные игрока для показа: уровень, опыт, очки, слоты.
     *
     * <p>Отдаётся тот же объект, что живёт в хранилище, а не копия. Менять его
     * мимо этого сервиса нельзя — иначе правило окажется в двух местах, и одно
     * из них однажды забудут.
     */
    public PlayerData snapshot(UUID player) {
        return data.load(player);
    }

    /** Сколько опыта осталось до следующего уровня; ноль на пределе. */
    public double xpToNextLevel(UUID player) {
        PlayerData d = data.load(player);
        return classOf(player)
                .filter(c -> d.level() < c.maxLevel())
                .map(c -> c.xpToNext(d.level()) - d.xp())
                .orElse(0.0);
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
        if (skill.get().internal()) {
            return new ClassOutcome.Unlock(ClassOutcome.Unlock.Kind.WRONG_CLASS,
                    "служебный навык класса, его не изучают");
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

    /** Изученные навыки игрока: по ним ищут пассивки для срабатывания. */
    public java.util.Collection<String> unlockedSkills(UUID player) {
        return java.util.List.copyOf(data.load(player).unlockedSkills());
    }

    /** Уровень навыка у игрока; ноль означает «не изучен». */
    public int skillLevel(UUID player, String skillId) {
        return data.load(player).skillLevel(skillId);
    }

    /**
     * Вкладывает очко в уровень уже изученного навыка.
     *
     * <p>Уровень — это то, по чему разворачиваются кривые баланса, поэтому
     * вложение очка видно прямо в уроне, а не только в описании.
     */
    public ClassOutcome.Upgrade upgrade(UUID player, String skillId) {
        PlayerData d = data.load(player);
        if (classOf(player).isEmpty()) {
            return new ClassOutcome.Upgrade(ClassOutcome.Upgrade.Kind.NO_CLASS, null);
        }
        if (!skills.has(skillId)) {
            return new ClassOutcome.Upgrade(ClassOutcome.Upgrade.Kind.UNKNOWN_SKILL, skillId);
        }
        int level = d.skillLevel(skillId);
        if (level == 0) {
            return new ClassOutcome.Upgrade(ClassOutcome.Upgrade.Kind.NOT_UNLOCKED, skillId);
        }
        if (level >= MAX_SKILL_LEVEL) {
            return new ClassOutcome.Upgrade(ClassOutcome.Upgrade.Kind.MAX_LEVEL,
                    "максимум " + MAX_SKILL_LEVEL);
        }
        if (d.unspentPoints() < 1) {
            return new ClassOutcome.Upgrade(ClassOutcome.Upgrade.Kind.NO_POINTS, null);
        }

        d.setUnspentPoints(d.unspentPoints() - 1);
        d.setSkillLevel(skillId, level + 1);
        data.saveLater(d);
        return new ClassOutcome.Upgrade(ClassOutcome.Upgrade.Kind.UPGRADED,
                "уровень " + (level + 1));
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
        // Пассивный навык в слоте — классическое «неправильное использование,
        // неотличимое от правильного»: слот занят, кнопка не делает ничего.
        Optional<SkillDef> bound = skills.find(skillId);
        if (bound.isPresent() && bound.get().internal()) {
            return new ClassOutcome.Bind(ClassOutcome.Bind.Kind.PASSIVE_SKILL,
                    "служебный навык класса");
        }
        if (bound.isPresent() && bound.get().passive()) {
            return new ClassOutcome.Bind(ClassOutcome.Bind.Kind.PASSIVE_SKILL,
                    "навык срабатывает сам: " + bound.get().trigger().name().toLowerCase(
                            java.util.Locale.ROOT));
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
