package ru.projectst.rpgcore.platform.gui;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.projectst.rpgcore.item.ItemAbility;
import ru.projectst.rpgcore.item.ItemDef;
import ru.projectst.rpgcore.item.ItemDraft;
import ru.projectst.rpgcore.item.ItemSlot;
import ru.projectst.rpgcore.item.Rarity;
import ru.projectst.rpgcore.platform.ItemForge;

/**
 * Верстак предметов: собрать предмет в игре и получить его в руки.
 *
 * <p>Нужен проверке навыков. Навык читает статы носителя, и проверить «как
 * влияет плюс сорок к магическому урону» можно только надев предмет, который его
 * даёт. Писать для этого файл, перезапускать сервер и выдавать командой — три
 * шага, из которых каждый можно забыть.
 *
 * <p><b>Собранный предмет становится файлом контента.</b> Не «временным
 * предметом»: временный жил бы по своим правилам и однажды разошёлся бы с
 * настоящими. Что именно пишется и почему — в {@link ItemForge}.
 *
 * <p>У каждой кнопки в описании написано, что делает левый щелчок, а что правый.
 * Кнопка, у которой правый щелчок делает что-то неожидаемое, хуже кнопки, у
 * которой его нет.
 */
public final class ForgeMenu extends Menu {

    private final ForgeContext context;
    private final Player player;

    public ForgeMenu(ForgeContext context, Player player) {
        this.context = context;
        this.player = player;
    }

