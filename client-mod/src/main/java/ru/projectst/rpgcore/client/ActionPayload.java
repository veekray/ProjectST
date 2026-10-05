package ru.projectst.rpgcore.client;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.projectst.rpgcore.net.Protocol;
import ru.projectst.rpgcore.net.StateCodec;

/**
 * Просьба к серверу.
 *
 * <p>Именно просьба: решает сервер. Клиент не знает ни про очки, ни про уровни,
 * ни про ману — он нажимает, а отказ приходит словами от тех же служб, что
 * отвечают на команду в чате. Поэтому подменённый мод может попросить ровно то,
 * что игрок может набрать руками.
 */
public record ActionPayload(byte[] data) implements CustomPacketPayload {

    public static final Type<ActionPayload> TYPE = new Type<>(
            ResourceLocation.parse(Protocol.CHANNEL_ACTION));

    public static final StreamCodec<FriendlyByteBuf, ActionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeBytes(payload.data()),
                    buffer -> {
                        byte[] data = new byte[buffer.readableBytes()];
                        buffer.readBytes(data);
                        return new ActionPayload(data);
                    });

    /** Отправляет действие серверу. */
    public static void send(Protocol.Action action, int number, String id) {
        PacketDistributor.sendToServer(
                new ActionPayload(StateCodec.writeAction(action, number, id)));
    }

    public static void send(Protocol.Action action) {
        send(action, 0, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
