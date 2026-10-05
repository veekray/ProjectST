package ru.projectst.rpgcore.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Конвертер мобов MythicMobs.
 *
 * <p>Главное, что здесь проверяется, — честность. Конвертер обязан перенести
 * понятное и <b>назвать</b> непонятое: тихий пропуск оставляет моба без половины
 * поведения, и обнаруживается это в бою, а не при переносе.
 */
class MobConverterTest {

    private static final String SOURCE = """
            Desert_Scorpion:
              Type: SILVERFISH
              Display: '&eПустынный скорпион'
              Health: 60
              Damage: 7
              Armor: 4
              Options:
                MovementSpeed: 0.3
                AlwaysShowName: true
              Drops:
              - STRING 1-3 0.6
              - GOLD_NUGGET 2 0.25
              Skills:
              - skill{s=Venom} @target ~onAttack
              - potion{type=POISON} @trigger ~onDamaged 0.4

            Sand_Revenant:
              Type: HUSK
              Health: 140
              Skills:
              - projectile{onHit=Boom} @target ~onTimer:100
            """;

    @Test
    @DisplayName("переносит то, что имеет прямое соответствие")
    void convertsTheObvious() {
        var result = MobConverter.convert("mobs.yml", SOURCE);

        assertEquals(2, result.converted());
        assertEquals(0, result.skipped());
        assertTrue(result.files().containsKey("desert_scorpion.yml"),
                result.files().keySet().toString());

        String yaml = result.files().get("desert_scorpion.yml");
        assertTrue(yaml.contains("type: SILVERFISH"), yaml);
        assertTrue(yaml.contains("health: 60"), yaml);
        assertTrue(yaml.contains("damage: 7"), yaml);
        assertTrue(yaml.contains("display: \"&eПустынный скорпион\""), yaml);
    }

    @Test
    @DisplayName("броня становится статом защиты: у нас её считает тот же конвейер")
    void armorBecomesDefense() {
        var result = MobConverter.convert("mobs.yml", SOURCE);

        String yaml = result.files().get("desert_scorpion.yml");
        assertTrue(yaml.contains("stats:"), yaml);
        assertTrue(yaml.contains("defense: 4"), yaml);
    }

    @Test
    @DisplayName("дроп переносится, доля единицы превращается в проценты")
    void dropsBecomePercents() {
        var result = MobConverter.convert("mobs.yml", SOURCE);

        String yaml = result.files().get("desert_scorpion.yml");
        assertTrue(yaml.contains("material: STRING"), yaml);
        assertTrue(yaml.contains("min: 1, max: 3"), yaml);
        assertTrue(yaml.contains("chance: 60"), yaml);
        assertTrue(yaml.contains("material: GOLD_NUGGET"), yaml);
        assertTrue(yaml.contains("chance: 25"), yaml);
    }

    @Test
    @DisplayName("навыки не переносятся, и об этом сказано числом")
    void skillsAreReportedNotGuessed() {
        var result = MobConverter.convert("mobs.yml", SOURCE);

        assertTrue(result.report().stream()
                        .anyMatch(line -> line.contains("Desert_Scorpion")
                                && line.contains("навыков не перенесено: 2")),
                result.report().toString());
        assertTrue(result.report().stream()
                        .anyMatch(line -> line.contains("Sand_Revenant")
                                && line.contains("навыков не перенесено: 1")),
                result.report().toString());

        String yaml = result.files().get("desert_scorpion.yml");
        assertFalse(yaml.contains("skills:"),
                "конвертер не придумывает навыки, которых не понял");
    }

    @Test
    @DisplayName("непереносимые настройки названы, а не пропущены молча")
    void unknownOptionsAreNamed() {
        var result = MobConverter.convert("mobs.yml", SOURCE);

        assertTrue(result.report().stream()
                        .anyMatch(line -> line.contains("скорость перемещения не перенесена")),
                result.report().toString());
    }

    @Test
    @DisplayName("моб без типа пропускается целиком и попадает в отчёт со строкой")
    void mobWithoutTypeIsSkipped() {
        var result = MobConverter.convert("mobs.yml", """
                Broken_Mob:
                  Health: 10
                """);

        assertEquals(0, result.converted());
        assertEquals(1, result.skipped());
        assertTrue(result.report().get(0).contains("нет ключа Type"),
                result.report().toString());
    }

    @Test
    @DisplayName("идентификатор приводится к нижнему регистру с подчёркиваниями")
    void identifierIsNormalised() {
        var result = MobConverter.convert("mobs.yml", """
                Lrd Gargoyle-Boss:
                  Type: ZOMBIE
                  Health: 10
                """);

        assertTrue(result.files().containsKey("lrd_gargoyle_boss.yml"),
                result.files().keySet().toString());
        assertTrue(result.files().values().iterator().next().contains("id: lrd_gargoyle_boss"));
    }

    @Test
    @DisplayName("таблица дропа не разбирается, но и не теряется: остаётся комментарием")
    void dropTableStaysVisible() {
        var result = MobConverter.convert("mobs.yml", """
                Boss:
                  Type: ZOMBIE
                  Health: 10
                  Drops:
                  - mythicmobs_item{type=SOME} 1 0.5
                  - boss_table 1 1
                """);

        String yaml = result.files().get("boss.yml");
        assertTrue(yaml.contains("# не разобрано: mythicmobs_item"), yaml);
        assertTrue(yaml.contains("material: BOSS_TABLE"),
                "строку, похожую на материал, конвертер переносит, а проверит её загрузчик: "
                        + yaml);
    }

    @Test
    @DisplayName("файл без объявлений мобов не молчит")
    void emptyFileIsReported() {
        var result = MobConverter.convert("empty.yml", "# только комментарий\n");

        assertEquals(0, result.converted());
        assertTrue(result.report().get(0).contains("не найдено"), result.report().toString());
    }
}
