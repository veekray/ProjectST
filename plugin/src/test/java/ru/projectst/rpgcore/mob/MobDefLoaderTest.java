package ru.projectst.rpgcore.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.projectst.rpgcore.item.ItemDef;
import ru.projectst.rpgcore.item.ItemRegistry;
import ru.projectst.rpgcore.item.ItemSlot;
import ru.projectst.rpgcore.item.Rarity;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.skill.NumberRef;
import ru.projectst.rpgcore.skill.SkillDef;
import ru.projectst.rpgcore.skill.SkillRegistry;
import ru.projectst.rpgcore.skill.SkillTrigger;
import ru.projectst.rpgcore.skill.Step;
import ru.projectst.rpgcore.skill.TargetSpec;
import ru.projectst.rpgcore.stat.Rounding;
import ru.projectst.rpgcore.stat.StatDef;
import ru.projectst.rpgcore.stat.StatRegistry;

/**
 * Чтение и связывание мобов.
 *
 * <p>Главные проверки те же, что у предметов: навык моба обязан быть служебным и
 * без класса, а дроп и статы — ссылаться на существующее. Моб со классовым
 * навыком игрока тащил бы за собой его баланс, ступени и стоимость маны, которой
 * у моба нет.
 */
class MobDefLoaderTest {

    private static final String GOOD = """
            id: desert_scorpion
            display: "Пустынный скорпион"
            type: silverfish
            health: 60
            damage: 7
            experience: 12
            glowing: true

            stats:
              defense: 4

            skills:
              - { skill: sting, trigger: attack, chance: 40 }
              - { skill: burrow, trigger: interval, every: 100 }

            drops:
              - { material: string, min: 1, max: 3, chance: 60 }
              - { item: focus, chance: 5 }
            """;

    private static MobDef load(String yaml, ContentErrors errors) {
        return MobDefLoader.load("mob.yml", yaml, errors).orElseThrow(
                () -> new AssertionError(errors.all().toString()));
    }

    @Test
    @DisplayName("моб читается целиком, тип и материал приводятся к верхнему регистру")
    void readsEverything() {
        ContentErrors errors = new ContentErrors();
        MobDef mob = load(GOOD, errors);

        assertTrue(errors.isEmpty(), () -> errors.all().toString());
        assertEquals("SILVERFISH", mob.entityType());
        assertEquals(60, mob.health(), 1e-9);
        assertEquals(7, mob.damage(), 1e-9);
        assertEquals(12, mob.experience());
        assertTrue(mob.glowing());
        assertEquals(4, mob.stats().get("defense"), 1e-9);
        assertEquals(2, mob.skills().size());
        assertEquals("STRING", mob.drops().get(0).material());
        assertEquals("focus", mob.drops().get(1).itemId());
    }

    @Test
    @DisplayName("навыки раскладываются по триггерам")
    void skillsSplitByTrigger() {
        MobDef mob = load(GOOD, new ContentErrors());

        assertEquals(1, mob.skillsOn(MobTrigger.ON_ATTACK).size());
        assertEquals(1, mob.skillsOn(MobTrigger.ON_INTERVAL).size());
        assertEquals(100, mob.skillsOn(MobTrigger.ON_INTERVAL).get(0).intervalTicks());
        assertTrue(mob.skillsOn(MobTrigger.ON_DEATH).isEmpty());
    }

    @Test
    @DisplayName("шанс по умолчанию — всегда, а не никогда")
    void defaultChanceIsAlways() {
        MobDef mob = load(GOOD, new ContentErrors());

        assertEquals(100, mob.skillsOn(MobTrigger.ON_INTERVAL).get(0).chance(), 1e-9);
        assertEquals(40, mob.skillsOn(MobTrigger.ON_ATTACK).get(0).chance(), 1e-9);
    }

    @Test
    @DisplayName("дроп считается по броску: шанс и количество отдельно")
    void dropRolls() {
        MobDrop drop = new MobDrop(null, "STRING", 2, 4, 50);

        assertEquals(0, drop.roll(0.9, 0.0), "шанс не прошёл — ничего");
        assertEquals(2, drop.roll(0.1, 0.0), "нижняя граница");
        assertEquals(4, drop.roll(0.1, 0.99), "верхняя граница");
        assertTrue(drop.roll(0.49, 0.5) >= 2);
    }

    @Test
    @DisplayName("периодический навык без промежутка не загружается")
    void intervalNeedsEvery() {
        ContentErrors errors = new ContentErrors();
        MobDefLoader.load("mob.yml", """
                id: m
                type: zombie
                skills:
                  - { skill: s, trigger: interval }
                """, errors);

        assertTrue(errors.all().stream().anyMatch(e -> e.what().contains("every")),
                errors.all().toString());
    }

