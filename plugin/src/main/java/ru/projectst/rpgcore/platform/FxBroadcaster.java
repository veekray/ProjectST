package ru.projectst.rpgcore.platform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.projectst.rpgcore.net.FxMessage;
import ru.projectst.rpgcore.net.Protocol;
import ru.projectst.rpgcore.net.StateCodec;
import ru.projectst.rpgcore.skill.Position;
import ru.projectst.rpgcore.skill.Zone;

/**
 * Кому какие эффекты: игрокам с модом — события, остальным — ванильные частицы.
 *
 * <p><b>Каждый видит один вид.</b> Ванильная частица, разосланная миром всем
 * подряд, легла бы у игрока с модом поверх его эффекта, и печать мага
 * рисовалась бы дважды. Поэтому частицы навыков уходят адресно, только тем, у
 * кого мода нет, а события — только тем, у кого он есть.
 *
 * <p><b>Игрок с модом не видит ванильных частиц навыков вовсе.</b> Даже у
 * действия без {@code fx} он получает событие, и мод рисует общий эффект по
 * форме в цветах класса. Ванильный вид — только запасной, для игроков без мода.
 *
 * <p><b>Зону узнаёт и тот, кто подошёл позже.</b> Печать живёт десять секунд,
 * и игрок, вошедший в радиус на пятой, не получил события о постановке. Служба
 * помнит, кому какую зону уже показала, и досылает остальным с остатком срока.
 * Без этого печати были бы видны только тем, кто стоял рядом в момент каста.
 *
 * <p>Всё здесь живёт в главном потоке: события навыков, тикер зон и отправка
 * идут оттуда, поэтому обычных коллекций достаточно.
 */
public final class FxBroadcaster {

    /** Насколько далеко мод получает эффекты: дальше их не разглядеть. */
    public static final double RANGE = 48;

    /**
     * Насколько далеко рассылаются ванильные частицы адресно.
     *
     * <p>Столько же, сколько у ванильной рассылки мира: игрок без мода видит
     * запасной вид там же, где видел бы его без нас.
     */
    public static final double VANILLA_RANGE = 32;

    /**
     * Запас на выход из радиуса: зона, на краю которой стоит игрок, не должна
     * мигать от каждого шага туда-обратно.
     */
    private static final double LEAVE_MARGIN = 16;

    /**
     * Сколько украшений уходит одному игроку за тик.
     *
     * <p>Зоны и снаряды — состояние, они уходят всегда. Вспышки сверх этого
     * отбрасываются: двадцать магов в одной точке не должны превращать канал в
     * мегабайты, а лишняя вспышка в такой каше всё равно не видна.
     */
    private static final int DECOR_PER_TICK = 384;

    private final Plugin plugin;
    private final LongSupplier clock;

    /** У кого стоит мод нужной версии: решает {@link ClientLink}. */
    private Predicate<UUID> modded = id -> false;

    private final Map<UUID, List<FxMessage.Event>> queue = new HashMap<>();

    /** Номера зон в канале: короче идентификатора и не выдают его наружу. */
    private final Map<UUID, Integer> zoneHandles = new HashMap<>();
    private final Map<Integer, Zone> zonesByHandle = new HashMap<>();

    /** Какие зоны какому игроку уже показаны. */
    private final Map<UUID, Set<Integer>> known = new HashMap<>();

    private int nextHandle = 1;

    /** Что каким игрокам показано из статусов на существах. */
    private final StatusFeed feed = new StatusFeed();

    /**
     * Зелья от навыков: существо → зелье → {полный срок, тик конца}.
     *
     * <p>Чужие зелья клиент не видит, а их пузырьки выключены. Без этой памяти
     * яд плюща на цели было бы не нарисовать ничем.
     */
    private final Map<UUID, Map<String, long[]>> potions = new HashMap<>();

    public FxBroadcaster(Plugin plugin, LongSupplier clock) {
        this.plugin = plugin;
        this.clock = clock;
    }

    /** Подключается после канала мода: тот создаётся позже мира. */
    public void useViewers(Predicate<UUID> modded) {
        this.modded = modded == null ? id -> false : modded;
    }

