package ru.projectst.rpgcore.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.projectst.rpgcore.balance.BalanceBook;
import ru.projectst.rpgcore.balance.BalanceLoader;
import ru.projectst.rpgcore.damage.DamageSchool;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.stat.Rounding;
import ru.projectst.rpgcore.stat.StatDef;
import ru.projectst.rpgcore.stat.StatEngine;
import ru.projectst.rpgcore.stat.StatRegistry;
import ru.projectst.rpgcore.stat.StatService;
import ru.projectst.rpgcore.status.StatusApplication;
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
    private static final UUID WORLD = UUID.randomUUID();

    /** Подставной мир: пишет, что от него просили. */
    private static final class FakeWorld implements SkillWorld {
        final List<String> calls = new ArrayList<>();
        final List<Runnable> delayed = new ArrayList<>();
        List<UUID> nextTargets = List.of(A, B);
        int resolveCount;
        /** Куда «попадёт» следующий снаряд; null — промах до конца дальности. */
        UUID nextProjectileTarget = A;
        ProjectileSpec lastProjectile;
        /** Призванные: тип и место. */
        final List<String> spawned = new ArrayList<>();
        UUID nextSpawn = UUID.randomUUID();
        RayHit nextRayHit = new RayHit(new Position(WORLD, 10, 64, 10), A);
        boolean playersOnly = true;

        @Override
        public List<UUID> resolveTargets(CastContext context, TargetSpec.Type type,
                                         double radius, double angle) {
            resolveCount++;
            calls.add("resolve " + type + " r=" + radius
                    + (context.origin() == null ? "" : " origin=" + context.origin().x()));
            return switch (type) {
                case SELF -> List.of(context.caster());
                case TRIGGER -> context.triggerOpt().map(List::of).orElse(List.of());
                default -> nextTargets;
            };
        }

        @Override
        public Optional<Position> positionOf(UUID entity) {
            return Optional.of(new Position(WORLD, 1, 2, 3));
        }

        @Override
        public boolean isPlayer(UUID entity) {
            return playersOnly || entity.equals(CASTER);
        }

        @Override
        public void dealDamage(UUID caster, UUID target, double amount,
                               DamageSchool school, String skillId) {
            calls.add("damage " + name(target) + " " + amount + " " + school);
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
        public void potion(UUID target, String effect, int durationTicks, int amplifier) {
            calls.add("potion " + name(target) + " " + effect + " " + durationTicks);
        }

        @Override
        public void push(UUID target, Position from, double strength, double lift) {
            calls.add("push " + name(target) + " " + strength + " lift=" + lift);
        }

        @Override
        public void pullTowards(UUID target, Position to, double strength) {
            calls.add("pull " + name(target) + " " + strength + " -> " + to.x());
        }

        @Override
        public void teleport(UUID target, Position to) {
            calls.add("teleport " + name(target) + " -> " + to.x());
        }

        @Override
        public Optional<Position> forwardOf(UUID entity, double distance) {
            return Optional.of(new Position(WORLD, distance, 64, 0));
        }

        @Override
        public RayHit castRay(UUID caster, double range, boolean stopAtEntity) {
            calls.add("ray " + range);
            return nextRayHit;
        }

        @Override
        public Optional<UUID> spawnMob(String type, Position at, double health) {
            calls.add("spawn " + type + " hp=" + health + " @" + at.x());
            spawned.add(type);
            return Optional.of(nextSpawn);
        }

        @Override
        public void despawn(UUID entity) {
            calls.add("despawn");
        }

        @Override
        public void setAttackTarget(UUID mob, UUID target) {
            calls.add("target");
        }

        @Override
        public void launchProjectile(UUID caster, ProjectileSpec spec,
                                     ProjectileHandler handler) {
            lastProjectile = spec;
            calls.add("projectile v=" + spec.speed() + " range=" + spec.range()
                    + " pierce=" + spec.pierce());
            if (nextProjectileTarget != null) {
                handler.hit(new Position(WORLD, 7, 64, 7), nextProjectileTarget);
            } else {
                handler.end(new Position(WORLD, 20, 64, 20));
            }
        }

        @Override
        public void particles(Position at, String particle, Action.Particles.Shape shape,
                              int count, double size) {
            calls.add("particles " + particle + " " + shape + " x" + count + " @" + at.x());
        }

        @Override
        public void sound(Position at, String sound, double volume, double pitch) {
            calls.add("sound " + sound + " @" + at.x());
        }

        @Override
        public void runLater(int ticks, Runnable task) {
            calls.add("later " + ticks);
            delayed.add(task);
        }

        void runAllDelayed() {
            List<Runnable> copy = List.copyOf(delayed);
            delayed.clear();
            copy.forEach(Runnable::run);
        }

        private static String name(UUID id) {
            if (id.equals(CASTER)) {
                return "caster";
            }
            return id.equals(A) ? "A" : "B";
        }
    }

    // ------------------------------------------------------------------ обвязка

    private static StatusService statuses() {
        var registry = StatusDefLoader.load("statuses.yml", """
                statuses:
                  mark:
                    category: mark
                  shield:
                    category: shield
                  charge:
                    category: buff
                    stacking: stacks
                    max-stacks: 3
                """, new ContentErrors()).orElseThrow();
        return new StatusService(registry, () -> 0L);
    }

    private static StatService stats() {
        Map<String, StatDef> defs = new LinkedHashMap<>();
        defs.put("magic_damage", new StatDef("magic_damage", "md", 0, -100, 1000, Rounding.NONE));
        return new StatService(new StatEngine(new StatRegistry(defs)));
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

    private static SkillDef parse(String id, String yaml) {
        ContentErrors errors = new ContentErrors();
        SkillDef def = SkillLoader.load(id + ".yml", yaml, errors).orElseThrow(
                () -> new AssertionError(errors.all().toString()));
        assertTrue(errors.isEmpty(), () -> errors.all().toString());
        return def;
    }

    private record Fixture(FakeWorld world, SkillRuntime runtime,
                           StatusService statuses, StatService stats, ZoneService zones,
                           MinionService minions) {
    }

    private static Fixture fixture(DoubleSupplier random, SkillDef... defs) {
        FakeWorld world = new FakeWorld();
        StatusService statusService = statuses();
        StatService statService = stats();
        Map<String, SkillDef> map = new LinkedHashMap<>();
        for (SkillDef def : defs) {
            map.put(def.id(), def);
        }
        ZoneService zoneService = new ZoneService(() -> 0L);
        MinionService minionService = new MinionService(() -> 0L);
        SkillRuntime runtime = new SkillRuntime(world, statusService, statService,
                balance(), new SkillRegistry(map), zoneService, minionService, random);
        return new Fixture(world, runtime, statusService, statService, zoneService,
                minionService);
    }

    private static Fixture fixture(SkillDef... defs) {
        return fixture(() -> 0.0, defs);
    }

    // ------------------------------------------------------------------ шаг

    @Test
    @DisplayName("цели шага вычисляются ровно один раз на все его действия")
    void targetsResolvedOncePerStep() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: $radius }
                    do:
                      - { action: damage, amount: 5 }
                      - { action: heal, amount: 1 }
                      - { action: message, text: "раз" }
                """);
        Fixture f = fixture(skill);

        f.runtime.cast(CASTER, skill, 1);

        assertEquals(1, f.world.resolveCount,
                "три действия — но выборка целей одна, иначе они могли бы разойтись");
    }

    @Test
    @DisplayName("каждое действие применяется ко всем целям шага")
    void everyActionHitsEveryTarget() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: $radius }
                    do:
                      - { action: damage, amount: 5, school: physical }
                """);
        Fixture f = fixture(skill);

        f.runtime.cast(CASTER, skill, 1);

        assertEquals(List.of("resolve ENEMIES_IN_RADIUS r=6.0",
                "damage A 5.0 PHYSICAL", "damage B 5.0 PHYSICAL"), f.world.calls);
    }

    @Test
    @DisplayName("кривая баланса разворачивается по уровню навыка")
    void balanceCurveUsesLevel() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: $radius }
                    do:
                      - { action: damage, amount: $damage }
                """);
        Fixture f = fixture(skill);
        f.world.nextTargets = List.of(A);

        f.runtime.cast(CASTER, skill, 4);

        assertTrue(f.world.calls.contains("damage A 16.0 MAGIC"),
                "10 + 2 * 3 = 16, " + f.world.calls);
    }

    // ------------------------------------------------------------------ условия

    @Test
    @DisplayName("условие на кастере отменяет шаг целиком")
    void casterConditionCancelsStep() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: $radius }
                    if:
                      - { caster: has-status, value: charge }
                    do:
                      - { action: damage, amount: 5 }
                """);
        Fixture f = fixture(skill);

        f.runtime.cast(CASTER, skill, 1);
        assertTrue(f.world.calls.isEmpty(), "шаг не выполнялся вовсе: " + f.world.calls);

        f.statuses.apply(CASTER, StatusApplication.of("charge", "test"));
        f.runtime.cast(CASTER, skill, 1);
        assertTrue(f.world.calls.contains("damage A 5.0 MAGIC"), f.world.calls.toString());
    }

    @Test
    @DisplayName("условие на цели отсеивает не прошедших, но шаг выполняется")
    void targetConditionFiltersTargets() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: $radius }
                    if:
                      - { target: has-status, value: mark }
                    do:
                      - { action: damage, amount: 5 }
                """);
        Fixture f = fixture(skill);
        f.statuses.apply(A, StatusApplication.of("mark", "test"));

        f.runtime.cast(CASTER, skill, 1);

        assertTrue(f.world.calls.contains("damage A 5.0 MAGIC"));
        assertFalse(f.world.calls.contains("damage B 5.0 MAGIC"), "B без метки отсеян");
    }

    @Test
    @DisplayName("отрицание условия работает")
    void negatedCondition() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: $radius }
                    if:
                      - { target: has-status, value: mark, not: true }
                    do:
                      - { action: damage, amount: 5 }
                """);
        Fixture f = fixture(skill);
        f.statuses.apply(A, StatusApplication.of("mark", "test"));

        f.runtime.cast(CASTER, skill, 1);

        assertFalse(f.world.calls.contains("damage A 5.0 MAGIC"));
        assertTrue(f.world.calls.contains("damage B 5.0 MAGIC"));
    }

    @Test
    @DisplayName("проверка стаков требует указанного количества")
    void statusStacksCondition() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: self }
                    if:
                      - { caster: status-stacks, value: "charge:3" }
                    do:
                      - { action: message, text: "полный" }
                """);
        Fixture f = fixture(skill);
        f.statuses.apply(CASTER, StatusApplication.of("charge", "test"));
        f.statuses.apply(CASTER, StatusApplication.of("charge", "test"));

        f.runtime.cast(CASTER, skill, 1);
        assertFalse(f.world.calls.contains("message caster полный"), "двух стаков мало");

        f.statuses.apply(CASTER, StatusApplication.of("charge", "test"));
        f.runtime.cast(CASTER, skill, 1);
        assertTrue(f.world.calls.contains("message caster полный"));
    }

    @Test
    @DisplayName("шанс берёт случайность извне и потому детерминирован в тесте")
    void chanceCondition() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: self }
                    if:
                      - { caster: chance, value: "50" }
                    do:
                      - { action: message, text: "повезло" }
                """);

        Fixture never = fixture(() -> 0.99, skill);
        never.runtime.cast(CASTER, skill, 1);
        assertFalse(never.world.calls.contains("message caster повезло"));

        Fixture always = fixture(() -> 0.1, skill);
        always.runtime.cast(CASTER, skill, 1);
        assertTrue(always.world.calls.contains("message caster повезло"));
    }

    // ------------------------------------------------------------------ примитивы

    @Test
    @DisplayName("push и pull получают силу и подъём")
    void pushAndPull() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: $radius }
                    do:
                      - { action: push, strength: 1.2, lift: 0.4 }
                      - { action: pull, strength: 0.6, ticks: 1 }
                """);
        Fixture f = fixture(skill);
        f.world.nextTargets = List.of(A);

        f.runtime.cast(CASTER, skill, 1);

        assertTrue(f.world.calls.contains("push A 1.2 lift=0.4"), f.world.calls.toString());
        assertTrue(f.world.calls.contains("pull A 0.6 -> 1.0"), f.world.calls.toString());
    }

    @Test
    @DisplayName("притяжение несколькими тиками откладывает последующие импульсы")
    void pullSpreadsOverTicks() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: $radius }
                    do:
                      - { action: pull, strength: 0.6, ticks: 3 }
                """);
        Fixture f = fixture(skill);
        f.world.nextTargets = List.of(A);

        f.runtime.cast(CASTER, skill, 1);

        assertEquals(1, f.world.calls.stream().filter(c -> c.startsWith("pull")).count(),
                "первый импульс сразу");
        assertEquals(2, f.world.calls.stream().filter(c -> c.startsWith("later")).count(),
                "остальные отложены");
    }

    @Test
    @DisplayName("временный стат ставится и снимается по таймеру")
    void modifyStatIsTemporary() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: $radius }
                    do:
                      - { action: modify-stat, stat: magic_damage, op: flat, value: -30, duration: 40 }
                """);
        Fixture f = fixture(skill);
        f.world.nextTargets = List.of(A);

        f.runtime.cast(CASTER, skill, 1);
        assertEquals(-30, f.stats.snapshot(A).get("magic_damage"), 1e-9);

        f.world.runAllDelayed();
        assertEquals(0, f.stats.snapshot(A).get("magic_damage"), 1e-9, "снят по таймеру");
    }

    @Test
    @DisplayName("луч выполняет навык в точке попадания, и там есть точка действия")
    void rayCastsSubSkillAtImpact() {
        SkillDef hit = parse("boom", """
                id: boom
                class: mage
                steps:
                  - target: { type: enemies_near_origin, radius: 4 }
                    do:
                      - { action: damage, amount: 7 }
                """);
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: ray, range: 20, on-hit: boom }
                """);
        Fixture f = fixture(skill, hit);
        f.world.nextTargets = List.of(A);

        f.runtime.cast(CASTER, skill, 1);

        assertTrue(f.world.calls.contains("ray 20.0"), f.world.calls.toString());
        assertTrue(f.world.calls.stream().anyMatch(c -> c.contains("origin=10.0")),
                "подчинённый навык получил точку попадания: " + f.world.calls);
        assertTrue(f.world.calls.contains("damage A 7.0 MAGIC"));
    }

    @Test
    @DisplayName("частицы в точке действия рисуются там, а не на целях")
    void particlesAtOrigin() {
        SkillDef hit = parse("boom", """
                id: boom
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: particles, particle: flame, shape: ring, count: 20, size: 3, at-origin: true }
                """);
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: ray, range: 20, on-hit: boom }
                """);
        Fixture f = fixture(skill, hit);

        f.runtime.cast(CASTER, skill, 1);

        assertTrue(f.world.calls.contains("particles flame RING x20 @10.0"),
                f.world.calls.toString());
    }

    @Test
    @DisplayName("навык не может звать сам себя бесконечно")
    void recursionIsBounded() {
        SkillDef loop = parse("loop", """
                id: loop
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: cast, skill: loop, at-targets: true }
                      - { action: message, text: "виток" }
                """);
        Fixture f = fixture(loop);

        f.runtime.cast(CASTER, loop, 1);

        long laps = f.world.calls.stream().filter(c -> c.contains("виток")).count();
        assertTrue(laps > 0 && laps <= 10, "витков должно быть конечное число, было " + laps);
    }

    @Test
    @DisplayName("при промахе визуал в точке действия всё равно рисуется")
    void visualsSurviveEmptyTargets() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: $radius }
                    do:
                      - { action: damage, amount: 5 }
                      - { action: particles, particle: flame, shape: sphere, count: 8, at-origin: true }
                      - { action: sound, sound: boom, at-origin: true }
                """);
        Fixture f = fixture(skill);
        f.world.nextTargets = List.of();

        f.runtime.cast(CASTER, skill, 1);

        assertFalse(f.world.calls.stream().anyMatch(c -> c.startsWith("damage")),
                "бить некого");
        assertTrue(f.world.calls.contains("particles flame SPHERE x8 @1.0"),
                "промах обязан быть видно: " + f.world.calls);
        assertTrue(f.world.calls.contains("sound boom @1.0"), f.world.calls.toString());
    }

    // ------------------------------------------------------------------ зоны

    @Test
    @DisplayName("печать ставится в точке действия и сразу видна условию")
    void zoneIsPlacedAndSeen() {
        SkillDef place = parse("place", """
                id: place
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: zone, tag: seal, radius: 4, duration: 200, at-origin: true }
                """);
        SkillDef check = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: self }
                    if:
                      - { caster: in-zone, value: "seal:own" }
                    do:
                      - { action: message, text: "усилен" }
                """);
        Fixture f = fixture(place, check);

        f.runtime.cast(CASTER, check, 1);
        assertFalse(f.world.calls.contains("message caster усилен"), "печати ещё нет");

        f.runtime.cast(CASTER, place, 1);
        f.runtime.cast(CASTER, check, 1);

        assertTrue(f.world.calls.contains("message caster усилен"),
                "печать обязана быть видна сразу: " + f.world.calls);
    }

    @Test
    @DisplayName("чужая печать своего каста не усиливает")
    void foreignZoneDoesNotEmpower() {
        SkillDef check = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: self }
                    if:
                      - { caster: in-zone, value: "seal:own" }
                    do:
                      - { action: message, text: "усилен" }
                """);
        Fixture f = fixture(check);
        f.zones.place("seal", A, new Position(WORLD, 1, 2, 3), 6, 200);

        f.runtime.cast(CASTER, check, 1);

        assertFalse(f.world.calls.contains("message caster усилен"));
    }

    @Test
    @DisplayName("снятые печати попадают в счётчик, и урон считается за каждую")
    void consumedZonesScaleDamage() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: consume-zones, tag: seal, radius: 8, counter: seals }
                  - target: { type: enemies_in_radius, radius: $radius }
                    do:
                      - { action: damage, amount: 4 * @seals }
                """);
        Fixture f = fixture(skill);
        f.world.nextTargets = List.of(A);
        Position here = new Position(WORLD, 1, 2, 3);
        f.zones.place("seal", CASTER, here, 3, 200);
        f.zones.place("seal", CASTER, here, 3, 200);
        f.zones.place("seal", CASTER, here, 3, 200);

        f.runtime.cast(CASTER, skill, 1);

        assertTrue(f.world.calls.contains("damage A 12.0 MAGIC"),
                "три печати по четыре: " + f.world.calls);
        assertEquals(0, f.zones.size(), "печати потрачены");
    }

    @Test
    @DisplayName("без печатей счётчик равен нулю, а не единице")
    void missingZonesMeanZero() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: consume-zones, tag: seal, radius: 8, counter: seals }
                  - target: { type: enemies_in_radius, radius: $radius }
                    do:
                      - { action: damage, amount: 4 * @seals }
                """);
        Fixture f = fixture(skill);
        f.world.nextTargets = List.of(A);

        f.runtime.cast(CASTER, skill, 1);

        assertTrue(f.world.calls.contains("damage A 0.0 MAGIC"),
                "ноль виден сразу, а «как будто одна печать» пришлось бы искать по логам: "
                        + f.world.calls);
    }

    @Test
    @DisplayName("счётчик виден подчинённому навыку, а не теряется на границе")
    void counterSurvivesSubSkill() {
        SkillDef hit = parse("boom", """
                id: boom
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: 5 }
                    do:
                      - { action: damage, amount: 2 * @seals }
                """);
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: consume-zones, tag: seal, radius: 8, counter: seals }
                      - { action: cast, skill: boom, at-targets: true }
                """);
        Fixture f = fixture(skill, hit);
        f.world.nextTargets = List.of(A);
        f.zones.place("seal", CASTER, new Position(WORLD, 1, 2, 3), 3, 200);
        f.zones.place("seal", CASTER, new Position(WORLD, 1, 2, 3), 3, 200);

        f.runtime.cast(CASTER, skill, 1);

        assertTrue(f.world.calls.contains("damage A 4.0 MAGIC"), f.world.calls.toString());
    }

    @Test
    @DisplayName("условие на число печатей требует именно столько")
    void zoneCountCondition() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: self }
                    if:
                      - { caster: zone-count, value: "seal:2" }
                    do:
                      - { action: message, text: "две" }
                """);
        Fixture f = fixture(skill);
        Position here = new Position(WORLD, 1, 2, 3);
        f.zones.place("seal", CASTER, here, 3, 200);

        f.runtime.cast(CASTER, skill, 1);
        assertFalse(f.world.calls.contains("message caster две"));

        f.zones.place("seal", CASTER, here, 3, 200);
        f.runtime.cast(CASTER, skill, 1);
        assertTrue(f.world.calls.contains("message caster две"));
    }

    // ------------------------------------------------------------------ снаряд и призыв

    @Test
    @DisplayName("снаряд уходит с посчитанными числами и бьёт в точке попадания")
    void projectileHitsAtImpact() {
        SkillDef impact = parse("boom", """
                id: boom
                class: mage
                steps:
                  - target: { type: enemies_near_origin, radius: 3 }
                    do:
                      - { action: damage, amount: 9 }
                """);
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: projectile, speed: 2.5, range: 30, pierce: 2, on-hit: boom }
                """);
        Fixture f = fixture(skill, impact);
        f.world.nextTargets = List.of(A);

        f.runtime.cast(CASTER, skill, 1);

        assertEquals(2.5, f.world.lastProjectile.speed(), 1e-9);
        assertEquals(30, f.world.lastProjectile.range(), 1e-9);
        assertEquals(2, f.world.lastProjectile.pierce());
        assertTrue(f.world.calls.stream().anyMatch(c -> c.contains("origin=7.0")),
                "навык попадания получил точку попадания: " + f.world.calls);
        assertTrue(f.world.calls.contains("damage A 9.0 MAGIC"));
    }

    @Test
    @DisplayName("промах выполняет навык конца, а не навык попадания")
    void projectileMissRunsOnEnd() {
        SkillDef impact = parse("boom", """
                id: boom
                class: mage
                steps:
                  - target: { type: enemies_near_origin, radius: 3 }
                    do:
                      - { action: damage, amount: 9 }
                """);
        SkillDef fizzle = parse("fizzle", """
                id: fizzle
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: message, text: "мимо" }
                """);
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: projectile, range: 30, on-hit: boom, on-end: fizzle }
                """);
        Fixture f = fixture(skill, impact, fizzle);
        f.world.nextProjectileTarget = null;

        f.runtime.cast(CASTER, skill, 1);

        assertTrue(f.world.calls.contains("message caster мимо"), f.world.calls.toString());
        assertFalse(f.world.calls.stream().anyMatch(c -> c.startsWith("damage")),
                "промах никого не задел");
    }

    @Test
    @DisplayName("призванный берётся на учёт с владельцем и снимается по сроку")
    void summonIsOwnedAndExpires() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: summon, mob: wolf, tag: beast, duration: 200, health: 40 }
                """);
        Fixture f = fixture(skill);

        f.runtime.cast(CASTER, skill, 1);

        assertEquals(List.of("wolf"), f.world.spawned);
        assertEquals(1, f.minions.size());
        assertTrue(f.minions.of(f.world.nextSpawn).orElseThrow().ownedBy(CASTER),
                "владелец записан сразу, а не штампуется обработчиком спавна");

        f.world.runAllDelayed();
        assertEquals(0, f.minions.size(), "срок жизни снял существо");
        assertTrue(f.world.calls.contains("despawn"));
    }

    @Test
    @DisplayName("свой призванный не попадает под свои площадные навыки")
    void ownMinionIsNotAnEnemy() {
        // Фильтр живёт в реализации мира, поэтому здесь проверяется само
        // правило службы: именно его читает BukkitSkillWorld.
        Fixture f = fixture();
        f.minions.register(A, CASTER, "beast", 200, true);

        assertTrue(f.minions.isOwnMinion(CASTER, A));
        assertFalse(f.minions.isOwnMinion(B, A), "чужой зверь — обычная цель");
    }

    @Test
    @DisplayName("отзыв убирает своих призванных с этим тегом")
    void dismissRemovesOwnMinions() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: dismiss, tag: beast }
                """);
        Fixture f = fixture(skill);
        f.minions.register(A, CASTER, "beast", 200, true);
        f.minions.register(B, CASTER, "other", 200, true);

        f.runtime.cast(CASTER, skill, 1);

        assertEquals(1, f.minions.size(), "чужой тег остался");
        assertTrue(f.minions.of(B).isPresent());
    }

    // ------------------------------------------------------------------ прочее

    @Test
    @DisplayName("шаг с задержкой откладывается, а не выполняется сразу")
    void delayedStepIsScheduled() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: self }
                    delay: 10
                    do:
                      - { action: message, text: "позже" }
                """);
        Fixture f = fixture(skill);

        f.runtime.cast(CASTER, skill, 1);
        assertEquals(List.of("later 10"), f.world.calls);

        f.world.runAllDelayed();
        assertTrue(f.world.calls.contains("message caster позже"), f.world.calls.toString());
    }

    @Test
    @DisplayName("статусы накладываются с источником навыка")
    void statusCarriesSkillAsSource() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: $radius }
                    do:
                      - { action: status, id: mark, duration: 100 }
                """);
        Fixture f = fixture(skill);
        f.world.nextTargets = List.of(A);

        f.runtime.cast(CASTER, skill, 1);

        assertTrue(f.statuses.isActing(A, "mark"));
        assertEquals(List.of("skill:test_skill"), List.copyOf(f.statuses.sources(A)));
    }

    @Test
    @DisplayName("щит накладывается с запасом из amount")
    void shieldCarriesAmount() {
        SkillDef skill = parse("test_skill", """
                id: test_skill
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: $radius }
                    do:
                      - { action: status, id: shield, duration: 100, amount: 25 }
                """);
        Fixture f = fixture(skill);
        f.world.nextTargets = List.of(A);

        f.runtime.cast(CASTER, skill, 1);

        assertEquals(25, f.statuses.defenderState(A).shieldPool(), 1e-9);
    }
}
