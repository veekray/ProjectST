package ru.projectst.rpgcore.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.projectst.rpgcore.balance.BalanceBook;
import ru.projectst.rpgcore.balance.BalanceLoader;
import ru.projectst.rpgcore.damage.DamageSchool;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.stat.Rounding;
import ru.projectst.rpgcore.stat.StatDef;
import ru.projectst.rpgcore.stat.StatRegistry;
import ru.projectst.rpgcore.status.StatusDefLoader;
import ru.projectst.rpgcore.status.StatusRegistry;

class SkillLoaderTest {

    private ContentErrors errors;

    private Optional<SkillDef> load(String yaml) {
        errors = new ContentErrors();
        return SkillLoader.load("mage_mana_bolt.yml", yaml, errors);
    }

    private static final String GOOD = """
            id: mage_mana_bolt
            display: "Мановый разряд"
            class: mage
            tier: 2
            cost: $mana
            cooldown: $cooldown

            steps:
              - target: { type: enemies_in_radius, radius: $radius }
                do:
                  - { action: damage, amount: $damage, school: magic }
                  - { action: status, id: mark, duration: 160 }
              - target: { type: self }
                delay: 4
                do:
                  - { action: message, text: "Разряд ушёл" }
            """;

    @Test
    @DisplayName("корректный навык читается целиком")
    void goodSkillLoads() {
        SkillDef skill = load(GOOD).orElseThrow();

        assertTrue(errors.isEmpty(), () -> errors.all().toString());
        assertEquals("mage_mana_bolt", skill.id());
        assertEquals("mage", skill.classId());
        assertEquals(2, skill.tier());
        assertEquals(2, skill.steps().size());

        Step first = skill.steps().get(0);
        assertEquals(TargetSpec.Type.ENEMIES_IN_RADIUS, first.target().type());
        assertEquals("radius", first.target().radius().balanceKey());
        assertEquals(2, first.actions().size());

        Action.Damage damage = assertInstanceOf(Action.Damage.class, first.actions().get(0));
        assertEquals("damage", damage.amount().balanceKey());
        assertEquals(DamageSchool.MAGIC, damage.school());

        Action.ApplyStatus status = assertInstanceOf(Action.ApplyStatus.class, first.actions().get(1));
        assertEquals("mark", status.statusId());
        assertEquals(160, status.duration().resolve(null, 1), 1e-9);

        assertEquals(4, skill.steps().get(1).delay().resolve(null, 1), 1e-9);
    }

    @Test
    @DisplayName("неизвестное действие называет допустимые")
    void unknownActionListsAllowed() {
        load("""
                id: s
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: взорвать, amount: 5 }
                """);

        assertTrue(errors.all().stream().anyMatch(e -> e.what().contains("damage")),
                errors.all().toString());
    }

    @Test
    @DisplayName("цель без радиуса там, где он нужен, — ошибка")
    void radiusRequired() {
        load("""
                id: s
                class: mage
                steps:
                  - target: { type: enemies_in_radius }
                    do:
                      - { action: damage, amount: 5 }
                """);

        assertTrue(errors.all().stream().anyMatch(e -> e.what().contains("radius")),
                errors.all().toString());
    }

    @Test
    @DisplayName("конус без угла — ошибка")
    void coneNeedsAngle() {
        load("""
                id: s
                class: mage
                steps:
                  - target: { type: enemies_in_cone, radius: 10 }
                    do:
                      - { action: damage, amount: 5 }
                """);

        assertTrue(errors.all().stream().anyMatch(e -> e.what().contains("angle")),
                errors.all().toString());
    }

    @Test
    @DisplayName("навык без шагов отвергается")
    void emptySkillRejected() {
        load("""
                id: s
                class: mage
                """);

        assertTrue(errors.all().stream().anyMatch(e -> e.what().contains("ничего не делает")),
                errors.all().toString());
    }

