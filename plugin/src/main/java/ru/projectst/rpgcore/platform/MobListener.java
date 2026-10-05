package ru.projectst.rpgcore.platform;

import java.util.function.DoubleSupplier;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import ru.projectst.rpgcore.mob.MobDef;
import ru.projectst.rpgcore.mob.MobDrop;
import ru.projectst.rpgcore.mob.MobTrigger;
import ru.projectst.rpgcore.mob.SpawnRule;

/**
 * Жизнь мобов: триггеры, дроп и подмена естественного спавна.
 *
 * <p>Дроп заменяет ванильный целиком, если объявлен хотя бы один свой: иначе
 * пустынный скорпион ронял бы и свой панцирь, и чешую серебрянки, и объяснить
 * второе было бы нечем.
 *
 * <p>Подмена идёт на естественном спавне и только на нём. Моб, появившийся по
 * нашей же команде или призванный навыком, под правило не попадает — иначе
 * правило «вместо серебрянки появляется скорпион» подменяло бы и скорпиона,
 * которого только что поставил администратор.
 */
public final class MobListener implements Listener {

    private final MobService mobs;
    private final RpgItems items;
    private final DoubleSupplier random;

    public MobListener(MobService mobs, RpgItems items, DoubleSupplier random) {
        this.mobs = mobs;
        this.items = items;
        this.random = random;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (mobs.idOf(entity).isPresent()) {
            return; // уже наш: правило не должно подменять само себя
        }
        String world = entity.getWorld().getName();
        String type = entity.getType().name();

        for (SpawnRule rule : mobs.registry().rulesFor(world, type)) {
            if (random.getAsDouble() * 100 >= rule.chance()) {
                continue;
            }
            var def = mobs.registry().find(rule.mobId());
            if (def.isEmpty()) {
                continue; // связывание об этом уже сказало
            }
            if (def.get().entityType().equals(type)) {
                // Тот же тип — достаточно переодеть на месте.
                mobs.apply(def.get(), entity);
            } else {
                event.setCancelled(true);
                mobs.spawn(rule.mobId(), entity.getLocation());
            }
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof LivingEntity victim) {
            mobs.defOf(victim).ifPresent(def -> mobs.fire(def, victim, MobTrigger.ON_DAMAGED,
                    event.getDamager().getUniqueId()));
        }
        if (event.getDamager() instanceof LivingEntity attacker) {
            mobs.defOf(attacker).ifPresent(def -> mobs.fire(def, attacker, MobTrigger.ON_ATTACK,
                    event.getEntity().getUniqueId()));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        var found = mobs.defOf(entity);
        if (found.isEmpty()) {
            return;
        }
        MobDef def = found.get();
        Player killer = entity.getKiller();

        mobs.fire(def, entity, MobTrigger.ON_DEATH,
                killer == null ? null : killer.getUniqueId());

        if (def.experience() >= 0) {
            event.setDroppedExp(def.experience());
        }
        if (def.drops().isEmpty()) {
            mobs.forget(entity.getUniqueId());
            return;
        }

        // Свой дроп заменяет ванильный целиком: иначе моб ронял бы и то, и другое.
        event.getDrops().clear();
        for (MobDrop drop : def.drops()) {
            int amount = drop.roll(random.getAsDouble(), random.getAsDouble());
            if (amount <= 0) {
                continue;
            }
            if (drop.custom()) {
                var item = itemStack(drop, amount);
                if (item != null) {
                    event.getDrops().add(item);
                }
            } else {
                Material material = Material.matchMaterial(drop.material());
                if (material != null) {
                    event.getDrops().add(new ItemStack(material, amount));
                }
            }
        }
        mobs.forget(entity.getUniqueId());
    }

    private ItemStack itemStack(MobDrop drop, int amount) {
        return items.registry().find(drop.itemId())
                .map(def -> items.build(def, amount))
                .orElse(null);
    }
}
