package ru.projectst.rpgcore.skill;

/**
 * Куда повёрнуто существо и кто у него за спиной.
 *
 * <p>Отдельно от мира намеренно: это чистая геометрия, и именно она может быть
 * неверной. Внутри платформы её нельзя было бы проверить тестом, а в старом
 * стеке ровно эта арифметика — счёт точки через синус от поворота цели,
 * подставленный в строку конфига, — и врала.
 */
public final class Facing {

    private Facing() {
    }

    /**
     * Стоит ли точка смещения позади существа.
     *
     * <p>Высота не учитывается: со спины удар или нет, решает направление по
     * земле, иначе удар сверху считался бы ударом сбоку.
     *
     * @param yawDegrees поворот существа, как его считает Minecraft:
     *                   ноль смотрит на юг, в сторону роста Z
     * @param dx         смещение наблюдателя от существа по X
     * @param dz         смещение наблюдателя от существа по Z
     * @param arcDegrees ширина тыльного сектора целиком: 120 означает по 60
     *                   градусов в каждую сторону от точки прямо за спиной
     */
    public static boolean isBehind(double yawDegrees, double dx, double dz,
                                   double arcDegrees) {
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0e-6) {
            // Стоим ровно в цели: спины нет ни у кого, и гадать не нужно.
            return false;
        }
        double yaw = Math.toRadians(yawDegrees);
        // Направление взгляда в координатах Minecraft: при нулевом повороте
        // существо смотрит в сторону роста Z, а X растёт влево от него.
        double facingX = -Math.sin(yaw);
        double facingZ = Math.cos(yaw);

        double cosine = (facingX * dx + facingZ * dz) / length;
        double angle = Math.toDegrees(Math.acos(Math.clamp(cosine, -1.0, 1.0)));
        return angle >= 180 - arcDegrees / 2;
    }

    /**
     * Куда игрок идёт: нажатые клавиши движения — в направление по земле.
     *
     * <p>Считает сервер, а не мод, хотя нажатия знает мод. Так сделано
     * намеренно: от клиента приходит только то, что он действительно знает
     * лучше сервера — какие клавиши держит игрок, — а поворот берётся тот, что
     * сервер видит сам. Поэтому подменённый мод может попросить рывок в одну из
     * восьми сторон относительно своего настоящего поворота, а не в любую точку
     * мира.
     *
     * @param yawDegrees поворот игрока, как его считает Minecraft:
     *                   ноль смотрит на юг, в сторону роста Z
     * @param forward    ход вперёд: от -1 (назад) до 1 (вперёд)
     * @param left       ход влево: от -1 (вправо) до 1 (влево)
     * @return направление или {@code null}, если игрок стоит на месте
     */
    public static Heading headingOf(double yawDegrees, double forward, double left) {
        double yaw = Math.toRadians(yawDegrees);
        // Вперёд — то же направление, что у взгляда по земле. Влево — оно же,
        // повёрнутое на девяносто градусов: при нулевом повороте игрок смотрит
        // на юг, и слева от него восток, то есть рост X.
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double leftX = Math.cos(yaw);
        double leftZ = Math.sin(yaw);
        return Heading.orNull(forwardX * forward + leftX * left,
                forwardZ * forward + leftZ * left);
    }
}
