package ru.projectst.rpgcore.platform.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Экран интерфейса.
 *
 * <p>Два решения, из-за которых этот класс вообще есть.
 *
 * <p>Первое: щелчок разбирает <b>сам экран</b>, через владельца инвентаря.
 * Обычный способ — сравнить заголовок окна и угадать по номеру ячейки — ломается
 * от любой правки текста и молча начинает делать не то, на что игрок нажал.
 * Здесь номер ячейки отображён в действие прямо в коде экрана.
 *
 * <p>Второе: экран собирается заново при каждом открытии и после каждого
 * действия. Экран, показывающий прежние числа после того, как игрок что-то
 * нажал, — такая же тихая ложь, как стат, которого нет в мире.
 */
public abstract class Menu {

    /**
     * Кнопка экрана: что нарисовать и что сделать по щелчку.
     *
     * <p>Действие получает вид щелчка. Нужно тем экранам, где одной ячейкой
     * правят число: левый прибавляет, правый убавляет. Разносить «плюс» и
     * «минус» по отдельным ячейкам значило бы вдвое больше кнопок и вдвое
     * меньше статов на экране.
     */
    protected record Button(ItemStack icon, Consumer<ClickType> action) {
    }

    private final List<Button> buttons = new ArrayList<>();
    private Inventory inventory;

    /** Заголовок окна. */
    protected abstract Component title();

    /** Сколько рядов. */
    protected abstract int rows();

    /** Заполняет экран: вызывается заново при каждой отрисовке. */
    protected abstract void layout();

    /** Открывает или перерисовывает экран. */
    public final void open(org.bukkit.entity.Player player) {
        buttons.clear();
        for (int i = 0; i < rows() * 9; i++) {
            buttons.add(null);
        }
        layout();

        if (inventory == null) {
            MenuHolder holder = new MenuHolder(this);
            inventory = Bukkit.createInventory(holder, rows() * 9, title());
            holder.attach(inventory);
        }
        inventory.clear();
        for (int slot = 0; slot < buttons.size(); slot++) {
            Button button = buttons.get(slot);
            if (button != null) {
                inventory.setItem(slot, button.icon());
            }
        }
        if (!inventory.getViewers().contains(player)) {
            player.openInventory(inventory);
        }
    }

    /**
     * Щелчок по инвентарю самого игрока при открытом экране.
     *
     * <p>По умолчанию не делает ничего: обычному экрану инвентарь игрока не
     * интересен. Нужен тем, кто переносит вещи — ячейкам артефактов, — и там
     * перенос делает сервер, а не курсор: щелчок всё равно отменён.
     *
     * @param slot ячейка инвентаря игрока: 0–8 — хотбар, 9–35 — сумка
     */
    public void clickOwn(int slot, ClickType type) {
    }

    /** Щелчок по ячейке. Пустая ячейка — не ошибка, просто ничего не делает. */
    public final void click(int slot, ClickType type) {
        if (slot < 0 || slot >= buttons.size()) {
            return;
        }
        Button button = buttons.get(slot);
        if (button != null && button.action() != null) {
            button.action().accept(type);
        }
    }

    /** Кнопка, которой всё равно, чем по ней щёлкнули: таких большинство. */
    protected final void put(int slot, ItemStack icon, Runnable action) {
        onClick(slot, icon, action == null ? null : type -> action.run());
    }

    /** Кнопка, которая разбирает вид щелчка сама. */
    protected final void onClick(int slot, ItemStack icon, Consumer<ClickType> action) {
        if (slot >= 0 && slot < buttons.size()) {
            buttons.set(slot, new Button(icon, action));
        }
    }

    protected final void put(int slot, ItemStack icon) {
        onClick(slot, icon, null);
    }

    /** Рамка из стекла: отделяет кнопки от пустоты, по ней щелчки не делают ничего. */
    protected final void fillBorder() {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int slot = 0; slot < rows() * 9; slot++) {
            boolean edge = slot < 9 || slot >= (rows() - 1) * 9
                    || slot % 9 == 0 || slot % 9 == 8;
            if (edge) {
                put(slot, pane);
            }
        }
    }

    // ------------------------------------------------------------------ предметы

    /**
     * Предмет по имени материала.
     *
     * <p>Неизвестный материал заменяется бумагой, а не роняет экран: иконка —
     * presentation-деталь, и опечатка в ней не должна мешать игроку пользоваться
     * навыком. Строка об этом уходит в лог один раз, при загрузке контента.
     */
    public static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material found = Material.matchMaterial(name);
        return found == null ? fallback : found;
    }

    public static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            List<Component> lines = new ArrayList<>();
            for (Component line : lore) {
                lines.add(line.decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lines);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    protected static Component grey(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    protected static Component white(String text) {
        return Component.text(text, NamedTextColor.WHITE);
    }

    protected static Component green(String text) {
        return Component.text(text, NamedTextColor.GREEN);
    }

    protected static Component red(String text) {
        return Component.text(text, NamedTextColor.RED);
    }

    protected static Component gold(String text) {
        return Component.text(text, NamedTextColor.GOLD);
    }

    protected static Component yellow(String text) {
        return Component.text(text, NamedTextColor.YELLOW);
    }

    protected static Component aqua(String text) {
        return Component.text(text, NamedTextColor.AQUA);
    }

    /** Округление для показа: числа в интерфейсе не должны пестрить нулями. */
    protected static String number(double value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.valueOf(Math.round(value * 10) / 10.0);
    }
}
