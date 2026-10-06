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
 * @param effect   во что превращается значение: кривая рейтинга и фраза для
 *                 игрока. {@code null} — стат не про проценты вовсе (запас
 *                 здоровья, восстановление в секунду), и показывать нечего
 */
public record StatDef(String id, String display, double base,
                      double min, double max, Rounding rounding, StatEffect effect) {

    public StatDef(String id, String display, double base, double min, double max,
                   Rounding rounding) {
        this(id, display, base, min, max, rounding, null);
    }

    /**
     * Доля, в которую превращается значение.
     *
     * <p>Без объявленной кривой значение считается процентами напрямую: так
     * ведут себя статы, которым кривая не нужна, и так же вёл себя весь конвейер
     * до её появления. Единственное место, где это решается, — здесь.
     */
    public double share(double value) {
        return effect == null ? value / 100.0 : effect.share(value);
    }

    /** Процент, в который превращается значение. */
    public double percent(double value) {
        return effect == null ? value : effect.percent(value);
    }

    /** Фраза для подсказки; пусто — пояснять нечего. */
    public String note(double value) {
        return effect == null ? "" : effect.note(value);
    }

    public StatDef {
        if (id == null || !id.equals(id.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("id стата должен быть в нижнем регистре: " + id);
        }
        if (min > max) {
            throw new IllegalArgumentException("min > max у стата " + id);
        }
    }
}
