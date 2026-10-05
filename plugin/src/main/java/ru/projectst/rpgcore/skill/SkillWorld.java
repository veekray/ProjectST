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

    /**
     * Создаёт существо в мире.
     *
     * @param type   тип: пока ванильный, свои мобы придут с их модулем
     * @param health сколько у него здоровья; ноль — оставить штатное
     * @return сущность, если тип известен и место нашлось
     */
    Optional<UUID> spawnMob(String type, Position at, double health);

    /** Убирает существо из мира. */
    void despawn(UUID entity);

    /** Указывает существу, кого бить. */
    void setAttackTarget(UUID mob, UUID target);

    /**
     * Запускает снаряд от глаз кастера по направлению взгляда.
     *
     * <p>Порт отвечает за полёт и попадания, исполнитель — за то, что
     * происходит в точке попадания. Обработчик зовётся на каждое попадание и,
     * отдельным вызовом, на исчерпание дальности.
     */
    void launchProjectile(UUID caster, ProjectileSpec spec, ProjectileHandler handler);

    void particles(Position at, String particle, Action.Particles.Shape shape,
                   int count, double size);

    void sound(Position at, String sound, double volume, double pitch);

    void runLater(int ticks, Runnable task);

    /**
     * Что делать с попаданием снаряда.
     *
     * <p>Два метода, а не один с флагом: «попал» и «не попал ни в кого» — это
     * разные события, и навык обязан различать их явно. В старом стеке и то и
     * другое приходило одним {@code onTick}, из-за чего взрыв на промахе
     * отличался от взрыва на попадании только порядком условий.
     */
    interface ProjectileHandler {

        /** Снаряд задел цель в этой точке. */
        void hit(Position point, UUID target);

        /** Снаряд исчерпал дальность или упёрся в блок, никого не задев. */
        void end(Position point);
    }

    /** Что нашёл луч. */
    record RayHit(Position point, UUID entity) {
        public boolean hitEntity() {
            return entity != null;
        }
    }
}
