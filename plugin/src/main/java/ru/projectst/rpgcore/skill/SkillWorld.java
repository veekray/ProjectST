package ru.projectst.rpgcore.skill;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ru.projectst.rpgcore.damage.DamageSchool;

/**
 * Всё, что исполнителю навыков нужно от мира.
 *
 * <p>Порт, а не реализация: Bukkit остаётся за границей {@code platform/}, и
 * благодаря этому {@link SkillRuntime} проверяется юнит-тестами без запуска
 * сервера — на подставном мире, который просто записывает вызовы.
 *
 * <p>Статусы и статы сюда не входят: ими исполнитель управляет напрямую через
 * свои сервисы, которым Bukkit не нужен.
 */
public interface SkillWorld {

    /**
     * Цели шага. Вычисляются один раз на шаг — это требование, а не деталь
     * реализации, см. {@link Step}.
     */
    List<UUID> resolveTargets(CastContext context, TargetSpec.Type type,
                              double radius, double angle);

    Optional<Position> positionOf(UUID entity);

    boolean isPlayer(UUID entity);

    /** Нанести урон. Проходит через единый конвейер, других путей нет. */
    void dealDamage(UUID caster, UUID target, double amount, DamageSchool school, String skillId);

    void heal(UUID target, double amount);

    void message(UUID target, String text);

    void potion(UUID target, String effect, int durationTicks, int amplifier);

    /** Отбросить цель от точки. */
    void push(UUID target, Position from, double strength, double lift);

    /** Толкнуть цель к точке одним импульсом. */
    void pullTowards(UUID target, Position to, double strength);

    void teleport(UUID target, Position to);

    /** Точка в нескольких блоках перед сущностью по направлению взгляда. */
    Optional<Position> forwardOf(UUID entity, double distance);

    /**
     * Первое, во что упрётся луч от глаз сущности.
     *
     * @return точка попадания и задетая сущность, если она была
     */
    RayHit castRay(UUID caster, double range, boolean stopAtEntity);

    void particles(Position at, String particle, Action.Particles.Shape shape,
                   int count, double size);

    void sound(Position at, String sound, double volume, double pitch);

    void runLater(int ticks, Runnable task);

    /** Что нашёл луч. */
    record RayHit(Position point, UUID entity) {
        public boolean hitEntity() {
            return entity != null;
        }
    }
}
