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

    /** Кто сейчас готовит навык: у него скорость не от стата, а фиксированная. */
    private java.util.function.Predicate<java.util.UUID> casting = id -> false;

    /**
     * Скорость во время подготовки: ровно половина ванильной базы ходьбы.
     *
     * <p>Решение владельца: бонусы к скорости во время каста не действуют
     * вовсе — ни стат, ни зелье, ни бег. Иначе подготовка у быстрого героя и у
     * медленного стоила бы по-разному, а цена каста должна быть одна.
     */
    static final double CAST_SPEED = 0.05;

    public VitalsSync(StatService stats) {
        this.stats = stats;
    }

    public void useCasting(java.util.function.Predicate<java.util.UUID> casting) {
        this.casting = casting == null ? id -> false : casting;
    }

    /**
     * Держит скорость кастера ровно на {@link #CAST_SPEED}.
     *
     * <p>Чужие модификаторы на атрибуте снимать нельзя — они не наши. Поэтому
     * подбирается база: такая, чтобы с ними итог был ровно нужным. Зовётся
     * каждый тик подготовки: модификаторы появляются и уходят (зелье, бег), и
     * база под них пересчитывается. Бег сбрасывается: ускорения во время каста
     * нет.
     */
    public void lockCastSpeed(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }
        if (player.isSprinting()) {
            player.setSprinting(false);
        }
        double base = attribute.getBaseValue();
        double value = attribute.getValue();
        if (base <= 0 || value <= 0) {
            attribute.setBaseValue(CAST_SPEED);
            return;
        }
        // Итог пропорционален базе при множителях; прибавки числом редки, и
        // следующий тик доберёт остаток.
        double wanted = base * CAST_SPEED / value;
        if (Math.abs(wanted - base) > 1.0E-5) {
            attribute.setBaseValue(Math.max(0.0001, wanted));
        }
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
        double share = stats.share(player.getUniqueId(), StatIds.ATTACK_SPEED);
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attribute == null) {
            return;
        }
        double wanted = 4.0 * Math.max(0.2, 1 + share);
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
        if (casting.test(player.getUniqueId())) {
            lockCastSpeed(player);
            return;
        }
        double share = stats.share(player.getUniqueId(), StatIds.MOVEMENT_SPEED);
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }
        double wanted = 0.1 * Math.max(0.2, 1 + share);
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
