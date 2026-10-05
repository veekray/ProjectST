package ru.projectst.rpgcore.skill;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Обстоятельства одного запуска навыка.
 *
 * <p>Три вещи, которых не хватало примитивам: кто кастует, на каком уровне, и
 * где находится «точка действия». Последняя нужна всему, что происходит не
 * вокруг кастера — попаданию луча, точке сбора, месту старта рывка.
 *
 * <p>В старом стеке это называлось origin и было источником половины ошибок,
 * потому что его можно было забыть задать и ничего бы не сказало. Здесь он
 * либо есть, либо его нет явно, и цель типа «рядом с origin» при отсутствии
 * точки вернёт пустой список, а не молча возьмёт кастера.
 *
 * @param caster  кто кастует
 * @param level   уровень навыка: по нему разворачиваются кривые баланса
 * @param origin  точка действия, если она задана
 * @param trigger кто спровоцировал: ударивший, вошедший в зону
 * @param counters числа, посчитанные по ходу каста: сколько печатей снято,
 *                 сколько целей задето. Карта общая на весь каст, включая
 *                 подчинённые навыки: счётчик, посчитанный первым шагом, обязан
 *                 быть виден второму, иначе «урон за каждую печать» выразить
 *                 нечем
 */
public record CastContext(UUID caster, int level, Position origin, UUID trigger,
                          Map<String, Double> counters) {

    public CastContext(UUID caster, int level, Position origin, UUID trigger) {
        this(caster, level, origin, trigger, new HashMap<>());
    }

    public static CastContext of(UUID caster, int level) {
        return new CastContext(caster, level, null, null, new HashMap<>());
    }

    /** Записывает счётчик. Перезапись намеренна: счёт идёт за текущий каст. */
    public void count(String name, double value) {
        counters.put(name, value);
    }

    public double counter(String name) {
        return counters.getOrDefault(name, 0.0);
    }

    public Optional<Position> originOpt() {
        return Optional.ofNullable(origin);
    }

    public Optional<UUID> triggerOpt() {
        return Optional.ofNullable(trigger);
    }

    public CastContext withOrigin(Position position) {
        return new CastContext(caster, level, position, trigger, counters);
    }

    public CastContext withTrigger(UUID entity) {
        return new CastContext(caster, level, origin, entity, counters);
    }

    /** Тот же каст от лица другого исполнителя: счётчики общие. */
    public CastContext withCaster(UUID other) {
        return new CastContext(other, level, origin, trigger, counters);
    }
}
