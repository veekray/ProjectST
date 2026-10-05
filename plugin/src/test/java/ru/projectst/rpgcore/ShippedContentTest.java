package ru.projectst.rpgcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.projectst.rpgcore.balance.BalanceBook;
import ru.projectst.rpgcore.balance.BalanceLoader;
import ru.projectst.rpgcore.classes.ClassDef;
import ru.projectst.rpgcore.classes.ClassDefLoader;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.skill.SkillDef;
import ru.projectst.rpgcore.skill.SkillLinker;
import ru.projectst.rpgcore.skill.SkillLoader;
import ru.projectst.rpgcore.stat.StatDefLoader;
import ru.projectst.rpgcore.stat.StatRegistry;
import ru.projectst.rpgcore.status.StatusDefLoader;
import ru.projectst.rpgcore.status.StatusRegistry;

/**
 * Приёмка переноса: весь поставляемый контент грузится и связывается.
 *
 * <p>Тест читает файлы с диска, а не список, вписанный в код. Поэтому новый
 * навык попадает под проверку самим фактом своего существования, а не после
 * того, как кто-то вспомнит дописать его имя в тест. Забытая строка в таком
 * списке — это та же тихая дыра, от которой уходит весь проект.
 *
 * <p>Именно это и означает «перенос состоялся»: двадцать четыре навыка мага,
 * друида, колдуна и плута читаются строгим загрузчиком, и ни одна их ссылка —
 * на ключ баланса, статус, класс, подчинённый навык или счётчик — не висит в
 * пустоте.
 */
class ShippedContentTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    /** Сколько активных навыков должно быть перенесено, по шесть на класс. */
    private static final int EXPECTED_SELECTABLE = 34;

    private static String read(String name) throws IOException {
        return Files.readString(RESOURCES.resolve(name), StandardCharsets.UTF_8);
    }

    private static List<Path> skillFiles() throws IOException {
        try (var files = Files.list(RESOURCES.resolve("skills"))) {
            return files.filter(p -> p.toString().endsWith(".yml")).sorted().toList();
        }
    }

    private static List<Path> classFiles() throws IOException {
        try (var files = Files.list(RESOURCES.resolve("classes"))) {
            return files.filter(p -> p.toString().endsWith(".yml")).sorted().toList();
        }
    }

    private record Content(List<SkillDef> skills, List<ClassDef> classes,
                           BalanceBook balance, StatusRegistry statuses, StatRegistry stats,
                           ru.projectst.rpgcore.item.ItemRegistry items,
                           List<ru.projectst.rpgcore.craft.RecipeDef> recipes,
                           ru.projectst.rpgcore.mob.MobRegistry mobs) {
    }

    private static List<Path> filesIn(String subfolder) throws IOException {
        Path dir = RESOURCES.resolve(subfolder);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var files = Files.list(dir)) {
            return files.filter(f -> f.toString().endsWith(".yml")).sorted().toList();
        }
    }

    private static Content load() throws IOException {
        ContentErrors errors = new ContentErrors();

        List<SkillDef> skills = new ArrayList<>();
        for (Path file : skillFiles()) {
            String name = file.getFileName().toString();
            SkillLoader.load(name, Files.readString(file, StandardCharsets.UTF_8), errors)
                    .ifPresent(skills::add);
        }
        List<ClassDef> classes = new ArrayList<>();
        for (Path file : classFiles()) {
            String name = file.getFileName().toString();
            ClassDefLoader.load(name, Files.readString(file, StandardCharsets.UTF_8), errors)
                    .ifPresent(classes::add);
        }
        BalanceBook balance = BalanceLoader
                .load("balance.yml", read("balance.yml"), errors).orElseThrow();
        StatusRegistry statuses = StatusDefLoader
                .load("statuses.yml", read("statuses.yml"), errors).orElseThrow();
        StatRegistry stats = StatDefLoader
                .load("stats.yml", read("stats.yml"), errors).orElseThrow();

        var rarities = ru.projectst.rpgcore.item.ItemDefLoader
                .loadRarities("rarities.yml", read("rarities.yml"), errors).orElseThrow();
        Map<String, ru.projectst.rpgcore.item.ItemDef> itemMap = new LinkedHashMap<>();
        for (Path file : filesIn("items")) {
            String name = file.getFileName().toString();
            ru.projectst.rpgcore.item.ItemDefLoader
                    .load(name, Files.readString(file, StandardCharsets.UTF_8), errors)
                    .ifPresent(item -> itemMap.put(item.id(), item));
        }
        List<ru.projectst.rpgcore.craft.RecipeDef> recipes = new ArrayList<>();
        for (Path file : filesIn("recipes")) {
            String name = file.getFileName().toString();
            ru.projectst.rpgcore.craft.RecipeLoader
                    .load(name, Files.readString(file, StandardCharsets.UTF_8), errors)
                    .ifPresent(recipes::add);
        }

        assertTrue(errors.isEmpty(), () -> "контент не читается:\n" + join(errors));
        Map<String, ru.projectst.rpgcore.mob.MobDef> mobMap = new LinkedHashMap<>();
        for (Path file : filesIn("mobs")) {
            String name = file.getFileName().toString();
            ru.projectst.rpgcore.mob.MobDefLoader
                    .load(name, Files.readString(file, StandardCharsets.UTF_8), errors)
                    .ifPresent(mob -> mobMap.put(mob.id(), mob));
        }
        var spawnRules = ru.projectst.rpgcore.mob.MobDefLoader
                .loadRules("spawn.yml", read("spawn.yml"), errors).orElseThrow();

        return new Content(skills, classes, balance, statuses, stats,
                new ru.projectst.rpgcore.item.ItemRegistry(itemMap, rarities), recipes,
                new ru.projectst.rpgcore.mob.MobRegistry(mobMap, spawnRules));
    }

    private static String join(ContentErrors errors) {
        StringBuilder out = new StringBuilder();
        errors.all().forEach(error -> out.append("  ").append(error).append('\n'));
        return out.toString();
    }

    @Test
    @DisplayName("весь поставляемый контент грузится и все ссылки разрешаются")
    void everythingLinks() throws IOException {
        Content content = load();
        Set<String> classIds = new LinkedHashSet<>();
        content.classes().forEach(def -> classIds.add(def.id()));

        ContentErrors link = new ContentErrors();
        SkillLinker.link(content.skills(), content.balance(), content.statuses(),
                classIds, content.stats(), link);

        assertTrue(link.isEmpty(), () -> "ссылки не разрешились:\n" + join(link));
    }

    @Test
    @DisplayName("перенесены все активные навыки, и у каждого класса их столько, сколько было")
    void allTwentyFourAreThere() throws IOException {
        Content content = load();

        Map<String, Integer> perClass = new LinkedHashMap<>();
        int selectable = 0;
        for (SkillDef skill : content.skills()) {
            if (skill.selectable()) {
                selectable++;
                perClass.merge(skill.classId(), 1, Integer::sum);
            }
        }

        assertEquals(EXPECTED_SELECTABLE, selectable,
                () -> "активных навыков по классам: " + perClass);
        assertEquals(Map.of("mage", 6, "druid", 6, "warlock", 6, "rogue", 6,
                "assassin", 10), perClass);
    }

    @Test
    @DisplayName("у каждого класса есть файл, и каждый навык принадлежит существующему классу")
    void everySkillHasItsClass() throws IOException {
        Content content = load();
        Set<String> classIds = new LinkedHashSet<>();
        content.classes().forEach(def -> classIds.add(def.id()));

        for (SkillDef skill : content.skills()) {
            if (skill.classId().isBlank()) {
                assertTrue(skill.internal(),
                        "без класса бывают только служебные навыки: " + skill.id());
                continue;
            }
            assertTrue(classIds.contains(skill.classId()),
                    "навык " + skill.id() + " ссылается на класс " + skill.classId());
        }
    }

    @Test
    @DisplayName("ступени навыков объявлены классом, иначе ограничение было бы фиктивным")
    void everyTierIsDeclared() throws IOException {
        Content content = load();
        Map<String, ClassDef> byId = new LinkedHashMap<>();
        content.classes().forEach(def -> byId.put(def.id(), def));

        for (SkillDef skill : content.skills()) {
            ClassDef owner = byId.get(skill.classId());
            if (owner == null || skill.internal()) {
                continue;
            }
            assertTrue(owner.declaresTier(skill.tier()),
                    "класс " + owner.id() + " не объявил ступень " + skill.tier()
                            + ", которую требует навык " + skill.id());
        }
    }

    @Test
    @DisplayName("служебные навыки не попадают игроку: ни изучить, ни повесить")
    void internalSkillsAreHidden() throws IOException {
        Content content = load();

        List<String> internal = content.skills().stream()
                .filter(SkillDef::internal).map(SkillDef::id).toList();

        assertFalse(internal.isEmpty(), "ядро мага и попадания снарядов — служебные");
        for (SkillDef skill : content.skills()) {
            if (skill.internal()) {
                assertFalse(skill.selectable(), skill.id() + " не должен быть выбираемым");
            }
        }
    }

    @Test
    @DisplayName("каждый класс платит объявленным ресурсом, и его статы существуют")
    void resourceStatsExist() throws IOException {
        Content content = load();

        for (ClassDef def : content.classes()) {
            assertTrue(content.stats().has(def.resource().maxStat()),
                    "класс " + def.id() + " платит статом " + def.resource().maxStat()
                            + ", которого нет в stats.yml");
            assertTrue(content.stats().has(def.resource().regenStat()),
                    "класс " + def.id() + " восстанавливает стат "
                            + def.resource().regenStat() + ", которого нет в stats.yml");
        }
    }

    @Test
    @DisplayName("базовые статы классов объявлены в stats.yml")
    void classStatsExist() throws IOException {
        Content content = load();

        for (ClassDef def : content.classes()) {
            for (String statId : def.statCurves().keySet()) {
                assertTrue(content.stats().has(statId),
                        "класс " + def.id() + " даёт базу стату " + statId
                                + ", которого нет в stats.yml");
            }
        }
    }

    @Test
    @DisplayName("у каждого навыка есть короткое описание для подсказки")
    void everySkillIsDescribed() throws IOException {
        Content content = load();

        for (SkillDef skill : content.skills()) {
            if (!skill.selectable()) {
                continue;
            }
            assertFalse(skill.description().isEmpty(),
                    "навык " + skill.id() + " без описания: в подсказке игрок увидит"
                            + " числа и ничего о том, что навык делает");
            for (String line : skill.description()) {
                assertFalse(line.matches(".*[0-9]+.*"),
                        "в описании " + skill.id() + " есть число: «" + line + "»."
                                + " Числа считает и присылает сервер, а переписанное"
                                + " в текст разъедется с балансом");
            }
        }
    }

    @Test
    @DisplayName("у каждого перенесённого навыка и класса своя иконка")
    void everythingHasAnIcon() throws IOException {
        Content content = load();

        for (SkillDef skill : content.skills()) {
            if (!skill.selectable()) {
                continue;
            }
            assertNotEquals(SkillDef.DEFAULT_ICON, skill.icon(),
                    "навык " + skill.id() + " остался с иконкой по умолчанию: в интерфейсе"
                            + " шесть одинаковых бумажек не отличить друг от друга");
            assertTrue(skill.icon().matches("[A-Z0-9_]+"),
                    "иконка должна быть именем предмета: " + skill.icon());
        }
        for (ClassDef def : content.classes()) {
            assertNotEquals(ClassDef.DEFAULT_ICON, def.icon(),
                    "класс " + def.id() + " остался с иконкой по умолчанию");
        }
    }

    // ------------------------------------------------------------------ предметы

    @Test
    @DisplayName("предметы и рецепты связываются: ни одной ссылки в пустоту")
    void itemsAndRecipesLink() throws IOException {
        Content content = load();
        Set<String> classIds = new LinkedHashSet<>();
        content.classes().forEach(def -> classIds.add(def.id()));
        Map<String, SkillDef> skillMap = new LinkedHashMap<>();
        content.skills().forEach(skill -> skillMap.put(skill.id(), skill));

        ContentErrors link = new ContentErrors();
        ru.projectst.rpgcore.item.ItemLinker.link(
                List.copyOf(toList(content.items().all())), content.items(), content.stats(),
                new ru.projectst.rpgcore.skill.SkillRegistry(skillMap), classIds, link);
        ru.projectst.rpgcore.craft.RecipeLinker.link(content.recipes(), content.items(), link);

        assertTrue(link.isEmpty(), () -> "ссылки предметов и рецептов не разрешились:\n"
                + join(link));
    }

    @Test
    @DisplayName("умение предмета — всегда служебный навык без класса")
    void itemAbilitiesAreInternal() throws IOException {
        Content content = load();
        Map<String, SkillDef> skillMap = new LinkedHashMap<>();
        content.skills().forEach(skill -> skillMap.put(skill.id(), skill));

        for (var item : content.items().all()) {
            for (var ability : item.abilities()) {
                SkillDef skill = skillMap.get(ability.skillId());
                assertTrue(skill != null, "умение " + item.id() + " ссылается на "
                        + ability.skillId());
                assertTrue(skill.internal(), ability.skillId() + " должен быть служебным: иначе"
                        + " предмет даёт классовый навык в обход изучения");
                assertTrue(skill.classId().isBlank(),
                        ability.skillId() + " не должен принадлежать классу");
            }
        }
    }

    @Test
    @DisplayName("каждый рецепт делает объявленный предмет")
    void recipesMakeDeclaredItems() throws IOException {
        Content content = load();

        assertFalse(content.recipes().isEmpty(), "рецепты должны быть поставлены");
        for (var recipe : content.recipes()) {
            assertTrue(content.items().has(recipe.resultItemId()),
                    "рецепт " + recipe.id() + " делает " + recipe.resultItemId());
        }
    }

    private static <T> List<T> toList(Iterable<T> source) {
        List<T> out = new ArrayList<>();
        source.forEach(out::add);
        return out;
    }

    // ------------------------------------------------------------------ мобы

    @Test
    @DisplayName("мобы и правила спавна связываются: ни одной ссылки в пустоту")
    void mobsLink() throws IOException {
        Content content = load();
        Map<String, SkillDef> skillMap = new LinkedHashMap<>();
        content.skills().forEach(skill -> skillMap.put(skill.id(), skill));

        ContentErrors link = new ContentErrors();
        ru.projectst.rpgcore.mob.MobLinker.link(toList(content.mobs().all()), content.mobs(),
                content.stats(), new ru.projectst.rpgcore.skill.SkillRegistry(skillMap),
                content.items(), link);

        assertTrue(link.isEmpty(), () -> "ссылки мобов не разрешились:" + System.lineSeparator() + join(link));
    }

    @Test
    @DisplayName("навык моба — всегда служебный навык без класса")
    void mobSkillsAreInternal() throws IOException {
        Content content = load();
        Map<String, SkillDef> skillMap = new LinkedHashMap<>();
        content.skills().forEach(skill -> skillMap.put(skill.id(), skill));

        assertFalse(toList(content.mobs().all()).isEmpty(), "мобы должны быть поставлены");
        for (var mob : content.mobs().all()) {
            for (var skill : mob.skills()) {
                SkillDef def = skillMap.get(skill.skillId());
                assertTrue(def != null, mob.id() + " ссылается на " + skill.skillId());
                assertTrue(def.internal(), skill.skillId() + " должен быть служебным");
                assertTrue(def.classId().isBlank(),
                        skill.skillId() + " не должен принадлежать классу");
            }
        }
    }

    @Test
    @DisplayName("каждое правило спавна ссылается на объявленного моба")
    void spawnRulesPointAtMobs() throws IOException {
        Content content = load();

        assertFalse(content.mobs().rules().isEmpty(), "правила спавна должны быть поставлены");
        for (var rule : content.mobs().rules()) {
            assertTrue(content.mobs().has(rule.mobId()),
                    "правило ссылается на " + rule.mobId());
        }
    }
}
