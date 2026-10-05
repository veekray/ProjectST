package ru.projectst.rpgcore.classes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.projectst.rpgcore.data.PlayerDataStore;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.skill.SkillDef;
import ru.projectst.rpgcore.skill.SkillLoader;
import ru.projectst.rpgcore.skill.SkillRegistry;
import ru.projectst.rpgcore.stat.Rounding;
import ru.projectst.rpgcore.stat.StatDef;
import ru.projectst.rpgcore.stat.StatEngine;
import ru.projectst.rpgcore.stat.StatRegistry;
import ru.projectst.rpgcore.stat.StatService;

class ClassServiceTest {

    private static final UUID PLAYER = UUID.randomUUID();

    private ClassService service;
    private StatService stats;
    private PlayerDataStore data;

    private static SkillDef skill(String id, String classId, int tier) {
        ContentErrors errors = new ContentErrors();
        return SkillLoader.load(id + ".yml", """
                id: %s
                class: %s
                tier: %d
                steps:
                  - target: { type: self }
                    do:
                      - { action: message, text: "раз" }
                """.formatted(id, classId, tier), errors).orElseThrow(
                () -> new AssertionError(errors.all().toString()));
    }

    private static ClassRegistry classes() {
        ContentErrors errors = new ContentErrors();
        ClassDef mage = ClassDefLoader.load("mage.yml", """
                id: mage
                display: "Маг"
                slots: 4
                tiers:
                  1: 1
                  2: 5
                  3: 15
                stats:
                  magic_damage: { base: 0, per-level: 0.5 }
                  defense: 5
                """, errors).orElseThrow(() -> new AssertionError(errors.all().toString()));
        ClassDef rogue = ClassDefLoader.load("rogue.yml", """
                id: rogue
                slots: 4
                max-level: 3
                points-per-level: 2
                xp:
                  base: 100
                  per-level: 100
                """, errors).orElseThrow();
        Map<String, ClassDef> map = new LinkedHashMap<>();
        map.put(mage.id(), mage);
        map.put(rogue.id(), rogue);
        return new ClassRegistry(map);
    }

    @BeforeEach
    void setUp(@TempDir Path dir) {
        Map<String, StatDef> defs = new LinkedHashMap<>();
        defs.put("magic_damage", new StatDef("magic_damage", "md", 0, -100, 1000, Rounding.NONE));
        defs.put("defense", new StatDef("defense", "def", 0, 0, 1000, Rounding.NONE));
        stats = new StatService(new StatEngine(new StatRegistry(defs)));

        Map<String, SkillDef> skills = new LinkedHashMap<>();
        skills.put("bolt", skill("bolt", "mage", 2));
        skills.put("collapse", skill("collapse", "mage", 3));
        skills.put("stab", skill("stab", "rogue", 1));

        data = new PlayerDataStore(dir, msg -> { });
        service = new ClassService(classes(), new SkillRegistry(skills), data, stats);
    }

    /**
     * Хранилище пишет в фоне. Без остановки JUnit не может удалить временный
     * каталог, пока поток записи ещё держит файл: тест падал не на проверке,
     * а на закрытии контекста.
     */
    @AfterEach
    void tearDown() {
        data.shutdown();
    }

    // ------------------------------------------------------------------ класс

    @Test
    @DisplayName("несуществующий класс не назначается")
    void unknownClassRejected() {
        assertFalse(service.setClass(PLAYER, "нет_такого"));
        assertTrue(service.classOf(PLAYER).isEmpty());
    }

    @Test
    @DisplayName("базовые статы приходят из класса и растут с уровнем")
    void baseStatsFollowLevel() {
        service.setClass(PLAYER, "mage");
        assertEquals(0, stats.snapshot(PLAYER).get("magic_damage"), 1e-9);
        assertEquals(5, stats.snapshot(PLAYER).get("defense"), 1e-9);

        service.setLevel(PLAYER, 11);

        assertEquals(5, stats.snapshot(PLAYER).get("magic_damage"), 1e-9, "0 + 0.5 * 10");
        assertEquals(5, stats.snapshot(PLAYER).get("defense"), 1e-9);
    }

    @Test
    @DisplayName("смена класса сбрасывает изученное и привязки")
    void changingClassResetsProgress() {
        service.setClass(PLAYER, "mage");
        service.setLevel(PLAYER, 20);
        service.grantPoints(PLAYER, 5);
        service.unlock(PLAYER, "bolt");
        service.bind(PLAYER, 1, "bolt");

        service.setClass(PLAYER, "rogue");

        assertTrue(data.load(PLAYER).unlockedSkills().isEmpty());
        assertTrue(data.load(PLAYER).slotBindings().isEmpty());
    }

