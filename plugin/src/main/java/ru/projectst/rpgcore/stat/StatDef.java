package ru.projectst.rpgcore.stat;

import java.util.Locale;

/**
 * Определение стата: что это за величина и в каких границах она живёт.
 *
 * @param id       идентификатор в нижнем регистре, например {@code skill_damage}
 * @param display  как показывать игроку
 * @param base     значение при полном отсутствии надбавок
 * @param min      нижняя граница итогового значения
 * @param max      верхняя граница итогового значения
 * @param rounding округление после ограничения
 */
public record StatDef(String id, String display, double base,
                      double min, double max, Rounding rounding) {

    public StatDef {
        if (id == null || !id.equals(id.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("id стата должен быть в нижнем регистре: " + id);
        }
        if (min > max) {
            throw new IllegalArgumentException("min > max у стата " + id);
        }
    }
}
