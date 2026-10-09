package ru.projectst.rpgcore.client;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ru.projectst.rpgcore.net.ClientState;
import ru.projectst.rpgcore.net.MenuData;
import ru.projectst.rpgcore.net.Protocol;
import ru.projectst.rpgcore.net.StateCodec;

/**
 * Приём состояния и рукопожатие.
 *
 * <p>Состояние держится в одном месте и переписывается целиком. Склеивать
 * частичные обновления означало бы хранить на клиенте то, чего сервер уже не
 * думает, — а это и есть второй источник правды, от которого проект уходит.
 */
@EventBusSubscriber(modid = RpgCoreClient.MOD_ID, value = Dist.CLIENT)
public final class ClientNetwork {

    private static volatile ClientState state;
    private static volatile MenuData menu;
    private static volatile boolean accepted;

    /**
     * Предлагали ли уже выбрать класс в этот заход.
     *
     * <p>Один раз за вход, а не каждый раз, когда приходят данные: экран,
     * открывающийся сам посреди игры, — это не забота, а помеха. Закрыл —
     * значит не сейчас.
     */
    private static volatile boolean classOffered;

    private ClientNetwork() {
    }

    public static Optional<ClientState> state() {
        return accepted ? Optional.ofNullable(state) : Optional.empty();
    }

    public static Optional<MenuData> menu() {
        return accepted ? Optional.ofNullable(menu) : Optional.empty();
    }

    /** Здороваемся при входе: до рукопожатия сервер ничего не присылает. */
    @SubscribeEvent
    public static void onJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        state = null;
        menu = null;
        accepted = false;
        classOffered = false;
        PacketDistributor.sendToServer(HelloPayload.of());
    }

    @SubscribeEvent
    public static void onQuit(ClientPlayerNetworkEvent.LoggingOut event) {
        state = null;
        menu = null;
        accepted = false;
        // Зоны и снаряды прошлого сервера не должны дорисовываться на следующем.
        FxEffects.clear();
    }

    static void onWelcome(WelcomePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            StateCodec.Welcome welcome;
            try {
                welcome = StateCodec.readWelcome(payload.data());
            } catch (RuntimeException e) {
                accepted = false;
                return;
            }
            accepted = welcome.outcome() == Protocol.Handshake.ACCEPTED;
            if (!accepted) {
                // Говорим в чат сами: сервер мог ответить моду, который этот
                // ответ ещё не умеет показать, и тогда игрок не узнает ничего.
                Minecraft client = Minecraft.getInstance();
                if (client.player != null) {
                    client.player.displayClientMessage(Component.literal(
                            welcome.outcome() == Protocol.Handshake.MOD_TOO_OLD
                                    ? "RpgCore: мод старее сервера, обновите мод"
                                    : "RpgCore: мод новее сервера, обновите плагин"), false);
                }
            }
        });
    }

    static void onMenu(MenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                menu = StateCodec.readMenu(payload.data());
                offerClassIfNeeded();
            } catch (RuntimeException e) {
                // Как и с состоянием: лучше пустой экран, чем экран с чужими
                // числами. Пустой виден сразу, чужие — нет.
                menu = null;
            }
        });
    }

    /**
     * Если класса нет — сразу показываем выбор.
     *
     * <p>Мод узнаёт класс сам, из тех же данных, что рисует: спрашивать игрока
     * «а кто ты» незачем. А вот когда класса нет, выбор нужен сразу — иначе
     * новый игрок стоит с пустым экраном и не знает, что от него хотят.
     */
    private static void offerClassIfNeeded() {
        MenuData current = menu;
        if (current == null || !current.classId().isEmpty() || classOffered) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Screen open = client.screen;
        // Не перебиваем ни чат, ни чужое окно: открываем только на чистом экране.
        if (open != null || client.player == null) {
            return;
        }
        classOffered = true;
        client.setScreen(new CharacterScreen());
    }

    /**
     * Видимые события навыков.
     *
     * <p>До рукопожатия не читаются: версия формата ещё не подтверждена, а
     * чужие байты, прочитанные наугад, — это кольцо не того радиуса.
     */
    static void onFx(FxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!accepted) {
                return;
            }
            try {
                FxEffects.accept(StateCodec.readFx(payload.data()));
            } catch (RuntimeException e) {
                // Испорченная пачка теряется целиком: лучше не показать вспышку,
                // чем показать её не там.
            }
        });
    }

    static void onState(StatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                state = StateCodec.readState(payload.data());
            } catch (RuntimeException e) {
                // Чужая версия или испорченный пакет: перестаём рисовать, а не
                // рисуем мусор. Полоса, показывающая неверное число, хуже
                // отсутствующей полосы.
                state = null;
            }
        });
    }
}
