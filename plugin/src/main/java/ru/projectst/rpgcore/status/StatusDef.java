package ru.projectst.rpgcore.status;

import java.util.Locale;
import java.util.Set;

/**
 * Определение статуса вместе с объявленными отношениями к другим статусам.
 *
 * <p>Отношения живут здесь, а не в коде механик. Это главное решение проекта:
 * пока «стан подавляет корни» было размазано по условиям в отдельных навыках,
 * правило нельзя было ни прочитать, ни проверить. Теперь его видно в одном
 * месте, и {@link StatusRegistry} проверяет его на противоречия при загрузке.
 *
 * @param id             идентификатор в нижнем регистре
 * @param category       категория
 * @param duration       длительность по умолчанию, тики
 * @param maxStacks      максимум стаков, не меньше 1
 * @param stacking       поведение при повторном наложении
 * @param priority       кто кого вытесняет в правиле исключительности
 * @param exclusiveWith  если задано — одновременно активен только один статус
 *                       этой категории; кто именно, решает приоритет
 * @param suppresses     пока этот статус активен, перечисленные не действуют,
 *                       но продолжают тикать и истекают по своему сроку
 * @param removes        наложение этого статуса удаляет перечисленные
 * @param blocks         пока этот статус активен, перечисленные не наложить
 * @param tags           произвольные метки для условий навыков
 * @param display        имя для показа игроку; пусто — показывается id
 * @param color          цвет для показа; пусто — цвет по категории
 * @param description    что статус делает, в два-три слова: «без навыков»,
 *                       «гасит удар». Пишется под значком, когда числа
 *                       показать нечем; пусто — не пишется ничего
 */
public record StatusDef(String id, StatusCategory category, int duration, int maxStacks,
                        Stacking stacking, int priority, StatusCategory exclusiveWith,
                        Set<String> suppresses, Set<String> removes, Set<String> blocks,
                        Set<String> tags, String display, String color, String description) {

    /** Статус без описания. */
    public StatusDef(String id, StatusCategory category, int duration, int maxStacks,
                     Stacking stacking, int priority, StatusCategory exclusiveWith,
                     Set<String> suppresses, Set<String> removes, Set<String> blocks,
                     Set<String> tags, String display, String color) {
        this(id, category, duration, maxStacks, stacking, priority, exclusiveWith,
                suppresses, removes, blocks, tags, display, color, null);
    }

    /** Статус, у которого нет своего имени и цвета: показывается по id. */
    public StatusDef(String id, StatusCategory category, int duration, int maxStacks,
                     Stacking stacking, int priority, StatusCategory exclusiveWith,
                     Set<String> suppresses, Set<String> removes, Set<String> blocks,
                     Set<String> tags) {
        this(id, category, duration, maxStacks, stacking, priority, exclusiveWith,
                suppresses, removes, blocks, tags, null, null);
    }

    public StatusDef {
        if (id == null || !id.equals(id.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("id статуса должен быть в нижнем регистре: " + id);
        }
        if (category == null) {
            throw new IllegalArgumentException("category обязательна у статуса " + id);
        }
        if (duration <= 0) {
            throw new IllegalArgumentException("длительность должна быть положительной: " + id);
        }
        if (maxStacks < 1) {
            throw new IllegalArgumentException("maxStacks не меньше 1: " + id);
        }
        suppresses = suppresses == null ? Set.of() : Set.copyOf(suppresses);
        removes = removes == null ? Set.of() : Set.copyOf(removes);
        blocks = blocks == null ? Set.of() : Set.copyOf(blocks);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        // Имя и цвет — для показа игроку. Пусто означает «по идентификатору» и
        // «по категории»: заставлять писать их у каждого стана было бы шумом.
        display = display == null || display.isBlank() ? id : display;
        color = color == null || color.isBlank() ? null : color.toUpperCase(Locale.ROOT);
        description = description == null ? "" : description.strip();
    }

    /**
     * Есть ли у статуса имя для игрока.
     *
     * <p>Без имени статус служебный — замок, отметка «уже сработало», счёт
     * ударов. Игроку его не показывают: строка «vuln_cd» ему ничего не скажет,
     * а место на экране займёт.
     */
    public boolean named() {
        return !display.equals(id);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /** Все идентификаторы, на которые ссылается этот статус. Нужно для проверки графа. */
    public Set<String> referencedIds() {
        Set<String> all = new java.util.LinkedHashSet<>(suppresses);
        all.addAll(removes);
        all.addAll(blocks);
        return all;
    }
}
