package ru.projectst.rpgcore.damage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.projectst.rpgcore.stat.StatSnapshot;

/**
 * Порядок шагов конвейера проверяется случаями, которые при другом порядке
 * дали бы другое число. «Работает» тут ничего не доказывает.
 */
class DamageEngineTest {

    /** Крит никогда не срабатывает. */
    private static final DamageEngine NO_CRIT = new DamageEngine(() -> 0.999);

    /** Крит срабатывает всегда, если шанс больше нуля. */
    private static final DamageEngine ALWAYS_CRIT = new DamageEngine(() -> 0.0);

    private static StatSnapshot stats(Object... pairs) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        }
        return new StatSnapshot(map);
    }

    private static DamageRequest magic(double base) {
        return DamageRequest.of(base, DamageSchool.MAGIC, "test_skill");
    }

    @Test
    @DisplayName("без статов урон равен базовому")
    void bareDamage() {
        DamageResult r = NO_CRIT.compute(magic(100), stats(), stats(), DefenderState.NONE);
        assertEquals(100, r.applied(), 1e-9);
        assertFalse(r.crit());
        assertFalse(r.blocked());
    }

    @Test
    @DisplayName("школьный стат и skill_damage перемножаются, а не суммируются")
    void schoolAndSkillDamageMultiply() {
        DamageResult r = NO_CRIT.compute(magic(100),
                stats(StatIds.MAGIC_DAMAGE, 50, StatIds.SKILL_DAMAGE, 50),
                stats(), DefenderState.NONE);

        // 100 * 1.5 * 1.5 = 225. При суммировании вышло бы 200.
        assertEquals(225, r.applied(), 1e-9);
    }

    @Test
    @DisplayName("физический урон не усиливается магическим статом")
    void schoolsDoNotLeak() {
        DamageResult r = NO_CRIT.compute(
                DamageRequest.of(100, DamageSchool.PHYSICAL, "s"),
                stats(StatIds.MAGIC_DAMAGE, 100), stats(), DefenderState.NONE);

        assertEquals(100, r.applied(), 1e-9);
    }

    @Test
    @DisplayName("уклонение убирает удар целиком и называет причину")
    void dodgeBlocksWholeHit() {
        DamageResult r = ALWAYS_CRIT.compute(magic(100), stats(),
                stats(StatIds.DODGE_RATING, 30), DefenderState.NONE);

        assertEquals(0, r.applied(), 1e-9);
        assertEquals(DamageResult.Blocker.DODGE, r.blockedBy(),
                "молчаливый ноль здесь неотличим от поломки");
        assertFalse(r.crit(), "уклонились — крита не было, а не был и погашен");
        assertEquals(100, r.afterScaling(), 1e-9,
                "в отладке должно быть видно, какой удар цель только что увела");
    }

    @Test
    @DisplayName("уклонение не спасает от неснижаемого урона")
    void dodgeDoesNotStopTrueDamage() {
        DamageResult r = NO_CRIT.compute(
                DamageRequest.of(100, DamageSchool.TRUE, "s"),
                stats(), stats(StatIds.DODGE_RATING, 100), DefenderState.NONE);

        assertEquals(100, r.applied(), 1e-9,
                "иначе яд переставал бы тикать по удачливой цели");
    }

    @Test
    @DisplayName("при нулевом уклонении бросок не делается вовсе")
    void noDodgeRollWithoutRating() {
        DamageResult r = ALWAYS_CRIT.compute(magic(100), stats(), stats(),
                DefenderState.NONE);

        assertFalse(r.blocked(), "ноль процентов не должен срабатывать на нулевом броске");
    }

    @Test
    @DisplayName("крит применяется до снижения целью")
    void critBeforeMitigation() {
        DamageResult r = ALWAYS_CRIT.compute(magic(100),
                stats(StatIds.CRIT_CHANCE, 100, StatIds.CRIT_POWER, 100),
                stats(StatIds.MAGIC_DEFENSE, 100), DefenderState.NONE);

        // 100 * 2 (крит) * 0.5 (сто рейтинга — ровно половина) = 100
        assertTrue(r.crit());
        assertEquals(100, r.applied(), 1e-9);
        assertEquals(200, r.afterScaling() * 2, 1e-9, "до снижения урон был удвоен");
    }

    @Test
    @DisplayName("без силы крита крит удваивает")
    void critWithoutPowerDoubles() {
        DamageResult r = ALWAYS_CRIT.compute(magic(100),
                stats(StatIds.CRIT_CHANCE, 100), stats(), DefenderState.NONE);

        assertEquals(200, r.applied(), 1e-9);
    }

    @Test
    @DisplayName("метка no_crit отключает крит")
    void noCritTag() {
        DamageResult r = ALWAYS_CRIT.compute(
                new DamageRequest(100, DamageSchool.MAGIC, "s", Set.of("no_crit")),
                stats(StatIds.CRIT_CHANCE, 100), stats(), DefenderState.NONE);

        assertFalse(r.crit());
        assertEquals(100, r.applied(), 1e-9);
    }

    @Test
    @DisplayName("школьная и общая защита перемножаются, а не складываются")
    void defencesMultiply() {
        DamageResult r = NO_CRIT.compute(magic(100), stats(),
                stats(StatIds.MAGIC_DEFENSE, 100, StatIds.GENERAL_DEFENSE, 100),
                DefenderState.NONE);

        // По половине от каждой: 100 * 0.5 * 0.5 = 25.
        // При сложении вышло бы ноль, и дальше понадобился бы потолок сверху.
        assertEquals(25, r.applied(), 1e-9);
    }

    @Test
    @DisplayName("каждый следующий пункт защиты даёт меньше предыдущего")
    void defenceHasDiminishingReturns() {
        double atHundred = NO_CRIT.compute(magic(100), stats(),
                stats(StatIds.MAGIC_DEFENSE, 100), DefenderState.NONE).applied();
        double atTwoHundred = NO_CRIT.compute(magic(100), stats(),
                stats(StatIds.MAGIC_DEFENSE, 200), DefenderState.NONE).applied();
        double atThreeHundred = NO_CRIT.compute(magic(100), stats(),
                stats(StatIds.MAGIC_DEFENSE, 300), DefenderState.NONE).applied();

        assertEquals(50, atHundred, 1e-9);
        assertEquals(100.0 / 3, atTwoHundred, 1e-9);
        assertEquals(25, atThreeHundred, 1e-9);

        // Первая сотня сняла пятьдесят урона, вторая — шестнадцать с третью,
        // третья — восемь с третью. Ровно это и значит убывающая отдача.
        assertTrue(atHundred - atTwoHundred > atTwoHundred - atThreeHundred,
                "иначе вторая сотня защиты стоила бы столько же, сколько первая");
    }

    @Test
    @DisplayName("ста процентов защиты не бывает ни при каком рейтинге")
    void defenceNeverReachesWhole() {
        DamageResult r = NO_CRIT.compute(magic(100), stats(),
                stats(StatIds.GENERAL_DEFENSE, 1_000_000), DefenderState.NONE);

        // Кривая сама не доходит до нуля, а нижний порог обещает десятую часть:
        // удар, не наносящий ничего, неотличим от поломки.
        assertEquals(10, r.applied(), 1e-9);
    }

    @Test
    @DisplayName("отрицательная защита — это уязвимость, и она линейна")
    void negativeDefenceIsVulnerability() {
        DamageResult r = NO_CRIT.compute(magic(100), stats(),
                stats(StatIds.MAGIC_DEFENSE, -25), DefenderState.NONE);

        assertEquals(125, r.applied(), 1e-9, "минус двадцать пять — это плюс четверть урона");
    }

    @Test
    @DisplayName("хуже, чем вдвое, уязвимость не делает")
    void vulnerabilityIsBounded() {
        DamageResult r = NO_CRIT.compute(magic(100), stats(),
                stats(StatIds.MAGIC_DEFENSE, -500), DefenderState.NONE);

        assertEquals(200, r.applied(), 1e-9);
    }

    @Test
    @DisplayName("защита одной школы не трогает другую")
    void defencesDoNotLeak() {
        DamageResult r = NO_CRIT.compute(magic(100), stats(),
                stats(StatIds.PHYSICAL_DEFENSE, 300), DefenderState.NONE);

        assertEquals(100, r.applied(), 1e-9);
    }

    @Test
    @DisplayName("чистый урон не снижается ничем")
    void trueDamageIgnoresMitigation() {
        DamageResult r = NO_CRIT.compute(
                DamageRequest.of(100, DamageSchool.TRUE, "s"), stats(),
                stats(StatIds.GENERAL_DEFENSE, 400, StatIds.PHYSICAL_DEFENSE, 400),
                DefenderState.NONE);

        assertEquals(100, r.applied(), 1e-9);
    }

    @Test
    @DisplayName("щит поглощает после снижения, а не до")
    void shieldAbsorbsAfterMitigation() {
        DamageResult r = NO_CRIT.compute(magic(100), stats(),
                stats(StatIds.MAGIC_DEFENSE, 100), DefenderState.shielded(30));

        // 100 -> 50 после защиты, щит съел 30, в здоровье ушло 20.
        // Если бы щит стоял до снижения, в здоровье ушло бы 35.
        assertEquals(30, r.absorbed(), 1e-9);
        assertEquals(20, r.applied(), 1e-9);
    }

    @Test
    @DisplayName("щит, поглотивший всё, даёт явную причину")
    void fullyAbsorbedIsExplained() {
        DamageResult r = NO_CRIT.compute(magic(50), stats(), stats(),
                DefenderState.shielded(200));

        assertEquals(0, r.applied(), 1e-9);
        assertEquals(50, r.absorbed(), 1e-9);
        assertEquals(DamageResult.Blocker.SHIELD, r.blockedBy());
    }

    @Test
    @DisplayName("неуязвимость отменяет урон, но промежуточные значения видны")
    void immunityKeepsBreakdown() {
        DamageResult r = NO_CRIT.compute(magic(100),
                stats(StatIds.MAGIC_DAMAGE, 100), stats(), DefenderState.immuneTarget());

        assertEquals(0, r.applied(), 1e-9);
        assertEquals(DamageResult.Blocker.IMMUNITY, r.blockedBy());
        assertEquals(200, r.afterScaling(), 1e-9,
                "без неуязвимости урон был бы 200 — это должно быть видно в отладке");
    }

    @Test
    @DisplayName("неуязвимая цель не жжёт щит впустую")
    void immunityDoesNotBurnShield() {
        DamageResult r = NO_CRIT.compute(magic(100), stats(), stats(),
                new DefenderState(true, 80));

        assertEquals(0, r.applied(), 1e-9);
        assertEquals(0, r.absorbed(), 1e-9, "щит не тронут: урона и так не было");
        assertEquals(DamageResult.Blocker.IMMUNITY, r.blockedBy());
    }

    @Test
    @DisplayName("skillId обязателен: без него урон нельзя объяснить")
    void skillIdRequired() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new DamageRequest(10, DamageSchool.MAGIC, "  ", Set.of()));
    }

    @Test
    @DisplayName("отрицательный базовый урон отвергается")
    void negativeBaseRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> DamageRequest.of(-1, DamageSchool.MAGIC, "s"));
    }
}
