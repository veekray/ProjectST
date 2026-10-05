package ru.projectst.rpgcore.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Караулит две вещи из SPEC, которые иначе разъезжаются молча.
 *
 * <p>Первая: Bukkit живёт только в {@code platform/} и в точке входа. Как только
 * он протечёт в {@code stat} или {@code damage}, эти модули перестанут
 * проверяться юнит-тестами, и проверить порядок применения надбавок станет
 * можно только на живом сервере.
 *
 * <p>Вторая: направление зависимостей по карте модулей. Обратная стрелка
 * означает цикл, а цикл означает, что модуль нельзя собрать и протестировать
 * отдельно.
 */
class DependencyDirectionTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    /** Кому какие пакеты проекта разрешено импортировать. */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "loader", Set.of(),
            "stat", Set.of("loader"),
            "damage", Set.of("loader", "stat"),
            "status", Set.of("loader", "stat", "damage"),
            "data", Set.of("loader", "stat"),
            "balance", Set.of("loader"),
            "classes", Set.of("loader", "stat", "balance", "skill", "data"),
            "skill", Set.of("loader", "stat", "damage", "status", "balance"),
            "cast", Set.of("loader", "stat", "damage", "status", "balance", "skill", "classes"),
            "platform", Set.of("loader", "stat", "damage", "status", "data", "balance", "skill",
                    "classes", "cast")
    );

    @Test
    @DisplayName("Bukkit не протекает за пределы platform/")
    void bukkitStaysInPlatform() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaFiles()) {
            String pkg = moduleOf(file);
            if (pkg.equals("platform") || pkg.isEmpty()) {
                continue; // platform/ и точка входа — единственные, кому можно
            }
            for (String line : Files.readAllLines(file)) {
                String t = line.strip();
                if (t.startsWith("import org.bukkit") || t.startsWith("import io.papermc")) {
                    violations.add(file + " → " + t);
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Bukkit за пределами platform/:\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("зависимости модулей идут только к ядру")
    void moduleDependenciesPointInward() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaFiles()) {
            String from = moduleOf(file);
            Set<String> allowed = ALLOWED.get(from);
            if (allowed == null) {
                continue; // модуль ещё не на карте (этапы 2-5) либо точка входа
            }
            for (String line : Files.readAllLines(file)) {
                String t = line.strip();
                if (!t.startsWith("import ru.projectst.rpgcore.")) {
                    continue;
                }
                String rest = t.substring("import ru.projectst.rpgcore.".length());
                int dot = rest.indexOf('.');
                if (dot < 0) {
                    continue;
                }
                String to = rest.substring(0, dot);
                if (!to.equals(from) && !allowed.contains(to)) {
                    violations.add(from + " не может зависеть от " + to + " (" + file + ")");
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "нарушено направление зависимостей:\n" + String.join("\n", violations));
    }

    private static List<Path> javaFiles() throws IOException {
        if (!Files.isDirectory(SOURCE_ROOT)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(SOURCE_ROOT)) {
            return walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    /** Имя модуля по пути файла: .../rpgcore/<модуль>/Файл.java, иначе пустая строка. */
    /**
     * Модуль файла — первый пакет после {@code rpgcore}.
     *
     * <p>Поиск идёт вверх по дереву, а не на один уровень: вложенный пакет вроде
     * {@code platform/gui} иначе не относился бы ни к какому модулю, то есть
     * выпадал бы из обеих проверок. Дыра в правиле, которое караулит дыры, —
     * последнее, что здесь нужно.
     */
    private static String moduleOf(Path file) {
        Path dir = file.getParent();
        String module = "";
        while (dir != null && dir.getFileName() != null) {
            Path parent = dir.getParent();
            if (parent != null && parent.getFileName() != null
                    && parent.getFileName().toString().equals("rpgcore")) {
                return dir.getFileName().toString();
            }
            module = dir.getFileName().toString();
            dir = parent;
        }
        return module.equals("rpgcore") ? "" : "";
    }
}
