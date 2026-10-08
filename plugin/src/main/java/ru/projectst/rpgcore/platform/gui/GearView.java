package ru.projectst.rpgcore.platform.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import ru.projectst.rpgcore.item.GearCell;
import ru.projectst.rpgcore.net.Protocol;
import ru.projectst.rpgcore.platform.GearSlots;

/**
 * Окно снаряжения: тринадцать ячеек и инвентарь игрока под ними.
 *
 * <p>Это настоящий контейнер, а не окно с иконками: вещи берутся курсором и
 * перетаскиваются, как в сундуке. Мод узнаёт его по ключу заголовка
 * ({@link Protocol#GEAR_TITLE}) и рисует своим экраном с раскладкой брони и
 * колец; сам контейнер — обычные два ряда по девять.
 *
 * <p><b>Ячейки окна — только витрина.</b> Где лежит вещь на самом деле, знает
 * {@link GearSlots}: броня — на игроке, кольца — в его данных. Поэтому каждый
 * щелчок по ячейке отменяется, а перенос делает сервер сам: читает настоящее
 * содержимое ячейки, проверяет вещь на курсоре, кладёт и забирает, и только
 * потом перерисовывает витрину. Ванилле здесь доверить нечего: она не знает, что
 * кольцо — не шлем, и записала бы в витрину то, чего нет нигде.
 *
 * <p>Так у вещи в каждый момент ровно одно место: курсор, инвентарь или
 * ячейка. Закрытие окна ничего не копирует обратно — копировать нечего, витрина
 * не хранит ничего своего, — поэтому закрытие с вещью на курсоре, смерть и
 * выход с сервера не умеют ни потерять вещь, ни удвоить её.
 */
public final class GearView implements InventoryHolder {

    private static final GearCell[] CELLS = GearCell.values();

    private final Player player;
    private final GearSlots gear;
    private final Runnable changed;
    private final Consumer<String> log;
    private final Inventory inventory;

    /** Что нарисовано в витрине: с этим сверяется, не попало ли туда чужое. */
    private final ItemStack[] drawn = new ItemStack[Protocol.GEAR_SIZE];

    /**
     * @param changed что сделать после переноса: пересчитать статы и обновить
     *                данные мода — снятый шлем обязан перестать считаться при
     *                том же щелчке, а не через секунду по таймеру
     * @param log     куда сказать о вещи, попавшей в витрину мимо сервера
     */
    public GearView(Player player, GearSlots gear, Runnable changed, Consumer<String> log) {
        this.player = player;
        this.gear = gear;
        this.changed = changed;
        this.log = log;
        // Запасной текст — для того, у кого мода нет: ключ он показал бы как есть.
        this.inventory = Bukkit.createInventory(this, Protocol.GEAR_SIZE,
                Component.translatable(Protocol.GEAR_TITLE, "Снаряжение"));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID owner() {
        return player.getUniqueId();
    }

    public void open() {
        draw();
        player.openInventory(inventory);
    }

    /**
     * Перерисовать витрину по настоящему положению вещей.
     *
     * <p>Снаряжение меняется и мимо окна: прочность шлема тратится в бою, вторая
     * рука меняется клавишей F. Витрина, показывающая прежнее, — та же тихая
     * ложь, что и экран с прежними числами.
     */
    public void refresh() {
        reclaim();
        draw();
    }

    /** Окно закрыли: вернуть владельцу то, что попало в витрину мимо сервера. */
    void closed() {
        reclaim();
    }

    // ------------------------------------------------------------------ щелчки

    void click(InventoryClickEvent event) {
        int raw = event.getRawSlot();
        if (raw < 0) {
            // Мимо окна: выбросить то, что на курсоре, — ванильное дело, ячеек
            // оно не касается.
            return;
        }
        if (raw < inventory.getSize()) {
            event.setCancelled(true);
            if (raw >= CELLS.length) {
                return;
            }
            GearCell cell = CELLS[raw];
            boolean done = switch (event.getClick()) {
                case LEFT -> clickCell(cell, false);
                case RIGHT -> clickCell(cell, true);
                case SHIFT_LEFT, SHIFT_RIGHT -> takeOff(cell);
                case NUMBER_KEY -> swapWithHotbar(cell, event.getHotbarButton());
                // Выбросить из ячейки, двойной щелчок, клавиша F, копия в
                // творческом: всё это ванилла сделала бы с витриной, а не с
                // настоящей вещью. Отказ молча: игрок ничего не просил надеть.
                default -> false;
            };
            finish(done);
            return;
        }

        // Свой инвентарь. Обычные щелчки — ванильные: они не касаются ячеек.
        if (event.getClick().isShiftClick()) {
            // Ванилла переложила бы вещь в витрину как в сундук — то есть
            // никуда. Надевает сервер, в подходящую ячейку.
            event.setCancelled(true);
            if (event.getClickedInventory() instanceof PlayerInventory own) {
                finish(putOn(own, event.getSlot()));
            }
            return;
        }
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                && showsSimilar(event.getCursor())) {
            // Двойной щелчок собирает одинаковые вещи со всего окна, в том
            // числе из витрины, — и собрал бы копию кольца, которое лежит в
            // данных игрока.
            event.setCancelled(true);
        }
    }

