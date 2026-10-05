package ru.projectst.rpgcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.projectst.rpgcore.damage.StatIds;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.stat.StatDefLoader;
import ru.projectst.rpgcore.stat.StatRegistry;
import ru.projectst.rpgcore.status.StatusDefLoader;
import ru.projectst.rpgcore.status.StatusRegistry;

/**
 * Проверяет контент, который плагин кладёт при первом запуске.
 *
 * <p>Без этого теста опечатку в поставляемом stats.yml обнаружил бы только
 * сервер при старте — то есть уже у пользователя. А раз загрузчик строгий,
 * любая опечатка там гарантированно отключила бы весь домен.
 */
class DefaultContentTest {

    private static String resource(String name) throws IOException {
        try (InputStream in = DefaultContentTest.class.getClassLoader()
                .getResourceAsStream(name)) {
            assertTrue(in != null, "ресурс не найден: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("поставляемый stats.yml грузится без ошибок")
    void defaultStatsAreValid() throws IOException {
        ContentErrors errors = new ContentErrors();
        StatRegistry registry = StatDefLoader
                .load("stats.yml", resource("stats.yml"), errors).orElseThrow();

        assertTrue(errors.isEmpty(), () -> errors.all().toString());
        assertTrue(registry.size() >= 10, "статов должно быть объявлено заметно больше нуля");
    }

    @Test
    @DisplayName("конвейер урона находит все статы, которые он читает")
    void damagePipelineStatsExist() throws IOException {
        ContentErrors errors = new ContentErrors();
        StatRegistry registry = StatDefLoader
                .load("stats.yml", resource("stats.yml"), errors).orElseThrow();

        for (String id : new String[] {
                StatIds.SKILL_DAMAGE, StatIds.PHYSICAL_DAMAGE, StatIds.MAGIC_DAMAGE,
                StatIds.CRIT_CHANCE, StatIds.CRIT_POWER,
                StatIds.DEFENSE, StatIds.MAGIC_RESISTANCE, StatIds.DAMAGE_REDUCTION}) {
            assertTrue(registry.has(id), "в stats.yml нет стата " + id
                    + ", который читает конвейер урона");
        }
    }

    @Test
    @DisplayName("поставляемый statuses.yml грузится без ошибок, граф конфликтов цел")
    void defaultStatusesAreValid() throws IOException {
        ContentErrors errors = new ContentErrors();
        StatusRegistry registry = StatusDefLoader
                .load("statuses.yml", resource("statuses.yml"), errors).orElseThrow();

        assertTrue(errors.isEmpty(), () -> errors.all().toString());
        assertTrue(registry.has("stun"));
        assertTrue(registry.has("banish"));
    }

    @Test
    @DisplayName("изъятие из боя сильнее любого контроля")
    void banishOutranksControls() throws IOException {
        ContentErrors errors = new ContentErrors();
        StatusRegistry registry = StatusDefLoader
                .load("statuses.yml", resource("statuses.yml"), errors).orElseThrow();

        int banish = registry.require("banish").priority();
        assertTrue(banish > registry.require("stun").priority(), "иначе стан вытеснит кокон");
        assertTrue(banish > registry.require("root").priority());
        assertTrue(registry.require("banish").suppresses().contains("stun"));
    }

    @Test
    @DisplayName("счётчик объявлен как стакающийся: иначе max-stacks не имел бы смысла")
    void chargeStacks() throws IOException {
        ContentErrors errors = new ContentErrors();
        StatusRegistry registry = StatusDefLoader
                .load("statuses.yml", resource("statuses.yml"), errors).orElseThrow();

        assertEquals(5, registry.require("charge").maxStacks());
    }

    /** Все навыки, которые плагин кладёт при первом запуске. */
    private static final String[] SKILLS = {
            "mage_mana_bolt", "mage_mana_bolt_impact", "mage_flux_loop", "mage_collapse",
            "mage_mana_ward"};

    private static java.util.List<ru.projectst.rpgcore.skill.SkillDef> skills(
            ContentErrors errors) throws IOException {
        var out = new java.util.ArrayList<ru.projectst.rpgcore.skill.SkillDef>();
        for (String id : SKILLS) {
            out.add(ru.projectst.rpgcore.skill.SkillLoader
                    .load(id + ".yml", resource("skills/" + id + ".yml"), errors)
                    .orElseThrow(() -> new AssertionError(errors.all().toString())));
        }
        return out;
    }

    @Test
    @DisplayName("поставляемые навыки грузятся и все их ссылки разрешаются")
    void defaultSkillsLink() throws IOException {
        ContentErrors errors = new ContentErrors();
        var loaded = skills(errors);
        assertTrue(errors.isEmpty(), () -> errors.all().toString());

        var balance = ru.projectst.rpgcore.balance.BalanceLoader
                .load("balance.yml", resource("balance.yml"), errors).orElseThrow();
        var statuses = StatusDefLoader
                .load("statuses.yml", resource("statuses.yml"), errors).orElseThrow();
        var stats = StatDefLoader
                .load("stats.yml", resource("stats.yml"), errors).orElseThrow();

        ContentErrors link = new ContentErrors();
        ru.projectst.rpgcore.skill.SkillLinker.link(loaded, balance, statuses,
                java.util.List.of(), stats, link);

        assertTrue(link.isEmpty(), () -> "ссылки поставляемых навыков не разрешились: "
                + link.all());
    }

    @Test
    @DisplayName("поставляемый класс грузится и навыки ссылаются на существующий класс")
    void defaultClassLinks() throws IOException {
        ContentErrors errors = new ContentErrors();
        var mage = ru.projectst.rpgcore.classes.ClassDefLoader
                .load("mage.yml", resource("classes/mage.yml"), errors).orElseThrow(
                        () -> new AssertionError(errors.all().toString()));
        assertTrue(errors.isEmpty(), () -> errors.all().toString());
        assertEquals(5, mage.levelForTier(2), "вторая ступень открыта с пятого уровня");

        var balance = ru.projectst.rpgcore.balance.BalanceLoader
                .load("balance.yml", resource("balance.yml"), new ContentErrors()).orElseThrow();
        var statuses = StatusDefLoader
                .load("statuses.yml", resource("statuses.yml"), new ContentErrors()).orElseThrow();

        ContentErrors link = new ContentErrors();
        ru.projectst.rpgcore.skill.SkillLinker.link(skills(new ContentErrors()), balance,
                statuses, java.util.Set.of(mage.id()), link);

        assertTrue(link.isEmpty(), () -> link.all().toString());
    }

    @Test
    @DisplayName("базовые статы класса объявлены в stats.yml")
    void classStatsExist() throws IOException {
        ContentErrors errors = new ContentErrors();
        var mage = ru.projectst.rpgcore.classes.ClassDefLoader
                .load("mage.yml", resource("classes/mage.yml"), errors).orElseThrow();
        var stats = StatDefLoader
                .load("stats.yml", resource("stats.yml"), errors).orElseThrow();

        for (String statId : mage.statCurves().keySet()) {
            assertTrue(stats.has(statId),
                    "класс даёт базу стату " + statId + ", которого нет в stats.yml");
        }
    }

    @Test
    @DisplayName("пассивный навык объявлен триггером и в слот не ставится")
    void passiveSkillIsDeclaredAsSuch() throws IOException {
        ContentErrors errors = new ContentErrors();
        var ward = ru.projectst.rpgcore.skill.SkillLoader.load("mage_mana_ward.yml",
                resource("skills/mage_mana_ward.yml"), errors).orElseThrow();

        assertTrue(errors.isEmpty(), () -> errors.all().toString());
        assertEquals(ru.projectst.rpgcore.skill.SkillTrigger.ON_DAMAGED, ward.trigger());
        assertTrue(ward.passive(), "иначе его можно было бы повесить на слот");
    }

    @Test
    @DisplayName("снаряд поставляемого разряда ссылается на существующий навык попадания")
    void projectileReferenceResolves() throws IOException {
        ContentErrors errors = new ContentErrors();
        var bolt = ru.projectst.rpgcore.skill.SkillLoader.load("mage_mana_bolt.yml",
                resource("skills/mage_mana_bolt.yml"), errors).orElseThrow();
        assertTrue(errors.isEmpty(), () -> errors.all().toString());

        var action = bolt.steps().get(0).actions().get(1);
        var projectile = (ru.projectst.rpgcore.skill.Action.Projectile) action;
        assertEquals("mage_mana_bolt_impact", projectile.onHit());
        assertEquals("mage_mana_bolt_impact", projectile.onEnd(),
                "разрыв в точке падения — то же действие, что и разрыв в цели");
    }
}
