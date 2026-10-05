package ru.projectst.rpgcore.platform;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.projectst.rpgcore.status.StatusRegistry;
import ru.projectst.rpgcore.status.StatusService;

/**
 * Делает статусы контроля настоящими.
 *
 * <p>До этого класса стан и корни были записью в реестре: они запрещали касты и
 * показывались в интерфейсе, но цель продолжала ходить. Это ровно та дыра,
 * которую проект обещал не повторять — объявление, за которым ничего не стоит.
 *
 * <p>Что именно делает статус, решает контент меткой {@code immobilize}, а не
 * имя статуса в коде: новый контроль обездвиживает, потому что так написано в
 * его файле, а не потому, что кто-то вспомнил дописать сюда ещё один {@code if}.
 *
 * <p><b>Мобы и игроки обездвиживаются по-разному</b>, и иначе нельзя. Мобу
 * выключается ИИ — это полная остановка. Игроку сервер не может запретить
 * двигаться напрямую: клавиши нажимает он сам, а сервер лишь принимает
 * результат. Поэтому игроку ставится предельное замедление и запрет прыжка, а
 * скорость ходьбы обнуляется. Это не абсолютная неподвижность: сдвинуться на
 * половину блока он сможет. Честнее сказать это здесь, чем изображать, будто
 * контроль над игроком и над мобом устроен одинаково.
 */
public final class StatusEffects {

    /** Метка статуса: пока он действует, цель не может двигаться. */
    public static final String TAG_IMMOBILIZE = "immobilize";

    private static final PotionEffectType SLOWNESS =
            Registry.EFFECT.get(NamespacedKey.minecraft("slowness"));
    private static final PotionEffectType JUMP_BOOST =
            Registry.EFFECT.get(NamespacedKey.minecraft("jump_boost"));

    private final StatusService statuses;
    private final StatusRegistry registry;

    /** Кого мы уже обездвижили: по этому списку возвращаем свободу. */
    private final Set<UUID> held = new HashSet<>();

    public StatusEffects(StatusService statuses, StatusRegistry registry) {
        this.statuses = statuses;
        this.registry = registry;
    }

    /** Держится ли на цели контроль, который обездвиживает. */
    public boolean immobilized(UUID target) {
        return statuses.acting(target).stream()
                .anyMatch(status -> registry.find(status.id())
                        .filter(def -> def.hasTag(TAG_IMMOBILIZE)).isPresent());
    }

    /**
     * Сверяет состояние мира со статусами.
     *
     * <p>Сверка, а не реакция на наложение: статус может истечь сам, цель может
     * выгрузиться и вернуться, а сервер — перезапуститься. Реакция на событие
     * однажды пропустит один из этих случаев, и моб останется стоять навсегда.
     */
    public void tick() {
        Set<UUID> stillHeld = new HashSet<>();

        for (UUID id : statuses.targets()) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof LivingEntity living) || living.isDead()) {
                continue;
            }
            if (!immobilized(id)) {
                continue;
            }
            hold(living);
            stillHeld.add(id);
        }

        for (UUID id : held) {
            if (stillHeld.contains(id)) {
                continue;
            }
            if (Bukkit.getEntity(id) instanceof LivingEntity living && !living.isDead()) {
                release(living);
            }
        }
        held.clear();
        held.addAll(stillHeld);
    }

    private void hold(LivingEntity living) {
        if (living instanceof Mob mob) {
            mob.setAI(false);
            return;
        }
        if (living instanceof Player player) {
            // Скорость ходьбы в ноль: единственное, чем сервер действительно
            // останавливает игрока. Остальное — замедление и запрет прыжка —
            // чтобы инерция и прыжки не растаскивали его на полблока.
            player.setWalkSpeed(0f);
            applyShort(player, SLOWNESS, 6);
            applyShort(player, JUMP_BOOST, 128);
        }
    }

    /** Возвращает подвижность: вызывается сверкой и при выходе игрока. */
    public void release(LivingEntity living) {
        if (living instanceof Mob mob) {
            mob.setAI(true);
            return;
        }
        if (living instanceof Player player) {
            player.setWalkSpeed(0.2f);
            if (SLOWNESS != null) {
                player.removePotionEffect(SLOWNESS);
            }
            if (JUMP_BOOST != null) {
                player.removePotionEffect(JUMP_BOOST);
            }
        }
    }

    /**
     * Короткий эффект, который продлевается тиком сверки.
     *
     * <p>Длительность чуть больше шага сверки: если сервер остановится, эффект
     * спадёт сам через полсекунды, а не останется на игроке навсегда.
     */
    private void applyShort(Player player, PotionEffectType type, int amplifier) {
        if (type == null) {
            return;
        }
        player.addPotionEffect(new PotionEffect(type, 15, amplifier, false, false, false));
    }

    /** Отпускает всех: при выключении плагина никто не должен остаться стоять. */
    public void releaseAll() {
        for (UUID id : held) {
            if (Bukkit.getEntity(id) instanceof LivingEntity living) {
                release(living);
            }
        }
        held.clear();
    }
}