    @Override
    protected Component title() {
        return gold("Верстак предметов");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void layout() {
        fillBorder();
        ItemDraft draft = context.forge().draft(player.getUniqueId());

        identity(draft);
        looks(draft);
        requirements(draft);
        preview(draft);
        actions(draft);
    }

    // ------------------------------------------------------------------ кто он

    private void identity(ItemDraft draft) {
        put(10, item(Material.NAME_TAG, yellow("Идентификатор"), List.of(
                        value(draft.id().isEmpty() ? "не задан" : draft.id()),
                        grey("Имя файла и метка в предмете."),
                        grey("Латиница, цифры, подчёркивание."),
                        click("ввести в чат"))),
                () -> context.prompt().ask(player, "Идентификатор предмета",
                        "например test_sword; сейчас: "
                                + (draft.id().isEmpty() ? "не задан" : draft.id()),
                        text -> {
                            current().id(text);
                            open(player);
                        },
                        () -> open(player)));

        put(11, item(Material.PAPER, yellow("Имя"), List.of(
                        value(draft.display().isEmpty() ? "не задано" : draft.display()),
                        grey("Как предмет называется игроку."),
                        grey("Цвет даёт редкость, а не имя."),
                        click("ввести в чат"))),
                () -> context.prompt().ask(player, "Имя предмета",
                        "сейчас: " + (draft.display().isEmpty() ? "не задано" : draft.display()),
                        text -> {
                            current().display(text);
                            open(player);
                        },
                        () -> open(player)));

        onClick(12, item(Material.ANVIL, yellow("Материал"), List.of(
                value(draft.material().isEmpty() ? "не задан" : draft.material()),
                grey("Ванильный предмет, которым он выглядит."),
                click("ввести в чат"),
                rightClick("взять из руки"))), type -> {
            if (type.isRightClick()) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    player.sendMessage(Component.text("В руке ничего нет",
                            NamedTextColor.RED));
                } else {
                    draft.material(hand.getType().name());
                }
                open(player);
                return;
            }
            context.prompt().ask(player, "Материал предмета",
                    "имя ванильного предмета, например DIAMOND_SWORD; сейчас: "
                            + (draft.material().isEmpty() ? "не задан" : draft.material()),
                    text -> {
                        // Материал проверяется здесь, а не при сохранении:
                        // неизвестный материал выдаётся бумагой, и предмет,
                        // молча ставший бумагой, выглядит поломкой верстака.
                        if (Material.matchMaterial(text) == null) {
                            player.sendMessage(Component.text(
                                    "Такого материала нет: " + text, NamedTextColor.RED));
                        } else {
                            current().material(text);
                        }
                        open(player);
                    },
                    () -> open(player));
        });
    }

    // ------------------------------------------------------------------ как выглядит

    private void looks(ItemDraft draft) {
        List<Rarity> rarities = new ArrayList<>();
        context.content().items().rarities().forEach(rarities::add);
        Rarity current = context.content().items().rarity(draft.rarityId());
        onClick(13, item(Material.NETHER_STAR, yellow("Редкость"), List.of(
                value(current.display() + " (" + current.id() + ")"),
                grey("Даёт цвет имени предмета."),
                click("следующая"),
                rightClick("предыдущая"))), type -> {
            draft.rarityId(cycle(rarities, current, type.isRightClick()).id());
            open(player);
        });

        onClick(14, item(Material.ARMOR_STAND, yellow("Слот"), List.of(
                value(draft.slot().key()),
                grey("Где предмет должен быть,"),
                grey("чтобы его статы считались."),
                grey("any — где угодно в инвентаре."),
                click("следующий"),
                rightClick("предыдущий"))), type -> {
            List<ItemSlot> slots = List.of(ItemSlot.values());
            draft.slot(cycle(slots, draft.slot(), type.isRightClick()));
            open(player);
        });

        put(15, item(draft.unbreakable() ? Material.OBSIDIAN : Material.GLASS,
                        yellow("Прочность"), List.of(
                        value(draft.unbreakable() ? "не ломается" : "ломается как обычно"),
                        click("переключить"))),
                () -> {
                    draft.unbreakable(!draft.unbreakable());
                    open(player);
                });

        List<Component> loreLines = new ArrayList<>();
        if (draft.lore().isEmpty()) {
            loreLines.add(value("пусто"));
        } else {
            draft.lore().forEach(line -> loreLines.add(white("• " + line)));
        }
        loreLines.add(grey("Строк не больше " + ItemDraft.MAX_LORE + "."));
        loreLines.add(click("добавить строку"));
        loreLines.add(rightClick("убрать последнюю"));
        onClick(16, item(Material.WRITABLE_BOOK, yellow("Описание"), loreLines), type -> {
            if (type.isRightClick()) {
                draft.removeLastLore();
                open(player);
                return;
            }
            context.prompt().ask(player, "Строка описания", "добавится в конец",
                    text -> {
                        current().addLore(text);
                        open(player);
                    },
                    () -> open(player));
        });

        onClick(21, item(Material.ITEM_FRAME, yellow("Модель"), List.of(
                value(draft.modelData() == 0 ? "не задана" : String.valueOf(draft.modelData())),
                grey("custom model data для ресурспака."),
                grey("Ноль — не задавать вовсе."),
                click("+1, с Shift +100"),
                rightClick("−1, с Shift −100"))), type -> {
            int step = type.isShiftClick() ? 100 : 1;
            draft.modelData(draft.modelData() + (type.isRightClick() ? -step : step));
            open(player);
        });
    }

    // ------------------------------------------------------------------ кому можно

    private void requirements(ItemDraft draft) {
        List<String> classes = new ArrayList<>(context.content().playerClasses().ids());
        onClick(19, item(Material.ENCHANTED_BOOK, yellow("Только для класса"), List.of(
                value(draft.requireClass() == null ? "любому" : draft.requireClass()),
                grey("Чужому класу статы не считаются,"),
                grey("и предмет скажет, почему."),
                click("следующий класс"),
                rightClick("снять требование"))), type -> {
            if (type.isRightClick() || classes.isEmpty()) {
                draft.requireClass(null);
                open(player);
                return;
            }
            int at = classes.indexOf(draft.requireClass());
            draft.requireClass(at + 1 >= classes.size() ? classes.get(0) : classes.get(at + 1));
            open(player);
        });

        onClick(20, item(Material.EXPERIENCE_BOTTLE, yellow("Нужен уровень"), List.of(
                value(draft.requireLevel() == 0 ? "без требования"
                        : String.valueOf(draft.requireLevel())),
                click("+1, с Shift +10"),
                rightClick("−1, с Shift −10"))), type -> {
            int step = type.isShiftClick() ? 10 : 1;
            draft.requireLevel(draft.requireLevel() + (type.isRightClick() ? -step : step));
            open(player);
        });

        List<Component> statLines = new ArrayList<>();
        if (draft.stats().isEmpty()) {
            statLines.add(value("ни одного"));
        } else {
            draft.stats().forEach((statId, line) -> statLines.add(
                    white(statLine(statId, line))));
        }
        statLines.add(click("открыть список статов"));
        put(23, item(Material.DIAMOND, yellow("Статы"), statLines),
                () -> new ForgeStatsMenu(context, player, 0).open(player));

        List<Component> abilityLines = new ArrayList<>();
        if (draft.abilities().isEmpty()) {
            abilityLines.add(value("ни одного"));
        } else {
            for (ItemAbility ability : draft.abilities()) {
                abilityLines.add(white(ability.skillId() + " — " + ability.trigger().name()
                        .toLowerCase(java.util.Locale.ROOT)));
            }
        }
        abilityLines.add(grey("Умения верстак не правит: их навык"));
        abilityLines.add(grey("обязан быть служебным и без класса,"));
        abilityLines.add(grey("а таких в списке почти нет."));
        abilityLines.add(grey("Уже записанные — сохраняются."));
        put(25, item(Material.BLAZE_ROD, yellow("Умения"), abilityLines));
    }

    // ------------------------------------------------------------------ итог

    private void preview(ItemDraft draft) {
        List<String> problems = draft.problems();
        if (problems.isEmpty()) {
            // Предпросмотр собирается тем же кодом, что и выдача: иначе
            // «в верстаке было иначе» стало бы отдельной жалобой.
            put(31, context.items().build(draft.toDef(), 1));
            return;
        }
        List<Component> lore = new ArrayList<>();
        lore.add(red("Пока не предмет:"));
        problems.forEach(problem -> lore.add(grey("• " + problem)));
        put(31, item(Material.BARRIER, red("Предпросмотр"), lore));
    }

    private void actions(ItemDraft draft) {
        List<Component> state = new ArrayList<>();
        state.add(grey(draft.replacing()
                ? "правится объявленный предмет" : "собирается новый предмет"));
        if (draft.replacing()) {
            state.add(grey("сохранение перезапишет его файл"));
        }
        put(28, item(Material.HOPPER, yellow("Открыть предмет из руки"), List.of(
                        grey("Берёт в черновик то, что в руке,"),
                        grey("если это наш предмет."),
                        click("открыть"))),
                () -> {
                    var def = context.items().defOf(player.getInventory().getItemInMainHand());
                    if (def.isEmpty()) {
                        player.sendMessage(Component.text(
                                "В руке нет предмета RpgCore", NamedTextColor.RED));
                    } else {
                        context.forge().edit(player.getUniqueId(), def.get());
                    }
                    open(player);
                });

        put(34, item(Material.CHEST, yellow("Список предметов"), List.of(
                        grey("Все объявленные предметы."),
                        click("открыть"))),
                () -> new ForgeItemsMenu(context, player, 0).open(player));

        put(37, item(Material.LECTERN, yellow("Состояние черновика"), state));

        put(39, item(Material.STRUCTURE_VOID, red("Очистить черновик"), List.of(
                        grey("Файл не трогает: стирает только"),
                        grey("то, что собрано в верстаке."),
                        click("очистить"))),
                () -> {
                    context.forge().reset(player.getUniqueId());
                    open(player);
                });

        put(41, item(Material.EMERALD_BLOCK, green("Сохранить и выдать"), List.of(
                        grey("Пишет файл, перечитывает контент"),
                        grey("и выдаёт предмет в инвентарь."),
                        grey("Если что-то не так — скажет, что"),
                        grey("именно, и файл не создаст."),
                        click("сохранить"))),
                this::save);

        put(43, item(Material.ARROW, yellow("Закрыть"), List.of(
                        grey("Черновик останется до перезапуска."))),
                player::closeInventory);
    }

    /**
     * Сохранение.
     *
     * <p>Рецепты перерегистрируются здесь же, как в {@code /rpg reload}: иначе
     * верстак остался бы с прежними, и предмет, участвующий в крафте, вёл бы себя
     * по-разному в зависимости от того, перезапускали сервер или нет.
     */
    private void save() {
        ItemForge.Result result = context.forge().save(player.getUniqueId());
        if (!result.ok()) {
            player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
            result.problems().forEach(problem ->
                    player.sendMessage(Component.text("  " + problem, NamedTextColor.GRAY)));
            open(player);
            return;
        }
        context.recipes().reload(context.content().recipes());

        player.sendMessage(Component.text(result.message(), NamedTextColor.GREEN));
        player.sendMessage(Component.text("  " + result.file(), NamedTextColor.DARK_GRAY));
        result.problems().forEach(problem ->
                player.sendMessage(Component.text("  " + problem, NamedTextColor.GOLD)));

        String id = context.forge().draft(player.getUniqueId()).id();
        context.content().items().find(id).ifPresent(def -> {
            var leftover = player.getInventory().addItem(context.items().build(def, 1));
            if (!leftover.isEmpty()) {
                player.sendMessage(Component.text("В инвентаре нет места",
                        NamedTextColor.GRAY));
            }
            // Снаряжение сверяется сразу: предмет мог попасть в руку, и ждать
            // секунды до пересчёта статов незачем.
            context.equipment().apply(player);
        });
        open(player);
    }

    // ------------------------------------------------------------------ мелочи

    /**
     * Черновик игрока сейчас.
     *
     * <p>Нужен ответам из чата: между вопросом и ответом игрок мог нажать
     * «очистить черновик», и запомненный объект тогда никуда не ведёт — правка
     * ушла бы в никуда, а экран показал бы прежнее.
     */
    private ItemDraft current() {
        return context.forge().draft(player.getUniqueId());
    }

    static String statLine(String statId, ItemDef.ItemStatLine line) {
        String suffix = switch (line.op()) {
            case FLAT -> "";
            case PERCENT -> "%";
            case MULT -> "×";
        };
        String sign = line.op() == ru.projectst.rpgcore.stat.StatOp.MULT ? ""
                : (line.value() >= 0 ? "+" : "");
        return line.op() == ru.projectst.rpgcore.stat.StatOp.MULT
                ? suffix + number(line.value()) + " " + statId
                : sign + number(line.value()) + suffix + " " + statId;
    }

    /** Следующий или предыдущий по кругу; пустой список — то же значение. */
    private static <T> T cycle(List<T> all, T current, boolean back) {
        if (all.isEmpty()) {
            return current;
        }
        int at = all.indexOf(current);
        int next = at < 0 ? 0 : (at + (back ? -1 : 1) + all.size()) % all.size();
        return all.get(next);
    }

    static Component value(String text) {
        return grey("Сейчас: ").append(white(text));
    }

    static Component click(String what) {
        return aqua("Щелчок: ").append(white(what));
    }

    static Component rightClick(String what) {
        return aqua("Правый: ").append(white(what));
    }
}
