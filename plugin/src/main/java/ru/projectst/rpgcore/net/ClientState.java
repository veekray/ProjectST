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
 */
public record ClientState(String resourceName, double resource, double resourceMax,
                          int level, String className,
                          List<StatusLine> statuses, List<CooldownLine> cooldowns,
                          List<SlotLine> slots) {

    public ClientState {
        resourceName = resourceName == null ? "" : resourceName;
        className = className == null ? "" : className;
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        cooldowns = cooldowns == null ? List.of() : List.copyOf(cooldowns);
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    /**
     * Действующий статус.
     *
     * @param id        идентификатор: по нему мод выбирает значок
     * @param stacks    стаки
     * @param remaining сколько тиков осталось
     * @param category  категория: по ней мод выбирает цвет
     */
    public record StatusLine(String id, int stacks, int remaining, String category) {
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
}
