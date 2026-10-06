package ru.projectst.rpgcore.platform.gui;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.projectst.rpgcore.item.ItemDef;

/**
 * Объявленные предметы: открыть на правку или выдать себе.
 *
 * <p>Сам предмет и есть своя иконка: он собирается тем же кодом, что и выдача, со
 * всеми статами и требованиями в описании. Список, в котором предметы нарисованы
 * бумажками, не отвечает на главный вопрос — как предмет выглядит в игре.
 *
 * <p>Список берётся из {@code ContentService} каждый раз: после сохранения в
 * верстаке реестр заменяется целиком, и экран, запомнивший прежний, показывал бы
 * предмет, которого уже нет.
 */
public final class ForgeItemsMenu extends Menu {

    private static final int[] GRID = {10, 11, 12, 13, 14, 15, 16,
                                       19, 20, 21, 22, 23, 24, 25,
                                       28, 29, 30, 31, 32, 33, 34,
                                       37, 38, 39, 40, 41, 42, 43};

    private final ForgeContext context;
    private final Player player;
    private final int page;

    public ForgeItemsMenu(ForgeContext context, Player player, int page) {
        this.context = context;
        this.player = player;
        this.page = Math.max(0, page);
    }

    @Override
    protected Component title() {
        return gold("Предметы");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void layout() {
        fillBorder();

        List<ItemDef> all = new ArrayList<>();
        context.content().items().all().forEach(all::add);

        int pages = Math.max(1, (all.size() + GRID.length - 1) / GRID.length);
        int from = Math.min(page, pages - 1) * GRID.length;

        for (int i = 0; i < GRID.length && from + i < all.size(); i++) {
            ItemDef def = all.get(from + i);
            var icon = context.items().build(def, 1);
            var meta = icon.getItemMeta();
            if (meta != null) {
                List<Component> lore = new ArrayList<>(
                        meta.lore() == null ? List.of() : meta.lore());
                lore.add(Component.empty());
                lore.add(ForgeMenu.click("открыть в верстаке")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                lore.add(ForgeMenu.rightClick("выдать себе")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }
            onClick(GRID[i], icon, type -> {
                if (type.isRightClick()) {
                    var leftover = player.getInventory()
                            .addItem(context.items().build(def, 1));
                    if (!leftover.isEmpty()) {
                        player.sendMessage(Component.text("В инвентаре нет места",
                                NamedTextColor.GRAY));
                    }
                    context.equipment().apply(player);
                    open(player);
                    return;
                }
                context.forge().edit(player.getUniqueId(), def);
                new ForgeMenu(context, player).open(player);
            });
        }

        if (all.isEmpty()) {
            put(31, item(Material.BARRIER, red("Ни одного предмета не объявлено"), List.of(
                    grey("Соберите первый в верстаке."))));
        }

        if (from > 0) {
            put(48, item(Material.ARROW, yellow("Предыдущая страница"), List.of()),
                    () -> new ForgeItemsMenu(context, player, page - 1).open(player));
        }
        if (from + GRID.length < all.size()) {
            put(50, item(Material.ARROW, yellow("Следующая страница"), List.of()),
                    () -> new ForgeItemsMenu(context, player, page + 1).open(player));
        }

        put(45, item(Material.ARROW, yellow("Назад в верстак"), List.of(
                        grey("Предметов: " + all.size()),
                        grey("Страница " + (Math.min(page, pages - 1) + 1) + " из " + pages))),
                () -> new ForgeMenu(context, player).open(player));
    }
}
