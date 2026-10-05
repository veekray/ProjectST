package ru.projectst.rpgcore.platform.gui;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.projectst.rpgcore.stat.StatDef;

/**
 * Статы: снимок всех значений и действующие статусы.
 *
 * <p>Показываются ровно те числа, которыми считает бой, — тот же снимок, что
 * берёт конвейер урона. Отдельный расчёт для показа означал бы два источника
 * правды, и расхождение между ними игрок заметил бы раньше, чем мы.
 */
public final class StatsMenu extends Menu {

    private final MenuContext context;
    private final Player player;

    public StatsMenu(MenuContext context, Player player) {
        this.context = context;
        this.player = player;
    }

    @Override
    protected Component title() {
        return gold("Статы");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void layout() {
        fillBorder();

        var id = player.getUniqueId();
        var snapshot = context.statValues().snapshot(id);

        int slot = 10;
        for (StatDef def : context.stats().all()) {
            if (slot > 43) {
                break;
            }
            double value = snapshot.get(def.id());
            List<Component> lore = new ArrayList<>();
            lore.add(grey("Значение: ").append(white(number(value))));
            lore.add(grey("База: ").append(white(number(def.base()))));
            lore.add(grey("Предел: ").append(white(number(def.min())
                    + " … " + number(def.max()))));
            lore.add(grey("Ключ: ").append(white(def.id())));

            put(slot, item(Material.PAPER, aqua(def.display()), lore));
            slot++;
            if (slot % 9 == 8) {
                slot += 2;
            }
        }

        // Действующие статусы: тот же ответ, что даёт /rpg debug.
        List<Component> lore = new ArrayList<>();
        var acting = context.statuses().acting(id);
        if (acting.isEmpty()) {
            lore.add(grey("Ничего не действует"));
        } else {
            acting.forEach(status -> lore.add(white(status.id())
                    .append(grey(" x" + status.stacks()))));
        }
        put(49, item(Material.POTION, yellow("Статусы"), lore));
        put(45, item(Material.ARROW, yellow("Назад"), List.of()),
                () -> new MainMenu(context, player).open(player));
    }
}