    @Test
    @DisplayName("повторное назначение того же класса прогресс не трогает")
    void sameClassKeepsProgress() {
        service.setClass(PLAYER, "mage");
        service.setLevel(PLAYER, 20);
        service.grantPoints(PLAYER, 5);
        service.unlock(PLAYER, "bolt");

        service.setClass(PLAYER, "mage");

        assertTrue(data.load(PLAYER).isUnlocked("bolt"));
    }

    // ------------------------------------------------------------------ изучение

    @Test
    @DisplayName("изучение тратит очко")
    void unlockSpendsPoint() {
        service.setClass(PLAYER, "mage");
        service.setLevel(PLAYER, 10);
        service.grantPoints(PLAYER, 2);

        assertEquals(ClassOutcome.Unlock.Kind.UNLOCKED, service.unlock(PLAYER, "bolt").kind());
        assertEquals(1, data.load(PLAYER).unspentPoints());
    }

    @Test
    @DisplayName("без очков навык не изучить")
    void noPoints() {
        service.setClass(PLAYER, "mage");
        service.setLevel(PLAYER, 10);

        assertEquals(ClassOutcome.Unlock.Kind.NO_POINTS, service.unlock(PLAYER, "bolt").kind());
    }

    @Test
    @DisplayName("ступень требует уровня, и отказ называет требуемый")
    void tierRequiresLevel() {
        service.setClass(PLAYER, "mage");
        service.setLevel(PLAYER, 4);
        service.grantPoints(PLAYER, 5);

        ClassOutcome.Unlock out = service.unlock(PLAYER, "bolt");

        assertEquals(ClassOutcome.Unlock.Kind.LEVEL_TOO_LOW, out.kind());
        assertTrue(out.detail().contains("5"), out.detail());
        assertEquals(5, data.load(PLAYER).unspentPoints(), "очко не потрачено");
    }

    @Test
    @DisplayName("чужой навык не изучить, и отказ называет его класс")
    void foreignSkillRejected() {
        service.setClass(PLAYER, "mage");
        service.setLevel(PLAYER, 20);
        service.grantPoints(PLAYER, 5);

        ClassOutcome.Unlock out = service.unlock(PLAYER, "stab");

        assertEquals(ClassOutcome.Unlock.Kind.WRONG_CLASS, out.kind());
        assertTrue(out.detail().contains("rogue"), out.detail());
    }

    @Test
    @DisplayName("повторное изучение не тратит очко")
    void alreadyUnlockedCostsNothing() {
        service.setClass(PLAYER, "mage");
        service.setLevel(PLAYER, 10);
        service.grantPoints(PLAYER, 2);
        service.unlock(PLAYER, "bolt");

        assertEquals(ClassOutcome.Unlock.Kind.ALREADY_UNLOCKED,
                service.unlock(PLAYER, "bolt").kind());
        assertEquals(1, data.load(PLAYER).unspentPoints());
    }

    @Test
    @DisplayName("без класса изучать нечего")
    void noClassNoUnlock() {
        assertEquals(ClassOutcome.Unlock.Kind.NO_CLASS, service.unlock(PLAYER, "bolt").kind());
    }

    // ------------------------------------------------------------------ слоты

    @Test
    @DisplayName("неизученный навык на слот не вешается")
    void bindRequiresUnlock() {
        service.setClass(PLAYER, "mage");

        assertEquals(ClassOutcome.Bind.Kind.NOT_UNLOCKED, service.bind(PLAYER, 1, "bolt").kind());
    }

    @Test
    @DisplayName("слот вне диапазона отвергается и называет предел")
    void slotRangeChecked() {
        service.setClass(PLAYER, "mage");
        service.setLevel(PLAYER, 10);
        service.grantPoints(PLAYER, 1);
        service.unlock(PLAYER, "bolt");

        ClassOutcome.Bind out = service.bind(PLAYER, 9, "bolt");

        assertEquals(ClassOutcome.Bind.Kind.BAD_SLOT, out.kind());
        assertTrue(out.detail().contains("4"), out.detail());
    }

    @Test
    @DisplayName("занятый слот заменяется, и прежний навык назван")
    void bindReplacesAndNamesPrevious() {
        service.setClass(PLAYER, "mage");
        service.setLevel(PLAYER, 20);
        service.grantPoints(PLAYER, 5);
        service.unlock(PLAYER, "bolt");
        service.unlock(PLAYER, "collapse");
        service.bind(PLAYER, 1, "bolt");

        ClassOutcome.Bind out = service.bind(PLAYER, 1, "collapse");

        assertEquals(ClassOutcome.Bind.Kind.REPLACED, out.kind());
        assertEquals("bolt", out.detail());
        assertEquals("collapse", service.skillInSlot(PLAYER, 1).orElseThrow().id());
    }

    // ------------------------------------------------------------------ опыт

