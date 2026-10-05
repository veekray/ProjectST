package ru.projectst.rpgcore.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Точка входа мода.
 *
 * <p>Мод живёт только на клиенте и только смотрит. Он регистрирует три полезных
 * груза, здоровается при входе и рисует то, что пришло. Ни одного действия он не
 * отправляет: нажатия перехватывает сервер, и поэтому игрок без мода играет
 * полностью.
 */
@Mod(value = RpgCoreClient.MOD_ID, dist = Dist.CLIENT)
public final class RpgCoreClient {

    public static final String MOD_ID = "rpgcore";

    /** Версия мода: уходит в рукопожатии, чтобы сервер мог назвать её в логе. */
    public static final String MOD_VERSION = "1.0.0";

    public RpgCoreClient(IEventBus modBus) {
        modBus.addListener(RpgCoreClient::registerPayloads);
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
        registrar.playToServer(HelloPayload.TYPE, HelloPayload.STREAM_CODEC,
                (payload, context) -> {
                    // Сервер — это плагин, он читает груз сам. Обработчик здесь
                    // нужен только потому, что регистрация требует его наличия.
                });
    }
}
