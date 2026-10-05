package ru.projectst.rpgcore.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.skill.NumberRef;
import ru.projectst.rpgcore.skill.SkillDef;
import ru.projectst.rpgcore.skill.SkillRegistry;
import ru.projectst.rpgcore.skill.SkillTrigger;
import ru.projectst.rpgcore.skill.Step;
import ru.projectst.rpgcore.skill.TargetSpec;
import ru.projectst.rpgcore.stat.Rounding;
import ru.projectst.rpgcore.stat.StatDef;
import ru.projectst.rpgcore.stat.StatOp;
import ru.projectst.rpgcore.stat.StatRegistry;

/**
 * Чтение и связывание предметов.
 *
 * <p>Главная проверка здесь — последняя: умение предмета обязано ссылаться на
 * служебный навык без класса. Иначе предмет выдаёт классовый навык в обход
 * изучения, и находят это не в ревью, а на сервере через неделю.
 */
class ItemDefLoaderTest {

    private static final String GOOD = """
            id: mage_staff
            display: "Посох"
            material: stick
            model-data: 4001
            rarity: uncommon
            slot: hand
            unbreakable: true

            lore:
              - "Строка описания"

            requires:
              class: mage
              level: 5

            stats:
              magic_damage: 6
              max_mana: { op: percent, value: 10 }

            abilities:
              - { skill: pulse, trigger: right-click }
            """;

    private static ItemDef load(String yaml, ContentErrors errors) {
        return ItemDefLoader.load("item.yml", yaml, errors).orElseThrow(
                () -> new AssertionError(errors.all().toString()));
    }

    @Test
    @DisplayName("предмет читается целиком, материал приводится к верхнему регистру")
    void readsEverything() {
        ContentErrors errors = new ContentErrors();
        ItemDef item = load(GOOD, errors);

        assertTrue(errors.isEmpty(), () -> errors.all().toString());
        assertEquals("mage_staff", item.id());
        assertEquals("STICK", item.material(), "материал пишут как угодно, хранится однообразно");
        assertEquals(4001, item.modelData());
        assertEquals(ItemSlot.HAND, item.slot());
        assertTrue(item.unbreakable());
        assertEquals(List.of("Строка описания"), item.lore());
        assertEquals("mage", item.requirement().classId());
        assertEquals(5, item.requirement().level());
        assertEquals(1, item.abilities().size());
        assertEquals(ItemTrigger.RIGHT_CLICK, item.abilities().get(0).trigger());
    }

    @Test
    @DisplayName("короткая запись стата — это плоская надбавка, а не другой синтаксис")
    void shortStatIsFlat() {
        ContentErrors errors = new ContentErrors();
        ItemDef item = load(GOOD, errors);

        assertEquals(StatOp.FLAT, item.stats().get("magic_damage").op());
        assertEquals(6, item.stats().get("magic_damage").value(), 1e-9);
        assertEquals(StatOp.PERCENT, item.stats().get("max_mana").op());
        assertEquals(10, item.stats().get("max_mana").value(), 1e-9);
    }

    @Test
    @DisplayName("надбавки превращаются в источник с именем слота")
    void modifiersCarryTheSource() {
        ContentErrors errors = new ContentErrors();
        ItemDef item = load(GOOD, errors);

        var modifiers = item.modifiers("item:hand");

        assertEquals(2, modifiers.size());
        assertTrue(modifiers.stream().allMatch(m -> m.source().equals("item:hand")),
                "по источнику видно, какой слот снимать при смене предмета");
    }

    @Test
    @DisplayName("предмет без материала не загружается")
    void materialIsRequired() {
        ContentErrors errors = new ContentErrors();
        var item = ItemDefLoader.load("item.yml", """
                id: broken
                display: "Без материала"
                """, errors);

        assertTrue(item.isEmpty());
        assertTrue(errors.all().stream().anyMatch(e -> e.what().contains("материал")),
                errors.all().toString());
    }

    @Test
    @DisplayName("пустой раздел требований — ошибка, а не «требований нет»")
    void emptyRequirementIsAnError() {
        ContentErrors errors = new ContentErrors();
        ItemDefLoader.load("item.yml", """
                id: thing
                material: stick
                requires: {}
                """, errors);

        assertTrue(errors.all().stream().anyMatch(e -> e.what().contains("пустой раздел")),
                errors.all().toString());
    }

