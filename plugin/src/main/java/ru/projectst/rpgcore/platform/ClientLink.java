package ru.projectst.rpgcore.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import ru.projectst.rpgcore.cast.CastService;
import ru.projectst.rpgcore.classes.ClassService;
import ru.projectst.rpgcore.net.ClientState;
import ru.projectst.rpgcore.net.Protocol;
import ru.projectst.rpgcore.net.StateCodec;
import ru.projectst.rpgcore.skill.SkillDef;
import ru.projectst.rpgcore.status.ActiveStatus;
import ru.projectst.rpgcore.status.StatusService;

/**
 * Связь с клиентским модом.
 *
 * <p><b>Мод — зритель.</b> Канал передаёт только посчитанное сервером, и
 * единственное, что приходит от клиента, — рукопожатие. Поэтому мод нельзя
 * использовать, чтобы что-то получить, и поэтому его отсутствие ничего не ломает:
 * нажатия идут через {@link SkillInputListener}, статы — через {@code VitalsSync},
 * и ни то, ни другое про мод не знает.
 *
 * <p><b>Состояние уходит только тем, кто поздоровался.</b> Игроки без мода не
 * получают ни байта: трафик на сотню игроков без мода — это чистая потеря, а
 * обнаруживается она как «сервер подлагивает», без всякой связи с причиной.
 *
 * <p><b>Шлём, когда изменилось.</b> Сравнение с прошлым отправленным снимком
 * дешевле, чем отправка тридцати байт двадцать раз в секунду, и, главное,
 * избавляет от выбора частоты: частота получается такой, какой нужно.
 */
public final class ClientLink implements PluginMessageListener {

    private final Plugin plugin;
    private final ClassService classes;
    private final CastService casts;
    private final StatusService statuses;

    /** Кто поздоровался и с какой версией мода. */
    private final Map<UUID, String> connected = new ConcurrentHashMap<>();

    /** Что ушло в прошлый раз: шлём только изменения. */
    private final Map<UUID, ClientState> lastSent = new ConcurrentHashMap<>();

    public ClientLink(Plugin plugin, ClassService classes, CastService casts,
                      StatusService statuses) {
        this.plugin = plugin;
        this.classes = classes;
        this.casts = casts;
        this.statuses = statuses;
    }

    /** Регистрирует каналы. Без этого Bukkit молча не доставит ни одного байта. */
    public void register() {
        var messenger = plugin.getServer().getMessenger();
        messenger.registerIncomingPluginChannel(plugin, Protocol.CHANNEL_HELLO, this);
        messenger.registerOutgoingPluginChannel(plugin, Protocol.CHANNEL_WELCOME);
        messenger.registerOutgoingPluginChannel(plugin, Protocol.CHANNEL_STATE);
    }

    /** Версия мода у игрока, если он здоровался. */
    public Optional<String> modVersion(UUID player) {
        return Optional.ofNullable(connected.get(player));
    }

    public int connectedCount() {
        return connected.size();
    }

    public void forget(UUID player) {
        connected.remove(player);
        lastSent.remove(player);
    }

    // ------------------------------------------------------------------ приём

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!Protocol.CHANNEL_HELLO.equals(channel)) {
            return;
        }
        StateCodec.Hello hello;
        try {
            hello = StateCodec.readHello(message);
        } catch (RuntimeException e) {
            // Испорченное приветствие — не повод падать и не повод молчать:
            // чаще всего это чужой мод, занявший то же имя канала.
            plugin.getLogger().warning("рукопожатие от " + player.getName()
                    + " не читается: " + e.getMessage());
            return;
        }

        Protocol.Handshake outcome = Protocol.decide(hello.version());
        player.sendPluginMessage(plugin, Protocol.CHANNEL_WELCOME,
                StateCodec.writeWelcome(outcome));

        if (outcome != Protocol.Handshake.ACCEPTED) {
            connected.remove(player.getUniqueId());
            // Отказ говорится игроку, а не только моду: мод может быть той
            // версии, которая этот ответ ещё не умеет показать.
            player.sendMessage(Component.text(outcome == Protocol.Handshake.MOD_TOO_OLD
                            ? "Мод RpgCore старее сервера: обновите мод"
                            : "Мод RpgCore новее сервера: обновите плагин",
                    NamedTextColor.RED));
            return;
        }
        connected.put(player.getUniqueId(), hello.modVersion());
        lastSent.remove(player.getUniqueId());
        send(player);
    }

    // ------------------------------------------------------------------ отправка

    /** Шлёт состояние всем, кто поздоровался и у кого оно изменилось. */
    public void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (connected.containsKey(player.getUniqueId())) {
                send(player);
            }
        }
    }

    private void send(Player player) {
        ClientState state = snapshot(player);
        ClientState previous = lastSent.get(player.getUniqueId());
        if (state.equals(previous)) {
            return;
        }
        lastSent.put(player.getUniqueId(), state);
        player.sendPluginMessage(plugin, Protocol.CHANNEL_STATE, StateCodec.writeState(state));
    }

    /** Собирает состояние из тех же сервисов, что отвечают командам и интерфейсу. */
    private ClientState snapshot(Player player) {
        UUID id = player.getUniqueId();
        var resource = casts.resource();
        var def = classes.classOf(id);
        var data = classes.snapshot(id);

        List<ClientState.StatusLine> statusLines = new ArrayList<>();
        long now = plugin.getServer().getCurrentTick();
        for (ActiveStatus status : statuses.acting(id)) {
            statusLines.add(new ClientState.StatusLine(status.id(), status.stacks(),
                    (int) status.remaining(now), status.category().name()));
        }

        List<ClientState.CooldownLine> cooldowns = new ArrayList<>();
        List<ClientState.SlotLine> slots = new ArrayList<>();
        int slotCount = def.map(own -> own.slots()).orElse(0);
        for (int slot = 1; slot <= slotCount; slot++) {
            String skillId = data.slotBindings().get(slot);
            if (skillId == null) {
                slots.add(new ClientState.SlotLine(slot, "", "", ""));
                continue;
            }
            Optional<SkillDef> skill = classes.skillInSlot(id, slot);
            slots.add(new ClientState.SlotLine(slot, skillId,
                    skill.map(SkillDef::display).orElse(skillId),
                    skill.map(SkillDef::icon).orElse(SkillDef.DEFAULT_ICON)));

            long remaining = casts.cooldowns().remaining(id, skillId);
            if (remaining > 0 && skill.isPresent()) {
                long total = casts.cooldownTicks(id, skill.get(),
                        Math.max(1, classes.skillLevel(id, skillId)));
                cooldowns.add(new ClientState.CooldownLine(skillId, (int) remaining,
                        (int) Math.max(remaining, total)));
            }
        }

        return new ClientState(resource.displayName(id), resource.current(id),
                resource.max(id), data.level(), def.map(own -> own.display()).orElse(""),
                statusLines, cooldowns, slots);
    }
}
