package ru.projectst.rpgcore.platform;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.inventory.ItemStack;
import ru.projectst.rpgcore.data.PlayerDataStore;
import ru.projectst.rpgcore.item.ItemDef;
import ru.projectst.rpgcore.item.ItemSlot;

/**
 * Слоты артефактов: ячейки вне инвентаря, в которых предмет считается надетым.
 *
 * <p>Нужны тому, что надевать некуда. Кольцо, печать, реликвия в ванильной броне
 * не живут, а статы давать должны — и держать их «просто в инвентаре» нельзя:
 * тогда любой предмет в рюкзаке начал бы считаться, и понять, откуда у игрока
 * числа, стало бы невозможно.
 *
 * <p><b>Предмет лежит в данных игрока, а не в инвентаре.</b> Поэтому он не
 * выпадает со смертью, не теряется при переполнении рюкзака и не занимает места.
 * Цена — его нужно явно вернуть, чтобы потрогать, и ровно для этого есть окно.
 *
 * <p>Записан предмет целиком, со всем, что на нём есть: прочностью,
 * зачарованиями, нашей меткой. Хранить один идентификатор было бы короче, но
 * тогда вынутый артефакт возвращался бы игроку не тем, что он положил.
 *
 * <p>Число слотов — настройка сервера. Если его уменьшили, артефакты из лишних
 * слотов статов не дают и ждут возврата владельцу: молча пропасть они не могут.
 */
public final class ArtifactSlots {

    /** Больше одного ряда ячеек в окне не поместится, и больше не нужно. */
    public static final int MAX_SLOTS = 7;

    private final PlayerDataStore data;
    private final RpgItems items;
    private final int slots;
    private final Consumer<String> log;

    /**
     * @param slots сколько ячеек у игрока; значение из config.yml
     * @param log   куда жаловаться на нечитаемую запись: в фоне бросать некуда,
     *              а молчать про потерянный артефакт нельзя
     */
    public ArtifactSlots(PlayerDataStore data, RpgItems items, int slots, Consumer<String> log) {
        this.data = data;
        this.items = items;
        this.slots = Math.clamp(slots, 0, MAX_SLOTS);
        this.log = log;
    }

    /** Сколько ячеек у игрока. Ноль означает «артефактов на сервере нет». */
    public int slotCount() {
        return slots;
    }

    /** Предмет в слоте, если он там есть и читается. */
    public Optional<ItemStack> get(UUID player, int slot) {
        return decode(player, data.load(player).artifact(slot));
    }

    /** Все артефакты по слотам: только те, что в пределах числа ячеек. */
    public Map<Integer, ItemStack> all(UUID player) {
        Map<Integer, ItemStack> out = new LinkedHashMap<>();
        for (int slot = 1; slot <= slots; slot++) {
            Optional<ItemStack> stack = get(player, slot);
            if (stack.isPresent()) {
                out.put(slot, stack.get());
            }
        }
        return out;
    }

    /**
     * Кладёт предмет в слот; {@code null} очищает.
     *
     * <p>Проверки «можно ли» здесь нет намеренно: решает вызывающий, и он же
     * объясняет игроку отказ словами. Этот метод — про хранение.
     */
    public void set(UUID player, int slot, ItemStack stack) {
        var own = data.load(player);
        own.setArtifact(slot, stack == null || stack.getType().isAir()
                ? null
                : Base64.getEncoder().encodeToString(stack.serializeAsBytes()));
        data.saveLater(own);
    }

    /** Первый свободный слот; ноль — все заняты. */
    public int firstFree(UUID player) {
        return data.load(player).firstFreeArtifactSlot(slots);
    }

    /**
     * Артефакты, оказавшиеся за пределами числа слотов.
     *
     * <p>Возвращаются владельцу окном: статов они не дают, и оставить их
     * недостижимыми — это тихая потеря вещей.
     */
    public Map<Integer, ItemStack> beyondSlots(UUID player) {
        var own = data.load(player);
        Map<Integer, ItemStack> out = new LinkedHashMap<>();
        for (int slot : own.artifactsBeyond(slots)) {
            Optional<ItemStack> stack = decode(player, own.artifact(slot));
            if (stack.isPresent()) {
                out.put(slot, stack.get());
            }
        }
        return out;
    }

    /**
     * Годится ли предмет в слот артефакта.
     *
     * <p>Только наш предмет и только объявленный артефактом. Ванильная палка в
     * слоте не дала бы ничего, а слот заняла бы — и выглядело бы это как
     * «артефакты не работают».
     */
    public Optional<ItemDef> accepts(ItemStack stack) {
        return items.defOf(stack)
                .filter(def -> def.slot() == ItemSlot.ARTIFACT || def.slot() == ItemSlot.ANY);
    }

    /** Почему предмет не годится; пустая строка — годится. */
    public String refusal(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "здесь ничего нет";
        }
        var def = items.defOf(stack);
        if (def.isEmpty()) {
            return "это не предмет RpgCore: статов у него нет и в слоте он их не даст";
        }
        if (def.get().slot() != ItemSlot.ARTIFACT && def.get().slot() != ItemSlot.ANY) {
            return "это не артефакт, его слот — " + def.get().slot().key();
        }
        return "";
    }

    private Optional<ItemStack> decode(UUID player, String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)));
        } catch (RuntimeException e) {
            // Запись могла остаться от другой версии игры. Не роняем окно и не
            // стираем запись: сказать о ней — единственное, что здесь честно.
            log.accept("артефакт игрока " + player + " не читается: " + e.getMessage());
            return Optional.empty();
        }
    }
}