    @Test
    @DisplayName("неизвестный триггер умения назван со списком допустимых")
    void unknownTriggerIsNamed() {
        ContentErrors errors = new ContentErrors();
        ItemDefLoader.load("item.yml", """
                id: thing
                material: stick
                abilities:
                  - { skill: pulse, trigger: когда-нибудь }
                """, errors);

        assertTrue(errors.all().get(0).what().contains("right-click"),
                errors.all().get(0).what());
    }

    @Test
    @DisplayName("требование объясняет отказ словами, а не молчанием")
    void requirementExplainsRefusal() {
        ItemRequirement requirement = new ItemRequirement("mage", 10);

        assertEquals("", requirement.refusal("mage", 10));
        assertTrue(requirement.refusal("rogue", 50).contains("mage"));
        assertTrue(requirement.refusal("mage", 9).contains("10"));
        assertTrue(ItemRequirement.NONE.any());
    }

    // ------------------------------------------------------------------ связывание

    private static SkillRegistry skills(SkillDef... defs) {
        Map<String, SkillDef> map = new java.util.LinkedHashMap<>();
        for (SkillDef def : defs) {
            map.put(def.id(), def);
        }
        return new SkillRegistry(map);
    }

    private static SkillDef skill(String id, String classId, boolean internal) {
        return new SkillDef(id, id, classId, 1, new NumberRef.Literal(0),
                new NumberRef.Literal(0),
                List.of(new Step(TargetSpec.self(),
                        List.of(new ru.projectst.rpgcore.skill.Action.Heal(
                                new NumberRef.Literal(1))), 0)),
                SkillTrigger.MANUAL, 0, internal);
    }

    private static StatRegistry stats() {
        return new StatRegistry(Map.of(
                "magic_damage", new StatDef("magic_damage", "md", 0, -100, 1000, Rounding.NONE),
                "max_mana", new StatDef("max_mana", "mp", 100, 0, 10000, Rounding.NONE)));
    }

    private static ItemRegistry registry(ItemDef item) {
        return new ItemRegistry(Map.of(item.id(), item),
                Map.of("uncommon", new Rarity("uncommon", "Необычный", "GREEN"),
                        "common", Rarity.COMMON));
    }

    @Test
    @DisplayName("связывание пропускает предмет, у которого всё на месте")
    void linkingAcceptsGoodItem() {
        ItemDef item = load(GOOD, new ContentErrors());
        ContentErrors link = new ContentErrors();

        ItemLinker.link(List.of(item), registry(item), stats(),
                skills(skill("pulse", "", true)), List.of("mage"), link);

        assertTrue(link.isEmpty(), () -> link.all().toString());
    }

    @Test
    @DisplayName("умение на классовом навыке — ошибка: это обход изучения")
    void abilityMustBeInternal() {
        ItemDef item = load(GOOD, new ContentErrors());
        ContentErrors link = new ContentErrors();

        ItemLinker.link(List.of(item), registry(item), stats(),
                skills(skill("pulse", "mage", false)), List.of("mage"), link);

        assertEquals(2, link.count(), () -> link.all().toString());
        assertTrue(link.all().stream().anyMatch(e -> e.what().contains("служебный")),
                link.all().toString());
        assertTrue(link.all().stream().anyMatch(e -> e.what().contains("класс")),
                link.all().toString());
    }

    @Test
    @DisplayName("необъявленные стат, редкость и класс ловятся связыванием")
    void linkingCatchesMissingReferences() {
        ItemDef item = load("""
                id: thing
                material: stick
                rarity: нет_такой
                requires:
                  class: нет_такого
                stats:
                  нет_такого_стата: 5
                """, new ContentErrors());
        ContentErrors link = new ContentErrors();

        ItemLinker.link(List.of(item), registry(item), stats(), skills(), List.of("mage"), link);

        assertEquals(3, link.count(), () -> link.all().toString());
    }

    @Test
    @DisplayName("редкости читаются, и обычная есть всегда")
    void raritiesAlwaysHaveCommon() {
        ContentErrors errors = new ContentErrors();
        var rarities = ItemDefLoader.loadRarities("rarities.yml", """
                rarities:
                  legendary:
                    display: "Легендарный"
                    color: gold
                """, errors).orElseThrow();

        assertTrue(errors.isEmpty(), () -> errors.all().toString());
        assertEquals("GOLD", rarities.get("legendary").color(),
                "цвет пишут как угодно, хранится однообразно");
        assertTrue(rarities.containsKey("common"),
                "предмет без указанной редкости должен чем-то выводиться");
        assertFalse(rarities.containsKey("rare"));
    }
}
