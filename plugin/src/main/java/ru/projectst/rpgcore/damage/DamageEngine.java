package ru.projectst.rpgcore.damage;

import java.util.function.DoubleSupplier;
import ru.projectst.rpgcore.stat.StatRegistry;
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

    /**
     * Сколько урона проходит всегда.
     *
     * <p>Не балансная ручка, а обещание: защита не обнуляет удар ни при каком
     * снаряжении. Кривая и сама не доходит до нуля, но два слоя, перемножаясь,
     * могут подойти к нему вплотную, а «удар, который не наносит ничего»
     * невозможно отличить от поломки.
     */
    private static final double MIN_TAKEN = 0.10;

    private final DoubleSupplier random;
    private final StatRegistry stats;

    /**
     * @param random источник случайности в диапазоне [0, 1) для крита.
     *               Вынесен параметром, чтобы тесты были детерминированными
     * @param stats  реестр статов: в нём лежат кривые, по которым рейтинг
     *               превращается в проценты. Конвейер обязан считать их тем же
     *               способом, которым меню их показывает, — иначе подсказка
     *               обещает одно, а бой делает другое
     */
    public DamageEngine(DoubleSupplier random, StatRegistry stats) {
        this.random = random;
        this.stats = stats;
    }

    public DamageResult compute(DamageRequest request, StatSnapshot attacker,
                                StatSnapshot defender, DefenderState state) {

        // 2. усиление атакующим
        double value = request.base();
        DamageSchool school = request.school();
        if (school.offenseStat() != null && attacker != null) {
            value *= 1 + share(attacker, school.offenseStat());
        }
        if (attacker != null) {
            value *= 1 + share(attacker, StatIds.SKILL_DAMAGE);
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
            double dodge = percent(defender, StatIds.DODGE_RATING);
            if (dodge > 0 && random.getAsDouble() * 100 < dodge) {
                return new DamageResult(0, 0, false, DamageResult.Blocker.DODGE,
                        afterScaling, afterScaling);
            }
        }

        // 4. крит
        boolean crit = false;
        if (attacker != null && !request.hasTag("no_crit")) {
            double chance = percent(attacker, StatIds.CRIT_CHANCE);
            if (chance > 0 && random.getAsDouble() * 100 < chance) {
                crit = true;
                // Сила крита — проценты сверх обычного урона. Ноль означает
                // обычный двойной урон: крит без стата всё равно крит.
                double power = share(attacker, StatIds.CRIT_POWER);
                value *= power > 0 ? 1 + power : 2;
            }
        }

        // 5. снижение целью
        //
        // Два слоя: своя защита школы и общая. Они перемножаются, а не
        // складываются. Сложение означало бы, что пятьдесят и пятьдесят гасят
        // удар насухо, и пришлось бы ставить потолок сверху — тот самый
        // костыль, из-за которого в старом стеке было непонятно, работает
        // следующий пункт защиты или уже нет.
        if (defender != null && school.mitigable()) {
            double taken = (1 - share(defender, school.defenseStat()))
                    * (1 - share(defender, StatIds.GENERAL_DEFENSE));
            value *= Math.max(MIN_TAKEN, taken);
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

    /**
     * Доля, в которую превращается стат: 0.29 означает двадцать девять процентов.
     *
     * <p>Считает её кривая из файла стата, а не этот класс. Так у защиты,
     * уклонения и урона одна и та же арифметика рейтинга, и меню показывает те же
     * числа — потому что спрашивает тот же реестр.
     */
    private double share(StatSnapshot snapshot, String statId) {
        return stats.share(statId, statOrZero(snapshot, statId));
    }

    /** То же в процентах: для броска кости, где сравнивают с сотней. */
    private double percent(StatSnapshot snapshot, String statId) {
        return stats.percent(statId, statOrZero(snapshot, statId));
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
