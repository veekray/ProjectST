package ru.projectst.rpgcore.stat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Посчитанные значения статов на один момент времени.
 *
 * <p>Неизменяемый: пересчёт создаёт новый снимок, а не правит старый. Так
 * исключается случай, когда половина кода уже увидела новое значение, а
 * половина ещё старое.
 */
public final class StatSnapshot {

    private final Map<String, Double> values;

    public StatSnapshot(Map<String, Double> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** Значение стата. Для необъявленного — исключение: молчаливый ноль прячет опечатку. */
    public double get(String statId) {
        Double v = values.get(statId);
        if (v == null) {
            throw new IllegalArgumentException("стат не объявлен или не посчитан: " + statId);
        }
        return v;
    }

    public Map<String, Double> asMap() {
        return values;
    }
}
