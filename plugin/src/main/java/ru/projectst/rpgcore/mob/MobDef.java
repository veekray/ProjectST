package ru.projectst.rpgcore.mob;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ru.projectst.rpgcore.stat.StatModifier;
import ru.projectst.rpgcore.stat.StatOp;

/**
 * Моб как объявление.
 *
 * <p>Ванильный тип, здоровье, урон, статы, навыки и дроп. В мире живёт обычное
 * существо с нашей меткой: по ней моб узнаётся после перезапуска, в отличие от
 * имени, которое меняет первый же бафф или перевод.
 *
 * @param id        идентификатор в нижнем регистре, он же имя файла
 * @param display   имя над головой
 * @param entityType ванильный тип существа
 * @param health    запас здоровья
 * @param damage    урон от обычного удара; ноль — оставить штатный
 * @param stats     надбавки к статам: по ним конвейер урона считает защиту
 * @param skills    навыки по триггерам
 * @param drops     дроп
 * @param experience сколько опыта даёт; отрицательное — оставить штатный
 * @param glowing   светится ли: заметные мобы нужны на арене и в подземелье
 * @param baby      детёныш, где тип это поддерживает
 */
public record MobDef(String id, String display, String entityType, double health, double damage,
                     Map<String, Double> stats, List<MobSkill> skills, List<MobDrop> drops,
                     int experience, boolean glowing, boolean baby) {

    public MobDef {
        if (id == null || !id.equals(id.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("id моба должен быть в нижнем регистре: " + id);
        }
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("у моба обязателен тип существа: " + id);
        }
        if (health <= 0) {
            throw new IllegalArgumentException("здоровье моба должно быть положительным: " + id);
        }
        stats = Collections.unmodifiableMap(new LinkedHashMap<>(
                stats == null ? Map.of() : stats));
        skills = skills == null ? List.of() : List.copyOf(skills);
        drops = drops == null ? List.of() : List.copyOf(drops);
    }

    /** Надбавки моба как источник для реестра статов. */
    public List<StatModifier> modifiers(String source) {
        List<StatModifier> out = new java.util.ArrayList<>();
        stats.forEach((statId, value) ->
                out.add(new StatModifier(statId, StatOp.FLAT, value, source)));
        return out;
    }

    /** Навыки, которые запускает этот триггер. */
    public List<MobSkill> skillsOn(MobTrigger trigger) {
        List<MobSkill> out = new java.util.ArrayList<>();
        for (MobSkill skill : skills) {
            if (skill.trigger() == trigger) {
                out.add(skill);
            }
        }
        return out;
    }
}
