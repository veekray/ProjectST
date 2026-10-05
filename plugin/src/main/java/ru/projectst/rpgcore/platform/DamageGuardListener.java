package ru.projectst.rpgcore.platform;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import ru.projectst.rpgcore.status.ActiveStatus;
import ru.projectst.rpgcore.status.StatusCategory;
import ru.projectst.rpgcore.status.StatusRegistry;
import ru.projectst.rpgcore.status.StatusService;

/**
 * Отмена урона статусами: изъятие из боя и поглощающая кора.
 *
 * <p><b>Зачем отдельный слушатель.</b> Конвейер урона считает только наш урон —
 * от навыков. Обычный удар мечом, падение, огонь и стрела идут мимо него, и до
 * этого класса кора друида не отменяла ровно то, ради чего она есть: удар по
 * тебе. Статус, который спасает от половины источников урона и молчит про
 * остальные, хуже отсутствующего.
 *
 * <p>Что именно отменять, решает контент: категория {@code immunity} отменяет
 * любой урон целиком, метка {@code absorb-hit} — один удар за стак. Поэтому
 * кора держит столько ударов, сколько у неё зарядов, а кокон колдуна не
 * пропускает ничего, и ни то, ни другое не названо в коде по имени.
 *
 * <p>Приоритет {@code HIGHEST}, то есть позже триггеров: пассивка «когда по мне
 * попали» должна успеть ответить до того, как удар будет отменён. Иначе кора
 * отменяла бы урон и заодно собственный ответ.
 */
public final class DamageGuardListener implements Listener {

    /** Метка статуса: пока он есть, каждый стак гасит один удар. */
    public static final String TAG_ABSORB_HIT = "absorb-hit";

    private final StatusService statuses;
    private final StatusRegistry registry;

    public DamageGuardListener(StatusService statuses, StatusRegistry registry) {
        this.statuses = statuses;
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        // Изъятие из боя: не проходит ничего и ни от кого.
        for (ActiveStatus status : statuses.acting(victim.getUniqueId())) {
            if (status.category() == StatusCategory.IMMUNITY) {
                event.setCancelled(true);
                return;
            }
        }

        // Заряд поглощения: гасит один удар и тратится.
        for (ActiveStatus status : statuses.acting(victim.getUniqueId())) {
            if (registry.find(status.id()).filter(def -> def.hasTag(TAG_ABSORB_HIT))
                    .isEmpty()) {
                continue;
            }
            event.setCancelled(true);
            statuses.removeStack(victim.getUniqueId(), status.id());

            if (victim instanceof Player player) {
                int left = statuses.all(player.getUniqueId()).stream()
                        .filter(active -> active.id().equals(status.id()))
                        .mapToInt(ActiveStatus::stacks).findFirst().orElse(0);
                player.sendActionBar(Component.text(
                        left > 0 ? "Удар поглощён, зарядов осталось: " + left
                                : "Удар поглощён, защита спала",
                        NamedTextColor.GOLD));
            }
            return;
        }
    }
}