    @Test
    @DisplayName("опыта хватило на уровень — выданы очки и пересчитана база")
    void experienceGivesLevelAndPoints() {
        service.setClass(PLAYER, "rogue");

        var out = service.addExperience(PLAYER, 100);

        assertEquals(1, out.levelsGained());
        assertEquals(2, out.pointsGained(), "по два очка за уровень у этого класса");
        assertEquals(2, out.level());
        assertEquals(0, out.xp(), 1e-9);
        assertEquals(2, service.snapshot(PLAYER).unspentPoints());
    }

    @Test
    @DisplayName("крупная награда поднимает сразу на несколько уровней, излишек остаётся")
    void experienceCanSkipSeveralLevels() {
        service.setClass(PLAYER, "rogue");

        var out = service.addExperience(PLAYER, 350);

        assertEquals(2, out.levelsGained(), "100 на второй, 200 на третий");
        assertEquals(3, out.level());
        assertTrue(out.atMaxLevel(), "третий уровень — предел этого класса");
        assertEquals(4, service.snapshot(PLAYER).unspentPoints());
    }

    @Test
    @DisplayName("недостаточный опыт копится, уровень не растёт")
    void experienceAccumulates() {
        service.setClass(PLAYER, "rogue");

        service.addExperience(PLAYER, 40);
        var out = service.addExperience(PLAYER, 30);

        assertFalse(out.leveledUp());
        assertEquals(70, out.xp(), 1e-9);
        assertEquals(30, service.xpToNextLevel(PLAYER), 1e-9);
    }

    @Test
    @DisplayName("на пределе уровня опыт не копится совсем")
    void experienceStopsAtMaxLevel() {
        service.setClass(PLAYER, "rogue");
        service.addExperience(PLAYER, 300);

        var out = service.addExperience(PLAYER, 1000);

        assertTrue(out.atMaxLevel());
        assertEquals(0, out.levelsGained());
        assertEquals(0, service.snapshot(PLAYER).xp(), 1e-9,
                "иначе поднятый позже предел выдал бы пачку уровней сразу");
    }

    @Test
    @DisplayName("без класса опыт никуда не копится")
    void experienceNeedsClass() {
        var out = service.addExperience(PLAYER, 500);

        assertEquals(0, out.levelsGained());
        assertEquals(0, service.snapshot(PLAYER).xp(), 1e-9);
    }

    @Test
    @DisplayName("уровень от опыта пересчитывает базовые статы класса")
    void experienceReappliesBaseStats() {
        service.setClass(PLAYER, "mage");
        double before = stats.snapshot(PLAYER).get("magic_damage");

        service.addExperience(PLAYER, 10_000);

        assertTrue(stats.snapshot(PLAYER).get("magic_damage") > before,
                "кривая класса считается от уровня, значит уровень обязан её сдвинуть");
    }

    // ------------------------------------------------------------------ уровни навыков

    @Test
    @DisplayName("вложенное очко поднимает уровень навыка, а не только счётчик")
    void upgradeRaisesSkillLevel() {
        service.setClass(PLAYER, "mage");
        service.setLevel(PLAYER, 10);
        service.grantPoints(PLAYER, 3);
        assertTrue(service.unlock(PLAYER, "bolt").succeeded());

        assertEquals(1, service.skillLevel(PLAYER, "bolt"), "изученный — первый уровень");
        assertTrue(service.upgrade(PLAYER, "bolt").succeeded());
        assertEquals(2, service.skillLevel(PLAYER, "bolt"));
        assertEquals(1, service.snapshot(PLAYER).unspentPoints(), "очко списано");
    }

    @Test
    @DisplayName("в неизученный навык очко не вложить")
    void upgradeNeedsUnlock() {
        service.setClass(PLAYER, "mage");
        service.grantPoints(PLAYER, 5);

        var out = service.upgrade(PLAYER, "bolt");

        assertEquals(ClassOutcome.Upgrade.Kind.NOT_UNLOCKED, out.kind(), out::toString);
        assertEquals(5, service.snapshot(PLAYER).unspentPoints());
    }

    @Test
    @DisplayName("выше предела уровень навыка не поднимается, очки не тратятся")
    void upgradeStopsAtMax() {
        service.setClass(PLAYER, "mage");
        service.setLevel(PLAYER, 10);
        service.grantPoints(PLAYER, 20);
        assertTrue(service.unlock(PLAYER, "bolt").succeeded());
        for (int i = 1; i < ClassService.MAX_SKILL_LEVEL; i++) {
            assertTrue(service.upgrade(PLAYER, "bolt").succeeded());
        }
        int left = service.snapshot(PLAYER).unspentPoints();

        var out = service.upgrade(PLAYER, "bolt");

        assertEquals(ClassOutcome.Upgrade.Kind.MAX_LEVEL, out.kind());
        assertEquals(left, service.snapshot(PLAYER).unspentPoints(), "отказ ничего не тратит");
    }

