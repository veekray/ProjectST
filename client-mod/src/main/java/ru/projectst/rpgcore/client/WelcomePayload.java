package ru.projectst.rpgcore.client;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.projectst.rpgcore.net.Protocol;

/** Ответ сервера на рукопожатие. */
public record WelcomePayload(byte[] data) implements CustomPacketPayload {

    public static final Type<WelcomePayload> TYPE = new Type<>(
            ResourceLocation.parse(Protocol.CHANNEL_WELCOME));

    public static final StreamCodec<FriendlyByteBuf, WelcomePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeBytes(payload.data()),
                    buffer -> {
                        byte[] data = new byte[buffer.readableBytes()];
                        buffer.readBytes(data);
                        return new WelcomePayload(data);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
