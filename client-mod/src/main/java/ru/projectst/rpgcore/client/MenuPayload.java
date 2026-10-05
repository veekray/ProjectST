package ru.projectst.rpgcore.client;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.projectst.rpgcore.net.Protocol;

/** Данные меню от сервера: классы, навыки, числа. */
public record MenuPayload(byte[] data) implements CustomPacketPayload {

    public static final Type<MenuPayload> TYPE = new Type<>(
            ResourceLocation.parse(Protocol.CHANNEL_MENU));

    public static final StreamCodec<FriendlyByteBuf, MenuPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeBytes(payload.data()),
                    buffer -> {
                        byte[] data = new byte[buffer.readableBytes()];
                        buffer.readBytes(data);
                        return new MenuPayload(data);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
