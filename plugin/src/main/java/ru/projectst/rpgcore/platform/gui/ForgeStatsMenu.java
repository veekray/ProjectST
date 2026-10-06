package ru.projectst.rpgcore.platform.gui;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.projectst.rpgcore.item.ItemDef;
import ru.projectst.rpgcore.item.ItemDraft;
import ru.projectst.rpgcore.stat.StatDef;
import ru.projectst.rpgcore.stat.StatOp;

/**
 * Статы предмета: по ячейке на стат, щелчками.
 *
 * <p>Список берётся из загруженного {@code stats.yml}, а не из таблицы в коде.
 * Поэтому выдумать стат здесь нельзя — а выдуманный стат не просто не сработал
 * бы: он падал бы при каждом пересчёте статов носителя, потому что движок строг к
 * необъявленным.
 *
 * <p>Числа правятся щелчками, а не вводом в чат: шаг в единицу и в десяток
 * покрывает всё, что нужно для проверки, а чат на каждое число означал бы десять
 * закрытий окна подряд.
 *
 * <p>Легенда управления лежит кнопкой на экране. Набор щелчков, который нужно
 * помнить, — это тот же молчаливый отказ: нажал не так, получил не то и не
 * понял, почему.
 */
public final class ForgeStatsMenu extends Menu {

    /** Ячейки под статы: четыре ряда по семь внутри рамки. */
    private static final int[] GRID = {10, 11, 12, 13, 14, 15, 16,
                                       19, 20, 21, 22, 23, 24, 25,
                                       28, 29, 30, 31, 32, 33, 34,
                                       37, 38, 39, 40, 41, 42, 43};

    private final ForgeContext context;
    private final Player player;
    private final int page;

    public ForgeStatsMenu(ForgeContext context, Player player, int page) {
        this.context = context;
        this.player = player;
        this.page = Math.max(0, page);
    }

    @Override
    protected Component title() {
        return gold("Статы предмета");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void layout() {
        fillBorder();
        ItemDraft draft = context.forge().draft(player.getUniqueId());

        List<StatDef> all = new ArrayList<>();
        context.content().stats().all().forEach(all::add);

        int pages = Math.max(1, (all.size() + GRID.length - 1) / GRID.length);
        int from = Math.min(page, pages - 1) * GRID.length;

        for (int i = 0; i < GRID.length && from + i < all.size(); i++) {
            StatDef def = all.get(from + i);
            put(GRID[i], draft, def);
        }

        if (from > 0) {
            put(48, item(Material.ARROW, yellow("Предыдущая страница"), List.of()),
                    () -> new ForgeStatsMenu(context, player, page - 1).open(player));
        }
        if (from + GRID.length < all.size()) {
            put(50, item(Material.ARROW, yellow("Следующая страница"), List.of()),
                    () -> new ForgeStatsMenu(context, player, page + 1).open(player));
        }

        put(49, item(Material.BOOK, yellow("Как править"), List.of(
                aqua("Щелчок").append(grey(" — прибавить")),
                aqua("Правый").append(grey(" — убавить")),
                aqua("Shift").append(grey(" — шагом в десять раз больше")),
                aqua("Q").append(grey(" — способ: плоско → проценты → множитель")),
                aqua("Shift+Q").append(grey(" — убрать надбавку")),
                grey(""),
                grey("Ноль убирает надбавку сам: «+0» в"),
                grey("описании предмета не делает ничего."))));

        put(45, item(Material.ARROW, yellow("Назад в верстак"), List.of(
                        grey("Страница " + (Math.min(page, pages - 1) + 1) + " из " + pages))),
                () -> new ForgeMenu(context, player).open(player));
    }

    private void put(int slot, ItemDraft draft, StatDef def) {
        ItemDef.ItemStatLine line = draft.stat(def.id());

        List<Component> lore = new ArrayList<>();
        lore.add(line == null
                ? grey("Надбавки нет")
                : white(ForgeMenu.statLine(def.id(), line)));
        lore.add(grey("Способ: ").append(white(opName(line == null ? StatOp.FLAT : line.op()))));
        lore.add(grey("Шаг: ").append(white(number(step(line)))));
        lore.add(grey("Ключ: ").append(white(def.id())));
        lore.add(grey("Предел стата: ").append(white(number(def.min())
                + " … " + number(def.max()))));

        // Предмет со надбавкой выглядит иначе, чем без неё: искать заданные
        // статы по описанию на двадцати восьми ячейках нельзя.
        Material icon = line == null ? Material.GRAY_DYE : Material.LIME_DYE;
        onClick(slot, item(icon, aqua(def.display()), lore), type -> {
            switch (type) {
                case LEFT -> draft.addStat(def.id(), step(line));
                case RIGHT -> draft.addStat(def.id(), -step(line));
                case SHIFT_LEFT -> draft.addStat(def.id(), step(line) * 10);
                case SHIFT_RIGHT -> draft.addStat(def.id(), -step(line) * 10);
                case DROP -> draft.cycleOp(def.id());
                case CONTROL_DROP -> draft.clearStat(def.id());
                default -> {
                    // Прочие щелчки ничего не делают: средний работает только в
                    // творческом режиме, и привязывать к нему действие значило бы
                    // кнопку, которая у половины игроков не нажимается.
                }
            }
            open(player);
        });
    }

    /**
     * Шаг правки.
     *
     * <p>У множителя он другой: ×1.5 набирать единицами — это полтора нажатия,
     * а ×16 от шага в единицу получается случайно.
     */
    private static double step(ItemDef.ItemStatLine line) {
        return line != null && line.op() == StatOp.MULT ? 0.1 : 1;
    }

    private static String opName(StatOp op) {
        return switch (op) {
            case FLAT -> "плоско";
            case PERCENT -> "проценты";
            case MULT -> "множитель";
        };
    }
}
