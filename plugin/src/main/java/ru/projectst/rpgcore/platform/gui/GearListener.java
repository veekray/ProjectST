package ru.projectst.rpgcore.platform.gui;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.Plugin;

/**
 * События окна снаряжения.
 *
 * <p>Окно узнаётся по владельцу, а не по заголовку — по той же причине, что и
 * остальные экраны ({@link MenuListener}): сравнение заголовков ломается от
 * правки текста и начинает молча пропускать щелчки дальше.
 *
 * <p>После каждого щелчка витрина перерисовывается ещё раз, тиком позже.
 * Ванильный щелчок по своему инвентарю может задеть и снаряжение — клавиша F
 * меняет вторую руку, — и окно обязано это показать.
 */
public final class GearListener implements Listener {

    private final Plugin plugin;

    public GearListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GearView view)) {
            return;
        }
        if (!event.getWhoClicked().getUniqueId().equals(view.owner())) {
            // Чужое снаряжение не трогает никто: окно открывается только своему.
            event.setCancelled(true);
            return;
        }
        view.click(event);
        Bukkit.getScheduler().runTask(plugin, view::refresh);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GearView view) {
            view.drag(event);
            Bukkit.getScheduler().runTask(plugin, view::refresh);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof GearView view) {
            view.closed();
        }
    }
}
