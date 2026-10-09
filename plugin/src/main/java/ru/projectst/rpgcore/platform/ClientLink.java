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
import ru.projectst.rpgcore.cast.CastOutcome;
import ru.projectst.rpgcore.cast.CastService;
import ru.projectst.rpgcore.classes.ClassDef;
import ru.projectst.rpgcore.classes.ClassRegistry;
import ru.projectst.rpgcore.classes.ClassService;
import ru.projectst.rpgcore.net.ClientState;
import ru.projectst.rpgcore.net.MenuData;
import ru.projectst.rpgcore.net.Protocol;
import ru.projectst.rpgcore.net.StateCodec;
import ru.projectst.rpgcore.skill.SkillDef;
import ru.projectst.rpgcore.skill.SkillRegistry;
import ru.projectst.rpgcore.stat.StatRegistry;
import ru.projectst.rpgcore.stat.StatService;
import ru.projectst.rpgcore.status.ActiveStatus;
import ru.projectst.rpgcore.status.StatusRegistry;
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

    /** Метка статуса, по которой он считается счётчиком ядра класса. */
    public static final String TAG_COUNTER = "counter";

    private final Plugin plugin;
    private final ClassService classes;
    private final CastService casts;
    private final StatusService statuses;
    private final StatusRegistry statusDefs;
    private final ClassRegistry classDefs;
    private final SkillRegistry skills;
    private final StatRegistry statDefs;
    private final StatService statValues;
    private final GearSlots gear;
    private final EquipmentWatcher equipment;
    private final ru.projectst.rpgcore.status.StatusStats statusStats;

    /** Кто поздоровался и с какой версией мода. */
    private final Map<UUID, String> connected = new ConcurrentHashMap<>();

    /** Что ушло в прошлый раз: шлём только изменения. */
    private final Map<UUID, ClientState> lastSent = new ConcurrentHashMap<>();

    public ClientLink(Plugin plugin, ClassService classes, CastService casts,
                      StatusService statuses, StatusRegistry statusDefs,
                      ClassRegistry classDefs, SkillRegistry skills, StatRegistry statDefs,
                      StatService statValues, GearSlots gear,
                      EquipmentWatcher equipment,
                      ru.projectst.rpgcore.status.StatusStats statusStats) {
        this.plugin = plugin;
        this.classes = classes;
        this.casts = casts;
        this.statuses = statuses;
        this.statusDefs = statusDefs;
        this.classDefs = classDefs;
        this.skills = skills;
        this.statDefs = statDefs;
        this.statValues = statValues;
        this.gear = gear;
        this.equipment = equipment;
        this.statusStats = statusStats;
    }

    /** Регистрирует каналы. Без этого Bukkit молча не доставит ни одного байта. */
    public void register() {
        var messenger = plugin.getServer().getMessenger();
        messenger.registerIncomingPluginChannel(plugin, Protocol.CHANNEL_HELLO, this);
        messenger.registerIncomingPluginChannel(plugin, Protocol.CHANNEL_ACTION, this);
        messenger.registerOutgoingPluginChannel(plugin, Protocol.CHANNEL_WELCOME);
        messenger.registerOutgoingPluginChannel(plugin, Protocol.CHANNEL_STATE);
        messenger.registerOutgoingPluginChannel(plugin, Protocol.CHANNEL_MENU);
        messenger.registerOutgoingPluginChannel(plugin, Protocol.CHANNEL_FX);
    }

    /** Версия мода у игрока, если он здоровался. */
    public Optional<String> modVersion(UUID player) {
        return Optional.ofNullable(connected.get(player));
    }

    /**
     * Стоит ли у игрока мод этой версии.
     *
     * <p>Только принятое рукопожатие: мод старой версии не прочитал бы эффекты,
     * и ему, как и игроку без мода, достаются ванильные частицы.
     */
    public boolean hasMod(UUID player) {
        return connected.containsKey(player);
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
        if (Protocol.CHANNEL_ACTION.equals(channel)) {
            onAction(player, message);
            return;
        }
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
        sendMenu(player);
    }

    /**
     * Просьба клиента.
     *
     * <p><b>Клиент просит, сервер решает.</b> Каждое действие идёт через те же
     * службы, что и команда в чате: {@code ClassService} проверяет уровень,
     * очки и класс, {@code CastService} — ресурс, перезарядку и статусы. Поэтому
     * подменённый клиент может попросить ровно то, что игрок может набрать
     * руками, и получит тот же отказ теми же словами.
     */
    private void onAction(Player player, byte[] message) {
        if (!connected.containsKey(player.getUniqueId())) {
            // Без рукопожатия действий не принимаем: версия формата неизвестна,
            // а читать чужие байты наугад — как раз то, от чего версия в первом
            // байте и защищает.
            return;
        }
        StateCodec.ActionRequest request;
        try {
            request = StateCodec.readAction(message);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("действие от " + player.getName()
                    + " не читается: " + e.getMessage());
            return;
        }

        UUID id = player.getUniqueId();
        switch (request.action()) {
            case CAST_SLOT -> {
                CastOutcome outcome = casts.castSlot(id, request.number());
                if (!outcome.succeeded() && outcome.kind() != CastOutcome.Kind.ON_COOLDOWN) {
                    player.sendActionBar(Component.text(outcome.toString(), NamedTextColor.RED));
                }
            }
            case UNLOCK -> {
                var outcome = classes.unlock(id, request.id());
                player.sendMessage(Component.text(outcome.toString(),
                        outcome.succeeded() ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            case UPGRADE -> {
                var outcome = classes.upgrade(id, request.id());
                player.sendMessage(Component.text(outcome.toString(),
                        outcome.succeeded() ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            case BIND -> {
                var outcome = classes.bind(id, request.number(), request.id());
                player.sendMessage(Component.text(outcome.toString(),
                        outcome.succeeded() ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            case UNBIND -> classes.unbind(id, request.number());
            case CHOOSE_CLASS -> {
                if (!classes.setClass(id, request.id())) {
                    player.sendMessage(Component.text("Такого класса нет: " + request.id(),
                            NamedTextColor.RED));
                }
            }
            case CAST_DASH -> {
                // Направление считает сервер: от клиента пришло только то, что
                // он знает лучше, — какие клавиши держит игрок. Поворот берётся
                // тот, что сервер видит сам, поэтому подменённый мод может
                // попросить одну из восьми сторон, а не любую точку мира.
                var heading = ru.projectst.rpgcore.skill.Facing.headingOf(
                        player.getLocation().getYaw(), request.forward(), request.left());
                CastOutcome outcome = casts.castInnate(id, heading);
                if (!outcome.succeeded() && outcome.kind() != CastOutcome.Kind.ON_COOLDOWN) {
                    player.sendActionBar(Component.text(outcome.toString(), NamedTextColor.RED));
                }
            }
            case OPEN_GEAR -> new ru.projectst.rpgcore.platform.gui.GearView(player, gear,
                    () -> {
                        equipment.apply(player);
                        refreshMenu(player);
                    },
                    warning -> plugin.getLogger().warning(warning)).open();
            case REFRESH_MENU -> {
                // Ничего не меняет: ответ уйдёт ниже вместе со всеми остальными.
            }
        }
        // После любого действия состояние пересобирается: экран с прежними
        // числами после нажатия — такая же тихая ложь, как стат, которого нет в
        // мире.
        lastSent.remove(id);
        send(player);
        // Меню — только если в нём могло измениться. Оно большое, а нажатие
        // навыка или рывка меняет одни перезарядки, и те идут состоянием.
        if (request.action() != Protocol.Action.CAST_SLOT
                && request.action() != Protocol.Action.CAST_DASH) {
            sendMenu(player);
        }
    }

    /**
     * Ячейки снаряжения, в которых вещь не действует.
     *
     * <p>Из той же сверки, что считает статы: окно, обещающее одно, когда бой
     * считает другое, хуже окна без обещаний.
     */
    private List<MenuData.GearLine> gearLines(Player player) {
        List<MenuData.GearLine> lines = new ArrayList<>();
        equipment.refusals(player).forEach((cell, refusal) ->
                lines.add(new MenuData.GearLine(cell.key(), refusal)));
        return lines;
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

    /**
     * Шлёт данные меню, если у игрока стоит мод.
     *
     * <p>Для изменений, пришедших не из канала: вещь надели в окне снаряжения,
     * и красная рамка у ячейки должна появиться сразу, а не при следующем
     * действии.
     */
    public void refreshMenu(Player player) {
        if (connected.containsKey(player.getUniqueId())) {
            sendMenu(player);
        }
    }

    /** Шлёт данные меню: по запросу и после каждого действия, а не постоянно. */
    public void sendMenu(Player player) {
        player.sendPluginMessage(plugin, Protocol.CHANNEL_MENU,
                StateCodec.writeMenu(menu(player)));
    }

    /**
     * Данные меню.
     *
     * <p>Числа берутся из тех же служб, что отвечают командам: стоимость и
     * перезарядка — из таблицы баланса навыка, статы — из того же снимка, что
     * читает конвейер урона. Отдельный расчёт для показа означал бы два
     * источника правды, и разошлись бы они молча.
     */
    private MenuData menu(Player player) {
        UUID id = player.getUniqueId();
        var own = classes.classOf(id);
        var data = classes.snapshot(id);

        List<MenuData.ClassLine> classLines = new ArrayList<>();
        for (ClassDef def : classDefs.all()) {
            classLines.add(new MenuData.ClassLine(def.id(), def.display(), def.icon(),
                    def.resource().display(), def.slots(), def.maxLevel()));
        }

        List<MenuData.SkillLine> skillLines = new ArrayList<>();
        if (own.isPresent()) {
            ClassDef def = own.get();
            for (SkillDef skill : skills.all()) {
                if (!skill.selectable() || !skill.classId().equals(def.id())) {
                    continue;
                }
                int level = data.skillLevel(skill.id());
                int boundSlot = 0;
                for (var entry : data.slotBindings().entrySet()) {
                    if (entry.getValue().equals(skill.id())) {
                        boundSlot = entry.getKey();
                    }
                }
                var table = casts.balanceOf(skill.id());
                int atLeast = Math.max(1, level);
                skillLines.add(new MenuData.SkillLine(skill.id(), skill.display(), skill.icon(),
                        skill.tier(), level,
                        ru.projectst.rpgcore.classes.ClassService.MAX_SKILL_LEVEL,
                        def.levelForTier(skill.tier()),
                        skill.resourceCost().resolve(table, atLeast),
                        skill.staminaCost().resolve(table, atLeast),
                        skill.cooldown().resolve(table, atLeast), boundSlot,
                        damageOf(skill, table, atLeast), skill.description()));
            }
            skillLines.sort(java.util.Comparator.comparingInt(MenuData.SkillLine::tier)
                    .thenComparing(MenuData.SkillLine::id));
        }

        List<MenuData.StatLine> statLines = new ArrayList<>();
        var snapshot = statValues.snapshot(id);
        for (var def : statDefs.all()) {
            double value = snapshot.get(def.id());
            statLines.add(new MenuData.StatLine(def.id(), def.display(), value,
                    noteFor(def.id(), value)));
        }

        return new MenuData(own.map(ClassDef::id).orElse(""), data.level(), data.xp(),
                classes.xpToNextLevel(id), data.unspentPoints(),
                own.map(ClassDef::slots).orElse(0), classLines, skillLines, statLines,
                gearLines(player));
    }

    /**
     * Наибольший урон навыка за одно попадание.
     *
     * <p>Наибольший, а не суммарный: у навыков с ветками обычная и усиленная
     * считаются по разным числам, и складывать их значило бы обещать урон,
     * который нельзя нанести за один каст. Считается по той же таблице баланса,
     * что и бой: число в подсказке обязано совпадать с числом в бою, а два
     * расчёта разошлись бы.
     *
     * <p>Урон, умноженный на счётчик каста, здесь ноль: сколько будет печатей,
     * до каста не знает никто, и выдумывать это в подсказке незачем.
     */
    /**
     * Во что превращается значение стата.
     *
     * <p>Спрашивается у самого стата: кривая и фраза объявлены в его файле, и
     * второй расчёт — хоть здесь, хоть в моде — однажды разошёлся бы с боем.
     * Стат без кривой возвращает пустую строку: запасу здоровья пояснять нечего.
     *
     * <p>Формулирует тоже сервер. Если бы число слал он, а слова подставлял
     * мод, они разъехались бы при первой же правке смысла.
     */
    private String noteFor(String statId, double value) {
        return statDefs.find(statId).map(def -> def.note(value)).orElse("");
    }

    private double damageOf(SkillDef skill, ru.projectst.rpgcore.balance.BalanceTable table,
                            int level) {
        double most = 0;
        for (var step : skill.steps()) {
            for (var action : step.actions()) {
                if (action instanceof ru.projectst.rpgcore.skill.Action.Damage damage) {
                    most = Math.max(most, damage.amount().resolve(table, level));
                }
            }
        }
        return most;
    }

    /**
     * Что статус даёт статам игрока, самое заметное первым.
     *
     * <p>Вклад считается как разница итога с надбавкой статуса и без неё — тем же
     * движком и той же кривой, что и бой. Число в процентах эффекта: «+40 к
     * скорости» игроку не скажет ничего, а «+12% к скорости» — то, что он
     * чувствует.
     */
    private List<ClientState.EffectLine> effects(UUID player, String statusId) {
        List<ru.projectst.rpgcore.stat.StatContribution> found = new ArrayList<>();
        var snapshot = statValues.snapshot(player);
        for (var modifier : statusStats.of(player, statusId)) {
            var def = statDefs.find(modifier.statId());
            if (def.isEmpty()) {
                continue;
            }
            double with = snapshot.getOrZero(modifier.statId());
            double without = statValues.valueWithout(player, modifier.statId(),
                    modifier.source());
            var contribution = ru.projectst.rpgcore.stat.StatContribution.of(def.get(), with,
                    without);
            if (!contribution.text().isEmpty()) {
                found.add(contribution);
            }
        }
        found.sort(java.util.Comparator.comparingDouble(
                (ru.projectst.rpgcore.stat.StatContribution c) -> Math.abs(c.amount())).reversed());
        List<ClientState.EffectLine> out = new ArrayList<>();
        for (var contribution : found) {
            out.add(new ClientState.EffectLine(contribution.statId(), contribution.text(),
                    contribution.good()));
        }
        return out;
    }

    /** Собирает состояние из тех же сервисов, что отвечают командам и интерфейсу. */
    private ClientState snapshot(Player player) {
        UUID id = player.getUniqueId();
        var resource = casts.resource();
        var def = classes.classOf(id);
        var data = classes.snapshot(id);

        List<ClientState.StatusLine> statusLines = new ArrayList<>();
        List<ClientState.CounterLine> counterLines = new ArrayList<>();
        long now = plugin.getServer().getCurrentTick();
        // Порядок наложения: значок, прыгающий по ряду при каждом обновлении, не
        // найти взглядом.
        List<ActiveStatus> acting = new ArrayList<>(statuses.acting(id));
        acting.sort(java.util.Comparator.comparingLong(ActiveStatus::appliedAtTick)
                .thenComparing(ActiveStatus::id));
        for (ActiveStatus status : acting) {
            var statusDef = statusDefs.find(status.id());
            String display = statusDef.map(d -> d.display()).orElse(status.id());
            String colour = statusDef.map(d -> d.color()).orElse(null);

            // Счётчик ядра — не эффект, который пройдёт, а ресурс, по которому
            // игрок принимает решения. Поэтому он уходит отдельным списком и
            // рисуется отдельно, а не теряется в строке из восьми статусов.
            if (statusDef.filter(d -> d.hasTag(TAG_COUNTER)).isPresent()) {
                counterLines.add(new ClientState.CounterLine(status.id(), display,
                        status.stacks(), statusDef.get().maxStacks(),
                        colour == null ? "" : colour));
                continue;
            }
            // Служебный статус — замок, отметка «уже сработало» — игроку не
            // показывается: его идентификатор ничего не скажет, а место займёт.
            if (statusDef.isEmpty() || !statusDef.get().named()) {
                continue;
            }
            statusLines.add(new ClientState.StatusLine(status.id(), display, status.stacks(),
                    (int) status.remaining(now), status.category().name(),
                    colour == null ? "" : colour, (int) Math.min(Integer.MAX_VALUE,
                    status.total()), statusDef.get().description(), effects(id, status.id())));
        }

        // Врождённый рывок: не на слоте и слотов не занимает, поэтому идёт
        // отдельным полем. Числа — из тех же перезарядок, что считают бой.
        ClientState.DashLine dash = null;
        Optional<SkillDef> innate = skills.innate();
        if (innate.isPresent()) {
            SkillDef skill = innate.get();
            int maxCharges = skill.charges();
            dash = new ClientState.DashLine(skill.id(), skill.display(),
                    casts.cooldowns().freeCharges(id, skill.id(), maxCharges), maxCharges,
                    (int) casts.cooldowns().untilNextCharge(id, skill.id()),
                    (int) Math.max(1, casts.cooldownTicks(id, skill, 1)));
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

            long remaining = casts.cooldowns().remaining(id, skillId,
                    skill.map(SkillDef::charges).orElse(1));
            if (remaining > 0 && skill.isPresent()) {
                long total = casts.cooldownTicks(id, skill.get(),
                        Math.max(1, classes.skillLevel(id, skillId)));
                cooldowns.add(new ClientState.CooldownLine(skillId, (int) remaining,
                        (int) Math.max(remaining, total)));
            }
        }

        var stamina = ru.projectst.rpgcore.classes.ResourceSpec.STAMINA;
        return new ClientState(resource.displayName(id), resource.current(id),
                resource.max(id), resource.current(id, stamina), resource.max(id, stamina),
                data.level(), def.map(own -> own.display()).orElse(""),
                statusLines, cooldowns, slots, counterLines, dash);
    }
}