    @Test
    @DisplayName("навык без класса отвергается")
    void classRequired() {
        load("""
                id: s
                steps:
                  - target: { type: self }
                    do:
                      - { action: message, text: "раз" }
                """);

        assertTrue(errors.all().stream().anyMatch(e -> e.what().contains("класс")),
                errors.all().toString());
    }

    @Test
    @DisplayName("мусор вместо числа называет полученное значение")
    void badNumberIsReported() {
        load("""
                id: s
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: heal, amount: много }
                """);

        assertTrue(errors.all().stream().anyMatch(e -> e.what().contains("много")),
                errors.all().toString());
    }

    @Test
    @DisplayName("неизвестный ключ в навыке ловится с номером строки")
    void unknownKeyCaught() {
        load("""
                id: s
                class: mage
                tierr: 3
                steps:
                  - target: { type: self }
                    do:
                      - { action: message, text: "раз" }
                """);

        assertTrue(errors.all().stream()
                        .anyMatch(e -> e.what().equals("неизвестный ключ") && e.at().line() == 3),
                errors.all().toString());
    }

    // ------------------------------------------------------------------ связывание

    private static StatusRegistry statuses() {
        return StatusDefLoader.load("statuses.yml", """
                statuses:
                  mark:
                    category: mark
                """, new ContentErrors()).orElseThrow();
    }

    private static BalanceBook balance(String yaml) {
        return BalanceLoader.load("balance.yml", yaml, new ContentErrors()).orElseThrow();
    }

    @Test
    @DisplayName("связывание пропускает навык, у которого все ссылки на месте")
    void linkingAcceptsCompleteSkill() {
        SkillDef skill = load(GOOD).orElseThrow();
        ContentErrors link = new ContentErrors();

        SkillLinker.link(List.of(skill), balance("""
                balance:
                  mage_mana_bolt:
                    mana: 8
                    cooldown: 4
                    radius: 6
                    damage: 12
                """), statuses(), List.of("mage"), link);

        assertTrue(link.isEmpty(), () -> link.all().toString());
    }

    @Test
    @DisplayName("ссылка на отсутствующий ключ баланса ловится до запуска")
    void linkingCatchesMissingBalanceKey() {
        SkillDef skill = load(GOOD).orElseThrow();
        ContentErrors link = new ContentErrors();

        SkillLinker.link(List.of(skill), balance("""
                balance:
                  mage_mana_bolt:
                    mana: 8
                    cooldown: 4
                    radius: 6
                """), statuses(), List.of("mage"), link);

        assertEquals(1, link.count(), () -> link.all().toString());
        assertTrue(link.all().get(0).what().contains("damage"), link.all().get(0).what());
    }

    @Test
    @DisplayName("ссылка на несуществующий статус ловится до запуска")
    void linkingCatchesMissingStatus() {
        SkillDef skill = load("""
                id: s
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: status, id: нет_такого }
                """).orElseThrow();
        ContentErrors link = new ContentErrors();

        SkillLinker.link(List.of(skill), BalanceBook.EMPTY, statuses(), List.of("mage"), link);

        assertEquals(1, link.count(), () -> link.all().toString());
        assertTrue(link.all().get(0).what().contains("несуществующий статус"));
    }

    @Test
    @DisplayName("ссылка на несуществующий класс ловится до запуска")
    void linkingCatchesMissingClass() {
        SkillDef skill = load("""
                id: s
                class: нет_такого
                steps:
                  - target: { type: self }
                    do:
                      - { action: message, text: "раз" }
                """).orElseThrow();
        ContentErrors link = new ContentErrors();

        SkillLinker.link(List.of(skill), BalanceBook.EMPTY, statuses(), List.of("mage"), link);

        assertEquals(1, link.count(), () -> link.all().toString());
        assertTrue(link.all().get(0).what().contains("класс"));
    }

