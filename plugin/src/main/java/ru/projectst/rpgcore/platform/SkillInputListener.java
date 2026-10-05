package ru.projectst.rpgcore.platform;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import ru.projectst.rpgcore.cast.CastOutcome;
import ru.projectst.rpgcore.cast.CastService;
import ru.projectst.rpgcore.skill.SkillDef;

/**
 * Применение навыков нажатием клавиш, без клиентского мода.
 *
 * <p>Ванильный клиент не умеет присылать свои клавиши, поэтому берутся те
 * нажатия, о которых сервер узнаёт: смена слота на хотбаре и смена рук. С
 * зажатым Shift они не делают ничего полезного в обычной игре, поэтому
 * перехватываются целиком:
 *
 * <ul>
 *   <li>Shift + 1…9 — применить навык из слота с этим номером;</li>
 *   <li>Shift + F — показать ману и перезарядки.</li>
 * </ul>
 *
 * <p>Без Shift всё работает как в ваниле: сменить оружие в бою по-прежнему
 * можно, и это важнее любой удобной раскладки.
 *
 * <p>Это временная раскладка, и так и записано: настоящие клавиши появятся с
 * клиентским модом (M14). Поэтому вся схема живёт в одном классе в
 * {@code platform/} — заменить её будет нечего бояться.
 */
public final class SkillInputListener implements Listener {

    private final CastService casts;

    public SkillInputListener(CastService casts) {
        this.casts = casts;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onHotbar(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        int slot = event.getNewSlot() + 1;

        CastOutcome outcome = casts.castSlot(player.getUniqueId(), slot);
        // Пустой слот и слот вне класса намеренно не перехватываются: иначе
        // Shift плюс цифра переставали бы менять оружие у того, кто ещё не
        // разложил навыки, и это выглядело бы как залипший хотбар.
        if (outcome.kind() == CastOutcome.Kind.EMPTY_SLOT
                || outcome.kind() == CastOutcome.Kind.BAD_SLOT
                || outcome.kind() == CastOutcome.Kind.NO_CLASS) {
            return;
        }

        event.setCancelled(true);
        player.updateInventory();
        report(player, slot, outcome);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        event.setCancelled(true);

        var id = player.getUniqueId();
        Component line = Component.text(casts.resource().displayName(id) + " ",
                        NamedTextColor.GRAY)
                .append(Component.text(Math.round(Math.floor(casts.resource().current(id)))
                        + "/" + Math.round(casts.resource().max(id)), NamedTextColor.AQUA));

        var blocker = casts.blockingStatus(id);
        if (blocker.isPresent()) {
            line = line.append(Component.text("  касты запрещены: ", NamedTextColor.GRAY))
                    .append(Component.text(blocker.get().id(), NamedTextColor.RED));
        }
        player.sendActionBar(line);
    }

    /**
     * Ответ игроку.
     *
     * <p>В строке действия, а не в чате: нажатие происходит в бою, и чат там
     * никто не читает. Отказ назван причиной — ровно то требование, из-за
     * которого ворота каста вообще существуют.
     */
    private void report(Player player, int slot, CastOutcome outcome) {
        if (outcome.succeeded()) {
            String name = casts.skillInSlot(player.getUniqueId(), slot)
                    .map(SkillDef::display).orElse("навык");
            player.sendActionBar(Component.text(name, NamedTextColor.AQUA));
            return;
        }
        player.sendActionBar(Component.text(reason(outcome), NamedTextColor.RED));
    }

    private static String reason(CastOutcome outcome) {
        String detail = outcome.detail() == null ? "" : ": " + outcome.detail();
        return switch (outcome.kind()) {
            case ON_COOLDOWN -> "Перезарядка" + detail;
            case NOT_ENOUGH_RESOURCE -> "Не хватает: " + (outcome.detail() == null ? "" : outcome.detail());
            case BLOCKED -> "Нельзя колдовать" + detail;
            case NOT_UNLOCKED -> "Навык не изучен";
            case WRONG_CLASS -> "Навык другого класса";
            case UNKNOWN_SKILL -> "Навык не загружен" + detail;
            // Остальные сюда не доходят: их onHotbar пропускает дальше.
            default -> outcome.toString();
        };
    }
}
