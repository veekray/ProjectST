package ru.projectst.rpgcore.skill;

import ru.projectst.rpgcore.balance.BalanceTable;

/**
 * Число в теле навыка: либо записано прямо, либо ссылается на слой баланса.
 *
 * <p>Ссылка пишется как {@code $damage}. Существование ключа проверяется
 * связыванием при загрузке, поэтому к моменту исполнения промах невозможен.
 *
 * <p>Прямые числа не запрещены: радиус партиклов или количество искр балансом
 * не правят, и гонять их через отдельный файл было бы церемонией без пользы.
 * Правило простое — всё, что влияет на бой, идёт ссылкой.
 */
public sealed interface NumberRef {

    double resolve(BalanceTable balance, int level);

    /** Ключ баланса, если это ссылка; иначе {@code null}. Нужно связыванию. */
    String balanceKey();

    record Literal(double value) implements NumberRef {
        @Override
        public double resolve(BalanceTable balance, int level) {
            return value;
        }

        @Override
        public String balanceKey() {
            return null;
        }
    }

    record FromBalance(String key) implements NumberRef {
        public FromBalance {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("ключ баланса обязателен");
            }
        }

        @Override
        public double resolve(BalanceTable balance, int level) {
            return balance.require(key, level);
        }

        @Override
        public String balanceKey() {
            return key;
        }
    }

    /**
     * Разбирает запись: {@code $key} — ссылка, иначе число.
     *
     * @throws NumberFormatException если это не ссылка и не число; вызывающая
     *         сторона обязана превратить это в ошибку контента с позицией
     */
    static NumberRef parse(String raw) {
        String text = raw.trim();
        if (text.startsWith("$")) {
            return new FromBalance(text.substring(1));
        }
        return new Literal(Double.parseDouble(text));
    }
}
