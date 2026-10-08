// КОПИЯ из плагина: plugin/src/main/java/ru/projectst/rpgcore/net/ClientState.java
// Менять только там и копировать сюда — см. client-mod/README.md.
package ru.projectst.rpgcore.net;

import java.util.List;

/**
 * Состояние игрока, которое видит мод.
 *
 * <p>Только то, что уже посчитано сервером. Мод не пересчитывает ничего: он
 * рисует. Любой расчёт на клиенте означал бы второй источник правды, а
 * расхождение между полосой маны и настоящей маной игрок заметит раньше нас.
 *
 * @param resourceName как называется ресурс: мана у мага, сила духа у воина
 * @param resource     текущий запас
 * @param resourceMax  предел запаса
 * @param stamina      выносливость: общий запас всех игроков, из которого
 *                     платится рывок. Отдельным полем, а не ещё одной строкой
 *                     в списке, потому что это полоса на экране, а не значение
 *                     в таблице
 * @param staminaMax   предел выносливости
 * @param level        уровень класса
 * @param className    имя класса, уже с цветовыми кодами из файла
 * @param statuses     действующие статусы
 * @param cooldowns    перезарядки навыков, которые на слотах
 * @param slots        что на каком слоте
 * @param counters     счётчики ядра класса: стаки Роста и Увядания у друида,
 *                     души у колдуна, печати у мага. Отдельно от статусов,
 *                     потому что это не эффект, который пройдёт, а ресурс, по
 *                     которому игрок принимает решения — и смотреть на него он
 *                     должен не в списке из восьми строк
 * @param dash         заряды врождённого рывка; {@code null} — врождённого
 *                     навыка в контенте нет, и рисовать нечего
 */
public record ClientState(String resourceName, double resource, double resourceMax,
                          double stamina, double staminaMax,
                          int level, String className,
                          List<StatusLine> statuses, List<CooldownLine> cooldowns,
                          List<SlotLine> slots, List<CounterLine> counters,
                          DashLine dash) {

    public ClientState(String resourceName, double resource, double resourceMax,
                       int level, String className,
                       List<StatusLine> statuses, List<CooldownLine> cooldowns,
                       List<SlotLine> slots, List<CounterLine> counters) {
        this(resourceName, resource, resourceMax, 0, 0, level, className, statuses,
                cooldowns, slots, counters, null);
    }

    public ClientState {
        resourceName = resourceName == null ? "" : resourceName;
        className = className == null ? "" : className;
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        cooldowns = cooldowns == null ? List.of() : List.copyOf(cooldowns);
        slots = slots == null ? List.of() : List.copyOf(slots);
        counters = counters == null ? List.of() : List.copyOf(counters);
    }

    /**
     * Действующий статус.
     *
     * <p>Список приходит в порядке наложения: первым — тот, что лёг раньше.
     * Значок, прыгающий по ряду при каждом обновлении, не найти взглядом.
     *
     * @param id          идентификатор: по нему мод выбирает значок
     * @param display     имя для показа
     * @param stacks      стаки
     * @param remaining   сколько тиков осталось
     * @param category    категория: по ней мод выбирает ряд и цвет, если своего нет
     * @param color       цвет из файла статуса; пусто — по категории
     * @param total       полный нынешний срок в тиках: без него не нарисовать
     *                    убывание
     * @param description что статус делает, в два-три слова; пишется под
     *                    значком, когда чисел нет
     * @param effects     что статус даёт статам, самое заметное первым
     */
    public record StatusLine(String id, String display, int stacks, int remaining,
                             String category, String color, int total, String description,
                             List<EffectLine> effects) {

        public StatusLine(String id, String display, int stacks, int remaining,
                          String category, String color) {
            this(id, display, stacks, remaining, category, color, remaining, "", List.of());
        }

        public StatusLine {
            description = description == null ? "" : description;
            effects = effects == null ? List.of() : List.copyOf(effects);
        }
    }

    /**
     * Что статус даёт одному стату.
     *
     * <p>Число готовое: «+12%», «-8%». Мод его не считает — кривая рейтинга живёт
     * на сервере, и второй её расчёт однажды показал бы не то, что в бою.
     *
     * @param statId по нему мод рисует значок стата
     * @param text   число для показа
     * @param good   хорошо ли это игроку: зелёным или красным
     */
    public record EffectLine(String statId, String text, boolean good) {
    }

    /**
     * Перезарядка.
     *
     * @param skillId   навык
     * @param remaining сколько тиков осталось
     * @param total     сколько всего было: без этого не нарисовать долю
     */
    public record CooldownLine(String skillId, int remaining, int total) {
    }

    /**
     * Слот.
     *
     * @param slot    номер
     * @param skillId навык; пустая строка — слот пуст
     * @param display имя навыка для показа
     * @param icon    имя предмета-значка
     */
    public record SlotLine(int slot, String skillId, String display, String icon) {
    }

    /**
     * Заряды врождённого рывка.
     *
     * <p>Отдельно от перезарядок слотов: рывок не на слоте и слотов не занимает.
     * Заряды и время до ближайшего приходят вместе — доля для полосы считается
     * из них на экране, но числа в ней те же, что в бою.
     *
     * @param skillId   навык: по нему мод выбирает значок, как и у слотов
     * @param display   имя для показа
     * @param charges   сколько зарядов сейчас
     * @param maxCharges сколько их всего
     * @param remaining сколько тиков до возврата ближайшего; ноль — все на месте
     * @param total     сколько тиков занимает возврат одного заряда
     */
    public record DashLine(String skillId, String display, int charges, int maxCharges,
                           int remaining, int total) {
    }

    /**
     * Счётчик ядра класса.
     *
     * @param id        идентификатор статуса-счётчика
     * @param display   имя для показа: «Рост», «Увядание», «Души»
     * @param stacks    сколько сейчас
     * @param maxStacks сколько бывает всего: без этого не нарисовать деления
     * @param color     цвет из файла статуса; пусто — цвет по умолчанию
     */
    public record CounterLine(String id, String display, int stacks, int maxStacks,
                              String color) {
    }
}
