package ru.projectst.rpgcore.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ru.projectst.rpgcore.stat.StatOp;

/**
 * Предмет, который сейчас собирают в верстаке.
 *
 * <p>Отдельно от {@link ItemDef} намеренно. Объявление — то, что уже прошло
 * загрузчик: ItemDef без материала не существует, и все, кто с ним работают, на
 * это полагаются. Черновик наоборот заведомо неполон, пока его собирают: без
 * идентификатора, без материала, с нулевыми статами. Правь редактор ItemDef
 * напрямую — и «предмет без материала» пришлось бы разрешить везде.
 *
 * <p>Поэтому превращение в объявление — отдельный шаг, который может и не
 * получиться. Что именно не так, {@link #problems()} говорит словами, а не
 * исключением: игрок в верстаке должен видеть, чего не хватает, а не ловить
 * отказ при сохранении.
 *
 * <p>Умения черновик не правит, но и не теряет: предмет с умением, открытый для
 * правки, сохранится вместе с ним. Молча терять то, чего не умеешь показать, —
 * худший вид редактора.
 */
public final class ItemDraft {

    /** Сколько строк описания имеет смысл держать: дальше оно не читается. */
    public static final int MAX_LORE = 10;

    private String id = "";
    private String display = "";
    private String material = "";
    private String rarityId = Rarity.COMMON.id();
    private ItemSlot slot = ItemSlot.HAND;
    private boolean unbreakable;
    private int modelData;
    private final List<String> lore = new ArrayList<>();
    private String requireClass;
    private int requireLevel;
    private final Map<String, ItemDef.ItemStatLine> stats = new LinkedHashMap<>();
    private final List<ItemAbility> abilities = new ArrayList<>();

    /**
     * Правка уже объявленного предмета, а не создание нового.
     *
     * <p>Нужно одному: разрешить перезапись файла. Без этого признака верстак не
     * отличал бы «я открыл этот предмет и меняю его» от «я случайно взял занятый
     * идентификатор», и второй случай молча затирал бы поставляемый контент.
     */
    private boolean replacing;

    /** Пустой черновик: именно пустой, а не «почти готовый предмет». */
    public ItemDraft() {
    }

    /** Черновик из объявления: открыть существующий предмет на правку. */
    public static ItemDraft of(ItemDef def) {
        ItemDraft draft = new ItemDraft();
        draft.id = def.id();
        draft.display = def.display();
        draft.material = def.material();
        draft.rarityId = def.rarityId();
        draft.slot = def.slot();
        draft.unbreakable = def.unbreakable();
        draft.modelData = def.modelData();
        draft.lore.addAll(def.lore());
        draft.requireClass = def.requirement().classId();
        draft.requireLevel = def.requirement().level();
        draft.stats.putAll(def.stats());
        draft.abilities.addAll(def.abilities());
        draft.replacing = true;
        return draft;
    }

    // ------------------------------------------------------------------ чтение

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public String material() {
        return material;
    }

    public String rarityId() {
        return rarityId;
    }

    public ItemSlot slot() {
        return slot;
    }

    public boolean unbreakable() {
        return unbreakable;
    }

    public int modelData() {
        return modelData;
    }

    public List<String> lore() {
        return List.copyOf(lore);
    }

    public String requireClass() {
        return requireClass;
    }

    public int requireLevel() {
        return requireLevel;
    }

    public Map<String, ItemDef.ItemStatLine> stats() {
        return Map.copyOf(stats);
    }

    public List<ItemAbility> abilities() {
        return List.copyOf(abilities);
    }

    public boolean replacing() {
        return replacing;
    }

    /** Надбавка к стату или {@code null}, если её нет. */
    public ItemDef.ItemStatLine stat(String statId) {
        return stats.get(statId);
    }

    // ------------------------------------------------------------------ правка

