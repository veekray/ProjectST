package ru.projectst.rpgcore.platform;

import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import ru.projectst.rpgcore.cast.CastService;
import ru.projectst.rpgcore.skill.MinionService;
import ru.projectst.rpgcore.skill.SkillTrigger;

/**
 * Превращает события сервера в срабатывания пассивных навыков.
 *
 * <p>Единственное место, где это происходит. Поэтому на вопрос «от чего вообще
 * может сработать навык» отвечает список методов этого класса, а не поиск по
 * всем файлам контента — в старом стеке триггеры были рассыпаны по строкам вида
 * {@code ~onDamaged} в десятках файлов, и полного списка не знал никто.
 *
 * <p>Приоритет {@code MONITOR} и {@code ignoreCancelled}: если урон отменил
 * регион или другой плагин, пассивка не срабатывает. Иначе навык «при
 * получении урона» работал бы там, где урона не было.
 */
public final class TriggerListener implements Listener {

    private final CastService casts;
    private final MinionService minions;

    public TriggerListener(CastService casts, MinionService minions) {
        this.casts = casts;
        this.minions = minions;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        if (victim instanceof Player hurt) {
            casts.fire(hurt.getUniqueId(), SkillTrigger.ON_DAMAGED, damager.getUniqueId());
        }
        // Урон от своего призванного считается уроном его владельца: иначе
        // «когда я наношу урон» не срабатывало бы от укуса собственного зверя,
        // хотя для игрока это тот же удар.
        UUID attacker = ownerOf(damager);
        if (attacker != null) {
            casts.fire(attacker, SkillTrigger.ON_DEAL_DAMAGE, victim.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        // Призванный умер — снимаем с учёта здесь же, пока его владелец ещё
        // известен: запись, оставшаяся после смерти, защищала бы от урона
        // уже несуществующее существо.
        minions.forget(event.getEntity().getUniqueId());

        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            casts.fire(killer.getUniqueId(), SkillTrigger.ON_KILL,
                    event.getEntity().getUniqueId());
        }
    }

    /** Игрок, которому принадлежит удар: он сам либо владелец призванного. */
    private UUID ownerOf(Entity damager) {
        if (damager instanceof Player player) {
            return player.getUniqueId();
        }
        return minions.of(damager.getUniqueId())
                .map(minion -> minion.owner())
                .orElse(null);
    }
}
