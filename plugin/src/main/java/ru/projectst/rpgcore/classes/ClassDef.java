package ru.projectst.rpgcore.classes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import ru.projectst.rpgcore.balance.BalanceValue;

/**
 * Класс персонажа.
 *
 * <p>Кривые базовых статов описываются тем же {@link BalanceValue}, что и числа
 * навыков. Повторно используется намеренно: иначе появились бы два похожих, но
 * чуть разных способа задать «растёт с уровнем», и однажды они разошлись бы в
 * поведении.
 *
 * @param id         идентификатор в нижнем регистре
 * @param display    название для игрока
 * @param slots      сколько слотов навыков у класса
 * @param tierLevels минимальный уровень игрока для каждой ступени
 * @param statCurves базовые статы класса и их рост по уровню
 */
public record ClassDef(String id, String display, int slots,
                       Map<Integer, Integer> tierLevels,
                       Map<String, BalanceValue> statCurves) {

    public ClassDef {
        if (id == null || !id.equals(id.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("id класса должен быть в нижнем регистре: " + id);
        }
        if (slots < 1) {
            throw new IllegalArgumentException("у класса должен быть хотя бы один слот: " + id);
        }
        tierLevels = Collections.unmodifiableMap(new LinkedHashMap<>(
                tierLevels == null ? Map.of() : tierLevels));
        statCurves = Collections.unmodifiableMap(new LinkedHashMap<>(
                statCurves == null ? Map.of() : statCurves));
    }

    /**
     * С какого уровня открыта ступень. Для необъявленной ступени — первый
     * уровень: отсутствие записи означает «ограничений нет», а не «недоступно».
     */
    public int levelForTier(int tier) {
        return tierLevels.getOrDefault(tier, 1);
    }

    public boolean declaresTier(int tier) {
        return tierLevels.containsKey(tier);
    }
}
