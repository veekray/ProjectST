package ru.projectst.rpgcore.stat;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Расчёт итоговых значений статов. Чистая арифметика, Bukkit здесь не нужен,
 * поэтому модуль целиком покрыт юнит-тестами.
 *
 * <p>Порядок строго такой и описан в SPEC:
 * <pre>
 *   база → сумма FLAT → сумма PERCENT → произведение MULT → min/max → округление
 * </pre>
 *
 * <p>Почему именно так. Если PERCENT применять до FLAT, «плоское +10» и «+100%»
 * дают разный результат в зависимости от порядка объявления надбавок, то есть от
 * того, в каком порядке надели предметы. Это и есть то самое «механики
 * накладываются непредсказуемо», от которого затевался проект.
 */
public final class StatEngine {

    private final StatRegistry registry;

    public StatEngine(StatRegistry registry) {
        this.registry = registry;
    }

    /**
     * Считает значение одного стата.
     *
     * @param statId    стат
     * @param base      база; если {@code null}, берётся из определения стата
     * @param modifiers надбавки из всех источников, порядок значения не имеет
     */
    public double compute(String statId, Double base, Collection<StatModifier> modifiers) {
        StatDef def = registry.find(statId).orElseThrow(
                () -> new IllegalArgumentException("стат не объявлен: " + statId));

        double value = base != null ? base : def.base();
        double percent = 0;
        double mult = 1;

        for (StatModifier m : modifiers) {
            if (!m.statId().equals(statId)) {
                continue;
            }
            switch (m.op()) {
                case FLAT -> value += m.value();
                case PERCENT -> percent += m.value();
                case MULT -> mult *= m.value();
            }
        }

        value *= 1 + percent / 100.0;
        value *= mult;
        value = Math.clamp(value, def.min(), def.max());
        return def.rounding().apply(value);
    }

    /** Считает все объявленные статы сразу: снимок для игрока. */
    public StatSnapshot computeAll(Map<String, Double> bases, Collection<StatModifier> modifiers) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (StatDef def : registry.all()) {
            values.put(def.id(), compute(def.id(), bases.get(def.id()), modifiers));
        }
        return new StatSnapshot(values);
    }
}