    @Test
    @DisplayName("смена класса сбрасывает и уровни навыков вместе с изученным")
    void changingClassClearsSkillLevels() {
        service.setClass(PLAYER, "mage");
        service.setLevel(PLAYER, 10);
        service.grantPoints(PLAYER, 5);
        service.unlock(PLAYER, "bolt");
        service.upgrade(PLAYER, "bolt");

        service.setClass(PLAYER, "rogue");

        assertEquals(0, service.skillLevel(PLAYER, "bolt"));
        assertTrue(service.snapshot(PLAYER).skillLevels().isEmpty());
    }

    // ------------------------------------------------------------------ обнуление

    private void prepared() {
        service.setClass(PLAYER, "mage");
        service.setLevel(PLAYER, 20);
        service.grantPoints(PLAYER, 10);
        service.unlock(PLAYER, "bolt");
        service.upgrade(PLAYER, "bolt");
        service.upgrade(PLAYER, "bolt");
        service.unlock(PLAYER, "collapse");
        service.bind(PLAYER, 1, "bolt");
    }

    @Test
    @DisplayName("сброс навыков возвращает ровно то, что было потрачено")
    void resetSkillsRefundsExactly() {
        prepared();
        // Потрачено: очко за bolt, два за его уровни, очко за collapse — четыре.
        int before = service.snapshot(PLAYER).unspentPoints();

        service.reset(PLAYER, ResetKind.SKILLS);

        var data = service.snapshot(PLAYER);
        assertEquals(before + 4, data.unspentPoints(),
                "возврат не тот — значит, тратили и возвращаем по разной арифметике");
        assertTrue(data.unlockedSkills().isEmpty());
        assertTrue(data.slotBindings().isEmpty(), "слоты без навыков бессмысленны");
        assertEquals(20, data.level(), "уровень сброс навыков не трогает");
    }

    @Test
    @DisplayName("сброс слотов не трогает ни навыки, ни очки")
    void resetSlotsTouchesOnlySlots() {
        prepared();
        int points = service.snapshot(PLAYER).unspentPoints();

        service.reset(PLAYER, ResetKind.SLOTS);

        var data = service.snapshot(PLAYER);
        assertTrue(data.slotBindings().isEmpty());
        assertEquals(3, service.skillLevel(PLAYER, "bolt"), "навык остался с уровнем");
        assertEquals(points, data.unspentPoints());
    }

    @Test
    @DisplayName("сброс прогресса оставляет изученное, но убирает уровень и очки")
    void resetProgressKeepsSkills() {
        prepared();

        service.reset(PLAYER, ResetKind.PROGRESS);

        var data = service.snapshot(PLAYER);
        assertEquals(1, data.level());
        assertEquals(0, data.xp(), 1e-9);
        assertEquals(0, data.unspentPoints());
        assertTrue(data.isUnlocked("bolt"), "навыки остались");
    }

    @Test
    @DisplayName("сброс класса убирает класс, навыки и слоты, но не уровень")
    void resetClassKeepsLevel() {
        prepared();

        service.reset(PLAYER, ResetKind.CLASS);

        var data = service.snapshot(PLAYER);
        assertTrue(service.classOf(PLAYER).isEmpty());
        assertTrue(data.unlockedSkills().isEmpty());
        assertTrue(data.slotBindings().isEmpty());
        assertEquals(20, data.level());
    }

    @Test
    @DisplayName("полный сброс делает игрока новым, и классовые статы уходят")
    void resetAllClearsEverything() {
        prepared();
        assertTrue(stats.snapshot(PLAYER).get("defense") > 0, "класс дал базу защиты");

        service.reset(PLAYER, ResetKind.ALL);

        var data = service.snapshot(PLAYER);
        assertTrue(service.classOf(PLAYER).isEmpty());
        assertEquals(1, data.level());
        assertEquals(0, data.unspentPoints());
        assertTrue(data.unlockedSkills().isEmpty());
        assertEquals(0, stats.snapshot(PLAYER).get("defense"), 1e-9,
                "иначе сброшенный игрок ходил бы с защитой от класса, которого у него нет");
    }

    @Test
    @DisplayName("что именно пропадёт, названо у каждого вида сброса")
    void everyKindSaysWhatItTakes() {
        for (ResetKind kind : ResetKind.values()) {
            assertFalse(kind.what().isBlank(),
                    "у " + kind.key() + " нет описания: «обнулить» без уточнения опасно");
        }
        assertEquals(ResetKind.ALL, ResetKind.of("all").orElseThrow());
        assertTrue(ResetKind.of("что-нибудь").isEmpty());
    }
}
