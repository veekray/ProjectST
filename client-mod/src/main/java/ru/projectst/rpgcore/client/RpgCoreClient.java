package ru.projectst.rpgcore.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Точка входа мода.
 *
 * <p>Мод живёт только на клиенте. Он здоровается при входе, рисует то, что
 * пришло, и просит сервер о том, что нажал игрок. Просит — не делает: каждое
 * действие идёт через те же службы плагина, что и команда в чате, со всеми их
 * проверками и отказами.
 */
@Mod(value = RpgCoreClient.MOD_ID, dist = Dist.CLIENT)
public final class RpgCoreClient {

    public static final String MOD_ID = "rpgcore";

    /** Версия мода: уходит в рукопожатии, чтобы сервер мог назвать её в логе. */
    public static final String MOD_VERSION = "1.0.0";

    public RpgCoreClient(IEventBus modBus) {
        modBus.addListener(RpgCoreClient::registerPayloads);
        // Раскладка читается при запуске: до первого кадра она уже нужна.
        HudLayout.load();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Версия канала — своя, в первом байте каждого сообщения. Версия
        // регистрации NeoForge к ней не относится и специально не совпадает:
        // совместимость формата решает наш байт, а не чужой счётчик.
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(WelcomePayload.TYPE, WelcomePayload.STREAM_CODEC,
                ClientNetwork::onWelcome);
        registrar.playToClient(StatePayload.TYPE, StatePayload.STREAM_CODEC,
                ClientNetwork::onState);
        registrar.playToClient(MenuPayload.TYPE, MenuPayload.STREAM_CODEC,
                ClientNetwork::onMenu);
        registrar.playToClient(FxPayload.TYPE, FxPayload.STREAM_CODEC,
                ClientNetwork::onFx);

        // Грузы к серверу. Обработчик здесь пустой: сервер — это плагин, он
        // читает их сам, а регистрация требует обработчик с обеих сторон.
        registrar.playToServer(HelloPayload.TYPE, HelloPayload.STREAM_CODEC,
                (payload, context) -> { });
        registrar.playToServer(ActionPayload.TYPE, ActionPayload.STREAM_CODEC,
                (payload, context) -> { });
    }
}
