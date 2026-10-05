package ru.projectst.rpgcore.platform.gui;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.projectst.rpgcore.classes.ClassDef;

/**
 * Выбор класса.
 *
 * <p>В подсказке прямо сказано, что смена класса сбрасывает изученное и слоты.
 * Это решение принято в {@code ClassService}, и игрок должен узнать о нём до
 * щелчка, а не после.
 */
public final class ClassMenu extends Menu {

    private final MenuContext context;
    private final Player player;

    public ClassMenu(MenuContext context, Player player) {
        this.context = context;
        this.player = player;
    }

    @Override
    protected Component title() {
        return gold("Выбор класса");
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void layout() {
        fillBorder();

        var current = context.playerClasses().classOf(player.getUniqueId());
        int slot = 10;
        for (ClassDef def : context.classes().all()) {
            if (slot > 16) {
                break;
            }
            boolean mine = current.map(own -> own.id().equals(def.id())).orElse(false);

            List<Component> lore = new ArrayList<>();
            lore.add(grey("Платит: ").append(white(def.resource().display())));
            lore.add(grey("Слотов: ").append(white(String.valueOf(def.slots()))));
            lore.add(grey("Предел уровня: ").append(white(String.valueOf(def.maxLevel()))));
            lore.add(Component.empty());
            if (mine) {
                lore.add(green("Это ваш класс"));
            } else if (current.isPresent()) {
                lore.add(red("Смена класса сбросит"));
                lore.add(red("изученное и слоты"));
            } else {
                lore.add(yellow("Нажмите, чтобы выбрать"));
            }

            final String id = def.id();
            put(slot, item(material(def.icon(), Material.BOOK),
                    gold(def.display().replace("&", "")), lore),
                    mine ? null : () -> {
                        if (context.playerClasses().setClass(player.getUniqueId(), id)) {
                            player.sendMessage(green("Класс выбран: ").append(white(id)));
                        }
                        new MainMenu(context, player).open(player);
                    });
            slot++;
        }

        put(22, item(Material.ARROW, yellow("Назад"), List.of()),
                () -> new MainMenu(context, player).open(player));
    }
}
