package ru.projectst.rpgcore.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import ru.projectst.rpgcore.balance.BalanceBook;
import ru.projectst.rpgcore.balance.BalanceLoader;
import ru.projectst.rpgcore.classes.ClassDef;
import ru.projectst.rpgcore.classes.ClassDefLoader;
import ru.projectst.rpgcore.classes.ClassRegistry;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.loader.SourceRef;
import ru.projectst.rpgcore.skill.SkillDef;
import ru.projectst.rpgcore.skill.SkillLinker;
import ru.projectst.rpgcore.skill.SkillLoader;
import ru.projectst.rpgcore.skill.SkillRegistry;
import ru.projectst.rpgcore.stat.StatDefLoader;
import ru.projectst.rpgcore.stat.StatRegistry;
import ru.projectst.rpgcore.status.StatusDefLoader;
import ru.projectst.rpgcore.status.StatusRegistry;

/**
 * Загрузка всего контента с диска, его проверка и связывание.
 *
 * <p>Живёт в {@code platform/}, потому что знает про файловую систему. Сами
 * загрузчики к файлам не привязаны — им отдают текст, поэтому они проверяются
 * юнит-тестами.
 *
 * <p>Порядок обязателен: сначала статы и статусы, потом баланс, потом навыки,
 * и только затем связывание. Иначе проверять ссылки было бы не с чем.
 */
public final class ContentService {

    private final Path folder;

    private StatRegistry stats = new StatRegistry(Map.of());
    private StatusRegistry statuses = new StatusRegistry(Map.of());
    private BalanceBook balance = BalanceBook.EMPTY;
    private SkillRegistry skills = SkillRegistry.EMPTY;
    private ClassRegistry playerClasses = ClassRegistry.EMPTY;
    private ru.projectst.rpgcore.item.ItemRegistry items =
            ru.projectst.rpgcore.item.ItemRegistry.EMPTY;
    private ru.projectst.rpgcore.craft.RecipeRegistry recipes =
            ru.projectst.rpgcore.craft.RecipeRegistry.EMPTY;
    private ru.projectst.rpgcore.mob.MobRegistry mobs =
            ru.projectst.rpgcore.mob.MobRegistry.EMPTY;

    public ContentService(Path folder) {
        this.folder = folder;
    }

    public StatRegistry stats() {
        return stats;
    }

    public StatusRegistry statuses() {
        return statuses;
    }

    public BalanceBook balance() {
        return balance;
    }

    public SkillRegistry skills() {
        return skills;
    }

    public ClassRegistry playerClasses() {
        return playerClasses;
    }

    public ru.projectst.rpgcore.item.ItemRegistry items() {
        return items;
    }

    public ru.projectst.rpgcore.craft.RecipeRegistry recipes() {
        return recipes;
    }

    public ru.projectst.rpgcore.mob.MobRegistry mobs() {
        return mobs;
    }

    /** Перечитывает контент и применяет его. */
    public ContentErrors reload() {
        ContentErrors errors = new ContentErrors();
        Loaded loaded = loadAll(errors);
        stats = loaded.stats();
        statuses = loaded.statuses();
        balance = loaded.balance();
        skills = loaded.skills();
        playerClasses = loaded.playerClasses();
        items = loaded.items();
        recipes = loaded.recipes();
        mobs = loaded.mobs();
        return errors;
    }

    /**
     * Проверяет контент, <b>ничего не применяя</b>: всё загружается в
     * выброшенные реестры. Отдельный путь нужен именно поэтому — иначе
     * «проверить» означало бы «применить», и битый файл всё равно попал бы
     * в игру.
     */
    public ContentErrors validateOnly() {
        ContentErrors errors = new ContentErrors();
        loadAll(errors);
        return errors;
    }

    private record Loaded(StatRegistry stats, StatusRegistry statuses,
                          BalanceBook balance, SkillRegistry skills,
                          ClassRegistry playerClasses,
                          ru.projectst.rpgcore.item.ItemRegistry items,
                          ru.projectst.rpgcore.craft.RecipeRegistry recipes,
                          ru.projectst.rpgcore.mob.MobRegistry mobs) {
    }

    private Loaded loadAll(ContentErrors errors) {
        StatRegistry loadedStats = read("stats.yml", errors)
                .flatMap(text -> StatDefLoader.load("stats.yml", text, errors))
                .orElseGet(() -> new StatRegistry(Map.of()));

        StatusRegistry loadedStatuses = read("statuses.yml", errors)
                .flatMap(text -> StatusDefLoader.load("statuses.yml", text, errors))
                .orElseGet(() -> new StatusRegistry(Map.of()));

        BalanceBook loadedBalance = read("balance.yml", errors)
                .flatMap(text -> BalanceLoader.load("balance.yml", text, errors))
                .orElse(BalanceBook.EMPTY);

        Map<String, SkillDef> skillMap = new LinkedHashMap<>();
        for (Path file : filesIn("skills", errors)) {
            String name = file.getFileName().toString();
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                SkillLoader.load(name, text, errors).ifPresent(skill -> {
                    if (skillMap.putIfAbsent(skill.id(), skill) != null) {
                        errors.add(SourceRef.ofFile(name), "id",
                                "навык с таким id уже загружен: " + skill.id());
                    }
                });
            } catch (IOException e) {
                errors.add(SourceRef.ofFile(name), "", "не читается: " + e.getMessage());
            }
        }