    /** Идентификатор приводится к нижнему регистру: разных «Id» в проекте нет. */
    public void id(String value) {
        this.id = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public void display(String value) {
        this.display = value == null ? "" : value.trim();
    }

    /** Материал приводится к верхнему регистру: имена ванильных материалов такие. */
    public void material(String value) {
        this.material = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public void rarityId(String value) {
        this.rarityId = value == null || value.isBlank()
                ? Rarity.COMMON.id() : value.trim().toLowerCase(Locale.ROOT);
    }

    public void slot(ItemSlot value) {
        this.slot = value == null ? ItemSlot.HAND : value;
    }

    public void unbreakable(boolean value) {
        this.unbreakable = value;
    }

    public void modelData(int value) {
        this.modelData = Math.max(0, value);
    }

    public void addLore(String line) {
        if (line != null && !line.isBlank() && lore.size() < MAX_LORE) {
            lore.add(line.trim());
        }
    }

    public void removeLastLore() {
        if (!lore.isEmpty()) {
            lore.remove(lore.size() - 1);
        }
    }

    public void requireClass(String classId) {
        this.requireClass = classId == null || classId.isBlank() ? null : classId;
    }

    public void requireLevel(int value) {
        this.requireLevel = Math.max(0, value);
    }

    /**
     * Меняет надбавку на величину шага.
     *
     * <p>Ноль означает «надбавки нет» и убирает её целиком: надбавка в ноль
     * попала бы в файл и в описание предмета строкой «+0», которая не делает
     * ничего и выглядит поломкой.
     */
    public void addStat(String statId, double delta) {
        ItemDef.ItemStatLine line = stats.get(statId);
        StatOp op = line == null ? StatOp.FLAT : line.op();
        double value = round((line == null ? 0 : line.value()) + delta);
        if (value == 0) {
            stats.remove(statId);
            return;
        }
        stats.put(statId, new ItemDef.ItemStatLine(op, value));
    }

    /**
     * Следующий способ применения: плоско → проценты → множитель.
     *
     * <p>У множителя значение начинается с единицы, а не с прежнего числа:
     * «×12» вместо «+12» — это не смена способа, а другой предмет.
     */
    public void cycleOp(String statId) {
        ItemDef.ItemStatLine line = stats.get(statId);
        if (line == null) {
            return;
        }
        StatOp next = switch (line.op()) {
            case FLAT -> StatOp.PERCENT;
            case PERCENT -> StatOp.MULT;
            case MULT -> StatOp.FLAT;
        };
        // Переход в множитель и из него сбрасывает величину: «×12» вместо «+12»
        // — это не смена способа применения, а совсем другой предмет.
        double value = switch (next) {
            case MULT -> 1.1;
            case FLAT -> line.op() == StatOp.MULT ? 1 : line.value();
            case PERCENT -> line.value();
        };
        stats.put(statId, new ItemDef.ItemStatLine(next, round(value)));
    }

    public void clearStat(String statId) {
        stats.remove(statId);
    }

    // ------------------------------------------------------------------ итог

    /**
     * Чего не хватает, чтобы черновик стал предметом.
     *
     * <p>Пустой список — можно сохранять. Здесь только то, что видно по самому
     * черновику; существование стата, редкости и класса проверяет связывание,
     * потому что для этого нужен загруженный контент.
     */
    public List<String> problems() {
        List<String> out = new ArrayList<>();
        if (id.isBlank()) {
            out.add("не задан идентификатор");
        } else if (!id.matches("[a-z0-9_]+")) {
            out.add("в идентификаторе допустимы только латиница в нижнем регистре,"
                    + " цифры и подчёркивание");
        }
        if (material.isBlank()) {
            out.add("не задан материал");
        }
        if (display.isBlank()) {
            out.add("не задано имя");
        }
        return out;
    }

    /**
     * Черновик как объявление.
     *
     * <p>Вызывать после {@link #problems()}: на неполном черновике бросит
     * исключение, потому что именно этого ItemDef и не допускает.
     */
    public ItemDef toDef() {
        return new ItemDef(id, display, material, modelData, List.copyOf(lore), rarityId, slot,
                new LinkedHashMap<>(stats), new ItemRequirement(requireClass, requireLevel),
                List.copyOf(abilities), unbreakable);
    }

    /** Десятая доля — предел точности редактора: мельче в бою не видно. */
    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