    void drag(InventoryDragEvent event) {
        // Мод тянуть по ячейкам не даёт: у него перетаскивание на ячейку — это
        // обычный щелчок. Протяжка сюда — от клиента без мода или от
        // подменённого, и раскладывать стопку по витрине ей нечего.
        for (int raw : event.getRawSlots()) {
            if (raw < inventory.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Щелчок по ячейке — то же, что в любом ванильном окне.
     *
     * <p>Левой: взять всё, положить сколько влезет, поменять местами. Правой:
     * взять половину, положить одну. Разница с ванильным окном одна — что
     * влезает, решает ячейка: одно кольцо, одна пара перчаток, стопка во
     * второй руке.
     */
    private boolean clickCell(GearCell cell, boolean right) {
        if (locked(cell)) {
            return false;
        }
        ItemStack cursor = player.getItemOnCursor();
        ItemStack inCell = gear.get(player, cell).orElse(null);

        if (empty(cursor)) {
            if (inCell == null || stuck(cell, inCell)) {
                return false;
            }
            int take = right ? (inCell.getAmount() + 1) / 2 : inCell.getAmount();
            gear.set(player, cell, withAmount(inCell, inCell.getAmount() - take));
            player.setItemOnCursor(withAmount(inCell, take));
            return true;
        }

        String refusal = gear.refusal(cell, cursor);
        if (!refusal.isEmpty()) {
            refuse(refusal);
            return false;
        }
        int limit = gear.limit(cell, cursor);

        if (inCell == null || inCell.isSimilar(cursor)) {
            int already = inCell == null ? 0 : inCell.getAmount();
            int put = Math.min(limit - already, right ? 1 : cursor.getAmount());
            if (put <= 0) {
                return false;
            }
            gear.set(player, cell, withAmount(cursor, already + put));
            player.setItemOnCursor(withAmount(cursor, cursor.getAmount() - put));
            return true;
        }

        // Обмен. Только если на курсоре столько, сколько ячейка примет: иначе
        // часть стопки осталась бы висеть между курсором и ячейкой.
        if (stuck(cell, inCell)) {
            return false;
        }
        if (cursor.getAmount() > limit) {
            refuse("в ячейку «" + cell.display() + "» помещается одна вещь:"
                    + " возьмите на курсор одну");
            return false;
        }
        gear.set(player, cell, cursor.clone());
        player.setItemOnCursor(inCell);
        return true;
    }

    /**
     * Shift-щелчок по ячейке: снять в инвентарь.
     *
     * <p>Ячейка очищается ровно на столько, сколько поместилось: снятое не
     * влезшее — это вещь, которая иначе пропала бы.
     */
    private boolean takeOff(GearCell cell) {
        if (locked(cell)) {
            return false;
        }
        ItemStack inCell = gear.get(player, cell).orElse(null);
        if (inCell == null || stuck(cell, inCell)) {
            return false;
        }
        Map<Integer, ItemStack> rest = player.getInventory().addItem(inCell.clone());
        if (rest.isEmpty()) {
            gear.set(player, cell, null);
            return true;
        }
        ItemStack left = rest.values().iterator().next();
        if (left.getAmount() >= inCell.getAmount()) {
            refuse("в инвентаре нет места");
            return false;
        }
        gear.set(player, cell, left);
        refuse("снято не всё: в инвентаре кончилось место");
        return true;
    }

    /** Клавиша с цифрой над ячейкой: обмен с этой ячейкой хотбара. */
    private boolean swapWithHotbar(GearCell cell, int button) {
        if (button < 0 || button > 8 || locked(cell)) {
            return false;
        }
        PlayerInventory own = player.getInventory();
        ItemStack hotbar = own.getItem(button);
        ItemStack inCell = gear.get(player, cell).orElse(null);
        if (inCell != null && stuck(cell, inCell)) {
            return false;
        }

        if (empty(hotbar)) {
            if (inCell == null) {
                return false;
            }
            own.setItem(button, inCell);
            gear.set(player, cell, null);
            return true;
        }
        String refusal = gear.refusal(cell, hotbar);
        if (!refusal.isEmpty()) {
            refuse(refusal);
            return false;
        }
        int limit = gear.limit(cell, hotbar);
        if (hotbar.getAmount() > limit) {
            if (inCell != null) {
                refuse("в ячейку «" + cell.display() + "» помещается одна вещь,"
                        + " а в хотбаре стопка");
                return false;
            }
            gear.set(player, cell, withAmount(hotbar, limit));
            own.setItem(button, withAmount(hotbar, hotbar.getAmount() - limit));
            return true;
        }
        own.setItem(button, inCell);
        gear.set(player, cell, hotbar.clone());
        return true;
    }

    /**
     * Shift-щелчок по вещи в инвентаре: надеть в первую подходящую свободную
     * ячейку.
     *
     * <p>Занятую ячейку не подменяем: снять одно и надеть другое одним нажатием
     * значило бы, что снятое уходит неизвестно куда, если в инвентаре нет места.
     */
    private boolean putOn(PlayerInventory own, int slot) {
        ItemStack stack = own.getItem(slot);
        if (empty(stack)) {
            return false;
        }
        List<GearCell> places = gear.placesFor(stack);
        if (places.isEmpty()) {
            refuse(gear.nowhere(stack));
            return false;
        }
        GearCell free = null;
        for (GearCell cell : places) {
            if (gear.get(player, cell).isEmpty() && !gear.unreadable(owner(), cell)) {
                free = cell;
                break;
            }
        }
        if (free == null) {
            refuse(places.size() == 1
                    ? "ячейка «" + places.get(0).display() + "» занята: сначала снимите то,"
                            + " что в ней"
                    : "все подходящие ячейки заняты: сначала снимите что-нибудь");
            return false;
        }
        int put = Math.min(gear.limit(free, stack), stack.getAmount());
        own.setItem(slot, withAmount(stack, stack.getAmount() - put));
        gear.set(player, free, withAmount(stack, put));
        return true;
    }

    // ------------------------------------------------------------------ витрина

    private void finish(boolean done) {
        if (done) {
            changed.run();
        }
        // Перерисовать и после отказа: клиент уже показал вещь в ячейке, и
        // витрина должна вернуть ему правду.
        refresh();
    }

    private void draw() {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack shown = i < CELLS.length ? gear.get(player, CELLS[i]).orElse(null) : null;
            inventory.setItem(i, shown);
            // Запоминаем то, что легло в окно, а не то, что туда клали: ядро
            // может записать вещь чуть иначе, и сверка с исходником приняла бы
            // эту разницу за чужую вещь — и «вернула» бы владельцу копию.
            ItemStack stored = inventory.getItem(i);
            drawn[i] = empty(stored) ? null : stored.clone();
        }
    }

    /**
     * Вещи, оказавшиеся в витрине мимо сервера, — обратно владельцу.
     *
     * <p>Щелчки по витрине отменяются все, поэтому сюда ничего попадать не
     * должно. Но если какой-то путь ядра положит вещь в витрину без события,
     * она пропала бы вместе с окном. Вернуть её дешевле, чем потом искать.
     */
    private void reclaim() {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack shown = inventory.getItem(i);
            ItemStack was = drawn[i];
            if (empty(shown) ? empty(was) : shown.equals(was)) {
                continue;
            }
            ItemStack foreign;
            if (empty(was)) {
                foreign = shown;
            } else if (!empty(shown) && shown.isSimilar(was)) {
                foreign = shown.getAmount() > was.getAmount()
                        ? withAmount(shown, shown.getAmount() - was.getAmount()) : null;
            } else {
                foreign = empty(shown) ? null : shown;
            }
            String where = i < CELLS.length ? CELLS[i].key() : String.valueOf(i);
            log.accept("окно снаряжения " + player.getName() + ": ячейка " + where
                    + " изменилась мимо сервера"
                    + (foreign == null ? "" : ", вещь возвращена владельцу"));
            if (foreign != null) {
                player.getInventory().addItem(foreign.clone()).values().forEach(
                        rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            }
            inventory.setItem(i, was == null ? null : was.clone());
        }
    }

    private boolean showsSimilar(ItemStack stack) {
        if (empty(stack)) {
            return false;
        }
        for (int i = 0; i < CELLS.length; i++) {
            ItemStack shown = inventory.getItem(i);
            if (!empty(shown) && shown.isSimilar(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Вещь в ячейке не снимается — и игрок узнаёт почему. */
    private boolean stuck(GearCell cell, ItemStack inCell) {
        String reason = gear.stuck(player, cell, inCell);
        if (reason.isEmpty()) {
            return false;
        }
        refuse(reason);
        return true;
    }

    /** Ячейка, запись которой не читается: трогать нельзя — затрётся вещь. */
    private boolean locked(GearCell cell) {
        if (!gear.unreadable(owner(), cell)) {
            return false;
        }
        refuse("запись в ячейке «" + cell.display() + "» не читается: сообщите"
                + " администратору, вещь не потеряна");
        return true;
    }

    private void refuse(String reason) {
        String text = reason.isEmpty() ? reason
                : Character.toUpperCase(reason.charAt(0)) + reason.substring(1);
        player.sendActionBar(Component.text(text, NamedTextColor.RED));
    }

    private static boolean empty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    /** Копия стопки с другим числом; ноль и меньше — пусто. */
    private static ItemStack withAmount(ItemStack stack, int amount) {
        if (amount <= 0) {
            return null;
        }
        ItemStack copy = stack.clone();
        copy.setAmount(amount);
        return copy;
    }
}
