package ru.projectst.rpgcore.client;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.projectst.rpgcore.net.Protocol;

/** Видимые события навыков от сервера: границы, зоны, снаряды, вспышки. */
public record FxPayload(byte[] data) implements CustomPacketPayload {

    public static final Type<FxPayload> TYPE = new Type<>(
            ResourceLocation.parse(Protocol.CHANNEL_FX));

    public static final StreamCodec<FriendlyByteBuf, FxPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeBytes(payload.data()),
                    buffer -> {
                        byte[] data = new byte[buffer.readableBytes()];
                        buffer.readBytes(data);
                        return new FxPayload(data);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
