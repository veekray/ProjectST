package ru.projectst.rpgcore.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Копия протокола в клиентском моде не должна расходиться с плагином.
 *
 * <p>Дублирование здесь сознательное: общий модуль заставил бы мод зависеть от
 * сборки плагина, а плагин — тащить сборку мода с его выкачиванием NeoForge из
 * сети. Цена такого решения одна — копия может отстать, и тогда сервер и мод
 * читают разный формат. Этот тест и есть плата по этому счёту: он падает ровно в
 * тот момент, когда копию забыли обновить, а не через неделю на сервере.
 *
 * <p>Если мода нет рядом (собирают только плагин), тест молчит: требовать
 * наличие чужого каталога он не вправе.
 */
class ProtocolCopyTest {

    /** Тесты идут из каталога plugin, мод лежит рядом. */
    private static final Path MOD_NET = Path.of("..", "client-mod", "src", "main", "java",
            "ru", "projectst", "rpgcore", "net");

    private static final Path PLUGIN_NET = Path.of("src", "main", "java",
            "ru", "projectst", "rpgcore", "net");

    private static final List<String> COPIED =
            List.of("Protocol.java", "ClientState.java", "StateCodec.java",
                    "MenuData.java");

    @Test
    @DisplayName("файлы протокола в моде совпадают с плагином до строки")
    void copiesMatch() throws IOException {
        if (!Files.isDirectory(MOD_NET)) {
            return; // мода рядом нет — проверять нечего
        }
        for (String name : COPIED) {
            Path mine = PLUGIN_NET.resolve(name);
            Path theirs = MOD_NET.resolve(name);
            assertTrue(Files.isRegularFile(theirs),
                    "в моде нет копии " + name + ": формат разойдётся молча");

            String source = Files.readString(mine, StandardCharsets.UTF_8);
            String copy = stripHeader(Files.readString(theirs, StandardCharsets.UTF_8));

            assertEquals(normalise(source), normalise(copy),
                    "копия " + name + " в client-mod отстала от плагина:"
                            + " скопируйте файл заново, иначе сервер и мод будут читать"
                            + " разный формат");
        }
    }

    @Test
    @DisplayName("версия протокола в копии та же: иначе рукопожатие откажет всем")
    void versionsMatch() throws IOException {
        if (!Files.isDirectory(MOD_NET)) {
            return;
        }
        String copy = Files.readString(MOD_NET.resolve("Protocol.java"),
                StandardCharsets.UTF_8);

        assertTrue(copy.contains("VERSION = " + Protocol.VERSION + ";"),
                "в копии мода другая версия протокола: сервер ответит отказом каждому игроку");
    }

    /** Убирает шапку «это копия», которой в исходнике нет. */
    private static String stripHeader(String text) {
        StringBuilder out = new StringBuilder();
        boolean started = false;
        for (String line : text.lines().toList()) {
            if (!started && line.startsWith("//")) {
                continue;
            }
            started = true;
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /** Перевод строк и завершающие пробелы к одному виду: различия не в них. */
    private static String normalise(String text) {
        StringBuilder out = new StringBuilder();
        for (String line : text.lines().toList()) {
            out.append(line.stripTrailing()).append('\n');
        }
        return out.toString().strip();
    }
}
