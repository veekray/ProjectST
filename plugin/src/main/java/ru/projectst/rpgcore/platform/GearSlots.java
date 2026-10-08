package ru.projectst.rpgcore.platform;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import ru.projectst.rpgcore.data.PlayerDataStore;
import ru.projectst.rpgcore.item.GearCell;

/**
 * Ячейки снаряжения: где лежит вещь и годится ли она сюда.
 *
 * <p><b>Два рода ячеек — одно обращение.</b> Броня и вторая рука — это
 * настоящая экипировка игрока: её видно на модели, она падает со смертью и
 * совпадает с обычным инвентарём. Кольца, амулет, браслет, перчатки и
 * артефакты лежат в данных игрока: в ванильной броне им места нет. Окну и
 * статам эта разница не нужна, поэтому она спрятана здесь, а наружу торчит одно
 * «что лежит в ячейке» и одно «положи в ячейку».
 *
 * <p><b>Свои ячейки не выпадают со смертью</b> и не теряются при полном
 * рюкзаке: вещь записана в данных игрока целиком, со всем, что на ней есть, —
 * прочностью, зачарованиями, нашей меткой. Хранить один идентификатор было бы
 * короче, но тогда снятое кольцо возвращалось бы игроку не тем, что он надел.
 */
public final class GearSlots {

    private final PlayerDataStore data;
    private final RpgItems items;
    private final Consumer<String> log;

    /**
     * @param log куда жаловаться на нечитаемую запись: в фоне бросать некуда,
     *            а молчать про потерянную вещь нельзя
     */
    public GearSlots(PlayerDataStore data, RpgItems items, Consumer<String> log) {
        this.data = data;
        this.items = items;
        this.log = log;
    }

    /** Что лежит в ячейке. Копия: менять вещь — только через {@link #set}. */
    public Optional<ItemStack> get(Player player, GearCell cell) {
        if (!cell.vanilla()) {
            return stored(player.getUniqueId(), cell);
        }
        ItemStack stack = vanilla(player.getInventory(), cell);
        return empty(stack) ? Optional.empty() : Optional.of(stack.clone());
    }

    /**
     * Вещь из своей ячейки, по одному идентификатору игрока.
     *
     * <p>Статы пересчитываются и для того, чей предмет только что лёг в
     * данные, — им нужен один идентификатор, а не живой игрок.
     */
    public Optional<ItemStack> stored(UUID player, GearCell cell) {
        if (cell.vanilla()) {
            return Optional.empty();
        }
        return decode(player, data.load(player).gearItem(cell.key()));
    }

    /**
     * В ячейке есть запись, но вещь из неё не читается.
     *
     * <p>Такая ячейка выглядит пустой, а пустой не является: положить в неё
     * что-то значило бы затереть запись и потерять вещь навсегда. Окно поэтому
     * её не трогает и говорит почему.
     */
    public boolean unreadable(UUID player, GearCell cell) {
        if (cell.vanilla()) {
            return false;
        }
        String encoded = data.load(player).gearItem(cell.key());
        return encoded != null && decode(player, encoded).isEmpty();
    }

    /**
     * Кладёт вещь в ячейку; пустое значение очищает её.
     *
     * <p>Проверки «можно ли» здесь нет намеренно: решает вызывающий, и он же
     * объясняет игроку отказ словами. Этот метод — про хранение.
     */
    public void set(Player player, GearCell cell, ItemStack stack) {
        ItemStack value = empty(stack) ? null : stack;
        if (cell.vanilla()) {
            PlayerInventory inventory = player.getInventory();
            switch (cell) {
                case HELMET -> inventory.setHelmet(value);
                case CHEST -> inventory.setChestplate(value);
                case LEGS -> inventory.setLeggings(value);
                case BOOTS -> inventory.setBoots(value);
                case OFFHAND -> inventory.setItemInOffHand(value);
                default -> throw new IllegalStateException("ячейка " + cell.key()
                        + " объявлена ванильной, а экипировки для неё нет");
            }
            return;
        }
        var own = data.load(player.getUniqueId());
        own.setGear(cell.key(), value == null ? null
                : Base64.getEncoder().encodeToString(value.serializeAsBytes()));
        data.saveLater(own);
    }

    /**
     * Почему вещь сюда не кладётся; пустая строка — кладётся.
     *
     * <p>Вторая рука принимает что угодно, как и в ванилле: факел, щит, стопку
     * стрел. Наш предмет идёт туда, куда объявлен, — посох в кольце не даст
     * ничего, а ячейку займёт, и выглядело бы это как «кольца не работают».
     * Чужая броня проверяется по ванильному правилу: шлем на голову, сапоги на
     * ноги.
     *
     * <p>Требования класса и уровня здесь не проверяются: вещь, которая пока не
     * по силам, надеть можно, — она лежит в ячейке, обведённая красным, и
     * начинает работать сама, когда игрок дорастёт.
     */
    public String refusal(GearCell cell, ItemStack stack) {
        if (empty(stack)) {
            return "здесь ничего нет";
        }
        if (cell == GearCell.OFFHAND) {
            return "";
        }
        var def = items.defOf(stack);
        if (def.isPresent()) {
            if (cell.fits(def.get().slot())) {
                return "";
            }
            return "это не " + cell.slot().title() + ": место этой вещи — "
                    + def.get().slot().title();
        }
        if (cell.vanilla()) {
            return stack.getType().getEquipmentSlot() == equipmentSlot(cell) ? ""
                    : "это не надевается в ячейку «" + cell.display().toLowerCase(Locale.ROOT)
                            + "»";
        }
        return "это не предмет RpgCore: в ячейке «" + cell.display().toLowerCase(Locale.ROOT)
                + "» он ничего не даст";
    }

