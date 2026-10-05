package ru.projectst.rpgcore.platform.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Хозяин окна — сам экран.
 *
 * <p>Благодаря этому слушатель щелчков не сравнивает заголовки и не угадывает,
 * чьё это окно: он спрашивает владельца. Сравнение заголовков — то же самое
 * «работает, пока не поправили текст», от которого проект и уходит.
 */
public final class MenuHolder implements InventoryHolder {

    private final Menu menu;
    private Inventory inventory;

    MenuHolder(Menu menu) {
        this.menu = menu;
    }

    public Menu menu() {
        return menu;
    }

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    /**
     * Окно этого экрана.
     *
     * <p>Может быть {@code null} ровно между созданием владельца и созданием
     * окна — внутри одного вызова. Снаружи его не увидеть.
     */
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
