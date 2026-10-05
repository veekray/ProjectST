package ru.projectst.rpgcore.skill;

/**
 * Снаряд с уже посчитанными числами: то, что уходит в мир.
 *
 * <p>Отдельно от {@link Action.Projectile} намеренно. В действии числа — это
 * ссылки в слой баланса, которые разворачиваются по уровню навыка; здесь их уже
 * нет, только готовые значения. Благодаря этому порт мира не знает ни про
 * баланс, ни про уровни.
 *
 * @param speed      блоков за тик
 * @param range      сколько блоков всего пролетит
 * @param hitRadius  радиус попадания: снаряд не точка, иначе мимо проходило бы
 *                   всё, что не на линии центра
 * @param gravity    на сколько блоков за тик снаряд опускается; ноль — летит
 *                   прямо
 * @param pierce     сколько целей пробивает; 1 — останавливается на первой
 * @param hitPlayers задевает игроков
 * @param hitMobs    задевает мобов
 * @param stopAtBlock останавливается о блок
 * @param particle   чем рисуется; {@code null} — невидимый
 * @param yawOffset  поворот вылета в градусах: так делается веер из нескольких
 *                   снарядов одним навыком
 */
public record ProjectileSpec(double speed, double range, double hitRadius, double gravity,
                             int pierce, boolean hitPlayers, boolean hitMobs,
                             boolean stopAtBlock, String particle, double yawOffset) {

    public ProjectileSpec(double speed, double range, double hitRadius, double gravity,
                          int pierce, boolean hitPlayers, boolean hitMobs,
                          boolean stopAtBlock, String particle) {
        this(speed, range, hitRadius, gravity, pierce, hitPlayers, hitMobs, stopAtBlock,
                particle, 0);
    }

    public ProjectileSpec {
        if (speed <= 0) {
            throw new IllegalArgumentException("скорость снаряда должна быть положительной");
        }
        if (range <= 0) {
            throw new IllegalArgumentException("дальность снаряда должна быть положительной");
        }
        if (pierce < 1) {
            throw new IllegalArgumentException("снаряд обязан задевать хотя бы одну цель");
        }
    }

    /** Сколько тиков снаряд живёт при этой скорости и дальности. */
    public int lifetimeTicks() {
        return (int) Math.ceil(range / speed);
    }
}
