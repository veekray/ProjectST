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

    /** Снимок без значений: для источника урона, у которого статов нет вовсе. */
    public static final StatSnapshot EMPTY = new StatSnapshot(java.util.Map.of());

    private final Map<String, Double> values;

    public StatSnapshot(Map<String, Double> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** Значение стата. Для необъявленного — исключение: молчаливый ноль прячет опечатку. */
    /**
     * Значение стата, который мог быть не объявлен.
     *
     * <p>Строгое чтение остаётся правилом: опечатка в контенте обязана падать.
     * Но у движка есть статы, которых сервер может не объявлять вовсе —
     * усиление и удлинение эффектов, — и тогда верный ответ «ноль», а не
     * остановка навыка посреди исполнения.
     */
    public double getOrZero(String statId) {
        Double value = values.get(statId);
        return value == null ? 0 : value;
    }

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