    @Test
    @DisplayName("прямые числа связывание не трогает")
    void literalsNeedNoBalance() {
        SkillDef skill = load("""
                id: s
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: 6 }
                    do:
                      - { action: damage, amount: 12 }
                """).orElseThrow();
        ContentErrors link = new ContentErrors();

        SkillLinker.link(List.of(skill), BalanceBook.EMPTY, statuses(), List.of("mage"), link);

        assertTrue(link.isEmpty(), () -> link.all().toString());
        assertEquals(Map.of(), Map.of());
    }

    @Test
    @DisplayName("цель у точки действия без вызывающего навыка — ошибка связывания")
    void linkingCatchesOrphanOriginTarget() {
        SkillDef skill = load("""
                id: s
                class: mage
                steps:
                  - target: { type: enemies_near_origin, radius: 4 }
                    do:
                      - { action: damage, amount: 5 }
                """).orElseThrow();
        ContentErrors link = new ContentErrors();

        SkillLinker.link(List.of(skill), BalanceBook.EMPTY, statuses(), List.of("mage"), link);

        assertEquals(1, link.count(), () -> link.all().toString());
        assertTrue(link.all().get(0).what().contains("точку действия"),
                link.all().get(0).what());
    }

    @Test
    @DisplayName("тот же навык, вызванный лучом, претензий не вызывает")
    void linkingAcceptsOriginTargetWhenCalled() {
        SkillDef hit = load("""
                id: boom
                class: mage
                steps:
                  - target: { type: enemies_near_origin, radius: 4 }
                    do:
                      - { action: damage, amount: 5 }
                """).orElseThrow();
        SkillDef caller = load("""
                id: s
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: ray, range: 20, on-hit: boom }
                """).orElseThrow();
        ContentErrors link = new ContentErrors();

        SkillLinker.link(List.of(caller, hit), BalanceBook.EMPTY, statuses(), List.of("mage"), link);

        assertTrue(link.isEmpty(), () -> link.all().toString());
    }

    @Test
    @DisplayName("ссылка на несуществующий навык в cast ловится до запуска")
    void linkingCatchesMissingSkillReference() {
        SkillDef skill = load("""
                id: s
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: cast, skill: нет_такого }
                """).orElseThrow();
        ContentErrors link = new ContentErrors();

        SkillLinker.link(List.of(skill), BalanceBook.EMPTY, statuses(), List.of("mage"), link);

        assertEquals(1, link.count(), () -> link.all().toString());
        assertTrue(link.all().get(0).what().contains("несуществующий навык"));
    }

    @Test
    @DisplayName("ссылка на необъявленный стат в modify-stat ловится до запуска")
    void linkingCatchesUnknownStat() {
        SkillDef skill = load("""
                id: s
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: modify-stat, stat: нет_такого, value: 5 }
                """).orElseThrow();
        ContentErrors link = new ContentErrors();
        StatRegistry stats = new StatRegistry(Map.of("magic_damage",
                new StatDef("magic_damage", "md", 0, -100, 1000, Rounding.NONE)));

        SkillLinker.link(List.of(skill), BalanceBook.EMPTY, statuses(), List.of("mage"),
                stats, link);

        assertEquals(1, link.count(), () -> link.all().toString());
        assertTrue(link.all().get(0).what().contains("необъявленный стат"));
    }

    // ------------------------------------------------------------------ надбавка статуса

    @Test
    @DisplayName("надбавка со статусом и своим сроком — ошибка загрузки: два срока у одного эффекта")
    void statusLinkRefusesDuration() {
        Optional<SkillDef> skill = load("""
                id: s
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: status, id: mark, duration: 40 }
                      - { action: modify-stat, stat: magic_damage, value: 5, duration: 40, status: mark }
                """);

        assertTrue(skill.isEmpty() || errors.count() > 0);
        assertTrue(errors.all().stream().anyMatch(e -> e.what().contains("сроком статуса")),
                () -> errors.all().toString());
    }

