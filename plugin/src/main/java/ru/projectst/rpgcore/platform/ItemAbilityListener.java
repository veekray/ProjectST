package ru.projectst.rpgcore.platform;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import ru.projectst.rpgcore.cast.CastOutcome;
import ru.projectst.rpgcore.cast.CastService;
import ru.projectst.rpgcore.item.ItemAbility;
import ru.projectst.rpgcore.item.ItemDef;
import ru.projectst.rpgcore.item.ItemTrigger;

/**
 * Умения предметов.
 *
 * <p>Умение — это обычный каст через {@link CastService}: ресурс, перезарядка,
 * запрещающие статусы. Поэтому посох не стреляет под тишиной и не обходит
 * перезарядку, и объяснять это отдельно не нужно — правил всего одни.
 *
 * <p>Отказ по перезарядке молчит, остальные называются в строке действия. Это
 * разница между «нажал рано» и «что-то не так»: первое игрок понимает сам по
 * индикатору, второе обязано быть сказано.
 */
public final class ItemAbilityListener implements Listener {

    private final RpgItems items;
    private final CastService casts;

    public ItemAbilityListener(RpgItems items, CastService casts) {
        this.items = items;
        this.casts = casts;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return; // левая рука щёлкает тем же событием, и умение сработало бы дважды
        }
        Player player = event.getPlayer();
        var def = items.defOf(event.getItem());
        if (def.isEmpty()) {
            return;
        }

        ItemTrigger trigger = switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK ->
                    player.isSneaking() ? ItemTrigger.SNEAK_RIGHT_CLICK : ItemTrigger.RIGHT_CLICK;
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> ItemTrigger.LEFT_CLICK;
            default -> null;
        };
        if (trigger == null) {
            return;
        }
        // С зажатым Shift подходит и обычный правый щелчок, если отдельного
        // умения на Shift у предмета нет: иначе приседающий игрок терял бы
        // умение посоха без всякой причины.
        if (fire(player, def.get(), trigger) || trigger != ItemTrigger.SNEAK_RIGHT_CLICK) {
            return;
        }
        fire(player, def.get(), ItemTrigger.RIGHT_CLICK);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        var def = items.defOf(player.getInventory().getItemInMainHand());
        def.ifPresent(item -> fire(player, item, ItemTrigger.ON_HIT));
    }

    /** @return {@code true}, если у предмета было умение на этот триггер */
    private boolean fire(Player player, ItemDef def, ItemTrigger trigger) {
        boolean found = false;
        for (ItemAbility ability : def.abilities()) {
            if (ability.trigger() != trigger) {
                continue;
            }
            found = true;
            CastOutcome outcome = casts.castItem(player.getUniqueId(), ability.skillId());
            if (!outcome.succeeded() && outcome.kind() != CastOutcome.Kind.ON_COOLDOWN) {
                player.sendActionBar(Component.text(outcome.toString(), NamedTextColor.RED));
            }
        }
        return found;
    }
}
