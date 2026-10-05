package ru.projectst.rpgcore.cast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.projectst.rpgcore.balance.BalanceBook;
import ru.projectst.rpgcore.balance.BalanceLoader;
import ru.projectst.rpgcore.classes.ClassDefLoader;
import ru.projectst.rpgcore.classes.ClassRegistry;
import ru.projectst.rpgcore.classes.ClassService;
import ru.projectst.rpgcore.damage.DamageSchool;
import ru.projectst.rpgcore.data.PlayerDataStore;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.skill.Action;
import ru.projectst.rpgcore.skill.CastContext;
import ru.projectst.rpgcore.skill.Position;
import ru.projectst.rpgcore.skill.SkillDef;
import ru.projectst.rpgcore.skill.SkillLoader;
import ru.projectst.rpgcore.skill.SkillRegistry;
import ru.projectst.rpgcore.skill.SkillRuntime;
import ru.projectst.rpgcore.skill.SkillWorld;
import ru.projectst.rpgcore.skill.TargetSpec;
import ru.projectst.rpgcore.skill.ZoneService;
import ru.projectst.rpgcore.stat.StatEngine;
import ru.projectst.rpgcore.stat.StatModifier;
import ru.projectst.rpgcore.stat.StatOp;
import ru.projectst.rpgcore.stat.StatService;
import ru.projectst.rpgcore.status.StatusApplication;
import ru.projectst.rpgcore.status.StatusDefLoader;
import ru.projectst.rpgcore.status.StatusRegistry;
import ru.projectst.rpgcore.status.StatusService;

/**
 * Ворота каста.
 *
 * <p>Проверяется не только «срабатывает или нет», но и <b>какая именно</b>
 * причина названа при отказе, и что отказ ничего не потратил. Ради этого проект
 * и затевался: нажатие впустую без объяснения было главной жалобой на старый
 * стек, а списанная мана при отказе — худшим из возможных последствий.
 */
class CastServiceTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID ENEMY = UUID.randomUUID();

    /** Мир, который только считает нанесённый урон. */
    private static final class FakeWorld implements SkillWorld {
        final List<Double> damage = new ArrayList<>();

        @Override
        public List<UUID> resolveTargets(CastContext context, TargetSpec.Type type,
                                         double radius, double angle) {
            return type == TargetSpec.Type.SELF ? List.of(context.caster()) : List.of(ENEMY);
        }

        @Override
        public Optional<Position> positionOf(UUID entity) {
            return Optional.of(new Position(UUID.randomUUID(), 0, 0, 0));
        }

        @Override
        public boolean isPlayer(UUID entity) {
            return entity.equals(PLAYER);
        }

        @Override
        public void dealDamage(UUID caster, UUID target, double amount,
                               DamageSchool school, String skillId) {
            damage.add(amount);
        }

        @Override
        public void heal(UUID target, double amount) {
        }

        @Override
        public void message(UUID target, String text) {
        }

        @Override
        public void potion(UUID target, String effect, int durationTicks, int amplifier) {
        }

        @Override
        public void push(UUID target, Position from, double strength, double lift) {
        }

        @Override
        public void pullTowards(UUID target, Position to, double strength) {
        }

        @Override
        public void teleport(UUID target, Position to) {
        }

        @Override
        public Optional<Position> forwardOf(UUID entity, double distance) {
            return positionOf(entity);
        }

        @Override
        public RayHit castRay(UUID caster, double range, boolean stopAtEntity) {
            return new RayHit(positionOf(caster).orElseThrow(), ENEMY);
        }

        @Override
        public void particles(Position at, String particle, Action.Particles.Shape shape,
                              int count, double size) {
        }

        @Override
        public void sound(Position at, String sound, double volume, double pitch) {
        }

        @Override
        public void runLater(int ticks, Runnable task) {
            task.run();
        }
    }

    private static final String SKILL = """
            id: bolt
            class: mage
            tier: 1
            mana: $mana
            cooldown: $cooldown
            steps:
              - target: { type: enemies_in_radius, radius: 5 }
                do:
                  - { action: damage, amount: $damage }
            """;

    private static final String BALANCE = """
            balance:
              bolt:
                mana: 20
                cooldown: 5
                damage:
                  base: 10
                  per-level: 5
            """;

    private PlayerDataStore data;
    private StatService stats;
    private StatusService statuses;
    private StatusRegistry statusDefs;
    private ClassService classes;
    private CastService casts;
    private ManaPool mana;
    private CooldownTracker cooldowns;
    private FakeWorld world;
    private SkillDef skill;
    private ZoneService zones;
    private long tick;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        ContentErrors errors = new ContentErrors();

        var statRegistry = ru.projectst.rpgcore.stat.StatDefLoader.load("stats.yml", """
                stats:
                  max_mana:
                    base: 100
                    min: 0
                    max: 10000
                  mana_regen:
                    base: 2
                    min: 0
                    max: 200
                  cooldown_reduction:
                    base: 0
                    min: 0
                    max: 90
                """, errors).orElseThrow();
        stats = new StatService(new StatEngine(statRegistry));

        statusDefs = StatusDefLoader.load("statuses.yml", """
                statuses:
                  silence:
                    category: debuff
                    duration: 60
                    tags: [blocks-cast]
                  mark:
                    category: mark
                    duration: 60
                """, errors).orElseThrow();
        statuses = new StatusService(statusDefs, () -> tick);

        skill = SkillLoader.load("bolt.yml", SKILL, errors).orElseThrow();
        SkillRegistry skills = new SkillRegistry(Map.of("bolt", skill));
        BalanceBook balance = BalanceLoader.load("balance.yml", BALANCE, errors).orElseThrow();
        assertTrue(errors.isEmpty(), () -> errors.all().toString());

        Map<String, ru.projectst.rpgcore.classes.ClassDef> defs = new LinkedHashMap<>();
        var mage = ClassDefLoader.load("mage.yml", """
                id: mage
                slots: 3
                tiers:
                  1: 1
                """, errors).orElseThrow();
        defs.put(mage.id(), mage);

        data = new PlayerDataStore(dir.resolve("players"), message -> {
            throw new AssertionError(message);
        });
        classes = new ClassService(new ClassRegistry(defs), skills, data, stats);
        classes.setClass(PLAYER, "mage");
        classes.grantPoints(PLAYER, 5);
        assertTrue(classes.unlock(PLAYER, "bolt").succeeded());

        world = new FakeWorld();
        zones = new ZoneService(() -> tick);
        SkillRuntime runtime = new SkillRuntime(world, statuses, stats, balance, skills,
                zones, () -> 0.0);
        mana = new ManaPool(stats);
        cooldowns = new CooldownTracker(() -> tick);
        casts = new CastService(classes, skills, balance, statuses, statusDefs, stats,
                mana, cooldowns, runtime);
    }

    @AfterEach
    void tearDown() throws IOException {
        data.shutdown();
    }

    // ------------------------------------------------------------------ успех

    @Test
    @DisplayName("успешный каст списывает ману и запускает перезарядку")
    void successSpendsAndStartsCooldown() {
        CastOutcome out = casts.cast(PLAYER, "bolt");

        assertEquals(CastOutcome.Kind.CAST, out.kind(), out::toString);
        assertEquals(80, mana.current(PLAYER), 1e-9, "100 минус 20");
        assertEquals(100, cooldowns.remaining(PLAYER, "bolt"), "5 секунд — это 100 тиков");
        assertEquals(List.of(10.0), world.damage, "первый уровень навыка");
    }

    @Test
    @DisplayName("уровень навыка берётся из вложенных очков, а не из уровня игрока")
    void levelComesFromInvestedPoints() {
        assertTrue(classes.upgrade(PLAYER, "bolt").succeeded());
        assertTrue(classes.upgrade(PLAYER, "bolt").succeeded());

        casts.cast(PLAYER, "bolt");

        assertEquals(List.of(20.0), world.damage, "10 + 5 * 2 на третьем уровне");
    }

    // ------------------------------------------------------------------ отказы

    @Test
    @DisplayName("перезарядка отказывает и не списывает ману второй раз")
    void cooldownRefusesWithoutSpending() {
        casts.cast(PLAYER, "bolt");
        double after = mana.current(PLAYER);

        CastOutcome out = casts.cast(PLAYER, "bolt");

        assertEquals(CastOutcome.Kind.ON_COOLDOWN, out.kind(), out::toString);
        assertEquals(after, mana.current(PLAYER), 1e-9, "отказ не трогает ману");
        assertEquals(1, world.damage.size(), "второго каста не было");
    }

    @Test
    @DisplayName("перезарядка кончается сама, по тому же времени, что и статусы")
    void cooldownExpires() {
        casts.cast(PLAYER, "bolt");
        tick += 100;

        assertTrue(cooldowns.ready(PLAYER, "bolt"));
        assertEquals(CastOutcome.Kind.CAST, casts.cast(PLAYER, "bolt").kind());
    }

    @Test
    @DisplayName("нехватка маны названа с числами и ничего не тратит")
    void notEnoughManaIsExplained() {
        mana.spend(PLAYER, 95);

        CastOutcome out = casts.cast(PLAYER, "bolt");

        assertEquals(CastOutcome.Kind.NOT_ENOUGH_MANA, out.kind());
        assertTrue(out.detail().contains("20"), out.detail());
        assertTrue(out.detail().contains("5"), out.detail());
        assertEquals(5, mana.current(PLAYER), 1e-9);
        assertTrue(cooldowns.ready(PLAYER, "bolt"), "перезарядка не запускалась");
        assertTrue(world.damage.isEmpty());
    }

    @Test
    @DisplayName("статус с меткой blocks-cast запрещает каст и назван по имени")
    void blockingStatusIsNamed() {
        statuses.apply(PLAYER, StatusApplication.of("silence", "test"));

        CastOutcome out = casts.cast(PLAYER, "bolt");

        assertEquals(CastOutcome.Kind.BLOCKED, out.kind());
        assertTrue(out.detail().contains("silence"), out.detail());
        assertEquals(100, mana.current(PLAYER), 1e-9);
    }

    @Test
    @DisplayName("статус без этой метки касту не мешает")
    void otherStatusesDoNotBlock() {
        statuses.apply(PLAYER, StatusApplication.of("mark", "test"));

        assertEquals(CastOutcome.Kind.CAST, casts.cast(PLAYER, "bolt").kind());
    }

    @Test
    @DisplayName("неизученный навык отказывает раньше, чем проверяется мана")
    void notUnlockedRefuses() {
        UUID other = UUID.randomUUID();
        classes.setClass(other, "mage");

        CastOutcome out = casts.cast(other, "bolt");

        assertEquals(CastOutcome.Kind.NOT_UNLOCKED, out.kind(), out::toString);
    }

    @Test
    @DisplayName("без класса каст отказывает, а не падает")
    void noClassRefuses() {
        CastOutcome out = casts.cast(UUID.randomUUID(), "bolt");

        assertEquals(CastOutcome.Kind.NO_CLASS, out.kind(), out::toString);
    }

    @Test
    @DisplayName("незагруженный навык назван отдельной причиной")
    void unknownSkillRefuses() {
        assertEquals(CastOutcome.Kind.UNKNOWN_SKILL, casts.cast(PLAYER, "нет_такого").kind());
    }

    // ------------------------------------------------------------------ слоты

    @Test
    @DisplayName("пустой слот говорит, что он пуст, а не молчит")
    void emptySlotIsNamed() {
        CastOutcome out = casts.castSlot(PLAYER, 2);

        assertEquals(CastOutcome.Kind.EMPTY_SLOT, out.kind(), out::toString);
    }

    @Test
    @DisplayName("слот вне диапазона класса называет, сколько слотов есть")
    void badSlotIsNamed() {
        CastOutcome out = casts.castSlot(PLAYER, 9);

        assertEquals(CastOutcome.Kind.BAD_SLOT, out.kind());
        assertTrue(out.detail().contains("3"), out.detail());
    }

    @Test
    @DisplayName("каст из слота идёт через те же ворота")
    void slotCastGoesThroughTheSameGate() {
        assertTrue(classes.bind(PLAYER, 1, "bolt").succeeded());

        assertEquals(CastOutcome.Kind.CAST, casts.castSlot(PLAYER, 1).kind());
        assertEquals(CastOutcome.Kind.ON_COOLDOWN, casts.castSlot(PLAYER, 1).kind());
    }

    // ------------------------------------------------------------------ числа

    @Test
    @DisplayName("сокращение перезарядки действует и ограничено сверху")
    void cooldownReductionIsCapped() {
        stats.setSource(PLAYER, "test",
                List.of(new StatModifier("cooldown_reduction", StatOp.FLAT, 50, "test")));
        assertEquals(50, casts.cooldownTicks(PLAYER, skill, 1), "половина от 100 тиков");

        stats.setSource(PLAYER, "test",
                List.of(new StatModifier("cooldown_reduction", StatOp.FLAT, 90, "test")));
        assertEquals(20, casts.cooldownTicks(PLAYER, skill, 1),
                "сокращение упирается в 80 процентов, иначе навык стрелял бы каждый тик");
    }

    @Test
    @DisplayName("запас маны следует за упавшим максимумом, а не висит выше него")
    void manaFollowsMaxDownwards() {
        stats.setSource(PLAYER, "buff",
                List.of(new StatModifier("max_mana", StatOp.FLAT, 100, "buff")));
        mana.fill(PLAYER);
        assertEquals(200, mana.current(PLAYER), 1e-9);

        stats.removeSource(PLAYER, "buff");

        assertEquals(100, mana.current(PLAYER), 1e-9, "баф снят — запас не может быть больше");
    }

    @Test
    @DisplayName("восстановление идёт по стату за секунду и не переливается через край")
    void regenRespectsMax() {
        mana.spend(PLAYER, 50);
        mana.regenerate(PLAYER, 3);
        assertEquals(56, mana.current(PLAYER), 1e-9, "2 маны в секунду");

        mana.regenerate(PLAYER, 1000);
        assertEquals(100, mana.current(PLAYER), 1e-9);
        assertFalse(mana.has(PLAYER, 101));
    }
}