    @Test
    @DisplayName("надбавка со статусом читается и связывается, если статус кладётся раньше")
    void statusLinkAccepted() {
        SkillDef skill = load("""
                id: s
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: status, id: mark, duration: 40 }
                      - { action: modify-stat, stat: magic_damage, value: 5, status: mark }
                """).orElseThrow();
        ContentErrors link = new ContentErrors();

        SkillLinker.link(List.of(skill), BalanceBook.EMPTY, statuses(), List.of("mage"), link);

        assertEquals(0, link.count(), () -> link.all().toString());
        var action = (Action.ModifyStat) skill.steps().get(0).actions().get(1);
        assertEquals("mark", action.statusId());
    }

    @Test
    @DisplayName("статус ниже надбавки в том же шаге — ошибка: надбавка не ляжет")
    void statusLinkOrderChecked() {
        SkillDef skill = load("""
                id: s
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: modify-stat, stat: magic_damage, value: 5, status: mark }
                      - { action: status, id: mark, duration: 40 }
                """).orElseThrow();
        ContentErrors link = new ContentErrors();

        SkillLinker.link(List.of(skill), BalanceBook.EMPTY, statuses(), List.of("mage"), link);

        assertEquals(1, link.count(), () -> link.all().toString());
        assertTrue(link.all().get(0).what().contains("позже надбавки"));
    }

    @Test
    @DisplayName("статус, который никто не кладёт, — ошибка; кладущий вызывающий навык — нет")
    void statusLinkNeedsSomeoneToApply() {
        SkillDef orphan = load("""
                id: s
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: modify-stat, stat: magic_damage, value: 5, status: mark }
                """).orElseThrow();
        ContentErrors alone = new ContentErrors();
        SkillLinker.link(List.of(orphan), BalanceBook.EMPTY, statuses(), List.of("mage"), alone);
        assertEquals(1, alone.count(), () -> alone.all().toString());
        assertTrue(alone.all().get(0).what().contains("не ляжет никогда"));

        SkillDef caller = load("""
                id: caller
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: status, id: mark, duration: 40 }
                      - { action: cast, skill: s }
                """).orElseThrow();
        ContentErrors together = new ContentErrors();
        SkillLinker.link(List.of(orphan, caller), BalanceBook.EMPTY, statuses(),
                List.of("mage"), together);
        assertEquals(0, together.count(), () -> together.all().toString());
    }

    @Test
    @DisplayName("число со счётчиком разбирается, а решётка вместо собаки — ошибка")
    void counterSyntax() {
        SkillDef ok = load("""
                id: s
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: consume-zones, tag: seal, radius: 8, counter: seals }
                      - { action: damage, amount: 4 * @seals }
                """).orElseThrow();
        Action damage = ok.steps().get(0).actions().get(1);
        assertEquals("seals", ((Action.Damage) damage).amount().counterName());

        ContentErrors errors = new ContentErrors();
        SkillLoader.load("s.yml", """
                id: s
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: damage, amount: "4 * seals" }
                """, errors);
        assertTrue(errors.all().get(0).what().contains("@имя"), errors.all().get(0).what());
    }

    @Test
    @DisplayName("счётчик, который никто не заполняет, ловится связыванием")
    void linkingCatchesOrphanCounter() {
        SkillDef skill = load("""
                id: s
                class: mage
                steps:
                  - target: { type: enemies_in_radius, radius: 5 }
                    do:
                      - { action: damage, amount: 4 * @seals }
                """).orElseThrow();
        ContentErrors link = new ContentErrors();

        SkillLinker.link(List.of(skill), BalanceBook.EMPTY, statuses(), List.of("mage"), link);

        assertEquals(1, link.count(), () -> link.all().toString());
        assertTrue(link.all().get(0).what().contains("не заполняется"),
                link.all().get(0).what());
    }

    @Test
    @DisplayName("зона требует тег, радиус и длительность")
    void zoneNeedsItsKeys() {
        ContentErrors errors = new ContentErrors();
        SkillLoader.load("s.yml", """
                id: s
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: zone, tag: seal, radius: 4 }
                """, errors);

        assertTrue(errors.all().get(0).what().contains("duration"),
                errors.all().get(0).what());
    }