    public boolean hasMod(Player player) {
        return modded.test(player.getUniqueId());
    }

    /** Кто видит точку: с модом в своём радиусе, без мода — в ванильном. */
    public record Viewers(List<Player> modded, List<Player> vanilla) {
    }

    public Viewers viewers(Location at) {
        return viewers(at, RANGE);
    }

    public Viewers viewers(Location at, double moddedRange) {
        List<Player> withMod = new ArrayList<>();
        List<Player> without = new ArrayList<>();
        World world = at.getWorld();
        if (world == null) {
            return new Viewers(withMod, without);
        }
        double modSq = moddedRange * moddedRange;
        double vanillaSq = VANILLA_RANGE * VANILLA_RANGE;
        for (Player player : world.getPlayers()) {
            double d = player.getLocation().distanceSquared(at);
            if (hasMod(player)) {
                if (d <= modSq) {
                    withMod.add(player);
                }
            } else if (d <= vanillaSq) {
                without.add(player);
            }
        }
        return new Viewers(withMod, without);
    }

    public int nextHandle() {
        return nextHandle++;
    }

    public void send(Player player, FxMessage.Event event) {
        queue.computeIfAbsent(player.getUniqueId(), id -> new ArrayList<>()).add(event);
    }

    public void send(Collection<Player> players, FxMessage.Event event) {
        for (Player player : players) {
            send(player, event);
        }
    }

    // ------------------------------------------------------------------ зоны

    /** Зона поставлена: показать тем, кто рядом, полным сроком. */
    public void zonePlaced(Zone zone, Location centre) {
        int handle = handleOf(zone);
        for (Player player : viewers(centre).modded()) {
            send(player, zoneOn(handle, zone, player, zone.totalTicks()));
            known.computeIfAbsent(player.getUniqueId(), id -> new HashSet<>()).add(handle);
        }
    }

