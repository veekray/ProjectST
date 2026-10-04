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
    @DisplayName("крит применяется до снижения целью")
    void critBeforeMitigation() {
        DamageResult r = ALWAYS_CRIT.compute(magic(100),
                stats(StatIds.CRIT_CHANCE, 100, StatIds.CRIT_POWER, 100),
                stats(StatIds.MAGIC_RESISTANCE, 50), DefenderState.NONE);

        // 100 * 2 (крит) * 0.5 (сопротивление) = 100
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
    @DisplayName("школьное сопротивление и damage_reduction суммируются")
    void reductionsAdd() {
        DamageResult r = NO_CRIT.compute(magic(100), stats(),
                stats(StatIds.MAGIC_RESISTANCE, 30, StatIds.DAMAGE_REDUCTION, 20),
                DefenderState.NONE);

        // 100 * (1 - 0.5) = 50. При перемножении было бы 56.
        assertEquals(50, r.applied(), 1e-9);
    }

    @Test
    @DisplayName("снижение урона ограничено сверху: цель не становится бессмертной")
    void reductionIsCapped() {
        DamageResult r = NO_CRIT.compute(magic(100), stats(),
                stats(StatIds.DAMAGE_REDUCTION, 500), DefenderState.NONE);

        assertEquals(20, r.applied(), 1e-9);
    }

    @Test
    @DisplayName("чистый урон не снижается ничем")
    void trueDamageIgnoresMitigation() {
        DamageResult r = NO_CRIT.compute(
                DamageRequest.of(100, DamageSchool.TRUE, "s"), stats(),
                stats(StatIds.DAMAGE_REDUCTION, 80, StatIds.DEFENSE, 80), DefenderState.NONE);

        assertEquals(100, r.applied(), 1e-9);
    }

    @Test
    @DisplayName("щит поглощает после снижения, а не до")
    void shieldAbsorbsAfterMitigation() {
        DamageResult r = NO_CRIT.compute(magic(100), stats(),
                stats(StatIds.MAGIC_RESISTANCE, 50), DefenderState.shielded(30));

        // 100 -> 50 после сопротивления, щит съел 30, в здоровье ушло 20.
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
