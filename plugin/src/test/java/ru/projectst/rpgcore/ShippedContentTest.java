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
    // Охотников девять, а не десять: десятым у него шёл Азарт, у которого
    // кнопки нет и не было — он копится от попаданий и включается сам.
    private static final int EXPECTED_SELECTABLE = 97;

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
        // Через ofEntries, а не of: у короткой формы потолок в десять пар, и
        // одиннадцатый класс её не переполняет молча — он её не компилирует.
        assertEquals(Map.ofEntries(
                Map.entry("mage", 6),
                Map.entry("druid", 6),
                Map.entry("warlock", 6),
                Map.entry("rogue", 10),
                Map.entry("assassin", 10),
                Map.entry("trickster", 10),
                Map.entry("hunter", 9),
                Map.entry("warrior", 10),
                Map.entry("knight", 10),
                Map.entry("berserker", 10),
                Map.entry("striker", 10)), perClass);
    }

    @Test
    @DisplayName("у каждого класса есть файл, и каждый навык принадлежит существующему классу")
    void everySkillHasItsClass() throws IOException {
        Content content = load();
        Set<String> classIds = new LinkedHashSet<>();
        content.classes().forEach(def -> classIds.add(def.id()));

        for (SkillDef skill : content.skills()) {
            if (skill.classId().isBlank()) {
                assertTrue(skill.internal() || skill.innate(),
                        "без класса бывают только служебные и врождённые навыки: "
                                + skill.id());
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
    @DisplayName("врождённый рывок есть, он один, и платит выносливостью")
    void innateDashIsThere() throws IOException {
        Content content = load();

        List<SkillDef> innate = content.skills().stream().filter(SkillDef::innate).toList();

        assertEquals(1, innate.size(),
                () -> "врождённый навык должен быть ровно один: " + innate.stream()
                        .map(SkillDef::id).toList());
        SkillDef dash = innate.get(0);
        assertTrue(dash.classId().isBlank(), "врождённый навык есть у всех, значит без класса");
        assertFalse(dash.selectable(), "его не изучают и не вешают на слот");
        assertFalse(dash.internal(), "его нажимает игрок, а не предмет и не триггер класса");
        assertTrue(dash.charges() > 1, "заряды — половина смысла рывка");
        assertNotEquals(SkillDef.DEFAULT_ICON, dash.icon(),
                "рывок рисуется на экране, значок ему нужен так же, как навыку на слоте");
        assertFalse(dash.description().isEmpty(), "подсказке нужно, что он делает");

        // Платит выносливостью, а не ресурсом класса: иначе уклонение
        // покупалось бы уроном. Числа — из той же таблицы, что читает бой.
        var table = content.balance().table(dash.id());
        assertTrue(dash.staminaCost().resolve(table, 1) > 0,
                "рывок обязан тратить выносливость: иначе её не из чего тратить вовсе");
        assertEquals(0, dash.resourceCost().resolve(table, 1),
                "ресурс класса рывок не трогает");
        assertTrue(dash.cooldown().resolve(table, 1) > 0,
                "без перезарядки потраченный заряд не вернётся никогда");
    }

    @Test
    @DisplayName("рывок идёт по ходу игрока, а не по взгляду")
    void dashFollowsMovement() throws IOException {
        Content content = load();
        SkillDef dash = content.skills().stream().filter(SkillDef::innate).findFirst()
                .orElseThrow();

        boolean alongMovement = dash.steps().stream()
                .flatMap(step -> step.actions().stream())
                .anyMatch(action -> action instanceof ru.projectst.rpgcore.skill.Action.Dash d
                        && d.alongMovement());

        assertTrue(alongMovement, "иначе бегущий в сторону игрок прыгнет туда, куда смотрит");
    }

    @Test
    @DisplayName("ни один класс не платит выносливостью: она общая и её ест рывок")
    void noClassPaysWithStamina() throws IOException {
        Content content = load();
        var stamina = ru.projectst.rpgcore.classes.ResourceSpec.STAMINA;

        for (ClassDef def : content.classes()) {
            assertNotEquals(stamina.maxStat(), def.resource().maxStat(),
                    "класс " + def.id() + " платит выносливостью, из которой платится рывок:"
                            + " тогда уклонение покупается уроном");
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

    // ------------------------------------------------------------------ эффекты мода

    /** Каталог эффектов в моде: тесты идут из каталога plugin, мод лежит рядом. */
    private static final Path MOD_CATALOG = Path.of("..", "client-mod", "src", "main", "java",
            "ru", "projectst", "rpgcore", "client", "FxStyle.java");

    /**
     * Каталог мода — стили ({@code FxStyle}) и сцены, разложенные по файлам
     * классов ({@code Scenes<Класс>}): эффект может быть и тем, и другим.
     */
    private static String modCatalog() throws IOException {
        StringBuilder catalog = new StringBuilder();
        try (var files = Files.list(MOD_CATALOG.getParent())) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".java"))
                    .sorted().toList()) {
                catalog.append(Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return catalog.toString();
    }

    @Test
    @DisplayName("каждый эффект поставляемого контента есть в каталоге мода")
    void everyShippedFxIsKnownToTheMod() throws IOException {
        if (!Files.isRegularFile(MOD_CATALOG)) {
            return; // мода рядом нет — проверять нечего
        }
        String catalog = modCatalog();
        // Неизвестный эффект мод рисует общим — игра не падает. Но эффект,
        // объявленный в своём контенте и не нарисованный, — это опечатка,
        // которую иначе нашли бы только глазами в игре.
        java.util.Set<String> missing = new java.util.TreeSet<>();
        for (SkillDef skill : load().skills()) {
            for (var step : skill.steps()) {
                for (var action : step.actions()) {
                    String fx = switch (action) {
                        case ru.projectst.rpgcore.skill.Action.Particles a -> a.fx();
                        case ru.projectst.rpgcore.skill.Action.Projectile a -> a.fx();
                        case ru.projectst.rpgcore.skill.Action.PlaceZone a -> a.fx();
                        case ru.projectst.rpgcore.skill.Action.Teleport a -> a.fx();
                        default -> null;
                    };
                    if (fx != null && !ru.projectst.rpgcore.skill.FxEvent.NONE.equals(fx)
                            && !catalog.contains("Map.entry(\"" + fx + "\"")) {
                        missing.add(skill.id() + ": " + fx);
                    }
                }
                if (step.telegraphed() && !catalog.contains("Map.entry(\"" + step.telegraph() + "\"")) {
                    missing.add(skill.id() + ": " + step.telegraph());
                }
            }
        }
        assertTrue(missing.isEmpty(), "эффектов нет в каталоге мода (FxStyle): " + missing);
    }

    @Test
    @DisplayName("у каждого видимого действия поставляемого контента есть эффект мода")
    void everyShippedVisualHasFx() throws IOException {
        // Игрок с модом не должен видеть ванильных частиц навыков. Сервер и так
        // шлёт ему общий эффект вместо частиц без fx, но общий — это заглушка:
        // навык, который в бою выглядит заглушкой, тоже ошибка, просто тихая.
        java.util.Set<String> bare = new java.util.TreeSet<>();
        java.util.Set<String> lonelyNone = new java.util.TreeSet<>();
        for (SkillDef skill : load().skills()) {
            for (var step : skill.steps()) {
                boolean stepHasOwnFx = false;
                for (var action : step.actions()) {
                    String fx = fxOf(action);
                    if (fx != null && !ru.projectst.rpgcore.skill.FxEvent.NONE.equals(fx)) {
                        stepHasOwnFx = true;
                    }
                }
                for (var action : step.actions()) {
                    boolean visible = switch (action) {
                        case ru.projectst.rpgcore.skill.Action.Particles a -> true;
                        case ru.projectst.rpgcore.skill.Action.Projectile a -> a.particle() != null;
                        case ru.projectst.rpgcore.skill.Action.PlaceZone a -> a.particle() != null;
                        case ru.projectst.rpgcore.skill.Action.Teleport a -> a.particle() != null;
                        default -> false;
                    };
                    String fx = fxOf(action);
                    if (visible && fx == null) {
                        bare.add(skill.id() + ": " + action.name());
                    }
                    // none — «украшение рядом со своим эффектом». Без своего
                    // эффекта в шаге игрок с модом не увидел бы ничего.
                    if (ru.projectst.rpgcore.skill.FxEvent.NONE.equals(fx) && !stepHasOwnFx) {
                        lonelyNone.add(skill.id());
                    }
                }
            }
        }
        assertTrue(bare.isEmpty(), "видимые действия без fx: " + bare);
        assertTrue(lonelyNone.isEmpty(), "fx: none без своего эффекта в шаге: " + lonelyNone);
    }

    @Test
    @DisplayName("граница области рисуется радиусом выборки, а не числом в файле")
    void areaEffectsFollowTheTargetRadius() throws IOException {
        // Круг, вписанный числом в шаг «на себя», не знает ни баланса, ни стата
        // радиуса: урон бьёт на девяти блоках, а круг стоит на четырёх. Роль
        // _wave и _aura говорит «это граница области» — значит, её размер
        // обязан быть size: radius в шаге, который эту область выбирает.
        java.util.Set<String> literal = new java.util.TreeSet<>();
        for (SkillDef skill : load().skills()) {
            for (var step : skill.steps()) {
                for (var action : step.actions()) {
                    if (action instanceof ru.projectst.rpgcore.skill.Action.Particles a
                            && a.fx() != null
                            && (a.fx().endsWith("_wave") || a.fx().endsWith("_aura"))
                            && !a.fitRadius()) {
                        literal.add(skill.id() + ": " + a.fx());
                    }
                }
            }
        }
        assertTrue(literal.isEmpty(), "граница области задана числом: " + literal);
    }

    @Test
    @DisplayName("у каждого навыка каждый звук — со своим видом для мода")
    void everySoundHasFx() throws IOException {
        // Все классы переведены на сцены: звук либо событие мода, либо его
        // играет сцена (fx: none). Звук без fx уходил бы игроку с модом
        // ванильным поверх звука сцены — тот же дубль, что и две картинки.
        java.util.Set<String> bare = new java.util.TreeSet<>();
        for (SkillDef skill : load().skills()) {
            for (var step : skill.steps()) {
                for (var action : step.actions()) {
                    if (action instanceof ru.projectst.rpgcore.skill.Action.Sound sound
                            && sound.fx() == null) {
                        bare.add(skill.id() + ": " + sound.sound());
                    }
                }
            }
        }
        assertTrue(bare.isEmpty(), "звуки без fx: " + bare);
    }

    @Test
    @DisplayName("каждый звук мода в контенте объявлен в sounds.json мода")
    void everySoundFxIsDeclaredByTheMod() throws IOException {
        Path sounds = MOD_CATALOG.getParent().resolve(Path.of("..", "..", "..", "..", "..",
                "resources", "assets", "rpgcore", "sounds.json")).normalize();
        if (!Files.isRegularFile(sounds)) {
            return; // мода рядом нет — проверять нечего
        }
        // Неизвестное событие менеджер звука молча пропустит, и игрок с модом
        // не услышит ничего — ванильный ему тоже не уходит.
        String declared = Files.readString(sounds, StandardCharsets.UTF_8);
        java.util.Set<String> missing = new java.util.TreeSet<>();
        for (SkillDef skill : load().skills()) {
            for (var step : skill.steps()) {
                for (var action : step.actions()) {
                    if (action instanceof ru.projectst.rpgcore.skill.Action.Sound sound
                            && sound.fx() != null
                            && !ru.projectst.rpgcore.skill.FxEvent.NONE.equals(sound.fx())
                            && !declared.contains("\"" + sound.fx() + "\": {")) {
                        missing.add(skill.id() + ": " + sound.fx());
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "звуков нет в sounds.json мода: " + missing);
    }

    private static String fxOf(ru.projectst.rpgcore.skill.Action action) {
        return switch (action) {
            case ru.projectst.rpgcore.skill.Action.Particles a -> a.fx();
            case ru.projectst.rpgcore.skill.Action.Projectile a -> a.fx();
            case ru.projectst.rpgcore.skill.Action.PlaceZone a -> a.fx();
            case ru.projectst.rpgcore.skill.Action.Teleport a -> a.fx();
            default -> null;
        };
    }
}
