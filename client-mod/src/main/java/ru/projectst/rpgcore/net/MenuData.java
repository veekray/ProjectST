// КОПИЯ из плагина: plugin/src/main/java/ru/projectst/rpgcore/net/MenuData.java
// Менять только там и копировать сюда — см. client-mod/README.md.
package ru.projectst.rpgcore.net;

import java.util.List;

/**
 * Всё, что нужно меню мода.
 *
 * <p>Присылается по запросу и после каждого действия, а не постоянно: меню
 * открыто редко, а данных в нём много. Состояние ({@link ClientState}) идёт
 * часто и остаётся маленьким — это разные сообщения именно поэтому.
 *
 * <p>Числа здесь уже посчитаны сервером: стоимость, перезарядка, требуемый
 * уровень. Мод не считает ничего и не знает про баланс — иначе правила
 * оказались бы в двух местах.
 *
 * @param classId   выбранный класс; пусто — не выбран
 * @param level     уровень
 * @param xp        опыт на текущем уровне
 * @param xpToNext  сколько ещё нужно; ноль — предел
 * @param points    свободные очки
 * @param slots     сколько слотов у класса
 * @param classes   все классы, какие есть
 * @param skills    навыки выбранного класса
 * @param stats     снимок статов
 */
public record MenuData(String classId, int level, double xp, double xpToNext, int points,
                       int slots, List<ClassLine> classes, List<SkillLine> skills,
                       List<StatLine> stats) {

    public MenuData {
        classId = classId == null ? "" : classId;
        classes = classes == null ? List.of() : List.copyOf(classes);
        skills = skills == null ? List.of() : List.copyOf(skills);
        stats = stats == null ? List.of() : List.copyOf(stats);
    }

    /**
     * Класс в списке выбора.
     *
     * @param resourceName чем платит: по этому слову игрок понимает разницу
     *                     раньше, чем прочтёт описание
     */
    public record ClassLine(String id, String display, String icon, String resourceName,
                            int slots, int maxLevel) {
    }

    /**
     * Навык в меню.
     *
     * @param level     уровень у игрока; ноль — не изучен
     * @param maxLevel  предел уровня навыка
     * @param required  уровень класса, с которого навык доступен
     * @param mana      стоимость
     * @param cooldown  перезарядка в секундах
     * @param boundSlot слот, на котором он стоит; ноль — ни на каком
     */
    public record SkillLine(String id, String display, String icon, int tier, int level,
                            int maxLevel, int required, double mana, double cooldown,
                            int boundSlot) {
    }

    /** Стат в меню: имя, значение и идентификатор для тех, кто правит файлы. */
    public record StatLine(String id, String display, double value) {
    }
}
