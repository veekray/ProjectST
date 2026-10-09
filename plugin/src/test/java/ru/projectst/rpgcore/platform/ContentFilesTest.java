package ru.projectst.rpgcore.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Файлы контента и версия плагина.
 *
 * <p>Главное — что правка администратора не теряется никогда, а свой нетронутый
 * файл плагин обновляет сам. Иначе новый плагин работал бы со старыми навыками,
 * и это выглядело бы как ошибка в коде: кольцо на пяти блоках при уроне на семи
 * с половиной.
 */
class ContentFilesTest {

    @TempDir
    Path folder;

    private static byte[] text(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String read(String name) throws IOException {
        return Files.readString(folder.resolve(name), StandardCharsets.UTF_8);
    }

    private void put(String name, String value) throws IOException {
        Files.createDirectories(folder.resolve(name).getParent());
        Files.writeString(folder.resolve(name), value, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("недостающий файл кладётся, совпадающий не трогается")
    void addsMissing() throws IOException {
        put("skills/a.yml", "v1");
        ContentFiles files = new ContentFiles(folder,
                Map.of("skills/a.yml", text("v1"), "skills/b.yml", text("v1")));

        ContentFiles.Report report = files.sync();

        assertEquals(List.of("skills/b.yml"), report.added());
        assertTrue(report.updated().isEmpty());
        assertTrue(report.kept().isEmpty());
        assertEquals("v1", read("skills/b.yml"));
    }

    @Test
    @DisplayName("свой нетронутый файл обновляется с новой версией плагина")
    void updatesUntouched() throws IOException {
        new ContentFiles(folder, Map.of("skills/a.yml", text("v1"))).sync();

        ContentFiles.Report report =
                new ContentFiles(folder, Map.of("skills/a.yml", text("v2"))).sync();

        assertEquals(List.of("skills/a.yml"), report.updated());
        assertEquals("v2", read("skills/a.yml"));
    }

    @Test
    @DisplayName("файл, правленный руками, остаётся и называется")
    void keepsEdited() throws IOException {
        new ContentFiles(folder, Map.of("skills/a.yml", text("v1"))).sync();
        put("skills/a.yml", "моя правка");

        ContentFiles.Report report =
                new ContentFiles(folder, Map.of("skills/a.yml", text("v2"))).sync();

        assertEquals(List.of("skills/a.yml"), report.kept());
        assertEquals("моя правка", read("skills/a.yml"), "правку администратора не теряем");
    }

    @Test
    @DisplayName("файл, о котором плагин ничего не помнит, тоже не трогается")
    void keepsUnknown() throws IOException {
        // Так лежат файлы с установки до учёта версий: старая версия или правка —
        // не отличить, и молча затирать нельзя.
        put("skills/a.yml", "старое");

        ContentFiles.Report report =
                new ContentFiles(folder, Map.of("skills/a.yml", text("v2"))).sync();

        assertEquals(List.of("skills/a.yml"), report.kept());
        assertEquals("старое", read("skills/a.yml"));
    }

    @Test
    @DisplayName("update меняет всё на версию плагина, прежнее — в копию, и дальше файл свой")
    void forceUpdateBacksUp() throws IOException {
        put("skills/a.yml", "старое");
        ContentFiles files = new ContentFiles(folder, Map.of("skills/a.yml", text("v2")));
        Path backup = folder.resolve("backup/now");

        ContentFiles.Report report = files.forceUpdate(backup);

        assertEquals(List.of("skills/a.yml"), report.updated());
        assertEquals("v2", read("skills/a.yml"));
        assertEquals("старое", Files.readString(backup.resolve("skills/a.yml")));
        assertTrue(files.outdated().isEmpty());
        // После update файл считается своим: следующая версия ляжет сама.
        ContentFiles.Report next =
                new ContentFiles(folder, Map.of("skills/a.yml", text("v3"))).sync();
        assertEquals(List.of("skills/a.yml"), next.updated());
    }

    @Test
    @DisplayName("контент — каталоги навыков, классов, предметов, рецептов, мобов и корневые yml")
    void contentNames() {
        assertTrue(ContentFiles.isContent("skills/berserker_quake.yml"));
        assertTrue(ContentFiles.isContent("classes/berserker.yml"));
        assertTrue(ContentFiles.isContent("balance.yml"));
        assertFalse(ContentFiles.isContent("plugin.yml"));
        assertFalse(ContentFiles.isContent("config.yml"));
        assertFalse(ContentFiles.isContent("skills/old/x.yml"));
        assertFalse(ContentFiles.isContent("assets/x.yml"));
    }
}
