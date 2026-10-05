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
 * Слоты: что на какой клавише.
 *
 * <p>Экран двухступенчатый. Сначала виден ряд слотов — ровно столько, сколько
 * даёт класс, и под каждым написано, какое нажатие его применяет. Щелчок по
 * слоту открывает выбор из изученных навыков; щелчок по занятому слоту с
 * клавишей Shift его освобождает, и об этом сказано в подсказке, а не осталось
 * догадкой.
 */
public final class SlotsMenu extends Menu {

    private final MenuContext context;
    private final Player player;
    private final Integer choosingFor;

    public SlotsMenu(MenuContext context, Player player) {
        this(context, player, null);
    }

    private SlotsMenu(MenuContext context, Player player, Integer choosingFor) {
        this.context = context;
        this.player = player;
        this.choosingFor = choosingFor;
    }

    @Override
    protected Component title() {
        return choosingFor == null ? gold("Слоты") : gold("Слот " + choosingFor);
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void layout() {
        fillBorder();

        var id = player.getUniqueId();
        var def = context.playerClasses().classOf(id);
        if (def.isEmpty()) {
            put(13, item(Material.BARRIER, red("Класс не выбран"), List.of()),
                    () -> new ClassMenu(context, player).open(player));
            return;
        }

        if (choosingFor == null) {
            layoutSlots(def.get());
        } else {
            layoutChoice(def.get());
        }

        put(31, item(Material.ARROW, yellow("Назад"), List.of()),
                choosingFor == null
                        ? () -> new MainMenu(context, player).open(player)
                        : () -> new SlotsMenu(context, player).open(player));
    }

    private void layoutSlots(ClassDef own) {
        var data = context.playerClasses().snapshot(player.getUniqueId());

        for (int slot = 1; slot <= own.slots() && slot <= 7; slot++) {
            String bound = data.slotBindings().get(slot);
            List<Component> lore = new ArrayList<>();
            lore.add(grey("Нажатие: ").append(white("Shift + " + slot)));
            lore.add(Component.empty());

            Material material;
            Component name;
            if (bound == null) {
                material = Material.LIGHT_GRAY_STAINED_GLASS_PANE;
                name = grey("Слот " + slot + " — пусто");
                lore.add(yellow("Нажмите, чтобы выбрать навык"));
            } else {
                var skill = context.skills().find(bound);
                material = material(skill.map(SkillDef::icon).orElse("PAPER"), Material.PAPER);
                name = aqua(skill.map(SkillDef::display).orElse(bound));
                lore.add(grey("Уровень навыка: ").append(white(String.valueOf(
                        context.playerClasses().skillLevel(player.getUniqueId(), bound)))));
                lore.add(yellow("Нажмите, чтобы заменить"));
                lore.add(red("Shift — освободить слот"));
            }

            final int number = slot;
            put(9 + slot + 1, item(material, name, lore),
                    () -> new SlotsMenu(context, player, number).open(player));
        }
    }

    /** Выбор навыка для слота: только изученные, потому что остальные откажут. */
    private void layoutChoice(ClassDef own) {
        var id = player.getUniqueId();
        var data = context.playerClasses().snapshot(id);

        List<SkillDef> learned = new ArrayList<>();
        for (SkillDef skill : context.skills().all()) {
            if (skill.selectable() && skill.classId().equals(own.id())
                    && data.isUnlocked(skill.id())) {
                learned.add(skill);
            }
        }
        learned.sort(Comparator.comparingInt(SkillDef::tier).thenComparing(SkillDef::id));

        if (learned.isEmpty()) {
            put(13, item(Material.BARRIER, red("Нет изученных навыков"),
                    List.of(grey("Изучите их в разделе «Навыки»."))),
                    () -> new SkillsMenu(context, player).open(player));
            return;
        }

        int slot = 10;
        for (SkillDef skill : learned) {
            if (slot > 25) {
                break;
            }
            put(slot, item(material(skill.icon(), Material.PAPER), aqua(skill.display()),
                    List.of(grey("Ступень ").append(white(String.valueOf(skill.tier()))),
                            yellow("Нажмите, чтобы занять слот " + choosingFor))),
                    () -> bind(skill));
            slot++;
            if (slot % 9 == 8) {
                slot += 2;
            }
        }

        put(29, item(Material.BARRIER, red("Освободить слот " + choosingFor), List.of()),
                this::unbind);
    }

    private void bind(SkillDef skill) {
        ClassOutcome.Bind out = context.playerClasses()
                .bind(player.getUniqueId(), choosingFor, skill.id());
        player.sendMessage(out.succeeded()
                ? green("Слот " + choosingFor + ": ").append(white(skill.display()))
                : red(out.toString()));
        new SlotsMenu(context, player).open(player);
    }

    private void unbind() {
        context.playerClasses().unbind(player.getUniqueId(), choosingFor);
        player.sendMessage(grey("Слот " + choosingFor + " освобождён"));
        new SlotsMenu(context, player).open(player);
    }
}
