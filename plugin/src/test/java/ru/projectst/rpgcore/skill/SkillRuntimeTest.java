package ru.projectst.rpgcore.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.projectst.rpgcore.balance.BalanceBook;
import ru.projectst.rpgcore.balance.BalanceLoader;
import ru.projectst.rpgcore.damage.DamageSchool;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.status.StatusDefLoader;
import ru.projectst.rpgcore.status.StatusService;

/**
 * Исполнитель проверяется на подставном мире, который просто записывает вызовы.
 * Благодаря порту {@link SkillWorld} для этого не нужен ни сервер, ни Bukkit.
 */
class SkillRuntimeTest {

    private static final UUID CASTER = UUID.randomUUID();
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    /** Подставной мир: пишет, что от него просили. */
    private static final class FakeWorld implements SkillWorld {
        final List<String> calls = new ArrayList<>();
        final List<Runnable> delayed = new ArrayList<>();
        List<UUID> nextTargets = List.of(A, B);
        int resolveCount;

        @Override
        public List<UUID> resolveTargets(UUID caster, TargetSpec.Type type,
                                         double radius, double angle) {
            resolveCount++;
            calls.add("resolve " + type + " r=" + radius + " a=" + angle);
            return type == TargetSpec.Type.SELF ? List.of(caster) : nextTargets;
        }

        @Override
        public void dealDamage(UUID caster, UUID target, double amount,
                               DamageSchool school, String skillId) {
            calls.add("damage " + name(target) + " " + amount + " " + school + " от " + skillId);
        }

        @Override
        public void heal(UUID target, double amount) {
            calls.add("heal " + name(target) + " " + amount);
        }

        @Override
        public void message(UUID target, String text) {
            calls.add("message " + name(target) + " " + text);
        }

        @Override
        public void runLater(int ticks, Runnable task) {
            calls.add("later " + ticks);
            delayed.add(task);
        }

        private static String name(UUID id) {
            if (id.equals(CASTER)) {
                return "caster";
            }
            return id.equals(A) ? "A" : "B";
        }
    }

    private static StatusService statuses() {
        var registry = StatusDefLoader.load("statuses.yml", """
                statuses:
                  mark:
                    category: mark
                  shield:
                    category: shield
                """, new ContentErrors()).orElseThrow();
        return new StatusService(registry, () -> 0L);
    }

    private static BalanceBook balance() {
        return BalanceLoader.load("balance.yml", """
                balance:
                  test_skill:
                    radius: 6
                    damage:
                      base: 10
                      per-level: 2
                """, new ContentErrors()).orElseThrow();
    }

    private static SkillDef skill(String yaml) {
        ContentErrors errors = new ContentErrors();
        SkillDef def = SkillLoader.load("test_skill.yml", yaml, errors).orElseThrow(
                () -> new AssertionError(errors.all().toString()));
        assertTrue(errors.isEmpty(), () -> errors.all().toString());
        return def;
    }

    @Test
    @DisplayName("цели шага вычисляются ровно один раз на все его действия")
    void targetsResolvedOncePerStep() {
        FakeWorld world = new FakeWorld();
        SkillRuntime runtime = new SkillRuntime(world, statuses(), balance());

        runtime.cast(CASTER, skill("""
                id: test_skill
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: $radius }
                    do:
                      - { action: damage, amount: 5 }
                      - { action: heal, amount: 1 }
                      - { action: message, text: "раз" }
                """), 1);

        assertEquals(1, world.resolveCount,
                "три действия — но выборка целей одна, иначе они могли бы разойтись");
    }

    @Test
    @DisplayName("каждое действие применяется ко всем целям шага")
    void everyActionHitsEveryTarget() {
        FakeWorld world = new FakeWorld();
        SkillRuntime runtime = new SkillRuntime(world, statuses(), balance());

        runtime.cast(CASTER, skill("""
                id: test_skill
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: $radius }
                    do:
                      - { action: damage, amount: 5, school: physical }
                """), 1);

        assertEquals(List.of(
                "resolve ENEMIES_IN_RADIUS r=6.0 a=0.0",
                "damage A 5.0 PHYSICAL от test_skill",
                "damage B 5.0 PHYSICAL от test_skill"), world.calls);
    }

