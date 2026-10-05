package ru.projectst.rpgcore.skill;

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
 */
public record CastContext(UUID caster, int level, Position origin, UUID trigger) {

    public static CastContext of(UUID caster, int level) {
        return new CastContext(caster, level, null, null);
    }

    public Optional<Position> originOpt() {
        return Optional.ofNullable(origin);
    }

    public Optional<UUID> triggerOpt() {
        return Optional.ofNullable(trigger);
    }

    public CastContext withOrigin(Position position) {
        return new CastContext(caster, level, position, trigger);
    }

    public CastContext withTrigger(UUID entity) {
        return new CastContext(caster, level, origin, entity);
    }
}
