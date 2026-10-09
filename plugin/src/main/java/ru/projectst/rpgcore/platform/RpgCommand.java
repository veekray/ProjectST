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
            "menu", "cast", "slot", "dash", "class", "skills", "unlock", "upgrade", "bind",
            "resource",
            "progress", "xp", "give", "items", "forge", "mobs", "spawn",
            "convert", "client", "area", "content", "reset");

    /** Подкоманды, которые меняют мир или смотрят чужие данные. */
    private static final Set<String> ADMIN_ONLY =
            Set.of("validate", "reload", "debug", "why", "xp", "give", "forge", "spawn", "area",
                    "content",
                    "convert", "reset");

    private static final String PERMISSION_ADMIN = "rpgcore.admin";

    private final ContentService content;
    private final StatService stats;
    private final StatusService statuses;
    private final ru.projectst.rpgcore.skill.SkillRuntime runtime;
    private final ru.projectst.rpgcore.classes.ClassService playerClasses;
    private final ru.projectst.rpgcore.cast.CastService casts;
    private final ru.projectst.rpgcore.platform.gui.MenuContext menus;
    private final RpgItems rpgItems;
    private final EquipmentWatcher equipment;
    private final RecipeRegistrar recipes;
    private final MobService mobs;
    private final ClientLink clientLink;
    private final java.nio.file.Path dataFolder;
    private final ru.projectst.rpgcore.platform.gui.ForgeContext forgeMenus;
    /** Файлы контента и их версии из jar; {@code null}, если сверка не удалась. */
    private final ContentFiles contentFiles;

    public RpgCommand(ContentService content, StatService stats, StatusService statuses,
                      ru.projectst.rpgcore.skill.SkillRuntime runtime,
                      ru.projectst.rpgcore.classes.ClassService playerClasses,
                      ru.projectst.rpgcore.cast.CastService casts,
                      ru.projectst.rpgcore.platform.gui.MenuContext menus,
                      RpgItems rpgItems, EquipmentWatcher equipment,
                      RecipeRegistrar recipes, MobService mobs, ClientLink clientLink,
                      ru.projectst.rpgcore.platform.gui.ForgeContext forgeMenus,
                      java.nio.file.Path dataFolder, ContentFiles contentFiles) {
        this.content = content;
        this.stats = stats;
        this.statuses = statuses;
        this.runtime = runtime;
        this.playerClasses = playerClasses;
        this.casts = casts;
        this.menus = menus;
        this.rpgItems = rpgItems;
        this.equipment = equipment;
        this.recipes = recipes;
        this.mobs = mobs;
        this.clientLink = clientLink;
        this.forgeMenus = forgeMenus;
        this.contentFiles = contentFiles;
        this.dataFolder = dataFolder;
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
            sender.sendMessage("§e/rpg dash §7— врождённый рывок (по взгляду:"
                    + " направление хода знает только мод)");
            sender.sendMessage("§e/rpg resource §7— запасы и перезарядки");
            sender.sendMessage("§e/rpg §7— открыть интерфейс (или §f/rpg menu§7)");
            sender.sendMessage("§e/rpg skills §7— навыки своего класса");
            sender.sendMessage("§e/rpg progress §7— уровень, опыт и очки");
            sender.sendMessage("§e/rpg xp <сколько> §7— выдать себе опыт для проверки");
            sender.sendMessage("§e/rpg items §7— предметы: окно у администратора,"
                    + " список в чате у остальных");
            sender.sendMessage("§e/rpg give <предмет> [сколько] §7— выдать себе предмет");
            sender.sendMessage("§e/rpg forge §7— верстак: собрать предмет со статами"
                    + " и получить его в руки");
            sender.sendMessage("§e/rpg mobs §7— список мобов и правил спавна");
            sender.sendMessage("§e/rpg spawn <моб> §7— поставить моба перед собой");
            sender.sendMessage("§e/rpg convert §7— перенести мобов из convert-in");
            sender.sendMessage("§e/rpg client §7— у кого стоит клиентский мод");
            sender.sendMessage("§e/rpg content [update] §7— какие файлы контента отличаются"
                    + " от версии в плагине; update — заменить их, прежние в backup");
            sender.sendMessage("§e/rpg area §7— показывать в чате радиус каждой области"
                    + " своих навыков: база, стат, итог и сколько задето");
            sender.sendMessage("§e/rpg reset <игрок> <что> §7— обнулить игрока целиком"
                    + " или частью");
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
            case "dash" -> dash(sender);
            // «mana» оставлено рядом с «resource»: команда жила под этим именем,
            // и отнимать её молча — то же, что молчаливый отказ.
            case "resource", "mana" -> resource(sender);
            case "menu" -> openMenu(sender);
            case "skills" -> listSkills(sender);
            case "progress" -> progress(sender);
            case "xp" -> giveXp(sender, args);
            case "give" -> give(sender, args);
            case "items" -> items(sender);
            case "forge" -> forge(sender);
            case "mobs" -> listMobs(sender);
            case "spawn" -> spawnMob(sender, args);
            case "convert" -> convert(sender);
            case "client" -> clientStatus(sender);
            case "area" -> areaTrace(sender);
            case "content" -> contentFiles(sender, args);
            case "reset" -> reset(sender, args);
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

    private boolean contentFiles(CommandSender sender, String[] args) {
        if (contentFiles == null) {
            sender.sendMessage("§cСверка контента с плагином не удалась при запуске: см. журнал.");
            return true;
        }
        try {
            if (args.length > 1 && args[1].equalsIgnoreCase("update")) {
                java.nio.file.Path backup = dataFolder.resolve("backup").resolve(
                        java.time.LocalDateTime.now().format(
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")));
                ContentFiles.Report report = contentFiles.forceUpdate(backup);
                sender.sendMessage("§aКонтент приведён к версии плагина: обновлено §f"
                        + report.updated().size() + "§a, добавлено §f" + report.added().size()
                        + (report.updated().isEmpty() ? ""
                                : "§a. Прежние файлы: §f" + dataFolder.relativize(backup)));
                // Перечитать сразу: иначе новые файлы лежали бы на диске, а
                // играли бы старые до перезапуска.
                return reload(sender);
            }
            List<String> outdated = contentFiles.outdated();
            if (outdated.isEmpty()) {
                sender.sendMessage("§aВесь поставляемый контент совпадает с версией плагина.");
            } else {
                sender.sendMessage("§eОтличаются от версии в плагине: §f" + outdated.size()
                        + "§e. Заменить: §f/rpg content update §7(прежние уйдут в backup)");
                outdated.stream().limit(15).forEach(name -> sender.sendMessage("§7  " + name));
                if (outdated.size() > 15) {
                    sender.sendMessage("§7  … и ещё " + (outdated.size() - 15));
                }
            }
        } catch (java.io.IOException e) {
            sender.sendMessage("§cФайлы контента не читаются: " + e.getMessage());
        }
        return true;
    }

    private boolean areaTrace(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cТолько для игрока: радиус считается от него.");
            return true;
        }
        boolean on = runtime.toggleAreaTrace(player.getUniqueId());
        sender.sendMessage(on
                ? "§aРадиус областей: показывается. §7Каждый шаг с областью пишет в чат"
                        + " базу из баланса, множитель стата и итог — им же рисуется граница."
                : "§7Радиус областей: скрыт.");
        return true;
    }

    private boolean validate(CommandSender sender) {
        ContentErrors errors = content.validateOnly();
        report(sender, errors, "Проверка");
        return true;
    }

    private boolean reload(CommandSender sender) {
        ContentErrors errors = content.reload();
        report(sender, errors, "Перезагрузка");
        // Рецепты перерегистрируются здесь же: иначе верстак остался бы с
        // прежними, и правка файла ничего бы не меняла до перезапуска.
        int added = recipes.reload(content.recipes());
        sender.sendMessage("§7Статов: §f" + content.stats().size()
                + "§7, статусов: §f" + content.statuses().size()
                + "§7, навыков: §f" + content.skills().size()
                + "§7, классов: §f" + content.playerClasses().size()
                + "§7, предметов: §f" + content.items().size()
                + "§7, рецептов: §f" + added
                + "§7, мобов: §f" + content.mobs().size());
        return true;
    }

    /**
     * Предметы: окно или список.
     *
     * <p>Окно открывается тому, кто может с предметами что-то сделать: из него
     * предмет выдаётся и открывается в верстаке, а это права администратора.
     * Остальным остаётся список в чате — он ничего не меняет и никому не вредит.
     */
    private boolean items(CommandSender sender) {
        if (sender instanceof Player player && sender.hasPermission(PERMISSION_ADMIN)) {
            new ru.projectst.rpgcore.platform.gui.ForgeItemsMenu(forgeMenus, player, 0)
                    .open(player);
            return true;
        }
        return listItems(sender);
    }

    /**
     * Верстак предметов.
     *
     * <p>Админская: она пишет файлы контента и перечитывает его. Поэтому и стоит
     * рядом с reload, а не в меню персонажа — игроку там делать нечего.
     */
    private boolean forge(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду выполняет игрок: верстак — это окно");
            return true;
        }
        new ru.projectst.rpgcore.platform.gui.ForgeMenu(forgeMenus, player).open(player);
        return true;
    }

    /** Выдача предмета себе: иначе проверить предмет можно только крафтом. */
    private boolean give(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду выполняет игрок");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cНужен предмет: /rpg give <предмет> [сколько]");
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        var def = content.items().find(id);
        if (def.isEmpty()) {
            sender.sendMessage("§cПредмет не загружен: " + id);
            return true;
        }
        int amount = 1;
        if (args.length > 2) {
            try {
                amount = Math.clamp(Integer.parseInt(args[2]), 1, 64);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cКоличество должно быть числом");
                return true;
            }
        }
        var leftover = player.getInventory().addItem(rpgItems.build(def.get(), amount));
        if (!leftover.isEmpty()) {
            sender.sendMessage("§7Часть не поместилась в инвентарь");
        }
        // Снаряжение сверяется сразу: предмет мог попасть в руку, и ждать
        // секунды до пересчёта статов незачем.
        equipment.apply(player);
        sender.sendMessage("§aВыдано: §f" + def.get().display() + " §7x" + amount);
        return true;
    }

    /**
     * Обнуление игрока.
     *
     * <p>Что именно пропадёт, названо до выполнения и перечислено в подсказке:
     * «обнулить» без уточнения — это команда, после которой администратор идёт
     * извиняться.
     */
    private boolean reset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cНужны игрок и что обнулять: /rpg reset <игрок> <что>");
            for (var kind : ru.projectst.rpgcore.classes.ResetKind.values()) {
                sender.sendMessage("§8  " + kind.key() + " §7— " + kind.what());
            }
            return true;
        }
        UUID target = resolve(sender, args[1]);
        if (target == null) {
            return true;
        }
        var kind = ru.projectst.rpgcore.classes.ResetKind.of(args[2]);
        if (kind.isEmpty()) {
            sender.sendMessage("§cНеизвестно, что обнулять: " + args[2]);
            return true;
        }

        String done = playerClasses.reset(target, kind.get());
        sender.sendMessage("§aОбнулено у §f" + args[1] + "§7: " + done);

        Player online = Bukkit.getPlayerExact(args[1]);
        if (online != null) {
            // Игрок должен узнать сам: иначе он обнаружит пропажу навыков в бою.
            online.sendMessage("§eВаш персонаж обнулён: §f" + done);
            equipment.apply(online);
            clientLink.sendMenu(online);
        }
        return true;
    }

    /**
     * Кто играет с модом.
     *
     * <p>Нужна ровно для того, чтобы не гадать: мод ничего не меняет в правилах,
     * поэтому его наличие иначе никак не проверить, а при разборе жалобы «у меня
     * не видно полосы» это первый вопрос.
     */
    private boolean clientStatus(CommandSender sender) {
        sender.sendMessage("§6Протокол канала: §f" + ru.projectst.rpgcore.net.Protocol.VERSION);
        sender.sendMessage("§7С модом сейчас: §f" + clientLink.connectedCount()
                + " §7из §f" + Bukkit.getOnlinePlayers().size());
        for (Player online : Bukkit.getOnlinePlayers()) {
            String version = clientLink.modVersion(online.getUniqueId()).orElse(null);
            sender.sendMessage("§8- §f" + online.getName() + " §7"
                    + (version == null ? "§8без мода" : "мод " + version));
        }
        sender.sendMessage("§8Плагин работает полностью и без мода: мод только показывает.");
        return true;
    }

    /** Мобы и правила подмены спавна: всё, что влияет на заселение мира. */
    private boolean listMobs(CommandSender sender) {
        if (content.mobs().size() == 0) {
            sender.sendMessage("§7Мобов не объявлено");
            return true;
        }
        sender.sendMessage("§6Мобов: §f" + content.mobs().size()
                + "§7, живых сейчас: §f" + mobs.living().size());
        for (var mob : content.mobs().all()) {
            sender.sendMessage("§8- §f" + mob.display() + " §8(" + mob.id() + ") §7"
                    + mob.entityType() + "§8, здоровье " + trim(mob.health())
                    + (mob.skills().isEmpty() ? "" : "§8, навыков " + mob.skills().size())
                    + (mob.drops().isEmpty() ? "" : "§8, дропа " + mob.drops().size()));
        }
        for (var rule : content.mobs().rules()) {
            sender.sendMessage("§8  правило: §7вместо §f" + rule.replaces()
                    + " §7появляется §f" + rule.mobId() + " §7с шансом §f"
                    + trim(rule.chance()) + "%");
        }
        return true;
    }

    /** Поставить моба перед собой: иначе проверить его можно только дождавшись спавна. */
    private boolean spawnMob(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду выполняет игрок");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cНужен моб: /rpg spawn <моб>");
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (content.mobs().find(id).isEmpty()) {
            sender.sendMessage("§cМоб не загружен: " + id);
            return true;
        }
        var where = player.getLocation().add(player.getLocation().getDirection().setY(0)
                .normalize().multiply(3));
        var spawned = mobs.spawn(id, where);
        sender.sendMessage(spawned.isPresent()
                ? "§aПоставлен: §f" + id
                : "§cНе удалось поставить: проверьте тип существа");
        return true;
    }

    /**
     * Перенос мобов MythicMobs.
     *
     * <p>Через каталоги, а не через чужую папку плагина: зависеть от расположения
     * MythicMobs значит сломаться от его обновления. Администратор кладёт файлы в
     * convert-in, забирает из convert-out вместе с отчётом.
     */
    private boolean convert(CommandSender sender) {
        java.nio.file.Path in = dataFolder.resolve("convert-in");
        java.nio.file.Path out = dataFolder.resolve("convert-out");
        if (!java.nio.file.Files.isDirectory(in)) {
            try {
                java.nio.file.Files.createDirectories(in);
            } catch (java.io.IOException e) {
                sender.sendMessage("§cНе создаётся каталог convert-in: " + e.getMessage());
                return true;
            }
            sender.sendMessage("§7Создан каталог §fconvert-in§7. Положите туда файлы"
                    + " мобов MythicMobs и повторите команду.");
            return true;
        }

        List<java.nio.file.Path> sources = new ArrayList<>();
        try (var files = java.nio.file.Files.list(in)) {
            files.filter(f -> f.toString().endsWith(".yml")).sorted().forEach(sources::add);
        } catch (java.io.IOException e) {
            sender.sendMessage("§cconvert-in не читается: " + e.getMessage());
            return true;
        }
        if (sources.isEmpty()) {
            sender.sendMessage("§7В convert-in нет файлов .yml");
            return true;
        }

        List<String> report = new ArrayList<>();
        int converted = 0;
        int skipped = 0;
        try {
            java.nio.file.Files.createDirectories(out);
            for (java.nio.file.Path source : sources) {
                String name = source.getFileName().toString();
                String text = java.nio.file.Files.readString(source,
                        java.nio.charset.StandardCharsets.UTF_8);
                var result = ru.projectst.rpgcore.convert.MobConverter.convert(name, text);
                converted += result.converted();
                skipped += result.skipped();
                report.addAll(result.report());
                for (var entry : result.files().entrySet()) {
                    java.nio.file.Files.writeString(out.resolve(entry.getKey()),
                            entry.getValue(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            java.nio.file.Files.writeString(out.resolve("REPORT.txt"),
                    String.join(System.lineSeparator(), report) + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            sender.sendMessage("§cОшибка записи: " + e.getMessage());
            return true;
        }

        sender.sendMessage("§aПеренесено мобов: §f" + converted
                + "§7, пропущено: §f" + skipped);
        sender.sendMessage("§7Непереносимых мест в отчёте: §f" + report.size()
                + " §8(convert-out/REPORT.txt)");
        sender.sendMessage("§7Файлы в §fconvert-out§7: проверьте и перенесите в §fmobs/§7,"
                + " затем §f/rpg reload");
        return true;
    }

    /** Что вообще объявлено: по идентификаторам их и выдают. */
    private boolean listItems(CommandSender sender) {
        if (content.items().size() == 0) {
            sender.sendMessage("§7Предметов не объявлено");
            return true;
        }
        sender.sendMessage("§6Предметов: §f" + content.items().size());
        for (var item : content.items().all()) {
            var rarity = content.items().rarity(item.rarityId());
            sender.sendMessage("§8- §f" + item.display() + " §8(" + item.id() + ") §7"
                    + rarity.display() + "§8, слот " + item.slot().key()
                    + (item.abilities().isEmpty() ? ""
                            : "§8, умений " + item.abilities().size()));
        }
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

    /**
     * Врождённый рывок из команды.
     *
     * <p>Идёт по взгляду, а не по ходу: направление хода знает мод, и в команде
     * его нет. Сказано об этом в подсказке — рывок, который «иногда не туда»,
     * выглядел бы поломкой.
     */
    private boolean dash(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду выполняет игрок");
            return true;
        }
        var out = casts.castInnate(player.getUniqueId(), null);
        sender.sendMessage((out.succeeded() ? "§a" : "§c") + out);
        return true;
    }

    /** Запасы, перезарядки и запрещающий статус — всё, что решает исход нажатия. */
    private boolean resource(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду выполняет игрок");
            return true;
        }
        UUID id = player.getUniqueId();
        sender.sendMessage("§6" + casts.resource().displayName(id) + ": §f"
                + trim(Math.floor(casts.resource().current(id)))
                + "§7/§f" + trim(casts.resource().max(id)));
        var stamina = ru.projectst.rpgcore.classes.ResourceSpec.STAMINA;
        sender.sendMessage("§6" + stamina.display() + ": §f"
                + trim(Math.floor(casts.resource().current(id, stamina)))
                + "§7/§f" + trim(casts.resource().max(id, stamina)));
        casts.blockingStatus(id).ifPresent(status ->
                sender.sendMessage("§cКасты запрещены статусом §f" + status.id()));

        boolean any = false;
        for (String skillId : content.skills().ids()) {
            int charges = content.skills().find(skillId)
                    .map(ru.projectst.rpgcore.skill.SkillDef::charges).orElse(1);
            long left = casts.cooldowns().remaining(id, skillId, charges);
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
        if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            List<String> kinds = new ArrayList<>();
            for (var kind : ru.projectst.rpgcore.classes.ResetKind.values()) {
                kinds.add(kind.key());
            }
            return kinds;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            List<String> ids = new ArrayList<>();
            content.mobs().ids().forEach(ids::add);
            return ids;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> ids = new ArrayList<>();
            content.items().ids().forEach(ids::add);
            return ids;
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