    /** Зону сняли навыком: тем, кто её видел, — куда её стянуло. */
    public void zoneConsumed(Zone zone, Position pulledTo) {
        Integer handle = zoneHandles.remove(zone.id());
        if (handle == null) {
            return;
        }
        zonesByHandle.remove(handle);
        Position to = pulledTo == null ? zone.center() : pulledTo;
        FxMessage.ZoneOff off = new FxMessage.ZoneOff(handle, FxMessage.ZoneEnd.CONSUMED,
                to.x(), to.y(), to.z());
        for (Map.Entry<UUID, Set<Integer>> entry : known.entrySet()) {
            if (entry.getValue().remove(handle)) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    send(player, off);
                }
            }
        }
    }

    /**
     * Сверка зон с тем, что видит каждый игрок с модом.
     *
     * <p>Зовётся тикером зон. Подошёл — получает зону с остатком срока; отошёл
     * далеко — снимает; зоны не стало по сроку — снимает тоже. Остаток считается
     * по тем же часам, по которым зона истекает.
     */
    public void syncZones(Collection<Zone> active) {
        long now = clock.getAsLong();
        Set<Integer> alive = new HashSet<>();
        for (Zone zone : active) {
            if (zone.visible()) {
                alive.add(handleOf(zone));
            }
        }
        double near = RANGE * RANGE;
        double far = (RANGE + LEAVE_MARGIN) * (RANGE + LEAVE_MARGIN);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!hasMod(player)) {
                continue;
            }
            Set<Integer> mine = known.computeIfAbsent(player.getUniqueId(),
                    id -> new HashSet<>());
            for (Zone zone : active) {
                if (!zone.visible()) {
                    continue;
                }
                int handle = handleOf(zone);
                double d = distanceSquared(player, zone.center());
                if (d <= near && mine.add(handle)) {
                    int remaining = (int) Math.max(1, zone.expiresAtTick() - now);
                    send(player, zoneOn(handle, zone, player, remaining));
                } else if (d > far && mine.remove(handle)) {
                    send(player, zoneOff(handle, zone, FxMessage.ZoneEnd.OUT_OF_RANGE));
                }
            }
            for (Integer handle : List.copyOf(mine)) {
                if (!alive.contains(handle)) {
                    mine.remove(handle);
                    Zone gone = zonesByHandle.get(handle);
                    if (gone != null) {
                        send(player, zoneOff(handle, gone, FxMessage.ZoneEnd.EXPIRED));
                    }
                }
            }
        }
        // Номера погасших зон больше не нужны никому.
        zonesByHandle.keySet().removeIf(handle -> !alive.contains(handle));
        zoneHandles.values().removeIf(handle -> !alive.contains(handle));
    }

    private int handleOf(Zone zone) {
        Integer handle = zoneHandles.get(zone.id());
        if (handle == null) {
            handle = nextHandle();
            zoneHandles.put(zone.id(), handle);
            zonesByHandle.put(handle, zone);
        }
        return handle;
    }

    private static FxMessage.ZoneOn zoneOn(int handle, Zone zone, Player viewer, int remaining) {
        Position c = zone.center();
        return new FxMessage.ZoneOn(handle, zone.fx(), zone.classId(), c.x(), c.y(), c.z(),
                (float) zone.radius(), zone.totalTicks(), Math.min(remaining, zone.totalTicks()),
                zone.ownedBy(viewer.getUniqueId()), BukkitSkillWorld.entityIdOf(zone.owner()));
    }

    private static FxMessage.ZoneOff zoneOff(int handle, Zone zone, FxMessage.ZoneEnd why) {
        Position c = zone.center();
        return new FxMessage.ZoneOff(handle, why, c.x(), c.y(), c.z());
    }

    private static double distanceSquared(Player player, Position at) {
        Location here = player.getLocation();
        if (here.getWorld() == null || !here.getWorld().getUID().equals(at.worldId())) {
            return Double.MAX_VALUE;
        }
        double dx = here.getX() - at.x();
        double dy = here.getY() - at.y();
        double dz = here.getZ() - at.z();
        return dx * dx + dy * dy + dz * dz;
    }

    // ------------------------------------------------------------------ статусы

    public void notePotion(UUID target, String effect, int ticks) {
        long now = clock.getAsLong();
        potions.computeIfAbsent(target, id -> new HashMap<>())
                .put(effect, new long[] {Math.max(1, ticks), now + Math.max(1, ticks)});
    }

    public void clearPotion(UUID target, String effect) {
        Map<String, long[]> mine = potions.get(target);
        if (mine != null) {
            mine.remove(effect);
        }
    }

    /**
     * Сверка статусов на существах с тем, что видит каждый игрок с модом.
     *
     * <p>Зовётся раз в тик. Видно то, что действует (подавленное не рисуется:
     * его и в бою нет), зелья от навыков — псевдостатусом {@code potion:<зелье>},
     * призванные — {@code minion:<тег>} с хозяином в источнике.
     */
    public void syncStatuses(ru.projectst.rpgcore.status.StatusService statuses,
                             ru.projectst.rpgcore.skill.MinionService minions) {
        long now = clock.getAsLong();
        Map<UUID, List<StatusFeed.Seen>> byEntity = new HashMap<>();
        Map<UUID, Integer> ids = new HashMap<>();
        for (UUID target : statuses.targets()) {
            for (ru.projectst.rpgcore.status.ActiveStatus status : statuses.acting(target)) {
                byEntity.computeIfAbsent(target, k -> new ArrayList<>()).add(new StatusFeed.Seen(
                        status.id(), idOf(casterOf(status.source()), ids),
                        (int) Math.min(Integer.MAX_VALUE, status.total()),
                        status.expiresAtTick(), status.stacks()));
            }
        }
        potions.values().forEach(mine -> mine.values().removeIf(p -> p[1] <= now));
        potions.values().removeIf(Map::isEmpty);
        for (Map.Entry<UUID, Map<String, long[]>> entry : potions.entrySet()) {
            for (Map.Entry<String, long[]> potion : entry.getValue().entrySet()) {
                byEntity.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(
                        new StatusFeed.Seen("potion:" + potion.getKey(), 0,
                                (int) potion.getValue()[0], potion.getValue()[1], 1));
            }
        }
        for (ru.projectst.rpgcore.skill.Minion minion : minions.all()) {
            // Срок призванного мод не рисует: он узнаёт, кто это и чей он.
            // Полный срок нулём — иначе он «менялся» бы каждый тик сверки.
            byEntity.computeIfAbsent(minion.entityId(), k -> new ArrayList<>()).add(
                    new StatusFeed.Seen("minion:" + minion.tag(), idOf(minion.owner(), ids),
                            0, minion.expiresAtTick(), 1));
        }
        Map<UUID, org.bukkit.entity.Entity> entities = new HashMap<>();
        for (UUID id : byEntity.keySet()) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(id);
            if (entity != null && !entity.isDead()) {
                entities.put(id, entity);
            }
        }
        double near = RANGE * RANGE;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!hasMod(player)) {
                continue;
            }
            Map<Integer, List<StatusFeed.Seen>> visible = new HashMap<>();
            Location here = player.getLocation();
            for (Map.Entry<UUID, org.bukkit.entity.Entity> entry : entities.entrySet()) {
                Location there = entry.getValue().getLocation();
                if (there.getWorld() == here.getWorld()
                        && there.distanceSquared(here) <= near) {
                    visible.put(entry.getValue().getEntityId(), byEntity.get(entry.getKey()));
                }
            }
            for (FxMessage.Event event : feed.diff(player.getUniqueId(), visible, now)) {
                send(player, event);
            }
        }
    }

    /** Кастер из источника статуса {@code skill:<навык>:<uuid>}; иначе {@code null}. */
    static UUID casterOf(String source) {
        if (source == null) {
            return null;
        }
        int cut = source.lastIndexOf(':');
        if (cut < 0) {
            return null;
        }
        try {
            return UUID.fromString(source.substring(cut + 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int idOf(UUID id, Map<UUID, Integer> cache) {
        if (id == null) {
            return 0;
        }
        return cache.computeIfAbsent(id, BukkitSkillWorld::entityIdOf);
    }

    // ------------------------------------------------------------------ отправка

    /**
     * Отправляет накопленное за тик.
     *
     * <p>Одной пачкой на игрока, а не сообщением на событие: Петля с кольцом,
     * пятью попаданиями и печатью — это семь событий за тик, и семь пакетов
     * вместо одного были бы чистыми накладными расходами.
     */
    public void flush() {
        if (queue.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, List<FxMessage.Event>> entry : queue.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !hasMod(player)) {
                continue;
            }
            List<FxMessage.Event> events = trim(entry.getValue());
            for (int from = 0; from < events.size(); from += StateCodec.FX_PER_MESSAGE) {
                List<FxMessage.Event> part = events.subList(from,
                        Math.min(events.size(), from + StateCodec.FX_PER_MESSAGE));
                player.sendPluginMessage(plugin, Protocol.CHANNEL_FX, StateCodec.writeFx(part));
            }
        }
        queue.clear();
    }

    /** Состояние уходит целиком, украшения — до предела. */
    private static List<FxMessage.Event> trim(List<FxMessage.Event> events) {
        List<FxMessage.Event> out = new ArrayList<>(events.size());
        int decor = 0;
        for (FxMessage.Event event : events) {
            // Состояние уходит всегда: потерянное «статус снят» оставило бы корни
            // на ногах навсегда, потерянное «каст кончился» — полосу на экране.
            boolean state = event instanceof FxMessage.ZoneOn
                    || event instanceof FxMessage.ZoneOff
                    || event instanceof FxMessage.Projectile
                    || event instanceof FxMessage.ProjectileEnd
                    || event instanceof FxMessage.StatusOn
                    || event instanceof FxMessage.StatusOff
                    || event instanceof FxMessage.CastStart
                    || event instanceof FxMessage.CastEnd
                    || event instanceof FxMessage.Telegraph;
            if (state || decor++ < DECOR_PER_TICK) {
                out.add(event);
            }
        }
        return out;
    }

    /** Игрок ушёл: его очередь и память о показанных зонах не нужны. */
    public void forget(UUID player) {
        queue.remove(player);
        known.remove(player);
        feed.forget(player);
    }

    /** Существо ушло из мира: его зелья помнить незачем. */
    public void forgetEntity(UUID entity) {
        potions.remove(entity);
    }
}
