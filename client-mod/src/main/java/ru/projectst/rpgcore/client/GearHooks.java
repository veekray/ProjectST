package ru.projectst.rpgcore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import ru.projectst.rpgcore.net.Protocol;

/**
 * Окно снаряжения вместо сундука.
 *
 * <p>Сервер открывает обычный контейнер на два ряда, и ванилла уже построила под
 * него сундук. Здесь, до того как сундук показан, он подменяется окном
 * {@link GearScreen} с меню {@link GearMenu}: номер окна тот же, порядок ячеек
 * тот же, поэтому сервер ничего не замечает.
 *
 * <p>Окно узнаётся по ключу заголовка, а не по тексту: текст правят, и окно,
 * узнаваемое по тексту, однажды открылось бы сундуком — без единой ошибки.
 */
@EventBusSubscriber(modid = RpgCoreClient.MOD_ID, value = Dist.CLIENT)
public final class GearHooks {

    private GearHooks() {
    }

    @SubscribeEvent
    public static void onOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof ContainerScreen chest)
                || chest.getMenu().getRowCount() * 9 != Protocol.GEAR_SIZE
                || !isGear(chest.getTitle())) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        // Откуда пришли, туда и вернёмся по Esc: из книги — туда, откуда открыли
        // книгу; из окна снаряжения (сервер открыл его заново) — туда же, куда
        // вело оно.
        Screen current = event.getCurrentScreen();
        Screen parent = current instanceof CharacterScreen book ? book.parent()
                : current instanceof GearScreen gear ? gear.parent() : null;

        GearMenu menu = new GearMenu(chest.getMenu().containerId, client.player.getInventory());
        // Сундук уже записан текущим меню игрока — содержимое, которое сервер
        // пришлёт следом, должно лечь в наше.
        client.player.containerMenu = menu;
        event.setNewScreen(new GearScreen(menu, client.player.getInventory(), chest.getTitle(),
                parent));
    }

    /**
     * Отказы сервера — в окно.
     *
     * <p>Сервер говорит их строкой над хотбаром, а открытое окно её почти
     * закрывает. Перехватываем только показ: строка остаётся и на своём месте.
     */
    @SubscribeEvent
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        if (event.isOverlay() && Minecraft.getInstance().screen instanceof GearScreen screen) {
            screen.notice(event.getMessage());
        }
    }

    private static boolean isGear(Component title) {
        return title.getContents() instanceof TranslatableContents translatable
                && Protocol.GEAR_TITLE.equals(translatable.getKey());
    }
}
