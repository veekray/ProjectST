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
}
