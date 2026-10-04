package ru.projectst.rpgcore.stat;

/**
 * Одна надбавка к стату.
 *
 * @param statId к какому стату
 * @param op     как участвует в расчёте
 * @param value  величина
 * @param source откуда пришла: предмет, статус, класс. Нужна для отладки
 *               ({@code /rpg debug}) и для снятия надбавок одним источником.
 */
public record StatModifier(String statId, StatOp op, double value, String source) {

    public StatModifier {
        if (statId == null || statId.isBlank()) {
            throw new IllegalArgumentException("statId обязателен");
        }
        if (op == null) {
            throw new IllegalArgumentException("op обязателен");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source обязателен: без него надбавку нельзя "
                    + "ни объяснить в отладке, ни снять");
        }
    }
}
