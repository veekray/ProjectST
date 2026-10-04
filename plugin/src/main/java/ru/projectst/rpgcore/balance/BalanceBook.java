package ru.projectst.rpgcore.balance;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Числа всех навыков.
 *
 * <p>Перезагружается целиком и независимо от файлов навыков: в этом и смысл
 * отдельного слоя — правка баланса не трогает логику и не требует перечитывать
 * её.
 */
public final class BalanceBook {

    public static final BalanceBook EMPTY = new BalanceBook(Map.of());

    private final Map<String, BalanceTable> bySkill;

    public BalanceBook(Map<String, BalanceTable> bySkill) {
        this.bySkill = Collections.unmodifiableMap(new LinkedHashMap<>(bySkill));
    }

    /**
     * Таблица навыка. Для навыка без чисел возвращается пустая таблица, а не
     * {@code null}: отсутствие раздела баланса законно, если скилл не использует
     * ни одной ссылки, и проверит это связывание.
     */
    public BalanceTable table(String skillId) {
        return bySkill.getOrDefault(skillId, BalanceTable.EMPTY);
    }

    public boolean has(String skillId) {
        return bySkill.containsKey(skillId);
    }

    public int size() {
        return bySkill.size();
    }

    public Iterable<String> skillIds() {
        return bySkill.keySet();
    }
}
