package ru.projectst.rpgcore.client;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.projectst.rpgcore.net.Protocol;

/**
 * Закладка книги героя в чужом окне инвентаря.
 *
 * <p>Мод <code>rpgui</code> подменяет ванильный инвентарь своим окном с
 * закладками. Книга героя должна открываться оттуда же: держать половину
 * персонажа в инвентаре, а половину за отдельной клавишей — значит заставлять
 * игрока помнить, где что лежит.
 *
 * <p>Встраивание сделано отражением, и это осознанный выбор, а не нехватка
 * терпения. Своего способа добавить закладку <code>rpgui</code> не даёт: список
 * закладок у него приватный, а исходников у нас нет. Зависимость на его сборку
 * означала бы, что наш мод не собирается без чужого артефакта и не запускается
 * без него у игрока.
 *
 * <p>Поэтому здесь нет ни одной ссылки на его классы: интерфейс закладки
 * реализован {@link Proxy}, вызовы разбираются <b>по имени метода</b>, а не по
 * сигнатуре. Его правка, переименование полей или другой размер окна ничего не
 * ломают — в худшем случае закладка не появится, о чём будет одна строка в
 * журнале. Клавиша книги при этом работает по-прежнему: встраивание — удобство,
 * а не единственный путь.
 */
@EventBusSubscriber(modid = RpgCoreClient.MOD_ID, value = Dist.CLIENT)
public final class RpgUiBridge {

    private static final Logger LOG = LoggerFactory.getLogger("rpgcore");

    /** Окно, в которое встраиваемся. Имя, а не класс: класса может не быть. */
    private static final String SCREEN_CLASS =
            "com.equipment.rpgui.client.screen.RpgInventoryScreen";
    private static final String TAB_CLASS = "com.equipment.rpgui.client.screen.RpgTab";

    /** Признак нашей закладки: по нему она узнаётся и не добавляется дважды. */
    private static final String TAB_ID = "rpgcore_book";

    /** Жалуемся один раз: строка в каждом открытии инвентаря — это спам. */
    private static boolean complained;

    private RpgUiBridge() {
    }

    /**
     * Встраиваемся после разметки окна.
     *
     * <p>Именно после: список закладок чужое окно наполняет в своём
     * {@code init()}, и раньше встраиваться не во что. Разметка повторяется при
     * каждом изменении размера экрана, поэтому перед вставкой проверяем, не
     * стоит ли закладка уже на месте.
     */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!screen.getClass().getName().equals(SCREEN_CLASS)) {
            return;
        }
        try {
            attach(screen);
        } catch (Throwable error) {
            // Ловим Throwable, а не Exception: отражение в чужой класс может
            // кончиться и ошибкой связывания. Инвентарь игрока из-за нашей
            // закладки падать не должен ни при каком исходе.
            if (!complained) {
                complained = true;
                LOG.warn("RpgCore: закладка книги в окне rpgui не встала, "
                        + "книга открывается своей клавишей", error);
            }
        }
    }

    private static void attach(Screen screen) throws ReflectiveOperationException {
        ClassLoader loader = screen.getClass().getClassLoader();
        Class<?> tabType = Class.forName(TAB_CLASS, false, loader);

        Field field = tabsField(screen.getClass());
        field.setAccessible(true);
        Object value = field.get(screen);
        if (!(value instanceof List<?> raw)) {
            return;
        }
        for (Object tab : raw) {
            if (ours(tab)) {
                return;
            }
        }

        Object proxy = Proxy.newProxyInstance(loader, new Class<?>[] {tabType},
                new Handler(screen));
        @SuppressWarnings("unchecked")
        List<Object> tabs = (List<Object>) raw;
        tabs.add(proxy);
    }

    /** Поле со списком закладок: единственное поле типа List в окне. */
    private static Field tabsField(Class<?> screenClass) throws NoSuchFieldException {
        for (Field field : screenClass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    && List.class.isAssignableFrom(field.getType())) {
                return field;
            }
        }
        throw new NoSuchFieldException("в " + screenClass.getName() + " нет списка закладок");
    }

    private static boolean ours(Object tab) {
        return tab != null && Proxy.isProxyClass(tab.getClass())
                && Proxy.getInvocationHandler(tab) instanceof Handler;
    }

    /**
     * Ответы на вызовы чужого интерфейса.
     *
     * <p>Разбор по имени метода, а не по сигнатуре: так закладка переживает
     * правку числа и порядка аргументов в чужом коде. Незнакомый метод получает
     * пустое значение своего типа — чужое окно от этого работает дальше, а не
     * падает.
     */
    private record Handler(Screen inventory) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "id" -> TAB_ID;
                case "title" -> Component.literal("Книга героя");
                case "icon" -> new ItemStack(Items.ENCHANTED_BOOK);
                case "onSelected" -> {
                    open();
                    yield null;
                }
                case "equals" -> proxy == (args == null ? null : args[0]);
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "RpgCore: книга героя";
                default -> blank(method.getReturnType());
            };
        }

        /**
         * Открываем книгу вместо того, чтобы рисовать внутри чужого окна.
         *
         * <p>Рисовать внутри значило бы верстать по его размерам, а они — его
         * дело: он их меняет, когда считает нужным, и наша страница разъехалась
         * бы молча, у игрока. Книга поэтому своя, а возврат по Esc ведёт обратно
         * в инвентарь, и переход читается как перелистывание, а не как уход.
         */
        private void open() {
            // Данные меню нужно попросить заново: они приходят по запросу, а не
            // постоянно, и за время с прошлого открытия уровень мог измениться.
            ActionPayload.send(Protocol.Action.REFRESH_MENU);

            // Снимаем выбор закладки в чужом окне. Иначе возврат по Esc снова
            // позовёт onSelected, и книга открылась бы сама — выйти стало бы
            // нельзя. Если снять не вышло, возвращаться просто некуда: закрытие
            // книги закроет и инвентарь, что хуже, но не ломает ничего.
            boolean released = releaseTab();
            Minecraft.getInstance().setScreen(
                    new CharacterScreen(released ? inventory : null));
        }

        private boolean releaseTab() {
            try {
                Field current = inventory.getClass().getDeclaredField("current");
                current.setAccessible(true);
                current.setInt(inventory, 0);
                return true;
            } catch (ReflectiveOperationException | RuntimeException error) {
                return false;
            }
        }

        private static Object blank(Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == void.class) {
                return null;
            }
            if (type == double.class) {
                return 0d;
            }
            if (type == float.class) {
                return 0f;
            }
            if (type == long.class) {
                return 0L;
            }
            return 0;
        }
    }
}
