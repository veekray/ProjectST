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
    private static final int EXPECTED_SELECTABLE = 24;

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
                           BalanceBook balance, StatusRegistry statuses, StatRegistry stats) {
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

        assertTrue(errors.isEmpty(), () -> "контент не читается:\n" + join(errors));
        return new Content(skills, classes, balance, statuses, stats);
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
    @DisplayName("перенесены все двадцать четыре активных навыка, по шесть на класс")
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
        assertEquals(Map.of("mage", 6, "druid", 6, "warlock", 6, "rogue", 6), perClass);
    }

    @Test
    @DisplayName("у каждого класса есть файл, и каждый навык принадлежит существующему классу")
    void everySkillHasItsClass() throws IOException {
        Content content = load();
        Set<String> classIds = new LinkedHashSet<>();
        content.classes().forEach(def -> classIds.add(def.id()));

        for (SkillDef skill : content.skills()) {
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
}
