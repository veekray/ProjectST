package ru.projectst.rpgcore.platform;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.Listener;
import ru.projectst.rpgcore.classes.ClassOutcome;
import ru.projectst.rpgcore.classes.ClassService;

/**
 * Источник опыта: убитые мобы.
 *
 * <p>Пока берётся ванильное число опыта с моба. Это временно и названо здесь
 * прямо: свои награды мобы объявят в M12 вместе с остальными своими числами.
 * Брать их из ваниля сейчас честнее, чем вписать коэффициент в код, который
 * потом никто не найдёт.
 *
 * <p>Опыт идёт тому, кто убил, и только если у него выбран класс. Молча копить
 * опыт без класса нельзя: он лёг бы в данные и выдал пачку уровней при выборе
 * класса, а выглядело бы это как ошибка.
 */
public final class ExperienceListener implements Listener {

    private final ClassService classes;

    public ExperienceListener(ClassService classes) {
        this.classes = classes;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || event.getDroppedExp() <= 0) {
            return;
        }
        if (classes.classOf(killer.getUniqueId()).isEmpty()) {
            return;
        }
        ClassOutcome.Experience out =
                classes.addExperience(killer.getUniqueId(), event.getDroppedExp());
        if (out.leveledUp()) {
            killer.sendActionBar(Component.text("Уровень " + out.level(), NamedTextColor.GOLD)
                    .append(Component.text("  +" + out.pointsGained() + " очк.",
                            NamedTextColor.YELLOW)));
        }
    }
}
