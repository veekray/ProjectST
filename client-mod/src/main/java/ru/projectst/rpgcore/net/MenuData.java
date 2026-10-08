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
 * @param gear      ячейки снаряжения, в которых вещь лежит, но не действует.
 *                  Только они: что лежит в ячейке, мод и так видит в контейнере
 */
public record MenuData(String classId, int level, double xp, double xpToNext, int points,
                       int slots, List<ClassLine> classes, List<SkillLine> skills,
                       List<StatLine> stats, List<GearLine> gear) {

    public MenuData(String classId, int level, double xp, double xpToNext, int points,
                    int slots, List<ClassLine> classes, List<SkillLine> skills,
                    List<StatLine> stats) {
        this(classId, level, xp, xpToNext, points, slots, classes, skills, stats, List.of());
    }

    public MenuData {
        classId = classId == null ? "" : classId;
        classes = classes == null ? List.of() : List.copyOf(classes);
        skills = skills == null ? List.of() : List.copyOf(skills);
        stats = stats == null ? List.of() : List.copyOf(stats);
        gear = gear == null ? List.of() : List.copyOf(gear);
    }

    /**
     * Ячейка снаряжения, в которой вещь не действует.
     *
     * <p>Предмет на месте, а статов нет — не тот класс, мало уровня, не та
     * ячейка. Мод обводит такую ячейку красным и пишет причину в подсказке:
     * без этого игрок видит надетую вещь и не понимает, куда делись числа.
     *
     * @param cell    имя ячейки из {@link Protocol#GEAR_CELLS}
     * @param refusal почему не действует, словами сервера
     */
    public record GearLine(String cell, String refusal) {

        public GearLine {
            cell = cell == null ? "" : cell;
            refusal = refusal == null ? "" : refusal;
        }
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
     * @param cost      стоимость ресурса класса: маны или силы духа
     * @param stamina   стоимость выносливости; ноль — навык её не тратит. Отдельно
     *                  от cost, потому что это другой запас: подсказка, которая
     *                  показывает половину цены, хуже подсказки без цены
     * @param cooldown  перезарядка в секундах
     * @param boundSlot слот, на котором он стоит; ноль — ни на каком
     * @param damage    наибольший урон за одно попадание на текущем уровне;
     *                  ноль — навык не бьёт. Считает сервер по тому же балансу,
     *                  что и бой: число в подсказке и число в бою обязаны
     *                  совпадать, а два расчёта разошлись бы
     * @param description короткое описание из файла навыка
     */
    public record SkillLine(String id, String display, String icon, int tier, int level,
                            int maxLevel, int required, double cost, double stamina,
                            double cooldown, int boundSlot, double damage,
                            List<String> description) {

        public SkillLine {
            description = description == null ? List.of() : List.copyOf(description);
        }
    }

    /**
     * Стат в меню.
     *
     * @param note пояснение, во что превращается значение: «режет 60% урона» у
     *             защит, где рейтинг и проценты — разные числа. Считает и
     *             формулирует сервер, потому что формула живёт у него; мод
     *             только показывает. Пусто — пояснять нечего, и так понятно
     */
    public record StatLine(String id, String display, double value, String note) {

        public StatLine(String id, String display, double value) {
            this(id, display, value, "");
        }

        public StatLine {
            note = note == null ? "" : note;
        }
    }
}
