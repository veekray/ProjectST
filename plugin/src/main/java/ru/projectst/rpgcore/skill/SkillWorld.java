package ru.projectst.rpgcore.skill;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ru.projectst.rpgcore.damage.DamageResult;
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

    /**
     * Куда смотрит существо, по земле.
     *
     * <p>По умолчанию выводится из точки впереди; мир с настоящим поворотом
     * отвечает точнее, потому что точка впереди упирается в стены.
     */
    default Optional<Heading> lookOf(UUID entity) {
        Optional<Position> here = positionOf(entity);
        Optional<Position> ahead = forwardOf(entity, 1);
        if (here.isEmpty() || ahead.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(Heading.orNull(ahead.get().x() - here.get().x(),
                ahead.get().z() - here.get().z()));
    }

    boolean isPlayer(UUID entity);

    /**
     * Нанести урон. Проходит через единый конвейер, других путей нет.
     *
     * @return что получилось у конвейера; {@code null}, если цели уже нет.
     *         Исполнителю нужен крит: вспышку крита рисует он, а знает о крите
     *         только конвейер
     */
    DamageResult dealDamage(UUID caster, UUID target, double amount, DamageSchool school,
                            String skillId);

    void heal(UUID target, double amount);

    /**
     * Отнимает долю предела здоровья, но не ниже единицы.
     *
     * <p>Не урон: ни конвейера, ни брони, ни события «по мне попали».
     */
    void sacrifice(UUID target, double share);

    void message(UUID target, String text);

    void potion(UUID target, String effect, int durationTicks, int amplifier);

    /** Снять эффект зелья: невидимость рвётся ударом, а не ждёт своего срока. */
    void clearPotion(UUID target, String effect);

    /** Отбросить цель от точки. */
    void push(UUID target, Position from, double strength, double lift);

    /** Толкнуть цель к точке одним импульсом. */
    void pullTowards(UUID target, Position to, double strength);

    void teleport(UUID target, Position to);

    /** Точка в нескольких блоках перед сущностью по направлению взгляда. */
    Optional<Position> forwardOf(UUID entity, double distance);

    /** Рывок сущности по её собственному взгляду. */
    /**
     * Рывок существа.
     *
     * @param heading куда двигать; {@code null} — туда, куда существо смотрит
     */
    void dash(UUID entity, double strength, double lift, Heading heading);

    /**
     * Точка рядом с сущностью по её направлению взгляда.
     *
     * @param behind true — позади неё, false — перед ней
     */
    Optional<Position> offsetOf(UUID entity, double distance, boolean behind);

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
     * Стоит ли наблюдатель у цели за спиной.
     *
     * @param arcDegrees ширина тыльного сектора в градусах: 120 означает, что
     *                   спиной считается треть круга позади цели
     */
    boolean isBehind(UUID observer, UUID subject, double arcDegrees);

    /** Предел здоровья цели: от него считается урон долей. */
    double maxHealthOf(UUID entity);

    /** Текущее здоровье цели. */
    double healthOf(UUID entity);

    /**
     * Меняет местами двоих.
     *
     * <p>Одной операцией, а не двумя телепортами: между ними второй оказался бы
     * в точке, куда уже перенесли первого, и один из них застрял бы в блоке.
     */
    void swap(UUID first, UUID second);

    /** Подсвечивает цель контуром сквозь стены на заданное число тиков. */
    void glow(UUID target, int ticks);

    /** Отправляет щит цели в перезарядку. */
    void disableShield(UUID target, int ticks);

    /**
     * Сбивает цель: существо выбирает себе другую жертву рядом.
     *
     * <p>По игрокам не работает: отнимать управление — это контроль, и он
     * делается статусами, которые видно на экране.
     */
    void confuse(UUID target, double radius);

    /** Переносит цель в случайную точку в радиусе от её нынешнего места. */
    void scatter(UUID target, double radius);

    /**
     * Заставляет мобов вокруг забыть, кого они били.
     *
     * <p>По игрокам не работает и работать не должно: отнимать управление —
     * это контроль, а он делается статусами, которые видно на экране.
     */
    void clearThreat(UUID caster, double radius);

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

    /**
     * Частицы с направлением: конус рисуется от вершины {@code at} вдоль оси.
     *
     * @param angle полная ширина конуса в градусах; для других форм не важна
     * @param axis  ось конуса по земле; {@code null} для других форм
     */
    default void particles(Position at, String particle, Action.Particles.Shape shape,
                           int count, double size, double angle, Heading axis) {
        particles(at, particle, shape, count, size);
    }

    void sound(Position at, String sound, double volume, double pitch);

    /**
     * Видимое событие для клиентского мода.
     *
     * <p>Мир сам решает, кому что показать: игрокам с модом — событие, без
     * мода — ванильный запасной вид из него же. Исполнитель не знает, у кого
     * стоит мод, и знать не должен.
     */
    void effect(FxEvent event);

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
