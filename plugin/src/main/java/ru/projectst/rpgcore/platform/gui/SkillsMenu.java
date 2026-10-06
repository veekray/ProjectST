package ru.projectst.rpgcore.platform.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.projectst.rpgcore.classes.ClassDef;
import ru.projectst.rpgcore.classes.ClassOutcome;
import ru.projectst.rpgcore.skill.SkillDef;

/**
 * Навыки класса: изучить, усилить, повесить на слот.
 *
 * <p>Главное в этом экране — подсказка под каждым навыком. Там написано, что
 * мешает: уровень, очки или то, что навык уже на максимуме. Кнопка, которая
 * просто не реагирует, — это и есть то самое «нажатие впустую без причины», от
 * которого затевался проект; в интерфейсе оно выглядит ещё хуже, чем в бою,
 * потому что игрок видит навык и не понимает, почему он недоступен.
 *
 * <p>Служебные и пассивные навыки сюда не попадают: их нельзя ни изучить, ни
 * повесить, а список, где половина строк ни на что не годится, хуже пустого.
 */
public final class SkillsMenu extends Menu {

    private static final int[] GRID = {10, 11, 12, 13, 14, 15, 16,
                                       19, 20, 21, 22, 23, 24, 25,
                                       28, 29, 30, 31, 32, 33, 34};

    private final MenuContext context;
    private final Player player;

    public SkillsMenu(MenuContext context, Player player) {
        this.context = context;
        this.player = player;
    }

    @Override
    protected Component title() {
        return gold("Навыки");
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected void layout() {
        fillBorder();

        var id = player.getUniqueId();
        var def = context.playerClasses().classOf(id);
        if (def.isEmpty()) {
            put(22, item(Material.BARRIER, red("Класс не выбран"), List.of()),
                    () -> new ClassMenu(context, player).open(player));
            return;
        }
        ClassDef own = def.get();
        var data = context.playerClasses().snapshot(id);

        List<SkillDef> skills = new ArrayList<>();
        for (SkillDef skill : context.skills().all()) {
            if (skill.selectable() && skill.classId().equals(own.id())) {
                skills.add(skill);
            }
        }
        skills.sort(Comparator.comparingInt(SkillDef::tier).thenComparing(SkillDef::id));

        for (int i = 0; i < skills.size() && i < GRID.length; i++) {
            SkillDef skill = skills.get(i);
            put(GRID[i], icon(own, skill, data), () -> click(skill));
        }

        put(40, item(Material.ARROW, yellow("Назад"), List.of()),
                () -> new MainMenu(context, player).open(player));
        put(38, item(Material.EXPERIENCE_BOTTLE, yellow("Свободных очков: "
                + data.unspentPoints()),
                List.of(grey("Очки дают уровни."))));
    }

    /** Иконка навыка со всем, что о нём нужно знать до щелчка. */
    private org.bukkit.inventory.ItemStack icon(ClassDef own, SkillDef skill,
                                                ru.projectst.rpgcore.data.PlayerData data) {
        int level = data.skillLevel(skill.id());
        int required = own.levelForTier(skill.tier());
        boolean enough = data.level() >= required;
        boolean maxed = level >= ru.projectst.rpgcore.classes.ClassService.MAX_SKILL_LEVEL;

        List<Component> lore = new ArrayList<>();
        lore.add(grey("Ступень ").append(white(String.valueOf(skill.tier()))));

        double cost = skill.resourceCost().resolve(
                contextBalance(skill), Math.max(1, level));
        double cooldown = skill.cooldown().resolve(
                contextBalance(skill), Math.max(1, level));
        lore.add(grey(own.resource().display() + ": ").append(white(number(cost))));
        lore.add(grey("Перезарядка: ").append(white(number(cooldown) + " с")));
        lore.add(Component.empty());

        if (level == 0) {
            if (!enough) {
                lore.add(red("Нужен уровень " + required));
            } else if (data.unspentPoints() < 1) {
                lore.add(red("Нет свободных очков"));
            } else {
                lore.add(green("Нажмите, чтобы изучить"));
            }
        } else {
            lore.add(grey("Уровень навыка: ").append(white(level + " / "
                    + ru.projectst.rpgcore.classes.ClassService.MAX_SKILL_LEVEL)));
            if (maxed) {
                lore.add(yellow("Максимальный уровень"));
            } else if (data.unspentPoints() < 1) {
                lore.add(red("Нет свободных очков"));
            } else {
                lore.add(green("Нажмите, чтобы вложить очко"));
            }
            lore.add(grey("Повесить на слот — в разделе «Слоты»"));
        }

        Material material = level > 0
                ? material(skill.icon(), Material.PAPER)
                : Material.GRAY_DYE;
        Component name = level > 0
                ? aqua(skill.display())
                : grey(skill.display());
        return item(material, name, lore);
    }

    /** Таблица баланса навыка: стоимость и перезарядку берём оттуда же, что бой. */
    private ru.projectst.rpgcore.balance.BalanceTable contextBalance(SkillDef skill) {
        return context.casts().balanceOf(skill.id());
    }

    private void click(SkillDef skill) {
        var id = player.getUniqueId();
        int level = context.playerClasses().skillLevel(id, skill.id());

        if (level == 0) {
            ClassOutcome.Unlock out = context.playerClasses().unlock(id, skill.id());
            player.sendMessage(out.succeeded()
                    ? green("Изучено: ").append(white(skill.display()))
                    : red(out.toString()));
        } else {
            ClassOutcome.Upgrade out = context.playerClasses().upgrade(id, skill.id());
            player.sendMessage(out.succeeded()
                    ? green(skill.display() + " — ").append(white(out.detail()))
                    : red(out.toString()));
        }
        // Экран собирается заново: иначе он показывал бы прежние числа после
        // того, как игрок уже что-то изменил.
        open(player);
    }
}
