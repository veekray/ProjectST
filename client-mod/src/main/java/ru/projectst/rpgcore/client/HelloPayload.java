package ru.projectst.rpgcore.client;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.projectst.rpgcore.net.Protocol;
import ru.projectst.rpgcore.net.StateCodec;

/** Приветствие клиента: версия формата и версия мода. */
public record HelloPayload(byte[] data) implements CustomPacketPayload {

    public static final Type<HelloPayload> TYPE = new Type<>(
            ResourceLocation.parse(Protocol.CHANNEL_HELLO));

    public static final StreamCodec<FriendlyByteBuf, HelloPayload> STREAM_CODEC =
            StreamCodec.of(HelloPayload::write, HelloPayload::read);

    public static HelloPayload of() {
        return new HelloPayload(StateCodec.writeHello(Protocol.VERSION,
                RpgCoreClient.MOD_VERSION));
    }

    private static void write(FriendlyByteBuf buffer, HelloPayload payload) {
        // Без длины впереди: плагин получает тело сообщения целиком, как его
        // отдаёт Bukkit. Любая обёртка здесь сдвинула бы все поля на стороне
        // сервера, и первым сломался бы байт версии — то есть именно та
        // проверка, которая должна была об этом сказать.
        buffer.writeBytes(payload.data());
    }

    private static HelloPayload read(FriendlyByteBuf buffer) {
        byte[] data = new byte[buffer.readableBytes()];
        buffer.readBytes(data);
        return new HelloPayload(data);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
