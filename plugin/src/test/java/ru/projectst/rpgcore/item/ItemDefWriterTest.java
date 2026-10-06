package ru.projectst.rpgcore.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.stat.StatOp;

/**
 * Писатель файла предмета против своего же загрузчика.
 *
 * <p>Это и есть плата по счёту за верстак: формат пишется в одном месте, читается
 * в другом, и разойтись они могут молча — новый ключ писатель не узнает,
 * переименованный потеряет. Поэтому здесь не «вывод похож на ожидаемый текст», а
 * круг: объявление → файл → объявление, и сверка с исходным. Сравнение с
 * заранее записанной строкой проверяло бы форматирование, а не смысл.
 */
class ItemDefWriterTest {

    /** Читает текст тем же загрузчиком, что и сервер; падает на любой ошибке. */
    private static ItemDef reread(String text) {
        ContentErrors errors = new ContentErrors();
        ItemDef def = ItemDefLoader.load("из_верстака.yml", text, errors)
                .orElseThrow(() -> new AssertionError("файл не прочитался: " + errors.all()));
        assertTrue(errors.isEmpty(), () -> "загрузчик нашёл ошибки: " + errors.all());
        return def;
    }

    @Test
    @DisplayName("предмет со всем, что бывает, проходит круг без потерь")
    void fullRoundTrip() {
        ItemDef original = new ItemDef("test_sword", "Меч: проверка #1", "DIAMOND_SWORD", 4001,
                List.of("Первая строка", "Вторая: с двоеточием"),
                "common", ItemSlot.HAND,
                Map.of("physical_damage", new ItemDef.ItemStatLine(StatOp.FLAT, 12),
                        "max_mana", new ItemDef.ItemStatLine(StatOp.PERCENT, 10),
                        "magic_damage", new ItemDef.ItemStatLine(StatOp.MULT, 1.5)),
                new ItemRequirement("mage", 7),
                List.of(new ItemAbility("staff_bolt", ItemTrigger.SNEAK_RIGHT_CLICK)),
                true);

        ItemDef back = reread(ItemDefWriter.write(original));

        assertEquals(original.id(), back.id());
        assertEquals(original.display(), back.display(), "двоеточие и решётка в имени уцелели");
        assertEquals(original.material(), back.material());
        assertEquals(original.modelData(), back.modelData());
        assertEquals(original.lore(), back.lore());
        assertEquals(original.rarityId(), back.rarityId());
        assertEquals(original.slot(), back.slot());
        assertEquals(original.stats(), back.stats(), "способ применения и величины те же");
        assertEquals(original.requirement(), back.requirement());
        assertEquals(original.abilities(), back.abilities(), "умение не потерялось");
        assertEquals(original.unbreakable(), back.unbreakable());
    }

    @Test
    @DisplayName("пустой предмет не обрастает разделами из ничего")
    void minimalStaysMinimal() {
        ItemDef original = new ItemDef("test_stone", "Камень", "STONE", 0, List.of(),
                "common", ItemSlot.ANY, Map.of(), ItemRequirement.NONE, List.of(), false);

        String text = ItemDefWriter.write(original);
        ItemDef back = reread(text);

        assertEquals(original, back);
        assertFalse(text.contains("lore:"), "пустого описания в файле быть не должно");
        assertFalse(text.contains("requires:"), "пустых требований тоже");
        assertFalse(text.contains("stats:"));
        assertFalse(text.contains("model-data"), "ноль модели не пишется: это не значение");
    }

    @Test
    @DisplayName("кавычки и обратный слэш в имени не ломают файл")
    void quotesSurvive() {
        ItemDef original = new ItemDef("test_quotes", "Меч \"Тень\" \\ пробы", "STICK", 0,
                List.of("строка с \"кавычками\""), "common", ItemSlot.HAND, Map.of(),
                ItemRequirement.NONE, List.of(), false);

        ItemDef back = reread(ItemDefWriter.write(original));

        assertEquals(original.display(), back.display());
        assertEquals(original.lore(), back.lore());
    }

    @Test
    @DisplayName("только требуемый уровень, без класса — тоже целый раздел")
    void levelOnlyRequirement() {
        ItemDef original = new ItemDef("test_level", "Проба уровня", "PAPER", 0, List.of(),
                "common", ItemSlot.ANY, Map.of(), new ItemRequirement(null, 15),
                List.of(), false);

        ItemDef back = reread(ItemDefWriter.write(original));

        assertEquals(15, back.requirement().level());
        assertEquals(null, back.requirement().classId());
    }

    @Test
    @DisplayName("черновик превращается в файл, а файл обратно в черновик")
    void draftSurvivesTheCircle() {
        ItemDraft draft = new ItemDraft();
        draft.id("Test_Draft");
        draft.display("Черновик");
        draft.material("iron_sword");
        draft.slot(ItemSlot.HAND);
        draft.addLore("строка");
        draft.addStat("physical_damage", 5);
        draft.addStat("physical_damage", 5);
        draft.requireLevel(3);

        assertTrue(draft.problems().isEmpty(), () -> draft.problems().toString());
        // Регистр приводится черновиком: разных «Id» и «iron_sword» в проекте нет.
        assertEquals("test_draft", draft.id());
        assertEquals("IRON_SWORD", draft.material());

        ItemDef back = reread(ItemDefWriter.write(draft.toDef()));
        ItemDraft again = ItemDraft.of(back);

        assertEquals(draft.id(), again.id());
        assertEquals(draft.stats(), again.stats(), "десять, собранные двумя щелчками");
        assertEquals(10, again.stat("physical_damage").value());
        assertEquals(3, again.requireLevel());
        assertTrue(again.replacing(), "открытый предмет правится, а не создаётся заново");
    }

    @Test
    @DisplayName("ноль убирает надбавку, а не пишет «+0»")
    void zeroRemovesTheStat() {
        ItemDraft draft = new ItemDraft();
        draft.addStat("physical_damage", 3);
        draft.addStat("physical_damage", -3);

        assertTrue(draft.stats().isEmpty(),
                "надбавка в ноль попала бы в описание строкой, которая ничего не делает");
    }

    @Test
    @DisplayName("смена способа применения не оставляет «×12»")
    void multiplierStartsFromOne() {
        ItemDraft draft = new ItemDraft();
        draft.addStat("physical_damage", 12);

        draft.cycleOp("physical_damage");
        assertEquals(StatOp.PERCENT, draft.stat("physical_damage").op());
        assertEquals(12, draft.stat("physical_damage").value(), "проценты от того же числа");

        draft.cycleOp("physical_damage");
        assertEquals(StatOp.MULT, draft.stat("physical_damage").op());
        assertEquals(1.1, draft.stat("physical_damage").value(),
                "×12 — это не смена способа, а другой предмет");
    }

    @Test
    @DisplayName("черновик называет, чего не хватает, а не отказывает молча")
    void problemsAreNamed() {
        ItemDraft empty = new ItemDraft();

        List<String> problems = empty.problems();

        assertEquals(3, problems.size(), () -> problems.toString());
        assertTrue(problems.toString().contains("идентификатор"));
        assertTrue(problems.toString().contains("материал"));
        assertTrue(problems.toString().contains("имя"));
    }

    @Test
    @DisplayName("идентификатор с пробелом назван ошибкой: он же имя файла")
    void idIsChecked() {
        ItemDraft draft = new ItemDraft();
        draft.id("меч проверки");
        draft.display("Меч");
        draft.material("STICK");

        assertFalse(draft.problems().isEmpty(),
                "кириллица и пробел в имени файла — это не имя файла");
    }
}
