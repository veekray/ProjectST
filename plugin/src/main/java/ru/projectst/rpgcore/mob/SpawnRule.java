package ru.projectst.rpgcore.mob;

import java.util.List;
import java.util.Locale;

/**
 * Правило подмены естественного спавна.
 *
 * <p>«В этом мире вместо такого-то ванильного существа с такой-то вероятностью
 * появляется наш моб». Этого достаточно, чтобы заселить мир, и это читается
 * целиком — в отличие от спавнеров с радиусами и условиями, которые в прежнем
 * стеке приходилось искать по трём файлам.
 *
 * @param mobId     кто появляется
 * @param replaces  какой ванильный тип подменяется
 * @param worlds    в каких мирах; пусто — в любых
 * @param chance    вероятность подмены в процентах
 */
public record SpawnRule(String mobId, String replaces, List<String> worlds, double chance) {

    public SpawnRule {
        if (mobId == null || mobId.isBlank()) {
            throw new IllegalArgumentException("у правила спавна обязателен моб");
        }
        if (replaces == null || replaces.isBlank()) {
            throw new IllegalArgumentException("у правила спавна обязателен подменяемый тип");
        }
        if (chance <= 0 || chance > 100) {
            throw new IllegalArgumentException("вероятность подмены от единицы до ста");
        }
        worlds = worlds == null ? List.of() : List.copyOf(worlds);
        replaces = replaces.toUpperCase(Locale.ROOT);
    }

    public boolean appliesTo(String world, String entityType) {
        if (!replaces.equalsIgnoreCase(entityType)) {
            return false;
        }
        return worlds.isEmpty() || worlds.contains(world);
    }
}
