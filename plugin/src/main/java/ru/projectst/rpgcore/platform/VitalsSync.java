package ru.projectst.rpgcore.platform;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import ru.projectst.rpgcore.damage.StatIds;
import ru.projectst.rpgcore.stat.StatService;

/**
 * Переносит статы, у которых есть ванильный двойник, в сам мир.
 *
 * <p>Пока это только здоровье. Стат, который нигде не виден, — хуже отсутствия
 * стата: в старом стеке такие числа существовали, расходились с тем, что
 * происходило в бою, и спорить с ними было нечем.
 *
 * <p>Сверка идёт по значению, а не по событию. Событие «стат изменился» пришлось
 * бы испускать из всех мест, которые его меняют, и однажды одно из них забыли
 * бы; сверка сама себя исправляет на следующем тике.
 */
public final class VitalsSync {

    private final StatService stats;

    public VitalsSync(StatService stats) {
        this.stats = stats;
    }

    public void apply(Player player) {
        double wanted = stats.snapshot(player.getUniqueId()).get(StatIds.MAX_HEALTH);
        if (wanted <= 0) {
            return;
        }
        // В 1.21.1 константа ещё GENERIC_MAX_HEALTH: переименовали её в 1.21.3.
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute == null || Math.abs(attribute.getBaseValue() - wanted) < 0.001) {
            return;
        }
        attribute.setBaseValue(wanted);
        // Текущее здоровье подрезаем, если запас упал: иначе клиент показывает
        // больше сердец, чем у игрока есть, и первый же удар выглядит критом.
        if (player.getHealth() > wanted) {
            player.setHealth(wanted);
        }
    }
}
