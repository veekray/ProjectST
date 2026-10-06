package ru.projectst.rpgcore.platform.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.projectst.rpgcore.item.ItemDef;
import ru.projectst.rpgcore.stat.StatOp;

/**
 * Ячейки артефактов: положить, забрать, увидеть, что они дают.
 *
 * <p><b>Предметы переносит сервер, а не курсор.</b> Все щелчки в окне по-прежнему
 * отменяются, а перенос делает плагин: щелчок по вещи в инвентаре кладёт её в
 * первую свободную ячейку, щелчок по ячейке возвращает артефакт в инвентарь.
 * Разрешить настоящее перетаскивание значило бы отвечать за курсор, за
 * shift-щелчок, за раскладывание по стопкам и за закрытие окна с вещью в руке —
 * и каждый из этих случаев умеет терять или удваивать предмет. Здесь терять
 * нечего: предмет в каждый момент лежит либо в инвентаре, либо в ячейке.
 *
 * <p>Каждый отказ назван словами: «это не артефакт», «все ячейки заняты», «в
 * инвентаре нет места». Щелчок, после которого ничего не произошло и никто не
 * объяснил почему, — это та самая жалоба, из-за которой затевался проект.
 */
public final class ArtifactsMenu extends Menu {

    /** Ряд ячеек внутри рамки: середина верхнего ряда. */
    private static final int ROW_START = 10;
    private static final int ROW_END = 16;

    private final MenuContext context;
    private final Player player;

    /** Какая ячейка нарисована в каком месте экрана: заполняется отрисовкой. */
    private final Map<Integer, Integer> slotAt = new LinkedHashMap<>();

    public ArtifactsMenu(MenuContext context, Player player) {
        this.context = context;
        this.player = player;
    }

