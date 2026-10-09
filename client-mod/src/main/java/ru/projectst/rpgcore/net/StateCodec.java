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
            out.writeFloat((float) state.stamina());
            out.writeFloat((float) state.staminaMax());
            out.writeShort(state.level());
            writeString(out, state.className());

            out.writeByte(Math.min(255, state.statuses().size()));
            for (ClientState.StatusLine line : limit(state.statuses())) {
                writeString(out, line.id());
                writeString(out, line.display());
                out.writeByte(Math.min(255, line.stacks()));
                out.writeInt(line.remaining());
                writeString(out, line.category());
                writeString(out, line.color());
                out.writeInt(line.total());
                writeString(out, line.description());
                out.writeByte(Math.min(255, line.effects().size()));
                for (ClientState.EffectLine effect : limit(line.effects())) {
                    writeString(out, effect.statId());
                    writeString(out, effect.text());
                    out.writeBoolean(effect.good());
                }
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

            out.writeByte(Math.min(255, state.counters().size()));
            for (ClientState.CounterLine line : limit(state.counters())) {
                writeString(out, line.id());
                writeString(out, line.display());
                out.writeByte(Math.min(255, line.stacks()));
                out.writeByte(Math.min(255, line.maxStacks()));
                writeString(out, line.color());
            }

            // Рывок есть не всегда: без врождённого навыка в контенте рисовать
            // нечего. Флаг, а не нулевые числа: ноль зарядов — это «все
            // потрачены», и путать его с «рывка нет» нельзя.
            ClientState.DashLine dash = state.dash();
            out.writeBoolean(dash != null);
            if (dash != null) {
                writeString(out, dash.skillId());
                writeString(out, dash.display());
                out.writeByte(Math.min(255, dash.charges()));
                out.writeByte(Math.min(255, dash.maxCharges()));
                out.writeInt(dash.remaining());
                out.writeInt(dash.total());
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
            double stamina = in.readFloat();
            double staminaMax = in.readFloat();
            int level = in.readShort();
            String className = readString(in);

            int statusCount = in.readUnsignedByte();
            List<ClientState.StatusLine> statuses = new ArrayList<>(statusCount);
            for (int i = 0; i < statusCount; i++) {
                String id = readString(in);
                String display = readString(in);
                int stacks = in.readUnsignedByte();
                int remaining = in.readInt();
                String category = readString(in);
                String color = readString(in);
                int total = in.readInt();
                String description = readString(in);
                int effectCount = in.readUnsignedByte();
                List<ClientState.EffectLine> effects = new ArrayList<>(effectCount);
                for (int e = 0; e < effectCount; e++) {
                    effects.add(new ClientState.EffectLine(readString(in), readString(in),
                            in.readBoolean()));
                }
                statuses.add(new ClientState.StatusLine(id, display, stacks, remaining,
                        category, color, total, description, effects));
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

            int counterCount = in.readUnsignedByte();
            List<ClientState.CounterLine> counters = new ArrayList<>(counterCount);
            for (int i = 0; i < counterCount; i++) {
                counters.add(new ClientState.CounterLine(readString(in), readString(in),
                        in.readUnsignedByte(), in.readUnsignedByte(), readString(in)));
            }
            ClientState.DashLine dash = null;
            if (in.readBoolean()) {
                dash = new ClientState.DashLine(readString(in), readString(in),
                        in.readUnsignedByte(), in.readUnsignedByte(), in.readInt(),
                        in.readInt());
            }
            return new ClientState(resourceName, resource, resourceMax, stamina, staminaMax,
                    level, className, statuses, cooldowns, slots, counters, dash);
        });
    }

    // ------------------------------------------------------------------ меню

    public static byte[] writeMenu(MenuData menu) {
        return write(out -> {
            out.writeByte(Protocol.VERSION);
            writeString(out, menu.classId());
            out.writeShort(menu.level());
            out.writeFloat((float) menu.xp());
            out.writeFloat((float) menu.xpToNext());
            out.writeShort(menu.points());
            out.writeByte(menu.slots());

            out.writeByte(Math.min(255, menu.classes().size()));
            for (MenuData.ClassLine line : limit(menu.classes())) {
                writeString(out, line.id());
                writeString(out, line.display());
                writeString(out, line.icon());
                writeString(out, line.resourceName());
                out.writeByte(line.slots());
                out.writeShort(line.maxLevel());
            }

            out.writeByte(Math.min(255, menu.skills().size()));
            for (MenuData.SkillLine line : limit(menu.skills())) {
                writeString(out, line.id());
                writeString(out, line.display());
                writeString(out, line.icon());
                out.writeByte(line.tier());
                out.writeByte(line.level());
                out.writeByte(line.maxLevel());
                out.writeShort(line.required());
                out.writeFloat((float) line.cost());
                out.writeFloat((float) line.stamina());
                out.writeFloat((float) line.cooldown());
                out.writeByte(line.boundSlot());
                out.writeFloat((float) line.damage());
                out.writeByte(Math.min(255, line.description().size()));
                for (String row : limit(line.description())) {
                    writeString(out, row);
                }
            }

            out.writeByte(Math.min(255, menu.stats().size()));
            for (MenuData.StatLine line : limit(menu.stats())) {
                writeString(out, line.id());
                writeString(out, line.display());
                out.writeFloat((float) line.value());
                writeString(out, line.note());
            }

            out.writeByte(Math.min(255, menu.gear().size()));
            for (MenuData.GearLine line : limit(menu.gear())) {
                writeString(out, line.cell());
                writeString(out, line.refusal());
            }
        });
    }

    public static MenuData readMenu(byte[] bytes) {
        return read(bytes, in -> {
            int version = in.readUnsignedByte();
            if (version != Protocol.VERSION) {
                throw new IllegalArgumentException("версия формата " + version
                        + ", поддерживается " + Protocol.VERSION);
            }
            String classId = readString(in);
            int level = in.readShort();
            double xp = in.readFloat();
            double xpToNext = in.readFloat();
            int points = in.readShort();
            int slots = in.readUnsignedByte();

            int classCount = in.readUnsignedByte();
            List<MenuData.ClassLine> classes = new ArrayList<>(classCount);
            for (int i = 0; i < classCount; i++) {
                classes.add(new MenuData.ClassLine(readString(in), readString(in),
                        readString(in), readString(in), in.readUnsignedByte(), in.readShort()));
            }

            int skillCount = in.readUnsignedByte();
            List<MenuData.SkillLine> skills = new ArrayList<>(skillCount);
            for (int i = 0; i < skillCount; i++) {
                String skillId = readString(in);
                String display = readString(in);
                String icon = readString(in);
                int tier = in.readUnsignedByte();
                int skillLevel = in.readUnsignedByte();
                int maxLevel = in.readUnsignedByte();
                int required = in.readShort();
                double cost = in.readFloat();
                double stamina = in.readFloat();
                double cooldown = in.readFloat();
                int boundSlot = in.readUnsignedByte();
                double damage = in.readFloat();
                int rows = in.readUnsignedByte();
                List<String> description = new ArrayList<>(rows);
                for (int row = 0; row < rows; row++) {
                    description.add(readString(in));
                }
                skills.add(new MenuData.SkillLine(skillId, display, icon, tier, skillLevel,
                        maxLevel, required, cost, stamina, cooldown, boundSlot, damage,
                        description));
            }

            int statCount = in.readUnsignedByte();
            List<MenuData.StatLine> stats = new ArrayList<>(statCount);
            for (int i = 0; i < statCount; i++) {
                stats.add(new MenuData.StatLine(readString(in), readString(in),
                        in.readFloat(), readString(in)));
            }

            int gearCount = in.readUnsignedByte();
            List<MenuData.GearLine> gear = new ArrayList<>(gearCount);
            for (int i = 0; i < gearCount; i++) {
                gear.add(new MenuData.GearLine(readString(in), readString(in)));
            }
            return new MenuData(classId, level, xp, xpToNext, points, slots, classes, skills,
                    stats, gear);
        });
    }

    // ------------------------------------------------------------------ действия

    /**
     * Просьба клиента.
     *
     * <p>Ввод движения пишется всегда, хотя читает его только рывок. Это
     * восемь байт на нажатие — и один формат сообщения вместо двух. Два формата
     * в одном канале означали бы, что читать его надо по-разному в зависимости
     * от действия, а именно так теряется байт и съезжает всё остальное.
     *
     * @param action  что просят
     * @param number  число: номер слота
     * @param id      идентификатор: навык или класс
     * @param forward ход вперёд: от -1 (назад) до 1 (вперёд). Читает только
     *                рывок
     * @param left    ход влево: от -1 (вправо) до 1 (влево)
     */
    public record ActionRequest(Protocol.Action action, int number, String id,
                                double forward, double left) {

        public ActionRequest(Protocol.Action action, int number, String id) {
            this(action, number, id, 0, 0);
        }
    }

    public static byte[] writeAction(Protocol.Action action, int number, String id) {
        return writeAction(action, number, id, 0, 0);
    }

    public static byte[] writeAction(Protocol.Action action, int number, String id,
                                     double forward, double left) {
        return write(out -> {
            out.writeByte(Protocol.VERSION);
            out.writeByte(action.code());
            out.writeByte(Math.clamp(number, 0, 255));
            writeString(out, id);
            out.writeFloat((float) Math.clamp(forward, -1, 1));
            out.writeFloat((float) Math.clamp(left, -1, 1));
        });
    }

    public static ActionRequest readAction(byte[] bytes) {
        return read(bytes, in -> {
            int version = in.readUnsignedByte();
            if (version != Protocol.VERSION) {
                throw new IllegalArgumentException("версия формата " + version
                        + ", поддерживается " + Protocol.VERSION);
            }
            Protocol.Action action = Protocol.Action.of(in.readUnsignedByte());
            int number = in.readUnsignedByte();
            String id = readString(in);
            // Границы ставятся и на чтении: клиент мог прислать что угодно, а
            // «что угодно» в направлении — это рывок на другой конец карты.
            double forward = Math.clamp(in.readFloat(), -1, 1);
            double left = Math.clamp(in.readFloat(), -1, 1);
            return new ActionRequest(action, number, id, forward, left);
        });
    }

    // ------------------------------------------------------------------ эффекты

    /** Больше событий в одном сообщении не кладём: остальные уходят следующим. */
    public static final int FX_PER_MESSAGE = 128;

    /**
     * Пачка видимых событий.
     *
     * <p>Вид события — байт впереди, по порядку {@link FxKind}. Новый вид — это
     * новая версия протокола: старый мод не знает его длины и съехал бы на всём
     * остальном в пачке.
     */
    public static byte[] writeFx(List<FxMessage.Event> events) {
        List<FxMessage.Event> part = events.size() <= FX_PER_MESSAGE
                ? events : events.subList(0, FX_PER_MESSAGE);
        return write(out -> {
            out.writeByte(Protocol.VERSION);
            out.writeByte(part.size());
            for (FxMessage.Event event : part) {
                switch (event) {
                    case FxMessage.Burst e -> {
                        out.writeByte(FxKind.BURST.ordinal());
                        writeString(out, e.fx());
                        writeString(out, e.classId());
                        out.writeByte(e.shape().ordinal());
                        writePoint(out, e.x(), e.y(), e.z());
                        out.writeFloat(e.radius());
                        out.writeFloat(e.angle());
                        out.writeFloat(e.axisX());
                        out.writeFloat(e.axisZ());
                    }
                    case FxMessage.ZoneOn e -> {
                        out.writeByte(FxKind.ZONE_ON.ordinal());
                        out.writeInt(e.id());
                        writeString(out, e.fx());
                        writeString(out, e.classId());
                        writePoint(out, e.x(), e.y(), e.z());
                        out.writeFloat(e.radius());
                        out.writeInt(e.totalTicks());
                        out.writeInt(e.remainingTicks());
                        out.writeBoolean(e.own());
                    }
                    case FxMessage.ZoneOff e -> {
                        out.writeByte(FxKind.ZONE_OFF.ordinal());
                        out.writeInt(e.id());
                        out.writeByte(e.reason().ordinal());
                        writePoint(out, e.toX(), e.toY(), e.toZ());
                    }
                    case FxMessage.Projectile e -> {
                        out.writeByte(FxKind.PROJECTILE.ordinal());
                        out.writeInt(e.id());
                        writeString(out, e.fx());
                        writeString(out, e.classId());
                        writePoint(out, e.x(), e.y(), e.z());
                        out.writeFloat(e.dx());
                        out.writeFloat(e.dy());
                        out.writeFloat(e.dz());
                        out.writeFloat(e.speed());
                        out.writeFloat(e.range());
                        out.writeFloat(e.gravity());
                    }
                    case FxMessage.ProjectileEnd e -> {
                        out.writeByte(FxKind.PROJECTILE_END.ordinal());
                        out.writeInt(e.id());
                        writePoint(out, e.x(), e.y(), e.z());
                        out.writeBoolean(e.hit());
                    }
                    case FxMessage.Hit e -> {
                        out.writeByte(FxKind.HIT.ordinal());
                        out.writeInt(e.entityId());
                        writeString(out, e.classId());
                        out.writeBoolean(e.crit());
                    }
                    case FxMessage.Trail e -> {
                        out.writeByte(FxKind.TRAIL.ordinal());
                        writeString(out, e.fx());
                        writeString(out, e.classId());
                        writePoint(out, e.fromX(), e.fromY(), e.fromZ());
                        writePoint(out, e.toX(), e.toY(), e.toZ());
                    }
                }
            }
        });
    }

    public static List<FxMessage.Event> readFx(byte[] bytes) {
        return read(bytes, in -> {
            int version = in.readUnsignedByte();
            if (version != Protocol.VERSION) {
                throw new IllegalArgumentException("версия формата " + version
                        + ", поддерживается " + Protocol.VERSION);
            }
            int count = in.readUnsignedByte();
            List<FxMessage.Event> out = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int code = in.readUnsignedByte();
                FxKind[] kinds = FxKind.values();
                if (code >= kinds.length) {
                    // Длины неизвестного вида не знаем: читать дальше — значит
                    // читать мусор. Остаток пачки отбрасывается целиком.
                    throw new IllegalArgumentException("неизвестный вид события " + code);
                }
                out.add(switch (kinds[code]) {
                    case BURST -> new FxMessage.Burst(readString(in), readString(in),
                            FxMessage.Shape.of(in.readUnsignedByte()),
                            in.readDouble(), in.readDouble(), in.readDouble(),
                            in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
                    case ZONE_ON -> new FxMessage.ZoneOn(in.readInt(), readString(in),
                            readString(in), in.readDouble(), in.readDouble(), in.readDouble(),
                            in.readFloat(), in.readInt(), in.readInt(), in.readBoolean());
                    case ZONE_OFF -> new FxMessage.ZoneOff(in.readInt(),
                            FxMessage.ZoneEnd.of(in.readUnsignedByte()),
                            in.readDouble(), in.readDouble(), in.readDouble());
                    case PROJECTILE -> new FxMessage.Projectile(in.readInt(), readString(in),
                            readString(in), in.readDouble(), in.readDouble(), in.readDouble(),
                            in.readFloat(), in.readFloat(), in.readFloat(),
                            in.readFloat(), in.readFloat(), in.readFloat());
                    case PROJECTILE_END -> new FxMessage.ProjectileEnd(in.readInt(),
                            in.readDouble(), in.readDouble(), in.readDouble(),
                            in.readBoolean());
                    case HIT -> new FxMessage.Hit(in.readInt(), readString(in),
                            in.readBoolean());
                    case TRAIL -> new FxMessage.Trail(readString(in), readString(in),
                            in.readDouble(), in.readDouble(), in.readDouble(),
                            in.readDouble(), in.readDouble(), in.readDouble());
                });
            }
            return out;
        });
    }

    /** Байт вида события. Порядок — часть формата, переставлять нельзя. */
    private enum FxKind { BURST, ZONE_ON, ZONE_OFF, PROJECTILE, PROJECTILE_END, HIT, TRAIL }

    private static void writePoint(DataOutputStream out, double x, double y, double z)
            throws IOException {
        out.writeDouble(x);
        out.writeDouble(y);
        out.writeDouble(z);
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
