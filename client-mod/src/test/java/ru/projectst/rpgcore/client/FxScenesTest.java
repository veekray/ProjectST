package ru.projectst.rpgcore.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Каталог сцен собирается: повтор ключа в одном {@code Map.ofEntries} уронил
 * бы инициализацию каталога, а с ней — все эффекты мода сразу.
 *
 * <p>Проверяется по исходникам: загрузить каталог без Minecraft нельзя, а
 * повтор ключа — это опечатка, видная в тексте.
 */
class FxScenesTest {

    private static final Path SOURCES = Path.of("src", "main", "java", "ru", "projectst", "rpgcore",
            "client");
    private static final Pattern MAP = Pattern.compile("Map\\.ofEntries\\((.*?)\\);", Pattern.DOTALL);
    private static final Pattern KEY = Pattern.compile("Map\\.entry\\(\"([^\"]+)\"");

    @Test
    @DisplayName("ни одна сцена и ни один облик статуса не объявлены дважды")
    void noSceneDeclaredTwice() throws IOException {
        Set<String> problems = new TreeSet<>();
        Map<String, String> owner = new HashMap<>();
        try (var files = Files.list(SOURCES)) {
            for (Path file : files.filter(f -> f.getFileName().toString().startsWith("Scenes"))
                    .sorted().toList()) {
                String name = file.getFileName().toString();
                Matcher maps = MAP.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (maps.find()) {
                    Set<String> seen = new HashSet<>();
                    Matcher keys = KEY.matcher(maps.group(1));
                    while (keys.find()) {
                        String key = keys.group(1);
                        if (!seen.add(key)) {
                            problems.add(name + ": " + key + " дважды в одной карте");
                        }
                        String was = owner.putIfAbsent(key, name);
                        if (was != null && !was.equals(name)) {
                            problems.add(key + ": и в " + was + ", и в " + name);
                        }
                    }
                }
            }
        }
        assertTrue(owner.size() > 200, "сцен подозрительно мало: " + owner.size());
        assertTrue(problems.isEmpty(), "повторы: " + problems);
    }
}
