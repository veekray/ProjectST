package ru.projectst.rpgcore.skill;

import java.util.List;
import java.util.UUID;
import ru.projectst.rpgcore.damage.DamageSchool;

/**
 * Всё, что исполнителю навыков нужно от мира.
 *
 * <p>Порт, а не реализация: Bukkit остаётся за границей {@code platform/}, и
 * благодаря этому {@link SkillRuntime} проверяется юнит-тестами без запуска
 * сервера — на подставном мире, который просто записывает вызовы.
 *
 * <p>Статусы сюда не входят: ими исполнитель управляет напрямую через
 * {@code StatusService}, которому Bukkit не нужен.
 */
public interface SkillWorld {

    /**
     * Цели шага. Вычисляются один раз на шаг — это требование, а не деталь
     * реализации, см. {@link Step}.
     *
     * @param radius радиус или дальность; ноль, если тип целей его не требует
     * @param angle  угол конуса в градусах; ноль, если тип целей его не требует
     */
    List<UUID> resolveTargets(UUID caster, TargetSpec.Type type, double radius, double angle);

    /** Нанести урон. Проходит через единый конвейер, других путей нет. */
    void dealDamage(UUID caster, UUID target, double amount, DamageSchool school, String skillId);

    void heal(UUID target, double amount);

    void message(UUID target, String text);

    /** Отложить выполнение. Нужен шагам с задержкой. */
    void runLater(int ticks, Runnable task);
}
