package ru.projectst.rpgcore.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    private static final List<String> SUB = List.of("validate", "reload", "debug", "why", "cast", "class", "unlock", "bind");

    private final ContentService content;
    private final StatService stats;
    private final StatusService statuses;
    private final ru.projectst.rpgcore.skill.SkillRuntime runtime;
    private final ru.projectst.rpgcore.classes.ClassService playerClasses;

    public RpgCommand(ContentService content, StatService stats, StatusService statuses,
                      ru.projectst.rpgcore.skill.SkillRuntime runtime,
                      ru.projectst.rpgcore.classes.ClassService playerClasses) {
        this.content = content;
        this.stats = stats;
        this.statuses = statuses;
        this.runtime = runtime;
        this.playerClasses = playerClasses;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/rpg validate §7— проверить контент, ничего не применяя");
            sender.sendMessage("§e/rpg reload §7— перечитать контент");
            sender.sendMessage("§e/rpg debug <игрок> §7— статы и активные статусы");
            sender.sendMessage("§e/rpg why <игрок> <статус> §7— почему статус не действует");
            sender.sendMessage("§e/rpg cast <навык> §7— выполнить навык от своего лица");
            sender.sendMessage("§e/rpg class <класс> §7— выбрать класс");
            sender.sendMessage("§e/rpg unlock <навык> §7— изучить навык");
            sender.sendMessage("§e/rpg bind <слот> <навык> §7— повесить навык на слот");
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "validate" -> validate(sender);
            case "reload" -> reload(sender);
            case "debug" -> debug(sender, args);
            case "why" -> why(sender, args);
            case "cast" -> cast(sender, args);
            case "class" -> chooseClass(sender, args);
            case "unlock" -> unlock(sender, args);
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
     * Ручной запуск навыка. Нужен до появления классов и слотов: иначе первый
     * настоящий навык нельзя было бы проверить в игре вообще.
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
        var skill = content.skills().find(args[1].toLowerCase(Locale.ROOT));
        if (skill.isEmpty()) {
            sender.sendMessage("§cНавык не загружен: " + args[1]);
            return true;
        }
        int level = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        runtime.cast(player.getUniqueId(), skill.get(), level);
        sender.sendMessage("§7Выполнен §f" + skill.get().id() + " §7уровня §f" + level);
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
            return SUB.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("debug") || args[0].equalsIgnoreCase("why"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cast")) {
            List<String> ids = new ArrayList<>();
            content.skills().ids().forEach(ids::add);
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
