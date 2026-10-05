package ru.projectst.rpgcore.damage;

import java.util.function.DoubleSupplier;
import ru.projectst.rpgcore.stat.StatSnapshot;

/**
 * Единственное место, где считается урон.
 *
 * <p>Порядок зафиксирован в SPEC и здесь же проверяется тестами:
 * <pre>
 *   1. база
 *   2. усиление от статов атакующего: школьный стат, затем skill_damage
 *   3. крит
 *   4. снижение статами цели: школьный стат, затем damage_reduction
 *   5. щиты
 *   6. неуязвимость
 * </pre>
 *
 * <p>Шаги 1–6 — чистая арифметика, поэтому класс не знает ни про Bukkit, ни про
 * сущности. Применение к здоровью (шаг 7) и испускание события живут в
 * {@code platform/}: там единственный вызов, который реально отнимает здоровье.
 * Это и есть «один конвейер»: без правила про единственный вызов требование
 * было бы декларацией.
 *
 * <p>Неуязвимость проверяется последней не случайно. Так в результат попадают
 * промежуточные значения, и в отладке видно, какой урон был бы без неё —
 * иначе «ноль» ничего не объясняет.
 */
public final class DamageEngine {

    /** Снижение урона не может превысить этот предел, иначе цель станет бессмертной. */
    private static final double MAX_REDUCTION_PERCENT = 80;

    private final DoubleSupplier random;

    /**
     * @param random источник случайности в диапазоне [0, 1) для крита.
     *               Вынесен параметром, чтобы тесты были детерминированными.
     */
    public DamageEngine(DoubleSupplier random) {
        this.random = random;
    }

    public DamageResult compute(DamageRequest request, StatSnapshot attacker,
                                StatSnapshot defender, DefenderState state) {

        // 2. усиление атакующим
        double value = request.base();
        DamageSchool school = request.school();
        if (school.offenseStat() != null && attacker != null) {
            value *= 1 + percent(attacker, school.offenseStat());
        }
        if (attacker != null) {
            value *= 1 + percent(attacker, StatIds.SKILL_DAMAGE);
        }
        double afterScaling = value;

        // 3. уклонение
        //
        // Бросок делается до крита и до снижения, но после усиления: так в
        // результате остаются промежуточные значения, и в отладке видно, какой
        // удар цель только что увела. Уклониться можно только от того, что
        // вообще снижаемо: от урона по времени и от чистого урона — нет, иначе
        // яд переставал бы тикать по удачливой цели.
        if (defender != null && school.mitigable() && !request.hasTag("no_dodge")) {
            double dodge = statOrZero(defender, StatIds.DODGE_RATING);
            if (dodge > 0 && random.getAsDouble() * 100 < dodge) {
                return new DamageResult(0, 0, false, DamageResult.Blocker.DODGE,
                        afterScaling, afterScaling);
            }
        }

        // 4. крит
        boolean crit = false;
        if (attacker != null && !request.hasTag("no_crit")) {
            double chance = statOrZero(attacker, StatIds.CRIT_CHANCE);
            if (chance > 0 && random.getAsDouble() * 100 < chance) {
                crit = true;
                // Сила крита задаётся в процентных пунктах сверх обычного урона:
                // 50 означает +50%. Ноль означает обычный двойной урон.
                double power = statOrZero(attacker, StatIds.CRIT_POWER);
                value *= power > 0 ? 1 + power / 100.0 : 2;
            }
        }

        // 5. снижение целью
        if (defender != null && school.mitigable()) {
            double reduction = statOrZero(defender, school.defenseStat())
                    + statOrZero(defender, StatIds.DAMAGE_REDUCTION);
            value *= 1 - Math.min(reduction, MAX_REDUCTION_PERCENT) / 100.0;
        }
        double afterMitigation = value;

        // 6. неуязвимость — после подсчёта промежуточных значений, но ДО щитов.
        //
        // Порядок именно такой по двум причинам сразу. Если проверять раньше,
        // в результате остаётся голый ноль, и в отладке не видно, каким урон
        // был бы без неё. Если проверять позже щитов — неуязвимая цель сожжёт
        // щит впустую, хотя урона и так не было.
        if (state.immune()) {
            return new DamageResult(0, 0, crit, DamageResult.Blocker.IMMUNITY,
                    afterScaling, afterMitigation);
        }

        // 7. щиты
        double absorbed = 0;
        if (state.shieldPool() > 0) {
            absorbed = Math.min(state.shieldPool(), value);
            value -= absorbed;
        }
        if (value <= 0 && absorbed > 0) {
            return new DamageResult(0, absorbed, crit, DamageResult.Blocker.SHIELD,
                    afterScaling, afterMitigation);
        }
        return new DamageResult(value, absorbed, crit, null, afterScaling, afterMitigation);
    }

    /** Значение стата как доля: 25 процентных пунктов превращаются в 0.25. */
    private static double percent(StatSnapshot snapshot, String statId) {
        return statOrZero(snapshot, statId) / 100.0;
    }

    /**
     * Статы, которых нет в реестре, для конвейера необязательны: сервер может
     * не объявлять, скажем, magic_resistance. Поэтому здесь ноль, а не
     * исключение — в отличие от {@link StatSnapshot#get}, где отсутствие стата
     * означает опечатку в коде.
     */
    private static double statOrZero(StatSnapshot snapshot, String statId) {
        Double v = snapshot.asMap().get(statId);
        return v == null ? 0 : v;
    }
}
