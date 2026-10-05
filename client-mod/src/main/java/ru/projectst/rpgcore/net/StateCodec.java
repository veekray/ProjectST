// КОПИЯ из плагина: plugin/src/main/java/ru/projectst/rpgcore/net/StateCodec.java
// Менять только там и копировать сюда — см. client-mod/README.md.
package ru.projectst.rpgcore.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Перевод состояния в байты и обратно.
 *
 * <p>Один класс на обе стороны: тот же код пишет на сервере и читает в моде.
 * Два независимых кодека — верный способ получить расхождение в одном поле,
 * которое проявится как сдвиг всех последующих чисел, то есть как бессмыслица
 * на экране без всякого указания на причину.
 *
 * <p>Формат простой и самоописывающийся по длинам: версия, затем поля по
 * порядку, списки — с числом элементов впереди. Ни отражения, ни сериализации
 * объектов: формат, который нельзя прочитать глазами, нельзя и отладить.
 */
public final class StateCodec {

    private StateCodec() {
    }

    /** Приветствие клиента: версия формата и строка версии мода. */
    public static byte[] writeHello(int version, String modVersion) {
        return write(out -> {
            out.writeByte(version);
            writeString(out, modVersion);
        });
    }

    public static Hello readHello(byte[] bytes) {
        return read(bytes, in -> new Hello(in.readUnsignedByte(), readString(in)));
    }

    /** Приветствие клиента. */
    public record Hello(int version, String modVersion) {
    }

    /** Ответ сервера: своя версия и исход. */
    public static byte[] writeWelcome(Protocol.Handshake outcome) {
        return write(out -> {
            out.writeByte(Protocol.VERSION);
            out.writeByte(outcome.code());
        });
    }

    public static Welcome readWelcome(byte[] bytes) {
        return read(bytes, in -> new Welcome(in.readUnsignedByte(),
                Protocol.Handshake.of(in.readUnsignedByte())));
    }

    /** Ответ сервера. */
    public record Welcome(int version, Protocol.Handshake outcome) {
    }

    // ------------------------------------------------------------------ состояние

    public static byte[] writeState(ClientState state) {
        return write(out -> {
            out.writeByte(Protocol.VERSION);
            writeString(out, state.resourceName());
            out.writeFloat((float) state.resource());
            out.writeFloat((float) state.resourceMax());
            out.writeShort(state.level());
            writeString(out, state.className());

            out.writeByte(Math.min(255, state.statuses().size()));
            for (ClientState.StatusLine line : limit(state.statuses())) {
                writeString(out, line.id());
                out.writeByte(Math.min(255, line.stacks()));
                out.writeInt(line.remaining());
                writeString(out, line.category());
            }

            out.writeByte(Math.min(255, state.cooldowns().size()));
            for (ClientState.CooldownLine line : limit(state.cooldowns())) {
                writeString(out, line.skillId());
                out.writeInt(line.remaining());
                out.writeInt(line.total());
            }

            out.writeByte(Math.min(255, state.slots().size()));
            for (ClientState.SlotLine line : limit(state.slots())) {
                out.writeByte(line.slot());
                writeString(out, line.skillId());
                writeString(out, line.display());
                writeString(out, line.icon());
            }
        });
    }

    public static ClientState readState(byte[] bytes) {
        return read(bytes, in -> {
            int version = in.readUnsignedByte();
            if (version != Protocol.VERSION) {
                throw new IllegalArgumentException("версия формата " + version
                        + ", поддерживается " + Protocol.VERSION);
            }
            String resourceName = readString(in);
            double resource = in.readFloat();
            double resourceMax = in.readFloat();
            int level = in.readShort();
            String className = readString(in);

            int statusCount = in.readUnsignedByte();
            List<ClientState.StatusLine> statuses = new ArrayList<>(statusCount);
            for (int i = 0; i < statusCount; i++) {
                statuses.add(new ClientState.StatusLine(readString(in), in.readUnsignedByte(),
                        in.readInt(), readString(in)));
            }

            int cooldownCount = in.readUnsignedByte();
            List<ClientState.CooldownLine> cooldowns = new ArrayList<>(cooldownCount);
            for (int i = 0; i < cooldownCount; i++) {
                cooldowns.add(new ClientState.CooldownLine(readString(in), in.readInt(),
                        in.readInt()));
            }

            int slotCount = in.readUnsignedByte();
            List<ClientState.SlotLine> slots = new ArrayList<>(slotCount);
            for (int i = 0; i < slotCount; i++) {
                slots.add(new ClientState.SlotLine(in.readUnsignedByte(), readString(in),
                        readString(in), readString(in)));
            }
            return new ClientState(resourceName, resource, resourceMax, level, className,
                    statuses, cooldowns, slots);
        });
    }

    /** Больше двухсот пятидесяти пяти строк в списке не бывает и не нужно. */
    private static <T> List<T> limit(List<T> source) {
        return source.size() <= 255 ? source : source.subList(0, 255);
    }

    // ------------------------------------------------------------------ строки

    /**
     * Строка: длина двумя байтами, затем UTF-8.
     *
     * <p>Не {@code writeUTF}: его ограничение в 65535 байт считается по
     * внутреннему представлению, и на длинных именах с кириллицей он падает
     * иначе, чем ожидаешь. Здесь длина обрезается явно.
     */
    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] data = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (data.length > 1024) {
            data = java.util.Arrays.copyOf(data, 1024);
        }
        out.writeShort(data.length);
        out.write(data);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        byte[] data = new byte[length];
        in.readFully(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ обвязка

    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    private interface Reader<T> {
        T read(DataInputStream in) throws IOException;
    }

    private static byte[] write(Writer writer) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writer.write(out);
        } catch (IOException e) {
            // Запись в память не может не удаться; если удалась — это не наша
            // ошибка формата, а что-то совсем иное, и прятать её нельзя.
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static <T> T read(byte[] bytes, Reader<T> reader) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return reader.read(in);
        } catch (IOException e) {
            throw new IllegalArgumentException("сообщение не читается: " + e.getMessage(), e);
        }
    }
}