    /**
     * Ячейки, куда вещь можно положить, по порядку предпочтения.
     *
     * <p>Порядок решает shift-щелчок: игрок не выбирает ячейку, и выбор обязан
     * быть предсказуемым — кольцо сначала на левую руку, артефакт слева
     * направо. Пустой список — вещь никуда не надевается.
     */
    public List<GearCell> placesFor(ItemStack stack) {
        List<GearCell> out = new ArrayList<>();
        if (empty(stack)) {
            return out;
        }
        var def = items.defOf(stack);
        List<GearCell> wanted;
        if (def.isPresent()) {
            wanted = GearCell.placesFor(def.get().slot());
        } else {
            // Чужая вещь — по ванильному правилу: броню на тело, щит во вторую
            // руку. Остальное shift-щелчком не надевается, как и в ванилле.
            EquipmentSlot slot = stack.getType().getEquipmentSlot();
            wanted = java.util.Arrays.stream(GearCell.values())
                    .filter(cell -> cell.vanilla() && equipmentSlot(cell) == slot)
                    .toList();
        }
        for (GearCell cell : wanted) {
            if (refusal(cell, stack).isEmpty()) {
                out.add(cell);
            }
        }
        return out;
    }

    /**
     * Почему вещь нельзя снять; пустая строка — можно.
     *
     * <p>Броню с проклятием несъёмности ванилла не отдаёт никому, кроме
     * творческого режима. Окно, которое снимает её в обход, превратило бы
     * проклятие в украшение.
     */
    public String stuck(Player player, GearCell cell, ItemStack stack) {
        if (empty(stack) || !cell.vanilla() || cell == GearCell.OFFHAND
                || player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return "";
        }
        return stack.containsEnchantment(org.bukkit.enchantments.Enchantment.BINDING_CURSE)
                ? "проклятие несъёмности: эту вещь не снять" : "";
    }

    /** Почему вещь никуда не надевается: для shift-щелчка, которому некуда положить. */
    public String nowhere(ItemStack stack) {
        var def = items.defOf(stack);
        if (def.isPresent() && def.get().slot() == ru.projectst.rpgcore.item.ItemSlot.HAND) {
            return "это оружие: его держат в руке, а не надевают";
        }
        return "это никуда не надевается";
    }

    /**
     * Сколько штук помещается в ячейку.
     *
     * <p>Во второй руке — стопка, как в ванилле: факелы и стрелы по одному там
     * никто не держит. В остальных — одна вещь: в ячейке кольца лежит кольцо, а
     * не шестнадцать колец, из которых действует одно.
     */
    public int limit(GearCell cell, ItemStack stack) {
        return cell == GearCell.OFFHAND && !empty(stack) ? stack.getMaxStackSize() : 1;
    }

    /**
     * Отдаёт владельцу вещи из исчезнувших ячеек.
     *
     * <p>В инвентарь, а что не влезло — под ноги: держать вещь в данных дальше
     * значило бы, что игрок её не видит и не может взять. Нечитаемая запись
     * остаётся в очереди — стереть её значило бы потерять вещь навсегда, а
     * прочитать её, возможно, сможет следующая версия.
     */
    public void deliverReturns(Player player) {
        var own = data.load(player.getUniqueId());
        if (own.returns().isEmpty()) {
            return;
        }
        List<String> kept = new ArrayList<>();
        int given = 0;
        for (String encoded : own.returns()) {
            Optional<ItemStack> stack = decode(player.getUniqueId(), encoded);
            if (stack.isEmpty()) {
                kept.add(encoded);
                continue;
            }
            player.getInventory().addItem(stack.get()).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(
                            player.getLocation(), rest));
            given++;
        }
        own.returns().clear();
        own.returns().addAll(kept);
        data.saveLater(own);
        if (given > 0) {
            player.sendMessage(Component.text("Артефакты из ячеек, которых больше нет, "
                    + "возвращены вам (" + given + "): что не влезло в инвентарь, лежит "
                    + "под ногами.", NamedTextColor.GOLD));
        }
    }

    /** Вещь из ванильной ячейки; {@code null} — пусто. */
    private static ItemStack vanilla(PlayerInventory inventory, GearCell cell) {
        return switch (cell) {
            case HELMET -> inventory.getHelmet();
            case CHEST -> inventory.getChestplate();
            case LEGS -> inventory.getLeggings();
            case BOOTS -> inventory.getBoots();
            case OFFHAND -> inventory.getItemInOffHand();
            default -> null;
        };
    }

    /** Ванильный слот, которому соответствует ячейка; {@code null} — своя. */
    private static EquipmentSlot equipmentSlot(GearCell cell) {
        return switch (cell) {
            case HELMET -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case BOOTS -> EquipmentSlot.FEET;
            case OFFHAND -> EquipmentSlot.OFF_HAND;
            default -> null;
        };
    }

    static boolean empty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
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
            log.accept("снаряжение игрока " + player + " не читается: " + e.getMessage());
            return Optional.empty();
        }
    }
}
