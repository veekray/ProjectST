package ru.projectst.rpgcore.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.projectst.rpgcore.loader.ContentError;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.stat.StatService;
import ru.projectst.rpgcore.stat.StatSnapshot;
import ru.projectst.rpgcore.status.ActiveStatus;
import ru.projectst.rpgcore.status.StatusService;

/**
 * Команда {@code /rpg}.
 *
 * <p>Главная из подкоманд — {@code why}. Требование SPEC «любой отказ объясним
 * командой, а не чтением кода» без неё остаётся декларацией: именно она
 * отвечает на вопрос, почему статус не действует.
 */
public final class RpgCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = List.of("validate", "reload", "debug", "why",
            "menu", "cast", "slot", "class", "skills", "unlock", "upgrade", "bind", "mana",
            "progress", "xp");

    /** Подкоманды, которые меняют мир или смотрят чужие данные. */
    private static final Set<String> ADMIN_ONLY =
            Set.of("validate", "reload", "debug", "why", "xp");

    private static final String PERMISSION_ADMIN = "rpgcore.admin";

    private final ContentService content;
    private final StatService stats;
    private final StatusService statuses;
    private final ru.projectst.rpgcore.skill.SkillRuntime runtime;
    private final ru.projectst.rpgcore.classes.ClassService playerClasses;
    private final ru.projectst.rpgcore.cast.CastService casts;
    private final ru.projectst.rpgcore.platform.gui.MenuContext menus;

    public RpgCommand(ContentService content, StatService stats, StatusService statuses,
                      ru.projectst.rpgcore.skill.SkillRuntime runtime,
                      ru.projectst.rpgcore.classes.ClassService playerClasses,
                      ru.projectst.rpgcore.cast.CastService casts,
                      ru.projectst.rpgcore.platform.gui.MenuContext menus) {
        this.content = content;
        this.stats = stats;
        this.statuses = statuses;
        this.runtime = runtime;
        this.playerClasses = playerClasses;
        this.casts = casts;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Пустая команда открывает интерфейс игроку и печатает список консоли:
        // игроку нужен экран, администратору — строки, которые можно скопировать.
        if (args.length == 0 && sender instanceof Player player) {
            new ru.projectst.rpgcore.platform.gui.MainMenu(menus, player).open(player);
            return true;
        }
        if (args.length > 0 && ADMIN_ONLY.contains(args[0].toLowerCase(Locale.ROOT))
                && !sender.hasPermission(PERMISSION_ADMIN)) {
            sender.sendMessage("§cЭта подкоманда только для администраторов");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e/rpg validate §7— проверить контент, ничего не применяя");
            sender.sendMessage("§e/rpg reload §7— перечитать контент");
            sender.sendMessage("§e/rpg debug <игрок> §7— статы и активные статусы");
            sender.sendMessage("§e/rpg why <игрок> <статус> §7— почему статус не действует");
            sender.sendMessage("§e/rpg cast <навык> §7— применить навык со всеми проверками");
            sender.sendMessage("§e/rpg cast <навык> raw [ур] §7— в обход маны и перезарядки");
            sender.sendMessage("§e/rpg slot <номер> §7— применить навык из слота");
            sender.sendMessage("§e/rpg mana §7— запас маны и перезарядки");
            sender.sendMessage("§e/rpg §7— открыть интерфейс (или §f/rpg menu§7)");
            sender.sendMessage("§e/rpg skills §7— навыки своего класса");
            sender.sendMessage("§e/rpg progress §7— уровень, опыт и очки");
            sender.sendMessage("§e/rpg xp <сколько> §7— выдать себе опыт для проверки");
            sender.sendMessage("§e/rpg class <класс> §7— выбрать класс");
            sender.sendMessage("§e/rpg unlock <навык> §7— изучить навык");
            sender.sendMessage("§e/rpg upgrade <навык> §7— вложить очко в уровень навыка");
            sender.sendMessage("§e/rpg bind <слот> <навык> §7— повесить навык на слот");
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "validate" -> validate(sender);
            case "reload" -> reload(sender);
            case "debug" -> debug(sender, args);
            case "why" -> why(sender, args);
            case "cast" -> cast(sender, args);
            case "slot" -> castSlot(sender, args);
            case "mana" -> mana(sender);
            case "menu" -> openMenu(sender);
            case "skills" -> listSkills(sender);
            case "progress" -> progress(sender);
            case "xp" -> giveXp(sender, args);
            case "class" -> chooseClass(sender, args);
            case "unlock" -> unlock(sender, args);
            case "upgrade" -> upgrade(sender, args);
            case "bind" -> bind(sender, args);
            default -> {
                sender.sendMessage("§cНеизвестная подкоманда: " + args[0]);
                yield true;
            }
        };
    }

    private boolean validate(CommandSender sender) {
        ContentErrors errors = content.validateOnly();
        report(sender, errors, "Проверка");
        return true;
    }

    private boolean reload(CommandSender sender) {
        ContentErrors errors = content.reload();
        report(sender, errors, "Перезагрузка");
        sender.sendMessage("§7Статов: §f" + content.stats().size()
                + "§7, статусов: §f" + content.statuses().size()
                + "§7, навыков: §f" + content.skills().size()
                + "§7, классов: §f" + content.playerClasses().size());
        return true;
    }

    private void report(CommandSender sender, ContentErrors errors, String what) {
        if (errors.isEmpty()) {
            sender.sendMessage("§a" + what + ": ошибок нет");
            return;
        }
        sender.sendMessage("§c" + what + ": ошибок " + errors.count());
        for (ContentError error : errors.all()) {
            sender.sendMessage("§7  " + error.at() + " §f" + error.path() + "§7: " + error.what());
        }
    }

    private boolean debug(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cНужно имя игрока: /rpg debug <игрок>");
            return true;
        }
        UUID uuid = resolve(sender, args[1]);
        if (uuid == null) {
            return true;
        }

        StatSnapshot snapshot = stats.snapshot(uuid);
        sender.sendMessage("§eСтаты §7" + args[1]);
        snapshot.asMap().forEach((id, value) ->
                sender.sendMessage("§7  " + id + ": §f" + trim(value)));
        sender.sendMessage("§7  источники: §f" + String.join(", ", stats.sources(uuid)));

        List<ActiveStatus> active = statuses.all(uuid);
        sender.sendMessage("§eСтатусы §7(" + active.size() + ")");
        for (ActiveStatus status : active) {
            String suppressed = statuses.suppressedBy(uuid, status.id())
                    .map(by -> " §c(подавлен: " + by + ")").orElse("");
            sender.sendMessage("§7  " + status.id() + " x" + status.stacks()
                    + " §7осталось §f" + status.remaining(nowTicks()) + "§7т"
                    + " §8от " + status.source() + suppressed);
        }
        return true;
    }

    private boolean why(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cНужны игрок и статус: /rpg why <игрок> <статус>");
            return true;
        }
        UUID uuid = resolve(sender, args[1]);
        if (uuid == null) {
            return true;
        }
        String statusId = args[2].toLowerCase(Locale.ROOT);

        if (content.statuses().find(statusId).isEmpty()) {
            sender.sendMessage("§cСтатус не объявлен: " + statusId);
            return true;
        }
        if (!statuses.isPresent(uuid, statusId)) {
            sender.sendMessage("§7Статус §f" + statusId + " §7не наложен на этого игрока");
            return true;
        }
        var suppressor = statuses.suppressedBy(uuid, statusId);
        if (suppressor.isPresent()) {
            sender.sendMessage("§cСтатус §f" + statusId + " §cподавлен статусом §f"
                    + suppressor.get() + " §7(правило suppresses)");
        } else {
            sender.sendMessage("§aСтатус §f" + statusId + " §aналожен и действует");
        }
        return true;
    }

    /**
     * Применение навыка.
     *
     * <p>По умолчанию идёт через {@code CastService}, то есть со всеми
     * проверками и списанием ресурсов — так же, как по нажатию клавиши.
     * Слово {@code raw} третьим аргументом пропускает ворота и запускает
     * навык напрямую: это нужно для отладки баланса, когда уровень задаётся
     * руками, и названо явно, чтобы случайно не измерять урон мимо кулдауна.
     */
    private boolean cast(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду выполняет игрок");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cНужен навык: /rpg cast <навык>");
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        boolean raw = args.length > 2 && args[2].equalsIgnoreCase("raw");
        if (raw && !sender.hasPermission(PERMISSION_ADMIN)) {
            sender.sendMessage("§cОбход проверок — только для администраторов");
            return true;
        }

        if (!raw) {
            var out = casts.cast(player.getUniqueId(), id);
            sender.sendMessage((out.succeeded() ? "§a" : "§c") + out);
            return true;
        }

        var skill = content.skills().find(id);
        if (skill.isEmpty()) {
            sender.sendMessage("§cНавык не загружен: " + args[1]);
            return true;
        }
        int level = 1;
        if (args.length > 3) {
            try {
                level = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cУровень должен быть числом");
                return true;
            }
        }
        runtime.cast(player.getUniqueId(), skill.get(), level);
        sender.sendMessage("§7Выполнен §f" + skill.get().id() + " §7уровня §f" + level
                + " §8(в обход маны и перезарядки)");
        return true;
    }

    private boolean castSlot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду выполняет игрок");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cНужен номер слота: /rpg slot <номер>");
            return true;
        }
        int slot;
        try {
            slot = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cНомер слота должен быть числом");
            return true;
        }
        var out = casts.castSlot(player.getUniqueId(), slot);
        sender.sendMessage((out.succeeded() ? "§a" : "§c") + out);
        return true;
    }

    /** Мана, перезарядки и запрещающий статус — всё, что решает исход нажатия. */
    private boolean mana(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду выполняет игрок");
            return true;
        }
        UUID id = player.getUniqueId();
        sender.sendMessage("§6" + casts.resource().displayName(id) + ": §f"
                + trim(Math.floor(casts.resource().current(id)))
                + "§7/§f" + trim(casts.resource().max(id)));
        casts.blockingStatus(id).ifPresent(status ->
                sender.sendMessage("§cКасты запрещены статусом §f" + status.id()));

        boolean any = false;
        for (String skillId : content.skills().ids()) {
            long left = casts.cooldowns().remaining(id, skillId);
            if (left > 0) {
                sender.sendMessage("§7перезарядка §f" + skillId + " §7— §f"
                        + trim(Math.round(left / 2.0) / 10.0) + " с");
                any = true;
            }
        }
        if (!any) {
            sender.sendMessage("§7перезарядок нет");
        }
        return true;
    }

    /** Интерфейс. Те же правила, что у команд: экран спрашивает те же сервисы. */
    private boolean openMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cИнтерфейс открывает игрок");
            return true;
        }
        new ru.projectst.rpgcore.platform.gui.MainMenu(menus, player).open(player);
        return true;
    }

    /**
     * Навыки своего класса: что есть, что изучено и чего не хватает.
     *
     * <p>Служебные и пассивные навыки сюда не попадают: изучить или
     * повесить их нельзя, а список, где половина строк ни на что не годится,
     * хуже пустого.
     */
    private boolean listSkills(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду выполняет игрок");
            return true;
        }
        UUID id = player.getUniqueId();
        var def = playerClasses.classOf(id);
        if (def.isEmpty()) {
            sender.sendMessage("§cКласс не выбран: /rpg class <класс>");
            return true;
        }
        var data = playerClasses.snapshot(id);
        sender.sendMessage("§6" + def.get().display() + "§7, свободных очков: §f"
                + data.unspentPoints());

        List<ru.projectst.rpgcore.skill.SkillDef> own = new ArrayList<>();
        for (var skill : content.skills().all()) {
            if (skill.selectable() && skill.classId().equals(def.get().id())) {
                own.add(skill);
            }
        }
        own.sort(java.util.Comparator.comparingInt(ru.projectst.rpgcore.skill.SkillDef::tier));

        for (var skill : own) {
            int level = data.skillLevel(skill.id());
            int required = def.get().levelForTier(skill.tier());
            String state;
            if (level > 0) {
                state = "§aур. " + level;
            } else if (data.level() < required) {
                state = "§cс уровня " + required;
            } else {
                state = "§eможно изучить";
            }
            String slot = "";
            for (var bound : data.slotBindings().entrySet()) {
                if (bound.getValue().equals(skill.id())) {
                    slot = " §8[слот " + bound.getKey() + "]";
                }
            }
            sender.sendMessage("§8" + skill.tier() + ". §f" + skill.display()
                    + " §8(" + skill.id() + ") " + state + slot);
        }
        return true;
    }

    /** Уровень, опыт и очки: то, по чему игрок решает, куда вкладываться. */
    private boolean progress(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду выполняет игрок");
            return true;
        }
        UUID id = player.getUniqueId();
        var def = playerClasses.classOf(id);
        if (def.isEmpty()) {
            sender.sendMessage("§cКласс не выбран: /rpg class <класс>");
            return true;
        }
        var data = playerClasses.snapshot(id);
        sender.sendMessage("§6" + def.get().display() + " §7— уровень §f" + data.level()
                + "§7/§f" + def.get().maxLevel());
        double left = playerClasses.xpToNextLevel(id);
        sender.sendMessage(left > 0
                ? "§7опыт: §f" + trim(Math.floor(data.xp()))
                        + " §7— до следующего §f" + trim(Math.ceil(left))
                : "§7предел уровня");
        sender.sendMessage("§7свободных очков: §f" + data.unspentPoints());

        for (var entry : data.slotBindings().entrySet()) {
            int level = data.skillLevel(entry.getValue());
            sender.sendMessage("§8слот " + entry.getKey() + ": §f" + entry.getValue()
                    + " §7ур. §f" + level);
        }
        return true;
    }

    /**
     * Выдача опыта себе. Нужна, чтобы проверять кривую и уровни, не убивая
     * мобов руками; доступ ограничен правом в plugin.yml.
     */
    private boolean giveXp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду выполняет игрок");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cНужно число: /rpg xp <сколько>");
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cОпыт должен быть числом");
            return true;
        }
        var out = playerClasses.addExperience(player.getUniqueId(), amount);
        sender.sendMessage("§a" + out);
        return true;
    }

    private boolean upgrade(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду выполняет игрок");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cНужен навык: /rpg upgrade <навык>");
            return true;
        }
        var out = playerClasses.upgrade(player.getUniqueId(), args[1].toLowerCase(Locale.ROOT));
        sender.sendMessage((out.succeeded() ? "§a" : "§c") + out);
        return true;
    }

    private boolean chooseClass(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду выполняет игрок");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cДоступные классы: §f"
                    + String.join(", ", content.playerClasses().ids()));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (playerClasses.setClass(player.getUniqueId(), id)) {
            sender.sendMessage("§aКласс выбран: §f" + id
                    + " §7(изученное и слоты сброшены)");
        } else {
            sender.sendMessage("§cТакого класса нет: " + id);
        }
        return true;
    }

    private boolean unlock(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду выполняет игрок");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cНужен навык: /rpg unlock <навык>");
            return true;
        }
        var out = playerClasses.unlock(player.getUniqueId(), args[1].toLowerCase(Locale.ROOT));
        sender.sendMessage((out.succeeded() ? "§a" : "§c") + out);
        return true;
    }

    private boolean bind(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду выполняет игрок");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§cНужны слот и навык: /rpg bind <слот> <навык>");
            return true;
        }
        int slot;
        try {
            slot = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cНомер слота должен быть числом");
            return true;
        }
        var out = playerClasses.bind(player.getUniqueId(), slot, args[2].toLowerCase(Locale.ROOT));
        sender.sendMessage((out.succeeded() ? "§a" : "§c") + out);
        return true;
    }

    private UUID resolve(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        if (offline != null) {
            return offline.getUniqueId();
        }
        sender.sendMessage("§cИгрок не найден: " + name);
        return null;
    }

    private static long nowTicks() {
        return Bukkit.getCurrentTick();
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            boolean admin = sender.hasPermission(PERMISSION_ADMIN);
            return SUB.stream()
                    .filter(s -> admin || !ADMIN_ONLY.contains(s))
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("debug") || args[0].equalsIgnoreCase("why"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("cast")
                || args[0].equalsIgnoreCase("unlock") || args[0].equalsIgnoreCase("upgrade")
                || args[0].equalsIgnoreCase("bind"))) {
            // Служебные и чужие навыки в подсказку не идут: предложить то,
            // что всё равно откажет, — это то же самое неправильное
            // использование, неотличимое от правильного, только в подсказке.
            String ownClass = sender instanceof Player player
                    ? playerClasses.classOf(player.getUniqueId())
                            .map(ru.projectst.rpgcore.classes.ClassDef::id).orElse(null)
                    : null;
            List<String> ids = new ArrayList<>();
            for (var skill : content.skills().all()) {
                boolean mine = ownClass == null || skill.classId().equals(ownClass);
                if (skill.selectable() && mine) {
                    ids.add(skill.id());
                }
            }
            return ids;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("why")) {
            List<String> ids = new ArrayList<>();
            content.statuses().all().forEach(def -> ids.add(def.id()));
            return ids;
        }
        return List.of();
    }
}
