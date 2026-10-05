package ru.projectst.rpgcore.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import ru.projectst.rpgcore.item.ItemDef;
import ru.projectst.rpgcore.item.ItemRegistry;
import ru.projectst.rpgcore.item.Rarity;
import ru.projectst.rpgcore.stat.StatOp;

/**
 * Превращает объявление предмета в предмет в мире и обратно.
 *
 * <p><b>Узнавание по метке, а не по описанию.</b> Идентификатор лежит в
 * постоянном контейнере предмета. Описание игрок меняет наковальней, переводом
 * или другим плагином — предмет, узнаваемый по описанию, однажды перестаёт
 * узнаваться, и его статы просто исчезают. По метке он узнаётся всегда, и это
 * прямая причина, по которой здесь не разбирается лор.
 *
 * <p>Описание собирается заново при каждой выдаче: статы, требования и умения
 * видны в предмете теми же числами, которыми их считает бой.
 */
public final class RpgItems {

    private final NamespacedKey idKey;
    private final ItemRegistry registry;

    public RpgItems(Plugin plugin, ItemRegistry registry) {
        this.idKey = new NamespacedKey(plugin, "item");
        this.registry = registry;
    }

    /** Реестр предметов: нужен тем, кто выдаёт предмет по идентификатору. */
    public ItemRegistry registry() {
        return registry;
    }

    /** Идентификатор нашего предмета, если это он. */
    public Optional<String> idOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Optional.empty();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING));
    }

    /** Объявление предмета, если стак — наш предмет и он всё ещё объявлен. */
    public Optional<ItemDef> defOf(ItemStack stack) {
        return idOf(stack).flatMap(registry::find);
    }

    /**
     * Собирает предмет.
     *
     * <p>Неизвестный материал заменяется бумагой с жалобой в лог: выдать предмет,
     * у которого опечатка в материале, лучше, чем уронить команду выдачи — но
     * молчать об этом нельзя.
     */
    public ItemStack build(ItemDef def, int amount) {
        Material material = Material.matchMaterial(def.material());
        if (material == null) {
            material = Material.PAPER;
        }
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        Rarity rarity = registry.rarity(def.rarityId());
        NamedTextColor color = color(rarity.color());
        meta.displayName(Component.text(def.display(), color)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(rarity.display(), color)
                .decoration(TextDecoration.ITALIC, false));
        for (String line : def.lore()) {
            lore.add(Component.text(line, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        if (!def.stats().isEmpty()) {
            lore.add(Component.empty());
            def.stats().forEach((statId, line) -> lore.add(statLine(statId, line)));
        }

        if (!def.abilities().isEmpty()) {
            lore.add(Component.empty());
            for (var ability : def.abilities()) {
                lore.add(Component.text("Умение: ", NamedTextColor.GRAY)
                        .append(Component.text(triggerName(ability.trigger()),
                                NamedTextColor.YELLOW))
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        if (!def.requirement().any()) {
            lore.add(Component.empty());
            if (def.requirement().classId() != null) {
                lore.add(Component.text("Только для: " + def.requirement().classId(),
                        NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }
            if (def.requirement().level() > 0) {
                lore.add(Component.text("Нужен уровень " + def.requirement().level(),
                        NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }
        }

        meta.lore(lore);
        meta.setUnbreakable(def.unbreakable());
        if (def.modelData() > 0) {
            meta.setCustomModelData(def.modelData());
        }
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, def.id());
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component statLine(String statId, ItemDef.ItemStatLine line) {
        String sign = line.value() >= 0 ? "+" : "";
        String suffix = line.op() == StatOp.PERCENT ? "%" : "";
        String value = line.value() == Math.rint(line.value())
                ? String.valueOf((long) line.value())
                : String.valueOf(line.value());
        NamedTextColor color = line.value() >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
        return Component.text(sign + value + suffix + " ", color)
                .append(Component.text(statId, NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false);
    }

    private static String triggerName(ru.projectst.rpgcore.item.ItemTrigger trigger) {
        return switch (trigger) {
            case RIGHT_CLICK -> "правый щелчок";
            case LEFT_CLICK -> "левый щелчок";
            case SNEAK_RIGHT_CLICK -> "Shift и правый щелчок";
            case ON_HIT -> "удар";
        };
    }

    /** Цвет редкости; неизвестное имя — серый, о нём скажет связывание. */
    private static NamedTextColor color(String name) {
        NamedTextColor found = NamedTextColor.NAMES.value(name.toLowerCase(java.util.Locale.ROOT));
        return found == null ? NamedTextColor.GRAY : found;
    }
}
