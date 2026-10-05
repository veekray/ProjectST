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
 * @param resourceName как называется ресурс: мана у мага, выносливость у плута
 * @param resource     текущий запас
 * @param resourceMax  предел запаса
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
 */
public record ClientState(String resourceName, double resource, double resourceMax,
                          int level, String className,
                          List<StatusLine> statuses, List<CooldownLine> cooldowns,
                          List<SlotLine> slots, List<CounterLine> counters) {

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
     * @param id        идентификатор: по нему мод выбирает значок
     * @param display   имя для показа
     * @param stacks    стаки
     * @param remaining сколько тиков осталось
     * @param category  категория: по ней мод выбирает цвет, если своего нет
     * @param color     цвет из файла статуса; пусто — по категории
     */
    public record StatusLine(String id, String display, int stacks, int remaining,
                             String category, String color) {
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
