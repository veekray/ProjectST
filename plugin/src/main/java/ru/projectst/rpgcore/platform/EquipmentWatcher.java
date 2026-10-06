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
 *
 * <p>Артефакты идут здесь же и тем же механизмом: свой источник на каждую
 * ячейку, те же требования, те же отказы словами. Отдельный расчёт для них
 * означал бы второй набор правил — и однажды артефакт считался бы иначе, чем
 * шлем, без всякой причины.
 */
public final class EquipmentWatcher {

    /** Общее начало имён источников: по нему видно, что надбавка от предмета. */
    public static final String SOURCE_PREFIX = "item:";

    /** Начало имён источников от артефактов: дальше идёт номер ячейки. */
    public static final String ARTIFACT_SOURCE_PREFIX = SOURCE_PREFIX + "artifact:";

    private final RpgItems items;
    private final StatService stats;
    private final ClassService classes;
    private final ArtifactSlots artifacts;

    /** Что в каком слоте было в прошлый раз: по этому решаем, нужно ли говорить. */
    private final Map<UUID, Map<ItemSlot, String>> lastSeen = new java.util.HashMap<>();

    /** То же про ячейки артефактов: ключ — номер ячейки. */
    private final Map<UUID, Map<Integer, String>> lastArtifacts = new java.util.HashMap<>();

    public EquipmentWatcher(RpgItems items, StatService stats, ClassService classes,
                            ArtifactSlots artifacts) {
        this.items = items;
        this.stats = stats;
        this.classes = classes;
        this.artifacts = artifacts;
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

        applyArtifacts(player, playerClass, level);
    }

    /**
     * Надбавки от артефактов.
     *
     * <p>Источник на ячейку, как и у брони: вынутый артефакт перестаёт считаться
     * сам, без отдельной команды «убрать». Ячейки считаются до объявленного
     * числа: если его уменьшили, артефакт из лишней ячейки статов не даёт — и
     * окно его вернёт, а не оставит действующим втихую.
     */
    private void applyArtifacts(Player player, String playerClass, int level) {
        UUID id = player.getUniqueId();
        Map<Integer, String> seen =
                lastArtifacts.computeIfAbsent(id, key -> new LinkedHashMap<>());
        Map<Integer, ItemStack> worn = artifacts.all(id);

        for (int slot = 1; slot <= artifacts.slotCount(); slot++) {
            String source = ARTIFACT_SOURCE_PREFIX + slot;
            Optional<ItemDef> def = items.defOf(worn.get(slot));
            if (def.isEmpty()) {
                stats.setSource(id, source, List.of());
                seen.remove(slot);
                continue;
            }
            ItemDef item = def.get();

            // Предмет, который перестал быть артефактом после правки файла, не
            // работает в ячейке — и это видно: статы исчезли, предмет на месте.
            if (item.slot() != ItemSlot.ARTIFACT && item.slot() != ItemSlot.ANY) {
                stats.setSource(id, source, List.of());
                continue;
            }

            String refusal = item.requirement().refusal(playerClass, level);
            if (!refusal.isEmpty()) {
                stats.setSource(id, source, List.of());
                if (!item.id().equals(seen.put(slot, item.id()))) {
                    player.sendMessage(Component.text(item.display() + ": " + refusal,
                            NamedTextColor.RED));
                }
                continue;
            }

            seen.put(slot, item.id());
            stats.setSource(id, source, new ArrayList<>(item.modifiers(source)));
        }
    }

    /** Забывает игрока: вышел — нечего и сверять. */
    public void forget(UUID player) {
        lastSeen.remove(player);
        lastArtifacts.remove(player);
    }
}
