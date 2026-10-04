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

    /** Перечитывает контент и применяет его. */
    public ContentErrors reload() {
        ContentErrors errors = new ContentErrors();
        Loaded loaded = loadAll(errors);
        stats = loaded.stats();
        statuses = loaded.statuses();
        balance = loaded.balance();
        skills = loaded.skills();
        playerClasses = loaded.playerClasses();
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
                          ClassRegistry playerClasses) {
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

        // Связывание последним: до него нет ни баланса, ни статусов, ни
        // классов для сверки.
        SkillLinker.link(skillMap.values(), loadedBalance, loadedStatuses,
                classMap.keySet(), errors);

        return new Loaded(loadedStats, loadedStatuses, loadedBalance,
                new SkillRegistry(skillMap), new ClassRegistry(classMap));
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
