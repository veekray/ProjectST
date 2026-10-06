package ru.projectst.rpgcore.platform.gui;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.projectst.rpgcore.classes.ClassDef;

/**
 * Главный экран: кто ты, сколько у тебя чего и куда идти дальше.
 *
 * <p>Здесь же видно ресурс и перезарядки — то, что решает исход нажатия. Это то
 * же требование, что у {@code /rpg mana}, только глазами: причина отказа должна
 * быть видна до нажатия, а не после.
 */
public final class MainMenu extends Menu {

    private final MenuContext context;
    private final Player player;

    public MainMenu(MenuContext context, Player player) {
        this.context = context;
        this.player = player;
    }

    @Override
    protected Component title() {
        return gold("Персонаж");
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
            put(22, item(Material.BOOK, yellow("Выбрать класс"),
                    List.of(grey("Класс ещё не выбран."),
                            grey("Без него навыки недоступны."))),
                    () -> new ClassMenu(context, player).open(player));
            return;
        }

        ClassDef own = def.get();
        var data = context.playerClasses().snapshot(id);
        double toNext = context.playerClasses().xpToNextLevel(id);

        List<Component> lore = new ArrayList<>();
        lore.add(grey("Уровень: ").append(white(data.level() + " / " + own.maxLevel())));
        lore.add(grey("Опыт: ").append(white(number(Math.floor(data.xp())))));
        lore.add(toNext > 0
                ? grey("До следующего: ").append(white(number(Math.ceil(toNext))))
                : yellow("Предел уровня"));
        lore.add(grey("Свободных очков: ").append(white(String.valueOf(data.unspentPoints()))));
        put(13, item(material(own.icon(), Material.BOOK),
                gold(own.display().replace("&", "")), lore));

        // Ресурс и запрет каста: всё, что решает исход нажатия.
        var resource = context.casts().resource();
        List<Component> resourceLore = new ArrayList<>();
        resourceLore.add(grey(resource.displayName(id) + ": ")
                .append(aqua(number(Math.floor(resource.current(id)))
                        + " / " + number(resource.max(id)))));
        context.casts().blockingStatus(id).ifPresent(status ->
                resourceLore.add(red("Касты запрещены: " + status.id())));
        put(11, item(Material.EXPERIENCE_BOTTLE, aqua(resource.displayName(id)), resourceLore),
                null);

        put(15, item(Material.ENCHANTED_BOOK, yellow("Навыки"),
                List.of(grey("Изучить и усилить."),
                        grey("Очков: ").append(white(String.valueOf(data.unspentPoints()))))),
                () -> new SkillsMenu(context, player).open(player));

        put(29, item(Material.HOPPER, yellow("Слоты"),
                List.of(grey("Что на какой клавише."),
                        grey("Клавиши назначаются в моде."))),
                () -> new SlotsMenu(context, player).open(player));

        put(31, item(Material.AMETHYST_SHARD, yellow("Артефакты"),
                List.of(grey("Ячейки для артефактов."),
                        grey("Пока лежат в них — статы работают."),
                        grey("Ячеек: ").append(white(
                                String.valueOf(context.artifacts().slotCount()))))),
                () -> new ArtifactsMenu(context, player).open(player));

        put(33, item(Material.IRON_SWORD, yellow("Статы"),
                List.of(grey("Снимок всех значений."))),
                () -> new StatsMenu(context, player).open(player));
    }
}
