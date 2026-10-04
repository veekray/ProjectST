package ru.projectst.rpgcore.balance;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Числа одного навыка. */
public final class BalanceTable {

    public static final BalanceTable EMPTY = new BalanceTable("", Map.of());

    private final String skillId;
    private final Map<String, BalanceValue> values;

    public BalanceTable(String skillId, Map<String, BalanceValue> values) {
        this.skillId = skillId;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public String skillId() {
        return skillId;
    }

    public Set<String> keys() {
        return values.keySet();
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public Optional<BalanceValue> find(String key) {
        return Optional.ofNullable(values.get(key));
    }

    /**
     * Значение на уровне. Отсутствие ключа — исключение, а не ноль: к моменту
     * исполнения все ссылки уже проверены связыванием, поэтому промах здесь
     * означает ошибку в коде, а не в контенте.
     */
    public double require(String key, int level) {
        BalanceValue value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "в балансе навыка " + skillId + " нет ключа \"" + key + "\"");
        }
        return value.at(level);
    }
}
