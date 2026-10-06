package ru.projectst.rpgcore.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.projectst.rpgcore.item.ItemDraft;
import ru.projectst.rpgcore.item.ItemSlot;

/**
 * Верстак: что попадает на диск и что не попадает.
 *
 * <p>Главное здесь — отказы. Файл предмета пишется в каталог контента, и битый
 * файл отключил бы домен предметов целиком при следующей загрузке — у всех, а не
 * у того, кто ошибся в верстаке. Поэтому проверяется не только «сохраняет», но и
 * что именно верстак отказывается сохранять и какими словами.
 */
class ItemForgeTest {

    private static final UUID PLAYER = UUID.randomUUID();

    private Path folder;
    private ContentService content;
    private ItemForge forge;

    @BeforeEach
    void setUp(@TempDir Path dir) throws IOException {
        folder = dir;
        // Минимальный контент: верстак обязан работать на пустом сервере, где
        // ещё ничего не объявлено, кроме статов.
        Files.writeString(dir.resolve("stats.yml"), """
                stats:
                  physical_damage:
                    display: "Физический урон"
                    base: 0
                    min: -90
                    max: 1000
                  max_mana:
                    display: "Запас маны"
                    base: 100
                    min: 0
                    max: 10000
                """, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("statuses.yml"), """
                statuses:
                  mark:
                    category: debuff
                    duration: 20
                """, StandardCharsets.UTF_8);
        // Пустого раздела баланса загрузчик не принимает, и правильно: пустой
        // раздел чаще значит «не дописал», чем «так и задумано».
        Files.writeString(dir.resolve("balance.yml"), """
                balance:
                  nothing:
                    value: 0
                """, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("rarities.yml"), """
                rarities:
                  rare:
                    display: "Редкий"
                    color: BLUE
                """, StandardCharsets.UTF_8);

        content = new ContentService(dir);
        var loaded = content.reload();
        assertTrue(loaded.isEmpty(), () -> "подготовленный контент должен читаться: "
                + loaded.all());
        // Движок статов — тот же, что в игре: верстак сверяется с ним, а не
        // только с файлом, потому что реестр движок получает при запуске.
        var stats = new ru.projectst.rpgcore.stat.StatService(
                new ru.projectst.rpgcore.stat.StatEngine(content.stats()));
        forge = new ItemForge(content, stats, dir);
    }

    private ItemDraft ready() {
        ItemDraft draft = forge.draft(PLAYER);
        draft.id("test_sword");
        draft.display("Меч проверки");
        draft.material("DIAMOND_SWORD");
        draft.slot(ItemSlot.HAND);
        return draft;
    }

    @Test
    @DisplayName("собранный предмет становится файлом и попадает в реестр")
    void saveWritesFileAndReloads() {
        ItemDraft draft = ready();
        draft.addStat("physical_damage", 12);

        ItemForge.Result result = forge.save(PLAYER);

        assertTrue(result.ok(), () -> result.message() + " " + result.problems());
        assertEquals(folder.resolve("items").resolve("test_sword.yml"), result.file());
        assertTrue(Files.isRegularFile(result.file()), "файл должен лежать на диске");
        assertTrue(result.problems().isEmpty(), () -> result.problems().toString());

        // Перечитан, а не «будет перечитан при перезапуске»: иначе предмет надет,
        // метка на месте, а статов нет.
        var declared = content.items().find("test_sword").orElseThrow();
        assertEquals("Меч проверки", declared.display());
        assertEquals(12, declared.stats().get("physical_damage").value());
    }

    @Test
    @DisplayName("неполный черновик не пишет файла и говорит, чего не хватает")
    void incompleteDraftIsRefused() {
        forge.draft(PLAYER).id("test_sword");

        ItemForge.Result result = forge.save(PLAYER);

        assertFalse(result.ok());
        assertTrue(result.problems().toString().contains("материал"),
                () -> result.problems().toString());
        assertFalse(Files.exists(folder.resolve("items")), "каталог не должен появиться");
    }

    @Test
    @DisplayName("несуществующий стат назван отказом, а не записан в файл")
    void unknownStatIsRefused() {
        ItemDraft draft = ready();
        draft.addStat("physikal_damage", 10);

        ItemForge.Result result = forge.save(PLAYER);

        assertFalse(result.ok(), "иначе предмет падал бы при каждом пересчёте статов носителя");
        assertTrue(result.problems().toString().contains("physikal_damage"),
                () -> result.problems().toString());
        assertFalse(Files.exists(folder.resolve("items").resolve("test_sword.yml")));
    }

    @Test
    @DisplayName("несуществующая редкость — тоже отказ со своим словом")
    void unknownRarityIsRefused() {
        ItemDraft draft = ready();
        draft.rarityId("legendarnyi");

        ItemForge.Result result = forge.save(PLAYER);

        assertFalse(result.ok());
        assertTrue(result.problems().toString().contains("legendarnyi"),
                () -> result.problems().toString());
    }

    @Test
    @DisplayName("занятый идентификатор не затирается молча")
    void existingItemIsNotOverwrittenByAccident() {
        ready().addStat("physical_damage", 12);
        assertTrue(forge.save(PLAYER).ok());

        // Другой игрок берёт то же имя, не открывая предмет: это не правка, это
        // случайное совпадение, и чужой файл от него страдать не должен.
        UUID other = UUID.randomUUID();
        ItemDraft draft = forge.draft(other);
        draft.id("test_sword");
        draft.display("Чужой меч");
        draft.material("STICK");

        ItemForge.Result result = forge.save(other);

        assertFalse(result.ok());
        assertTrue(result.message().contains("уже есть"), result::message);
        assertEquals("Меч проверки", content.items().find("test_sword").orElseThrow().display(),
                "первый предмет остался как был");
    }

    @Test
    @DisplayName("открытый на правку предмет перезаписывается своим же файлом")
    void editingSavesOverTheSameFile() {
        ready().addStat("physical_damage", 12);
        assertTrue(forge.save(PLAYER).ok());

        // После сохранения черновик остаётся открытым как правка — можно править
        // дальше, не собирая заново.
        ItemDraft draft = forge.draft(PLAYER);
        assertTrue(draft.replacing());
        draft.display("Меч проверки, второй");
        draft.addStat("physical_damage", 8);

        ItemForge.Result result = forge.save(PLAYER);

        assertTrue(result.ok(), () -> result.message() + " " + result.problems());
        var declared = content.items().find("test_sword").orElseThrow();
        assertEquals("Меч проверки, второй", declared.display());
        assertEquals(20, declared.stats().get("physical_damage").value());
    }

    @Test
    @DisplayName("правка объявленного предмета открывается из реестра")
    void editOpensDeclaredItem() {
        ready().addStat("physical_damage", 12);
        assertTrue(forge.save(PLAYER).ok());

        UUID other = UUID.randomUUID();
        forge.edit(other, forge.declared("test_sword").orElseThrow());

        ItemDraft draft = forge.draft(other);
        assertEquals("DIAMOND_SWORD", draft.material());
        assertEquals(12, draft.stat("physical_damage").value());
        assertTrue(draft.replacing());
    }

    @Test
    @DisplayName("черновик уходит с игроком, файл остаётся")
    void draftsAreForgotten() {
        ready();
        forge.forget(PLAYER);

        assertTrue(forge.draft(PLAYER).id().isEmpty(), "новый черновик пуст");
    }
}
