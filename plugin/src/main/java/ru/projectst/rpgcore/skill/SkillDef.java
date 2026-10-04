package ru.projectst.rpgcore.skill;

import java.util.List;
import java.util.Locale;

/**
 * Навык целиком: логика, привязка к классу и ступени, стоимость.
 *
 * <p>Это и есть обещанное «один скилл — один файл». Всё, что раньше было
 * размазано по четырём плагинам, лежит рядом: тело в {@code steps}, ступень и
 * класс здесь же, числа — ссылками в слой баланса.
 *
 * @param id          идентификатор, он же имя файла
 * @param display     название для игрока
 * @param classId     класс, которому принадлежит навык
 * @param tier        ступень: с какой её можно открыть
 * @param manaCost    стоимость маны
 * @param cooldown    перезарядка в секундах
 * @param steps       тело навыка
 */
public record SkillDef(String id, String display, String classId, int tier,
                       NumberRef manaCost, NumberRef cooldown, List<Step> steps) {

    public SkillDef {
        if (id == null || !id.equals(id.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("id навыка должен быть в нижнем регистре: " + id);
        }
        if (tier < 1 || tier > 5) {
            throw new IllegalArgumentException("ступень навыка от 1 до 5: " + id);
        }
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
