package ru.projectst.rpgcore.platform;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import ru.projectst.rpgcore.skill.MinionService;

/**
 * Правила обращения призванных со своим владельцем.
 *
 * <p>Два запрета, и оба взяты из того, что ломалось в старом стеке: зверь бил
 * собственного хозяина, а хозяин — зверя. Там это лечили проверками внутри
 * каждого навыка; здесь это одно правило снаружи, одинаковое для всех
 * призванных.
 */
public final class MinionListener implements Listener {

    private final MinionService minions;

    public MinionListener(MinionService minions) {
        this.minions = minions;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        // Свой зверь не бьёт владельца.
        if (minions.isOwnMinion(victim.getUniqueId(), damager.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        // И владелец не бьёт своего зверя — ни рукой, ни площадным навыком.
        if (minions.isOwnMinion(damager.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Зверь не берёт в цель владельца.
     *
     * <p>Отдельно от урона: без этого он бесконечно пытался бы ударить хозяина,
     * проигрывая анимацию атаки впустую. Ровно это и выглядело как «волк всё
     * время машет лапами, хотя врагов рядом нет».
     */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() == null) {
            return;
        }
        if (minions.isOwnMinion(event.getTarget().getUniqueId(), event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
