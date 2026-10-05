package ru.projectst.rpgcore.skill;

import java.util.UUID;

/**
 * Точка в мире без привязки к Bukkit.
 *
 * <p>Нужна, чтобы исполнитель мог говорить о местах — точке попадания, точке
 * сбора, месте старта рывка — не зная про {@code org.bukkit.Location} и
 * оставаясь проверяемым юнит-тестами.
 */
public record Position(UUID worldId, double x, double y, double z) {

    public Position offset(double dx, double dy, double dz) {
        return new Position(worldId, x + dx, y + dy, z + dz);
    }

    public double distanceTo(Position other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