    @Test
    @DisplayName("строка дропа без предмета и без материала не загружается")
    void dropNeedsOneKind() {
        ContentErrors errors = new ContentErrors();
        MobDefLoader.load("mob.yml", """
                id: m
                type: zombie
                drops:
                  - { min: 1, max: 2 }
                """, errors);

        assertTrue(errors.all().stream().anyMatch(e -> e.what().contains("либо item")),
                errors.all().toString());
    }

    @Test
    @DisplayName("моб без типа существа не загружается")
    void typeIsRequired() {
        ContentErrors errors = new ContentErrors();

        assertTrue(MobDefLoader.load("mob.yml", """
                id: m
                health: 10
                """, errors).isEmpty());
        assertTrue(errors.all().stream().anyMatch(e -> e.what().contains("тип существа")),
                errors.all().toString());
    }

    @Test
    @DisplayName("правило спавна подходит по миру и типу, пустой список миров — любой")
    void spawnRuleMatches() {
        SpawnRule anywhere = new SpawnRule("m", "silverfish", List.of(), 50);
        SpawnRule onlyWorld = new SpawnRule("m", "HUSK", List.of("desert"), 50);

        assertTrue(anywhere.appliesTo("world", "SILVERFISH"), "тип сравнивается без регистра");
        assertFalse(anywhere.appliesTo("world", "ZOMBIE"));
        assertTrue(onlyWorld.appliesTo("desert", "HUSK"));
        assertFalse(onlyWorld.appliesTo("world", "HUSK"));
    }

    // ------------------------------------------------------------------ связывание

    private static SkillDef skill(String id, String classId, boolean internal) {
        return new SkillDef(id, id, classId, 1, new NumberRef.Literal(0),
                new NumberRef.Literal(0),
                List.of(new Step(TargetSpec.self(),
                        List.of(new ru.projectst.rpgcore.skill.Action.Heal(
                                new NumberRef.Literal(1))), 0)),
                SkillTrigger.MANUAL, 0, internal);
    }

    private static SkillRegistry skills(SkillDef... defs) {
        Map<String, SkillDef> map = new java.util.LinkedHashMap<>();
        for (SkillDef def : defs) {
            map.put(def.id(), def);
        }
        return new SkillRegistry(map);
    }

    private static ItemRegistry items(String... ids) {
        Map<String, ItemDef> map = new java.util.LinkedHashMap<>();
        for (String id : ids) {
            map.put(id, new ItemDef(id, id, "STICK", 0, List.of(), "common", ItemSlot.HAND,
                    Map.of(), null, List.of(), false));
        }
        return new ItemRegistry(map, Map.of("common", Rarity.COMMON));
    }

    private static StatRegistry stats() {
        return new StatRegistry(Map.of("defense",
                new StatDef("defense", "def", 0, 0, 1000, Rounding.NONE)));
    }

    @Test
    @DisplayName("связывание пропускает моба, у которого всё на месте")
    void linkingAcceptsGoodMob() {
        MobDef mob = load(GOOD, new ContentErrors());
        MobRegistry registry = new MobRegistry(Map.of(mob.id(), mob),
                List.of(new SpawnRule(mob.id(), "SILVERFISH", List.of(), 50)));
        ContentErrors link = new ContentErrors();

        MobLinker.link(List.of(mob), registry, stats(),
                skills(skill("sting", "", true), skill("burrow", "", true)),
                items("focus"), link);

        assertTrue(link.isEmpty(), () -> link.all().toString());
    }

    @Test
    @DisplayName("классовый навык у моба — ошибка: это баланс игрока")
    void mobSkillsMustBeInternal() {
        MobDef mob = load(GOOD, new ContentErrors());
        MobRegistry registry = new MobRegistry(Map.of(mob.id(), mob), List.of());
        ContentErrors link = new ContentErrors();

        MobLinker.link(List.of(mob), registry, stats(),
                skills(skill("sting", "mage", false), skill("burrow", "", true)),
                items("focus"), link);

        assertEquals(2, link.count(), () -> link.all().toString());
        assertTrue(link.all().stream().anyMatch(e -> e.what().contains("служебным")));
        assertTrue(link.all().stream().anyMatch(e -> e.what().contains("класс")));
    }

    @Test
    @DisplayName("несуществующий предмет в дропе и правило без моба ловятся")
    void linkingCatchesMissingReferences() {
        MobDef mob = load(GOOD, new ContentErrors());
        MobRegistry registry = new MobRegistry(Map.of(mob.id(), mob),
                List.of(new SpawnRule("нет_такого", "ZOMBIE", List.of(), 10)));
        ContentErrors link = new ContentErrors();

        MobLinker.link(List.of(mob), registry, stats(),
                skills(skill("sting", "", true), skill("burrow", "", true)),
                items(), link);

        assertEquals(2, link.count(), () -> link.all().toString());
        assertTrue(link.all().stream().anyMatch(e -> e.what().contains("focus")));
        assertTrue(link.all().stream().anyMatch(e -> e.what().contains("нет_такого")));
    }
}
