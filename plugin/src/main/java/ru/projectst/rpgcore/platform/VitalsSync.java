package ru.projectst.rpgcore.platform;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import ru.projectst.rpgcore.damage.StatIds;
import ru.projectst.rpgcore.stat.StatService;

/**
 * Переносит статы, у которых есть ванильный двойник, в сам мир.
 *
 * <p>Здоровье, скорость бега и скорость атаки. Стат, который нигде не виден,
 * — хуже отсутствия
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
        applyHealth(player);
        applySpeed(player);
        applyAttackSpeed(player);
    }

    /**
     * Скорость атаки.
     *
     * <p>Ванильная база — 4 удара в секунду, и от неё считается процент. Как и
     * у ходьбы, правится базовое значение: модификаторов на игроке может висеть
     * сколько угодно чужих, и складывать с ними своё число значило бы начать
     * войну за один атрибут с каждым соседним плагином.
     */
    private void applyAttackSpeed(Player player) {
        double percent = stats.snapshot(player.getUniqueId())
                .getOrZero(StatIds.ATTACK_SPEED);
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attribute == null) {
            return;
        }
        double wanted = 4.0 * Math.max(0.2, 1 + percent / 100.0);
        if (Math.abs(attribute.getBaseValue() - wanted) < 0.0001) {
            return;
        }
        attribute.setBaseValue(wanted);
    }

    /**
     * Скорость передвижения.
     *
     * <p>Ванильная база ходьбы — 0.1, и от неё считается процент. Правится
     * базовое значение, а не модификатор: модификаторов на игроке может висеть
     * сколько угодно чужих, и сложить их в своё число значило бы начать войну
     * за один и тот же атрибут с каждым соседним плагином.
     */
    private void applySpeed(Player player) {
        double percent = stats.snapshot(player.getUniqueId())
                .getOrZero(StatIds.MOVEMENT_SPEED);
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }
        double wanted = 0.1 * Math.max(0.2, 1 + percent / 100.0);
        if (Math.abs(attribute.getBaseValue() - wanted) < 0.0001) {
            return;
        }
        attribute.setBaseValue(wanted);
    }

    private void applyHealth(Player player) {
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