    // ------------------------------------------------------------------ триггеры

    @Test
    @DisplayName("без ключа on навык применяется вручную")
    void defaultTriggerIsManual() {
        SkillDef skill = load("""
                id: s
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: message, text: "раз" }
                """).orElseThrow();

        assertEquals(SkillTrigger.MANUAL, skill.trigger());
        assertFalse(skill.passive());
    }

    @Test
    @DisplayName("триггер читается из навыка, а не из чужого файла")
    void triggerIsReadFromTheSkill() {
        SkillDef skill = load("""
                id: s
                class: mage
                on: damaged
                steps:
                  - target: { type: trigger }
                    do:
                      - { action: damage, amount: 3 }
                """).orElseThrow();

        assertEquals(SkillTrigger.ON_DAMAGED, skill.trigger());
        assertTrue(skill.passive());
    }

    @Test
    @DisplayName("неизвестный триггер назван ошибкой со списком допустимых")
    void unknownTriggerIsNamed() {
        ContentErrors errors = new ContentErrors();
        SkillLoader.load("s.yml", """
                id: s
                class: mage
                on: когда_захочется
                steps:
                  - target: { type: self }
                    do:
                      - { action: message, text: "раз" }
                """, errors);

        assertTrue(errors.all().get(0).what().contains("damaged"),
                errors.all().get(0).what());
    }

    @Test
    @DisplayName("периодический навык без промежутка — ошибка")
    void intervalNeedsEvery() {
        ContentErrors errors = new ContentErrors();
        SkillLoader.load("s.yml", """
                id: s
                class: mage
                on: interval
                steps:
                  - target: { type: self }
                    do:
                      - { action: heal, amount: 1 }
                """, errors);

        assertEquals(1, errors.count(), () -> errors.all().toString());
        assertTrue(errors.all().get(0).what().contains("every"), errors.all().get(0).what());
    }

    @Test
    @DisplayName("промежуток без периодического триггера — тоже ошибка, а не мусор")
    void everyWithoutIntervalIsRefused() {
        ContentErrors errors = new ContentErrors();
        SkillLoader.load("s.yml", """
                id: s
                class: mage
                every: 40
                steps:
                  - target: { type: self }
                    do:
                      - { action: heal, amount: 1 }
                """, errors);

        assertEquals(1, errors.count(), () -> errors.all().toString());
        assertTrue(errors.all().get(0).what().contains("interval"), errors.all().get(0).what());
    }

    @Test
    @DisplayName("снаряд, который никого не задевает, ловится при загрузке")
    void projectileMustHitSomething() {
        ContentErrors errors = new ContentErrors();
        SkillLoader.load("s.yml", """
                id: s
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: projectile, range: 20, on-hit: boom, hit-players: false, hit-mobs: false }
                """, errors);

        assertTrue(errors.all().get(0).what().contains("ни во что не попадёт"),
                errors.all().get(0).what());
    }

    @Test
    @DisplayName("ссылка снаряда на несуществующий навык ловится связыванием")
    void projectileReferencesAreChecked() {
        SkillDef skill = load("""
                id: s
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: projectile, range: 20, on-hit: нет_такого }
                """).orElseThrow();
        ContentErrors link = new ContentErrors();

        SkillLinker.link(List.of(skill), BalanceBook.EMPTY, statuses(), List.of("mage"), link);

        assertEquals(1, link.count(), () -> link.all().toString());
        assertTrue(link.all().get(0).what().contains("несуществующий навык"));
    }

    @Test
    @DisplayName("призыв требует тип, тег и срок жизни")
    void summonNeedsItsKeys() {
        ContentErrors errors = new ContentErrors();
        SkillLoader.load("s.yml", """
                id: s
                class: mage
                steps:
                  - target: { type: self }
                    do:
                      - { action: summon, mob: wolf, tag: beast }
                """, errors);

        assertTrue(errors.all().get(0).what().contains("duration"),
                errors.all().get(0).what());
    }
}