    @Override
    protected Component title() {
        return gold("Артефакты");
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void layout() {
        fillBorder();
        slotAt.clear();

        int count = context.artifacts().slotCount();
        if (count <= 0) {
            put(13, item(Material.BARRIER, red("Ячеек артефактов нет"), List.of(
                    grey("Их число задано в config.yml:"),
                    white("artifact-slots"),
                    grey("сейчас ноль, поэтому класть некуда."))));
            back();
            return;
        }

        var worn = context.artifacts().all(player.getUniqueId());
        int from = ROW_START + Math.max(0, (ROW_END - ROW_START + 1 - count) / 2);

        for (int i = 0; i < count; i++) {
            int place = from + i;
            int slot = i + 1;
            slotAt.put(place, slot);
            drawSlot(place, slot, worn.get(slot));
        }

        summary(worn);
        help();
        beyond();
        back();
    }

    /** Ячейка: артефакт или пустое место, по которому видно, что оно место. */
    private void drawSlot(int place, int slot, ItemStack stack) {
        if (stack == null) {
            put(place, item(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    grey("Ячейка артефакта " + slot), List.of(
                            grey("Пусто."),
                            ForgeMenu.click("положить — щелчок по вещи в инвентаре"))));
            return;
        }
        ItemStack icon = stack.clone();
        var meta = icon.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
            lore.add(Component.empty());
            lore.add(ForgeMenu.click("забрать в инвентарь").decoration(
                    net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(lore);
            icon.setItemMeta(meta);
        }
        put(place, icon, () -> take(slot));
    }

    /**
     * Что артефакты дают вместе.
     *
     * <p>Складывается из тех же надбавок, которые уходят в статы, а не
     * пересчитывается по-своему: иначе окно обещало бы одно, а бой считал другое.
     * Требования учитываются — артефакт, который игроку не по классу, в сумму не
     * попадает и назван отдельно.
     */
    private void summary(Map<Integer, ItemStack> worn) {
        var id = player.getUniqueId();
        String playerClass = context.playerClasses().classOf(id)
                .map(def -> def.id()).orElse(null);
        int level = context.playerClasses().snapshot(id).level();

        Map<String, Double> flat = new LinkedHashMap<>();
        Map<String, Double> percent = new LinkedHashMap<>();
        Map<String, Double> mult = new LinkedHashMap<>();
        List<Component> refused = new ArrayList<>();

        for (ItemStack stack : worn.values()) {
            var def = context.items().defOf(stack);
            if (def.isEmpty()) {
                continue;
            }
            ItemDef item = def.get();
            String refusal = item.requirement().refusal(playerClass, level);
            if (!refusal.isEmpty()) {
                refused.add(red(item.display() + ": " + refusal));
                continue;
            }
            item.stats().forEach((statId, line) -> {
                Map<String, Double> into = switch (line.op()) {
                    case FLAT -> flat;
                    case PERCENT -> percent;
                    case MULT -> mult;
                };
                into.merge(statId, line.value(),
                        line.op() == StatOp.MULT ? (a, b) -> a * b : Double::sum);
            });
        }

        List<Component> lore = new ArrayList<>();
        if (flat.isEmpty() && percent.isEmpty() && mult.isEmpty()) {
            lore.add(grey("Пока ничего: ячейки пусты."));
        }
        flat.forEach((statId, value) -> lore.add(green(sign(value) + number(value) + " ")
                .append(grey(statId))));
        percent.forEach((statId, value) -> lore.add(green(sign(value) + number(value) + "% ")
                .append(grey(statId))));
        mult.forEach((statId, value) -> lore.add(green("×" + number(value) + " ")
                .append(grey(statId))));
        if (!refused.isEmpty()) {
            lore.add(Component.empty());
            lore.add(red("Не действуют:"));
            lore.addAll(refused);
        }
        lore.add(Component.empty());
        lore.add(grey("Статы целиком — в разделе «Статы»."));
        put(21, item(Material.DIAMOND, aqua("Что дают артефакты"), lore));
    }

    private void help() {
        put(23, item(Material.BOOK, yellow("Как это работает"), List.of(
                ForgeMenu.click("по вещи в инвентаре — положить"),
                ForgeMenu.click("по ячейке — забрать"),
                grey(""),
                grey("Артефакт в ячейке считается надетым:"),
                grey("его статы работают, пока он там."),
                grey("Он лежит отдельно от инвентаря —"),
                grey("не выпадает со смертью и не"),
                grey("занимает места в сумке."),
                grey(""),
                grey("Класть можно только предметы,"),
                grey("у которых слот artifact."))));
    }

    /** Артефакты из ячеек, которых больше нет: вернуть владельцу одним щелчком. */
    private void beyond() {
        var extra = context.artifacts().beyondSlots(player.getUniqueId());
        if (extra.isEmpty()) {
            return;
        }
        List<Component> lore = new ArrayList<>();
        lore.add(grey("Ячеек на сервере стало меньше."));
        lore.add(grey("Эти артефакты статов не дают:"));
        extra.forEach((slot, stack) -> lore.add(white("ячейка " + slot + ": ")
                .append(grey(context.items().defOf(stack)
                        .map(ItemDef::display).orElse(stack.getType().name())))));
        lore.add(ForgeMenu.click("вернуть всё в инвентарь"));
        put(25, item(Material.CHEST, red("Лишние артефакты"), lore), () -> {
            int left = 0;
            for (var entry : extra.entrySet()) {
                if (player.getInventory().addItem(entry.getValue()).isEmpty()) {
                    context.artifacts().set(player.getUniqueId(), entry.getKey(), null);
                } else {
                    // Ячейка не очищается: потерять вещь из-за полного инвентаря
                    // хуже, чем вернуть её в два приёма.
                    left++;
                }
            }
            if (left > 0) {
                player.sendMessage(Component.text(
                        "Не поместилось: " + left + ". Освободите место и повторите",
                        NamedTextColor.GRAY));
            }
            refresh();
        });
    }

    private void back() {
        put(28, item(Material.ARROW, yellow("Назад"), List.of(grey("К окну персонажа."))),
                () -> new MainMenu(context, player).open(player));
    }

    // ------------------------------------------------------------------ перенос

    /** Щелчок по инвентарю игрока: кладём вещь в первую свободную ячейку. */
    @Override
    public void clickOwn(int slot, org.bukkit.event.inventory.ClickType type) {
        if (context.artifacts().slotCount() <= 0) {
            return;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        String refusal = context.artifacts().refusal(stack);
        if (!refusal.isEmpty()) {
            player.sendMessage(Component.text(refusal, NamedTextColor.RED));
            return;
        }
        int free = context.artifacts().firstFree(player.getUniqueId());
        if (free == 0) {
            player.sendMessage(Component.text(
                    "Все ячейки артефактов заняты: сначала заберите один",
                    NamedTextColor.RED));
            return;
        }

        // Берём ровно один предмет из стопки: в ячейке лежит артефакт, а не
        // стопка артефактов, и остаток обязан остаться у игрока.
        ItemStack one = stack.clone();
        one.setAmount(1);
        stack.setAmount(stack.getAmount() - 1);
        player.getInventory().setItem(slot, stack.getAmount() <= 0 ? null : stack);

        context.artifacts().set(player.getUniqueId(), free, one);
        player.sendMessage(Component.text(context.items().defOf(one)
                        .map(ItemDef::display).orElse("Артефакт")
                        + " — в ячейке " + free, NamedTextColor.GREEN));
        refresh();
    }

    /** Забрать артефакт: только если он целиком поместился в инвентарь. */
    private void take(int slot) {
        var own = context.artifacts().get(player.getUniqueId(), slot);
        if (own.isEmpty()) {
            return;
        }
        if (!player.getInventory().addItem(own.get()).isEmpty()) {
            // Ячейка не очищается: артефакт, пропавший при полном инвентаре, —
            // это потерянная вещь, а не неудобство.
            player.sendMessage(Component.text("В инвентаре нет места для артефакта",
                    NamedTextColor.RED));
            return;
        }
        context.artifacts().set(player.getUniqueId(), slot, null);
        refresh();
    }

    /**
     * Пересчитать статы и перерисовать.
     *
     * <p>Статы сверяются сразу, а не раз в секунду по таймеру: игрок положил
     * артефакт и смотрит в то же окно — числа обязаны измениться при нём.
     */
    private void refresh() {
        context.equipment().apply(player);
        open(player);
    }

    private static String sign(double value) {
        return value >= 0 ? "+" : "";
    }
}
