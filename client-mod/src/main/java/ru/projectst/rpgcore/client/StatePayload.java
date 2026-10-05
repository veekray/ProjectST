package ru.projectst.rpgcore.client;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.projectst.rpgcore.net.Protocol;

/** Состояние игрока от сервера. */
public record StatePayload(byte[] data) implements CustomPacketPayload {

    public static final Type<StatePayload> TYPE = new Type<>(
            ResourceLocation.parse(Protocol.CHANNEL_STATE));

    public static final StreamCodec<FriendlyByteBuf, StatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeBytes(payload.data()),
                    buffer -> {
                        byte[] data = new byte[buffer.readableBytes()];
                        buffer.readBytes(data);
                        return new StatePayload(data);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
