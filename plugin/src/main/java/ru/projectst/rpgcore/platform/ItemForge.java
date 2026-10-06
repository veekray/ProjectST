package ru.projectst.rpgcore.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import ru.projectst.rpgcore.item.ItemDef;
import ru.projectst.rpgcore.item.ItemDefLoader;
import ru.projectst.rpgcore.item.ItemDefWriter;
import ru.projectst.rpgcore.item.ItemDraft;
import ru.projectst.rpgcore.item.ItemLinker;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.stat.StatService;

/**
 * Верстак предметов: собранный в игре предмет становится файлом контента.
 *
 * <p><b>Почему через файл, а не мимо.</b> Статы предмета читаются по его
 * объявлению, найденному в реестре по метке в самом предмете. Предмет, который
 * существует только в памяти верстака, пришлось бы искать как-то иначе — то есть
 * завести второй путь к статам снаряжения. Второй путь к одному и тому же
 * однажды расходится с первым, и расхождение выглядит как «предмет надет, а
 * цифры не изменились». Поэтому верстак пишет обычный файл и перечитывает
 * контент: дальше предмет ничем не отличается от написанного руками.
 *
 * <p><b>На диск попадает только то, что читается.</b> Порядок жёсткий: черновик
 * проверяется сам, превращается в объявление, печатается в текст, <b>читается
 * обратно своим же загрузчиком</b> и связывается с контентом. Любая осечка —
 * отказ со строками загрузчика, и файл не создаётся. Иначе опечатка в верстаке
 * роняла бы весь домен предметов при следующей загрузке, причём у всех.
 *
 * <p>Черновики живут в памяти и теряются при перезапуске. Это сознательно:
 * недоделанный предмет — это заметка, а не контент. Сохранённый предмет лежит
 * файлом и переживает всё.
 */
public final class ItemForge {

    /**
     * Чем закончилась попытка сохранить.
     *
     * @param ok       записан ли файл
     * @param message  короткий итог для игрока
     * @param problems что именно не так; при {@code ok} — ошибки перечитанного
     *                 контента, если они там есть
     * @param file     куда записано; {@code null}, если не записано
     */
    public record Result(boolean ok, String message, List<String> problems, Path file) {

        public Result {
            problems = problems == null ? List.of() : List.copyOf(problems);
        }

        static Result refused(String message, List<String> problems) {
            return new Result(false, message, problems, null);
        }
    }

    private final ContentService content;
    private final StatService stats;
    private final Path dataFolder;
    private final Map<UUID, ItemDraft> drafts = new HashMap<>();

    public ItemForge(ContentService content, StatService stats, Path dataFolder) {
        this.content = content;
        this.stats = stats;
        this.dataFolder = dataFolder;
    }

    /** Черновик игрока; у кого его нет — пустой, а не «последний сохранённый». */
    public ItemDraft draft(UUID player) {
        return drafts.computeIfAbsent(player, key -> new ItemDraft());
    }

    /** Открыть объявленный предмет на правку. */
    public void edit(UUID player, ItemDef def) {
        drafts.put(player, ItemDraft.of(def));
    }

    public void reset(UUID player) {
        drafts.put(player, new ItemDraft());
    }

    public void forget(UUID player) {
        drafts.remove(player);
    }

    /** Объявление предмета по идентификатору: нужно верстаку, чтобы открыть его. */
    public Optional<ItemDef> declared(String id) {
        return content.items().find(id);
    }

    /**
     * Проверяет черновик, пишет файл и перечитывает контент.
     *
     * <p>Перечитывается всё, а не один предмет: реестры заменяются целиком, и
     * подменить в них одну запись нельзя — они на то и неизменяемые.
     */
    public Result save(UUID player) {
        ItemDraft draft = draft(player);

        List<String> problems = new ArrayList<>(draft.problems());
        if (!problems.isEmpty()) {
            return Result.refused("Черновик не готов", problems);
        }

        // Занятый идентификатор — не повод затирать чужой файл молча. Правка
        // разрешена только тому черновику, который из этого предмета и открыт.
        if (!draft.replacing() && content.items().has(draft.id())) {
            return Result.refused("Предмет с таким идентификатором уже есть",
                    List.of("откройте " + draft.id() + " в верстаке, если хотите его править",
                            "или задайте другой идентификатор"));
        }

        ItemDef def = draft.toDef();
        String text = ItemDefWriter.write(def);
        String file = def.id() + ".yml";

        // Чтение своего же вывода. Это не перестраховка: писатель и загрузчик —
        // два метода одного формата, и разойтись они могут. Пусть лучше верстак
        // откажет сейчас, чем предметы не загрузятся при следующем старте.
        ContentErrors errors = new ContentErrors();
        Optional<ItemDef> parsed = ItemDefLoader.load(file, text, errors);
        if (parsed.isEmpty() || !errors.isEmpty()) {
            return Result.refused("Собранный файл не читается своим же загрузчиком",
                    lines(errors));
        }

        // Связывание: статы, редкость, класс требования и навыки умений. Без
        // этого в предмет попал бы стат, которого нет, — и не сработал бы молча.
        ItemLinker.link(List.of(parsed.get()), content.items(), content.stats(),
                content.skills(), content.playerClasses().ids(), errors);
        if (!errors.isEmpty()) {
            return Result.refused("Предмет ссылается на то, чего нет", lines(errors));
        }

        // Отдельно — сверка с живым движком статов. Он получил реестр при
        // запуске, и перечитанный stats.yml его не меняет: стат, добавленный в
        // файл на ходу, прошёл бы связывание и всё равно не применился бы.
        // Молча. Поэтому лучше отказать и назвать причину.
        List<String> unknown = new ArrayList<>();
        for (String statId : parsed.get().stats().keySet()) {
            if (!stats.knows(statId)) {
                unknown.add(statId + " — объявлен в stats.yml, но движок получил реестр"
                        + " при запуске и этого стата не знает; перезапустите сервер");
            }
        }
        if (!unknown.isEmpty()) {
            return Result.refused("Движок не знает этих статов", unknown);
        }

        Path target = dataFolder.resolve("items").resolve(file);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Result.refused("Файл не записался: " + e.getMessage(), List.of());
        }

        ContentErrors reloaded = content.reload();
        // Черновик остаётся открытым и помечается правкой: сохранил — и правь
        // дальше тот же предмет, не собирая его заново.
        drafts.put(player, ItemDraft.of(parsed.get()));
        return new Result(true, reloaded.isEmpty()
                ? "Предмет сохранён, контент перечитан"
                : "Предмет сохранён, но в контенте есть ошибки",
                lines(reloaded), target);
    }

    private static List<String> lines(ContentErrors errors) {
        List<String> out = new ArrayList<>();
        errors.all().forEach(error -> out.add(error.toString()));
        return out;
    }
}
