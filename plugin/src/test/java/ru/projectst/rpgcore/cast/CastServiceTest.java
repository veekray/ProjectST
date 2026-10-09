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
import ru.projectst.rpgcore.classes.ResourceSpec;
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
import ru.projectst.rpgcore.skill.SkillTrigger;
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
        @Override
        public void sacrifice(UUID target, double share) {
        }

        @Override
        public void glow(UUID target, int ticks) {
        }

        @Override
        public void disableShield(UUID target, int ticks) {
        }


        @Override
        public void confuse(UUID target, double radius) {
        }

        @Override
        public boolean isBehind(UUID observer, UUID subject, double arcDegrees) {
            return false;
        }

        @Override
        public double maxHealthOf(UUID entity) {
            return 20;
        }

        @Override
        public double healthOf(UUID entity) {
            return 20;
        }

        @Override
        public void swap(UUID first, UUID second) {
        }

        @Override
        public void scatter(UUID target, double radius) {
        }

        @Override
        public void clearThreat(UUID caster, double radius) {
        }

        final List<Double> damage = new ArrayList<>();
        /** Что случается в момент нанесения урона: нужно для проверки рекурсии. */
        Runnable onDamage;

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
        public ru.projectst.rpgcore.damage.DamageResult dealDamage(UUID caster, UUID target,
                                                                   double amount,
                                                                   DamageSchool school,
                                                                   String skillId) {
            damage.add(amount);
            if (onDamage != null) {
                onDamage.run();
            }
            return null;
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
        public void clearPotion(UUID target, String effect) {
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

        /** Рывки: сколько их было и куда шёл последний. */
        int dashes;
        ru.projectst.rpgcore.skill.Heading lastHeading;

        @Override
        public void dash(UUID entity, double strength, double lift,
                         ru.projectst.rpgcore.skill.Heading heading) {
            dashes++;
            lastHeading = heading;
        }

        @Override
        public Optional<Position> offsetOf(UUID entity, double distance, boolean behind) {
            return positionOf(entity);
        }

        @Override
        public Optional<UUID> spawnMob(String type, Position at, double health) {
            return Optional.empty();
        }

        @Override
        public void despawn(UUID entity) {
        }

        @Override
        public void setAttackTarget(UUID mob, UUID target) {
        }

        @Override
        public void launchProjectile(UUID caster, ru.projectst.rpgcore.skill.ProjectileSpec spec,
                                     ProjectileHandler handler) {
            handler.hit(new Position(UUID.randomUUID(), 0, 0, 0), ENEMY);
        }

        @Override
        public void particles(Position at, String particle, Action.Particles.Shape shape,
                              int count, double size) {
        }

        @Override
        public void sound(Position at, String sound, double volume, double pitch) {
        }

        @Override
        public void effect(ru.projectst.rpgcore.skill.FxEvent event) {
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
            cost: $mana
            cooldown: $cooldown
            steps:
              - target: { type: enemies_in_radius, radius: 5 }
                do:
                  - { action: damage, amount: $damage }
            """;

    /** Пассивка: когда по мне попали, бью ударившего. */
    private static final String THORNS = """
            id: thorns
            class: mage
            tier: 1
            on: damaged
            cost: 0
            cooldown: 0
            steps:
              - target: { type: trigger }
                do:
                  - { action: damage, amount: 3 }
            """;

    /** Периодическая пассивка. */
    private static final String AURA = """
            id: aura
            class: mage
            tier: 1
            on: interval
            every: 40
            cost: 0
            cooldown: 0
            steps:
              - target: { type: self }
                do:
                  - { action: heal, amount: 1 }
            """;

    /**
     * Врождённый рывок: без класса, с зарядами, за выносливость.
     *
     * <p>Повторяет поставляемый контент ровно в том, что проверяется: заряды,
     * второй запас и направление по ходу.
     */
    private static final String DASH = """
            id: dash
            innate: true
            charges: 3
            stamina: $stamina
            cooldown: $cooldown
            steps:
              - target: { type: self }
                do:
                  - { action: dash, strength: 1.5, direction: movement }
            """;

    private static final String BALANCE = """
            balance:
              bolt:
                mana: 20
                cooldown: 5
                damage:
                  base: 10
                  per-level: 5
              dash:
                stamina: 20
                cooldown: 6
            """;

    private PlayerDataStore data;
    private StatService stats;
    private StatusService statuses;
    private StatusRegistry statusDefs;
    private ClassService classes;
    private CastService casts;
    private ResourcePool mana;
    private CooldownTracker cooldowns;
    private FakeWorld world;
    private SkillDef skill;
    private ZoneService zones;
    private ru.projectst.rpgcore.skill.MinionService minions;
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
                  max_stamina:
                    base: 100
                    min: 0
                    max: 10000
                  stamina_regen:
                    base: 4
                    min: 0
                    max: 200
                  cooldown_reduction:
                    base: 0
                    min: 0
                    max: 10000
                    effect:
                      cap: 60
                      text: "N% к времени перезарядки"
                      inverted: true
                """, errors).orElseThrow();
        stats = new StatService(new StatEngine(statRegistry));

        statusDefs = StatusDefLoader.load("statuses.yml", """
                statuses:
                  silence:
                    category: debuff
                    duration: 60
                    tags: [blocks-cast]
                  root:
                    category: control
                    duration: 60
                    tags: [immobilize]
                  mark:
                    category: mark
                    duration: 60
                """, errors).orElseThrow();
        statuses = new StatusService(statusDefs, () -> tick);

        skill = SkillLoader.load("bolt.yml", SKILL, errors).orElseThrow();
        SkillDef thorns = SkillLoader.load("thorns.yml", THORNS, errors).orElseThrow();
        SkillDef aura = SkillLoader.load("aura.yml", AURA, errors).orElseThrow();
        Map<String, SkillDef> byId = new LinkedHashMap<>();
        byId.put("bolt", skill);
        byId.put("thorns", thorns);
        byId.put("aura", aura);
        byId.put("dash", SkillLoader.load("dash.yml", DASH, errors).orElseThrow());
        SkillRegistry skills = new SkillRegistry(byId);
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
        minions = new ru.projectst.rpgcore.skill.MinionService(() -> tick);
        SkillRuntime runtime = new SkillRuntime(world, statuses, stats, balance, skills,
                zones, minions, () -> 0.0);
        mana = new ResourcePool(stats, classes);
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

    // ------------------------------------------------------------------ рывок

    @Test
    @DisplayName("врождённый рывок работает без класса и без изучения")
    void innateNeedsNeitherClassNorUnlock() {
        UUID stranger = UUID.randomUUID();

        CastOutcome out = casts.castInnate(stranger, null);

        assertEquals(CastOutcome.Kind.CAST, out.kind(), out::toString);
        assertEquals(1, world.dashes, "иначе игрок до выбора класса остался бы без движения");
    }

    @Test
    @DisplayName("три заряда — три рывка подряд, четвёртый отказывает")
    void threeChargesThenRefusal() {
        for (int i = 1; i <= 3; i++) {
            assertEquals(CastOutcome.Kind.CAST, casts.castInnate(PLAYER, null).kind(),
                    "рывок номер " + i);
        }

        CastOutcome fourth = casts.castInnate(PLAYER, null);

        assertEquals(CastOutcome.Kind.ON_COOLDOWN, fourth.kind(), fourth::toString);
        assertTrue(fourth.toString().contains("зарядов нет"), fourth::toString);
        assertEquals(3, world.dashes, "четвёртого рывка не было");
        assertEquals(40, mana.current(PLAYER, ResourceSpec.STAMINA), 1e-9,
                "сто минус три по двадцать");
    }

    @Test
    @DisplayName("заряды возвращаются по одному, считая от своей траты")
    void chargesReturnOneByOne() {
        casts.castInnate(PLAYER, null);
        tick += 10;
        casts.castInnate(PLAYER, null);

        // Первый заряд потрачен в нулевой тик, второй — в десятый. Шесть секунд
        // — это сто двадцать тиков, поэтому к тику 125 вернулся только первый.
        tick += 115;

        assertEquals(2, cooldowns.freeCharges(PLAYER, "dash", 3),
                "один заряд ещё в пути, два свободны");
        tick += 10;
        assertEquals(3, cooldowns.freeCharges(PLAYER, "dash", 3), "вернулись оба");
    }

    @Test
    @DisplayName("нехватка выносливости названа своим словом и ничего не тратит")
    void notEnoughStaminaIsExplained() {
        mana.spend(PLAYER, ResourceSpec.STAMINA, 85);
        double resourceBefore = mana.current(PLAYER);

        CastOutcome out = casts.castInnate(PLAYER, null);

        assertEquals(CastOutcome.Kind.NOT_ENOUGH_RESOURCE, out.kind(), out::toString);
        assertTrue(out.toString().toLowerCase(java.util.Locale.ROOT).contains("выносливость"),
                out::toString);
        assertEquals(15, mana.current(PLAYER, ResourceSpec.STAMINA), 1e-9,
                "отказ не трогает выносливость");
        assertEquals(resourceBefore, mana.current(PLAYER), 1e-9,
                "и ресурс класса тем более: рывок его не касается");
        assertEquals(0, world.dashes);
    }

    @Test
    @DisplayName("ресурс класса и выносливость — разные запасы и не трогают друг друга")
    void poolsAreSeparate() {
        casts.cast(PLAYER, "bolt");

        assertEquals(80, mana.current(PLAYER), 1e-9, "мана ушла на навык");
        assertEquals(100, mana.current(PLAYER, ResourceSpec.STAMINA), 1e-9,
                "выносливость навык класса не трогает");

        casts.castInnate(PLAYER, null);

        assertEquals(80, mana.current(PLAYER), 1e-9, "рывок ману не трогает");
        assertEquals(80, mana.current(PLAYER, ResourceSpec.STAMINA), 1e-9);
    }

    @Test
    @DisplayName("направление хода доходит до рывка как есть")
    void headingReachesTheDash() {
        casts.castInnate(PLAYER, new ru.projectst.rpgcore.skill.Heading(0, -1));

        assertEquals(new ru.projectst.rpgcore.skill.Heading(0, -1), world.lastHeading,
                "иначе рывок ушёл бы по взгляду, а игрок просил назад");
    }

    @Test
    @DisplayName("под корнями рывка нет, но ударить можно")
    void rootHoldsTheDashButNotTheSpell() {
        statuses.apply(PLAYER, new StatusApplication("root", 60, 0, "test"));

        CastOutcome dash = casts.castInnate(PLAYER, null);

        assertEquals(CastOutcome.Kind.BLOCKED, dash.kind(), dash::toString);
        assertTrue(dash.toString().contains("root"), dash::toString);
        assertEquals(0, world.dashes, "иначе рывок был бы бесплатным снятием корней");
        assertEquals(100, mana.current(PLAYER, ResourceSpec.STAMINA), 1e-9,
                "отказ не тратит выносливость");

        // Корни запрещают ходить, а не бить: у них нет метки blocks-cast, и
        // навык, который никого не двигает, под ними работает.
        assertEquals(CastOutcome.Kind.CAST, casts.cast(PLAYER, "bolt").kind());
    }

    @Test
    @DisplayName("рывок по имени идёт тем же путём: класс для него не нужен")
    void innateByNameWorks() {
        UUID stranger = UUID.randomUUID();

        CastOutcome out = casts.cast(stranger, "dash");

        assertEquals(CastOutcome.Kind.CAST, out.kind(), out::toString);
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

        assertEquals(CastOutcome.Kind.NOT_ENOUGH_RESOURCE, out.kind());
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
    @DisplayName("сокращение перезарядки считается кривой и не доходит до потолка")
    void cooldownReductionFollowsTheCurve() {
        // Шестьдесят рейтинга при потолке шестьдесят — это тридцать процентов:
        // 60 * 60 / 120. Перезарядка навыка — пять секунд, то есть сто тиков.
        stats.setSource(PLAYER, "test",
                List.of(new StatModifier("cooldown_reduction", StatOp.FLAT, 60, "test")));
        assertEquals(70, casts.cooldownTicks(PLAYER, skill, 1), "сто тиков минус тридцать");

        // Сколько бы рейтинга ни набрали, кривая к потолку только стремится:
        // отдельный предел сверху не нужен, и навык не начнёт стрелять каждый тик.
        stats.setSource(PLAYER, "test",
                List.of(new StatModifier("cooldown_reduction", StatOp.FLAT, 1_000_000, "test")));
        long huge = casts.cooldownTicks(PLAYER, skill, 1);
        assertEquals(40, huge, "шестьдесят процентов — предел, которого кривая не достигает");
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

    // ------------------------------------------------------------------ триггеры

    @Test
    @DisplayName("пассивка срабатывает по событию и бьёт того, кто ударил")
    void passiveFiresOnTrigger() {
        assertTrue(classes.unlock(PLAYER, "thorns").succeeded());

        var out = casts.fire(PLAYER, SkillTrigger.ON_DAMAGED, ENEMY);

        assertEquals(1, out.size(), out::toString);
        assertEquals(CastOutcome.Kind.CAST, out.get(0).kind(), out::toString);
        assertEquals(List.of(3.0), world.damage, "урон ушёл по триггеру");
    }

    @Test
    @DisplayName("не изученная пассивка не срабатывает")
    void passiveNeedsUnlock() {
        var out = casts.fire(PLAYER, SkillTrigger.ON_DAMAGED, ENEMY);

        assertTrue(out.isEmpty(), out::toString);
        assertTrue(world.damage.isEmpty());
    }

    @Test
    @DisplayName("чужой триггер пассивку не трогает")
    void wrongTriggerDoesNothing() {
        assertTrue(classes.unlock(PLAYER, "thorns").succeeded());

        assertTrue(casts.fire(PLAYER, SkillTrigger.ON_KILL, ENEMY).isEmpty());
        assertTrue(world.damage.isEmpty());
    }

    @Test
    @DisplayName("пассивка, наносящая урон по триггеру урона, не зовёт себя бесконечно")
    void reentryIsBlocked() {
        assertTrue(classes.unlock(PLAYER, "thorns").succeeded());

        // Изнутри каста приходит то же событие: ровно так выглядит отдача по
        // отдаче. Второй вход обязан быть отброшен.
        world.onDamage = () -> casts.fire(PLAYER, SkillTrigger.ON_DAMAGED, ENEMY);
        casts.fire(PLAYER, SkillTrigger.ON_DAMAGED, ENEMY);

        assertEquals(1, world.damage.size(), "один удар, а не лавина: " + world.damage);
    }

    @Test
    @DisplayName("периодическая пассивка держит свой промежуток перезарядкой")
    void intervalKeepsItsPeriod() {
        assertTrue(classes.unlock(PLAYER, "aura").succeeded());

        assertEquals(CastOutcome.Kind.CAST,
                casts.fire(PLAYER, SkillTrigger.ON_INTERVAL, null).get(0).kind());
        assertEquals(40, cooldowns.remaining(PLAYER, "aura"),
                "у навыка нулевая перезарядка, промежуток держит её сам");

        assertTrue(casts.fire(PLAYER, SkillTrigger.ON_INTERVAL, null).isEmpty(),
                "отказ по перезарядке не попадает в ответ: пассивки не шумят");

        tick += 40;
        assertEquals(CastOutcome.Kind.CAST,
                casts.fire(PLAYER, SkillTrigger.ON_INTERVAL, null).get(0).kind());
    }

    @Test
    @DisplayName("пассивку нельзя применить вручную, и причина названа")
    void passiveCannotBeCastManually() {
        assertTrue(classes.unlock(PLAYER, "thorns").succeeded());

        CastOutcome out = casts.cast(PLAYER, "thorns");

        assertEquals(CastOutcome.Kind.NOT_MANUAL, out.kind(), out::toString);
        assertTrue(out.detail().contains("damaged"), out.detail());
        assertTrue(world.damage.isEmpty());
    }

    @Test
    @DisplayName("пассивку нельзя повесить на слот: кнопка не делала бы ничего")
    void passiveCannotBeBound() {
        assertTrue(classes.unlock(PLAYER, "thorns").succeeded());

        var out = classes.bind(PLAYER, 1, "thorns");

        assertEquals(ru.projectst.rpgcore.classes.ClassOutcome.Bind.Kind.PASSIVE_SKILL,
                out.kind(), out::toString);
    }

    @Test
    @DisplayName("тишина глушит и пассивки тоже")
    void silenceBlocksPassives() {
        assertTrue(classes.unlock(PLAYER, "thorns").succeeded());
        statuses.apply(PLAYER, StatusApplication.of("silence", "test"));

        var out = casts.fire(PLAYER, SkillTrigger.ON_DAMAGED, ENEMY);

        assertEquals(CastOutcome.Kind.BLOCKED, out.get(0).kind(), out::toString);
        assertTrue(world.damage.isEmpty());
    }
}