    @Test
    @DisplayName("кривая баланса разворачивается по уровню навыка")
    void balanceCurveUsesLevel() {
        FakeWorld world = new FakeWorld();
        world.nextTargets = List.of(A);
        SkillRuntime runtime = new SkillRuntime(world, statuses(), balance());

        runtime.cast(CASTER, skill("""
                id: test_skill
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: $radius }
                    do:
                      - { action: damage, amount: $damage }
                """), 4);

        assertTrue(world.calls.contains("damage A 16.0 MAGIC от test_skill"),
                "10 + 2 * 3 = 16, " + world.calls);
    }

    @Test
    @DisplayName("шаг с задержкой откладывается, а не выполняется сразу")
    void delayedStepIsScheduled() {
        FakeWorld world = new FakeWorld();
        SkillRuntime runtime = new SkillRuntime(world, statuses(), balance());

        runtime.cast(CASTER, skill("""
                id: test_skill
                class: mage
                steps:
                  - target: { type: self }
                    delay: 10
                    do:
                      - { action: message, text: "позже" }
                """), 1);

        assertEquals(List.of("later 10"), world.calls);

        world.delayed.get(0).run();

        assertTrue(world.calls.contains("message caster позже"), world.calls.toString());
    }

    @Test
    @DisplayName("пустая выборка целей не вызывает ни одного действия")
    void emptyTargetsSkipActions() {
        FakeWorld world = new FakeWorld();
        world.nextTargets = List.of();
        SkillRuntime runtime = new SkillRuntime(world, statuses(), balance());

        runtime.cast(CASTER, skill("""
                id: test_skill
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: $radius }
                    do:
                      - { action: damage, amount: 5 }
                """), 1);

        assertEquals(List.of("resolve ENEMIES_IN_RADIUS r=6.0 a=0.0"), world.calls);
    }

    @Test
    @DisplayName("статусы накладываются с источником навыка")
    void statusCarriesSkillAsSource() {
        FakeWorld world = new FakeWorld();
        world.nextTargets = List.of(A);
        StatusService statuses = statuses();
        SkillRuntime runtime = new SkillRuntime(world, statuses, balance());

        runtime.cast(CASTER, skill("""
                id: test_skill
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: $radius }
                    do:
                      - { action: status, id: mark, duration: 100 }
                """), 1);

        assertTrue(statuses.isActing(A, "mark"));
        assertEquals(List.of("skill:test_skill"), List.copyOf(statuses.sources(A)));
    }

    @Test
    @DisplayName("щит накладывается с запасом из amount")
    void shieldCarriesAmount() {
        FakeWorld world = new FakeWorld();
        world.nextTargets = List.of(A);
        StatusService statuses = statuses();
        SkillRuntime runtime = new SkillRuntime(world, statuses, balance());

        runtime.cast(CASTER, skill("""
                id: test_skill
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: $radius }
                    do:
                      - { action: status, id: shield, duration: 100, amount: 25 }
                """), 1);

        assertEquals(25, statuses.defenderState(A).shieldPool(), 1e-9);
    }

    @Test
    @DisplayName("remove-status снимает наложенное")
    void removeStatusWorks() {
        FakeWorld world = new FakeWorld();
        world.nextTargets = List.of(A);
        StatusService statuses = statuses();
        SkillRuntime runtime = new SkillRuntime(world, statuses, balance());

        runtime.cast(CASTER, skill("""
                id: test_skill
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: $radius }
                    do:
                      - { action: status, id: mark, duration: 100 }
                      - { action: remove-status, id: mark }
                """), 1);

        assertTrue(statuses.all(A).isEmpty());
    }
}
