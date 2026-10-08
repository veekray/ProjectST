package ru.projectst.rpgcore.platform;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.projectst.rpgcore.classes.ClassService;
import ru.projectst.rpgcore.item.GearCell;
import ru.projectst.rpgcore.item.ItemDef;
import ru.projectst.rpgcore.item.ItemSlot;
import ru.projectst.rpgcore.stat.StatModifier;
import ru.projectst.rpgcore.stat.StatService;

/**
 * Статы от надетых предметов.
 *
 * <p>Каждый слот — отдельный источник надбавок в {@link StatService}. Поэтому
 * снятый предмет перестаёт считаться сам, без отдельной команды «убрать», а два
 * предмета в двух слотах не вытесняют друг друга. В прежнем стеке источник был
 * один на всё снаряжение, и забытый пересчёт оставлял надбавки от предмета,
 * которого на игроке уже нет.
 *
 * <p>Пересчёт идёт по снимку, а не по событию снятия. События смены снаряжения в
 * Bukkit неполны: предмет меняется и перетаскиванием, и командой, и смертью, и
 * расходом прочности. Сверка со снимком сама себя исправляет на следующем тике.
 *
 * <p>Предмет с невыполненным требованием надбавок не даёт и говорит, почему.
 * Молча не давать — худший вариант: предмет надет, цифры не изменились, причину
 * не найти.
 *
 * <p>Кольца, амулет, браслет, перчатки и артефакты идут здесь же и тем же
 * механизмом: свой источник на каждую ячейку, те же требования, те же отказы
 * словами. Отдельный расчёт для них означал бы второй набор правил — и однажды
 * кольцо считалось бы иначе, чем шлем, без всякой причины.
 */
public final class EquipmentWatcher {

    /** Общее начало имён источников: по нему видно, что надбавка от предмета. */
    public static final String SOURCE_PREFIX = "item:";

    /** Начало имён источников от своих ячеек снаряжения: дальше идёт имя ячейки. */
    public static final String GEAR_SOURCE_PREFIX = SOURCE_PREFIX + "gear:";

    private final RpgItems items;
    private final StatService stats;
    private final ClassService classes;
    private final GearSlots gear;

    /**
     * Что в каком источнике было в прошлый раз: по этому решаем, нужно ли
     * говорить. Ключ — имя источника, значение — идентификатор предмета.
     */
    private final Map<UUID, Map<String, String>> lastSeen = new HashMap<>();

    public EquipmentWatcher(RpgItems items, StatService stats, ClassService classes,
                            GearSlots gear) {
        this.items = items;
        this.stats = stats;
        this.classes = classes;
        this.gear = gear;
    }

    /** Имя источника надбавок ячейки. Ванильные зовутся как раньше — по слоту. */
    public static String sourceOf(GearCell cell) {
        return cell.vanilla() ? SOURCE_PREFIX + cell.slot().key()
                : GEAR_SOURCE_PREFIX + cell.key();
    }

    /** Сверяет надбавки игрока с тем, что на нём надето. */
    public void apply(Player player) {
        UUID id = player.getUniqueId();
        String playerClass = classes.classOf(id).map(def -> def.id()).orElse(null);
        int level = classes.snapshot(id).level();
        Map<String, String> seen = lastSeen.computeIfAbsent(id, key -> new HashMap<>());

        // Оружие в руке — не ячейка окна, но надетое: его надбавки считаются
        // тем же путём, что и всё остальное.
        wear(player, playerClass, level, seen, SOURCE_PREFIX + ItemSlot.HAND.key(),
                player.getInventory().getItemInMainHand(),
                slot -> slot == ItemSlot.HAND || slot == ItemSlot.ANY);

        for (GearCell cell : GearCell.values()) {
            wear(player, playerClass, level, seen, sourceOf(cell), worn(player, cell),
                    cell::fits);
        }
    }

    /**
     * Почему вещи в ячейках не действуют.
     *
     * <p>Те же правила, что у {@link #apply}, и в том же порядке: окно, которое
     * обещает одно, когда статы считают другое, хуже окна без обещаний. В
     * ответе только ячейки, где вещь лежит, а надбавок не даёт.
     */
    public Map<GearCell, String> refusals(Player player) {
        UUID id = player.getUniqueId();
        String playerClass = classes.classOf(id).map(def -> def.id()).orElse(null);
        int level = classes.snapshot(id).level();

        Map<GearCell, String> out = new EnumMap<>(GearCell.class);
        for (GearCell cell : GearCell.values()) {
            Optional<ItemDef> def = items.defOf(worn(player, cell));
            if (def.isEmpty()) {
                continue;
            }
            ItemDef item = def.get();
            if (!cell.fits(item.slot())) {
                out.put(cell, "не на своём месте: это " + item.slot().title());
                continue;
            }
            String refusal = item.requirement().refusal(playerClass, level);
            if (!refusal.isEmpty()) {
                out.put(cell, refusal);
            }
        }
        return out;
    }

    private ItemStack worn(Player player, GearCell cell) {
        return (cell.vanilla() ? gear.get(player, cell) : gear.stored(player.getUniqueId(), cell))
                .orElse(null);
    }

    /**
     * Надбавки одного источника.
     *
     * @param fits подходит ли объявленный слот предмета этому месту: посох в
     *             шлеме не оружие, кольцо в руке не кольцо
     */
    private void wear(Player player, String playerClass, int level, Map<String, String> seen,
                      String source, ItemStack stack, Predicate<ItemSlot> fits) {
        UUID id = player.getUniqueId();
        Optional<ItemDef> def = items.defOf(stack);
        if (def.isEmpty()) {
            stats.setSource(id, source, List.of());
            seen.remove(source);
            return;
        }
        ItemDef item = def.get();

        // Предмет в чужом месте не работает. Молча: в руке может оказаться что
        // угодно, и сообщение на каждый взятый в руку артефакт было бы шумом.
        // Окно снаряжения показывает это красной рамкой.
        if (!fits.test(item.slot())) {
            stats.setSource(id, source, List.of());
            return;
        }

        String refusal = item.requirement().refusal(playerClass, level);
        if (!refusal.isEmpty()) {
            stats.setSource(id, source, List.of());
            // Сообщение один раз на надевание, а не каждый тик сверки.
            if (!item.id().equals(seen.put(source, item.id()))) {
                player.sendMessage(Component.text(item.display() + ": " + refusal,
                        NamedTextColor.RED));
            }
            return;
        }

        seen.put(source, item.id());
        stats.setSource(id, source, new ArrayList<>(item.modifiers(source)));
    }

    /** Забывает игрока: вышел — нечего и сверять. */
    public void forget(UUID player) {
        lastSeen.remove(player);
    }
}
