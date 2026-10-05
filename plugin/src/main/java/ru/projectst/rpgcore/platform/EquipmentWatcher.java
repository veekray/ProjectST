package ru.projectst.rpgcore.platform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.projectst.rpgcore.classes.ClassService;
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
 */
public final class EquipmentWatcher {

    /** Общее начало имён источников: по нему видно, что надбавка от предмета. */
    public static final String SOURCE_PREFIX = "item:";

    private final RpgItems items;
    private final StatService stats;
    private final ClassService classes;

    /** Что в каком слоте было в прошлый раз: по этому решаем, нужно ли говорить. */
    private final Map<UUID, Map<ItemSlot, String>> lastSeen = new java.util.HashMap<>();

    public EquipmentWatcher(RpgItems items, StatService stats, ClassService classes) {
        this.items = items;
        this.stats = stats;
        this.classes = classes;
    }

    /** Сверяет надбавки игрока с тем, что на нём надето. */
    public void apply(Player player) {
        UUID id = player.getUniqueId();
        String playerClass = classes.classOf(id).map(def -> def.id()).orElse(null);
        int level = classes.snapshot(id).level();

        Map<ItemSlot, ItemStack> worn = new LinkedHashMap<>();
        var inventory = player.getInventory();
        worn.put(ItemSlot.HAND, inventory.getItemInMainHand());
        worn.put(ItemSlot.OFFHAND, inventory.getItemInOffHand());
        worn.put(ItemSlot.HELMET, inventory.getHelmet());
        worn.put(ItemSlot.CHEST, inventory.getChestplate());
        worn.put(ItemSlot.LEGS, inventory.getLeggings());
        worn.put(ItemSlot.BOOTS, inventory.getBoots());

        Map<ItemSlot, String> seen = lastSeen.computeIfAbsent(id, key -> new LinkedHashMap<>());

        for (Map.Entry<ItemSlot, ItemStack> entry : worn.entrySet()) {
            ItemSlot slot = entry.getKey();
            String source = SOURCE_PREFIX + slot.key();
            Optional<ItemDef> def = items.defOf(entry.getValue());

            if (def.isEmpty()) {
                stats.setSource(id, source, List.of());
                seen.remove(slot);
                continue;
            }
            ItemDef item = def.get();

            // Предмет в чужом слоте не работает: посох в шлеме не оружие.
            if (item.slot() != slot && item.slot() != ItemSlot.ANY) {
                stats.setSource(id, source, List.of());
                continue;
            }

            String refusal = item.requirement().refusal(playerClass, level);
            if (!refusal.isEmpty()) {
                stats.setSource(id, source, List.of());
                // Сообщение один раз на надевание, а не каждый тик сверки.
                if (!item.id().equals(seen.put(slot, item.id()))) {
                    player.sendMessage(Component.text(item.display() + ": " + refusal,
                            NamedTextColor.RED));
                }
                continue;
            }

            seen.put(slot, item.id());
            List<StatModifier> modifiers = new ArrayList<>(item.modifiers(source));
            stats.setSource(id, source, modifiers);
        }
    }

    /** Забывает игрока: вышел — нечего и сверять. */
    public void forget(UUID player) {
        lastSeen.remove(player);
    }
}
