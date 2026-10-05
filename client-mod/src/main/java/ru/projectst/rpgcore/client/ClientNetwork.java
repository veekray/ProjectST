package ru.projectst.rpgcore.client;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ru.projectst.rpgcore.net.ClientState;
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
    private static volatile boolean accepted;

    private ClientNetwork() {
    }

    public static Optional<ClientState> state() {
        return accepted ? Optional.ofNullable(state) : Optional.empty();
    }

    /** Здороваемся при входе: до рукопожатия сервер ничего не присылает. */
    @SubscribeEvent
    public static void onJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        state = null;
        accepted = false;
        PacketDistributor.sendToServer(HelloPayload.of());
    }

    @SubscribeEvent
    public static void onQuit(ClientPlayerNetworkEvent.LoggingOut event) {
        state = null;
        accepted = false;
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
