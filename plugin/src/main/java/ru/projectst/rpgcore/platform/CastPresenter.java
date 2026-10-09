package ru.projectst.rpgcore.platform;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.projectst.rpgcore.cast.CastService;
import ru.projectst.rpgcore.net.FxMessage;
import ru.projectst.rpgcore.skill.SkillDef;

/**
 * Подготовка каста в мире: что видят другие, что чувствует сам кастер.
 *
 * <p>Служба каста решает, когда подготовка началась и чем кончилась, а здесь —
 * как это выглядит: событие моду (замах у всех рядом, полоса каста у самого
 * кастера), фиксированная скорость, пока идёт подготовка, и слова, если её
 * сорвали. Сорванный каст без слов выглядел бы как навык, который не сработал.
 */
public final class CastPresenter implements CastService.CastListener {

    private final FxBroadcaster fx;
    private final VitalsSync vitals;

    public CastPresenter(FxBroadcaster fx, VitalsSync vitals) {
        this.fx = fx;
        this.vitals = vitals;
    }

    @Override
    public void started(UUID playerId, SkillDef skill, int ticks) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        vitals.lockCastSpeed(player);
        fx.send(fx.viewers(player.getLocation()).modded(), new FxMessage.CastStart(
                player.getEntityId(), skill.id(), skill.classId(), ticks));
    }

    @Override
    public void ended(UUID playerId, SkillDef skill, boolean completed, String reason) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        // Скорость возвращается сразу, а не со следующей сверкой раз в секунду:
        // секунда на половине скорости после каста — это наказание ни за что.
        vitals.apply(player);
        fx.send(fx.viewers(player.getLocation()).modded(),
                new FxMessage.CastEnd(player.getEntityId(), completed));
        if (!completed && reason != null && !reason.isBlank()) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    skill.display().replaceAll("&[0-9a-fk-or]", "") + ": " + reason,
                    net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    /** Тик: скорость тех, кто готовит, держится ровно на половине базы. */
    public void tick(CastService casts) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (casts.isCasting(player.getUniqueId())) {
                vitals.lockCastSpeed(player);
            }
        }
    }
}