        Map<String, ClassDef> classMap = new LinkedHashMap<>();
        for (Path file : filesIn("classes", errors)) {
            String name = file.getFileName().toString();
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                ClassDefLoader.load(name, text, errors).ifPresent(def -> {
                    if (classMap.putIfAbsent(def.id(), def) != null) {
                        errors.add(SourceRef.ofFile(name), "id",
                                "класс с таким id уже загружен: " + def.id());
                    }
                });
            } catch (IOException e) {
                errors.add(SourceRef.ofFile(name), "", "не читается: " + e.getMessage());
            }
        }

        // Редкости до предметов: предмет ссылается на редкость, а не наоборот.
        Map<String, ru.projectst.rpgcore.item.Rarity> rarities = read("rarities.yml", errors)
                .flatMap(text -> ru.projectst.rpgcore.item.ItemDefLoader
                        .loadRarities("rarities.yml", text, errors))
                .orElseGet(() -> new LinkedHashMap<>(Map.of(
                        ru.projectst.rpgcore.item.Rarity.COMMON.id(),
                        ru.projectst.rpgcore.item.Rarity.COMMON)));

        Map<String, ru.projectst.rpgcore.item.ItemDef> itemMap = new LinkedHashMap<>();
        for (Path file : filesIn("items", errors)) {
            String name = file.getFileName().toString();
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                ru.projectst.rpgcore.item.ItemDefLoader.load(name, text, errors)
                        .ifPresent(item -> {
                            if (itemMap.putIfAbsent(item.id(), item) != null) {
                                errors.add(SourceRef.ofFile(name), "id",
                                        "предмет с таким id уже загружен: " + item.id());
                            }
                        });
            } catch (IOException e) {
                errors.add(SourceRef.ofFile(name), "", "не читается: " + e.getMessage());
            }
        }

        Map<String, ru.projectst.rpgcore.craft.RecipeDef> recipeMap = new LinkedHashMap<>();
        for (Path file : filesIn("recipes", errors)) {
            String name = file.getFileName().toString();
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                ru.projectst.rpgcore.craft.RecipeLoader.load(name, text, errors)
                        .ifPresent(recipe -> {
                            if (recipeMap.putIfAbsent(recipe.id(), recipe) != null) {
                                errors.add(SourceRef.ofFile(name), "id",
                                        "рецепт с таким id уже загружен: " + recipe.id());
                            }
                        });
            } catch (IOException e) {
                errors.add(SourceRef.ofFile(name), "", "не читается: " + e.getMessage());
            }
        }

        Map<String, ru.projectst.rpgcore.mob.MobDef> mobMap = new LinkedHashMap<>();
        for (Path file : filesIn("mobs", errors)) {
            String name = file.getFileName().toString();
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                ru.projectst.rpgcore.mob.MobDefLoader.load(name, text, errors)
                        .ifPresent(mob -> {
                            if (mobMap.putIfAbsent(mob.id(), mob) != null) {
                                errors.add(SourceRef.ofFile(name), "id",
                                        "моб с таким id уже загружен: " + mob.id());
                            }
                        });
            } catch (IOException e) {
                errors.add(SourceRef.ofFile(name), "", "не читается: " + e.getMessage());
            }
        }

        // Правила спавна лежат отдельным файлом: они не про одного моба, а про
        // мир, и держать их внутри моба значило бы искать по всем файлам, кто
        // где появляется.
        List<ru.projectst.rpgcore.mob.SpawnRule> spawnRules =
                Files.isRegularFile(folder.resolve("spawn.yml"))
                        ? read("spawn.yml", errors)
                                .flatMap(text -> ru.projectst.rpgcore.mob.MobDefLoader
                                        .loadRules("spawn.yml", text, errors))
                                .orElseGet(List::of)
                        : List.of();

        // Связывание последним: до него нет ни баланса, ни статусов, ни
        // классов, ни предметов для сверки.
        SkillRegistry skillRegistry = new SkillRegistry(skillMap);
        ru.projectst.rpgcore.item.ItemRegistry itemRegistry =
                new ru.projectst.rpgcore.item.ItemRegistry(itemMap, rarities);

        SkillLinker.link(skillMap.values(), loadedBalance, loadedStatuses,
                classMap.keySet(), loadedStats, errors);
        ru.projectst.rpgcore.item.ItemLinker.link(itemMap.values(), itemRegistry,
                loadedStats, skillRegistry, classMap.keySet(), errors);
        ru.projectst.rpgcore.craft.RecipeLinker.link(recipeMap.values(), itemRegistry, errors);

        ru.projectst.rpgcore.mob.MobRegistry mobRegistry =
                new ru.projectst.rpgcore.mob.MobRegistry(mobMap, spawnRules);
        ru.projectst.rpgcore.mob.MobLinker.link(mobMap.values(), mobRegistry, loadedStats,
                skillRegistry, itemRegistry, errors);

        return new Loaded(loadedStats, loadedStatuses, loadedBalance,
                skillRegistry, new ClassRegistry(classMap), itemRegistry,
                new ru.projectst.rpgcore.craft.RecipeRegistry(recipeMap), mobRegistry);
    }

    private List<Path> filesIn(String subfolder, ContentErrors errors) {
        Path dir = folder.resolve(subfolder);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.list(dir)) {
            List<Path> files = new ArrayList<>(
                    walk.filter(p -> p.toString().endsWith(".yml")).toList());
            files.sort(Path::compareTo);
            return files;
        } catch (IOException e) {
            errors.add(SourceRef.ofFile(subfolder + "/"), "",
                    "каталог не читается: " + e.getMessage());
            return List.of();
        } catch (UncheckedIOException e) {
            errors.add(SourceRef.ofFile(subfolder + "/"), "", "каталог не читается");
            return List.of();
        }
    }

    private Optional<String> read(String name, ContentErrors errors) {
        Path file = folder.resolve(name);
        if (!Files.isRegularFile(file)) {
            errors.add(SourceRef.ofFile(name), "", "файл отсутствует");
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            errors.add(SourceRef.ofFile(name), "", "не читается: " + e.getMessage());
            return Optional.empty();
        }
    }
}
