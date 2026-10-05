package ru.projectst.rpgcore.platform.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Щелчки по экранам интерфейса.
 *
 * <p>Чьё это окно, определяет владелец, а не заголовок: сравнение заголовков
 * ломается от правки текста и начинает молча пропускать щелчки дальше — прямо в
 * инвентарь игрока.
 *
 * <p>Любое действие в окне отменяется до разбора. Иначе предмет-иконку можно
 * было бы вытащить себе, и «кнопка» стала бы бесплатным предметом — классический
 * способ выдать всем звёзды Нижнего мира.
 */
public final class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        // Щелчки по своему инвентарю при открытом экране тоже отменены выше, но
        // в действие превращаются только те, что по самому экрану.
        if (event.getClickedInventory() == event.getInventory()) {
            holder.menu().click(event.getSlot());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }
}
