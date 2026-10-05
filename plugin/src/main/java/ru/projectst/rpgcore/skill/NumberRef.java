package ru.projectst.rpgcore.skill;

import java.util.Map;
import ru.projectst.rpgcore.balance.BalanceTable;

/**
 * Число в теле навыка: записано прямо, ссылается на слой баланса или умножается
 * на счётчик каста.
 *
 * <p>Ссылка пишется как {@code $damage}. Существование ключа проверяется
 * связыванием при загрузке, поэтому к моменту исполнения промах невозможен.
 *
 * <p>Прямые числа не запрещены: радиус партиклов или количество искр балансом
 * не правят, и гонять их через отдельный файл было бы церемонией без пользы.
 * Правило простое — всё, что влияет на бой, идёт ссылкой.
 *
 * <p>Третья форма — {@code $damage * @seals}: число, умноженное на счётчик,
 * который заполнил предыдущий шаг. Это весь язык выражений, и больше его не
 * будет: ровно одно умножение на счётчик покрывает «урон за каждую печать», а
 * полноценные формулы в конфиге означали бы отладку арифметики без отладчика.
 */
public sealed interface NumberRef {

    /** Счётчики каста отсутствуют: форма со счётчиком даст ноль. */
    double resolve(BalanceTable balance, int level);

    double resolve(BalanceTable balance, int level, Map<String, Double> counters);

    /** Ключ баланса, если это ссылка; иначе {@code null}. Нужно связыванию. */
    String balanceKey();

    /** Имя счётчика, если число на него умножается; иначе {@code null}. */
    default String counterName() {
        return null;
    }

    record Literal(double value) implements NumberRef {
        @Override
        public double resolve(BalanceTable balance, int level) {
            return value;
        }

        @Override
        public double resolve(BalanceTable balance, int level, Map<String, Double> counters) {
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
        public double resolve(BalanceTable balance, int level, Map<String, Double> counters) {
            return resolve(balance, level);
        }

        @Override
        public String balanceKey() {
            return key;
        }
    }

    /**
     * Число, умноженное на счётчик каста.
     *
     * <p>Отсутствующий счётчик — это ноль, а не единица. Так промах по имени
     * счётчика даёт ноль урона, что видно сразу, а не тихое «как будто одна
     * печать», которое пришлось бы искать по логам.
     */
    record Scaled(NumberRef base, String counter) implements NumberRef {
        public Scaled {
            if (base == null) {
                throw new IllegalArgumentException("множимое обязательно");
            }
            if (counter == null || counter.isBlank()) {
                throw new IllegalArgumentException("имя счётчика обязательно");
            }
        }

        @Override
        public double resolve(BalanceTable balance, int level) {
            return 0;
        }

        @Override
        public double resolve(BalanceTable balance, int level, Map<String, Double> counters) {
            return base.resolve(balance, level, counters)
                    * counters.getOrDefault(counter, 0.0);
        }

        @Override
        public String balanceKey() {
            return base.balanceKey();
        }

        @Override
        public String counterName() {
            return counter;
        }
    }

    /**
     * Разбирает запись: {@code $key} — ссылка, {@code X * @счётчик} — число со
     * счётчиком, иначе число.
     *
     * @throws NumberFormatException если это не ссылка и не число; вызывающая
     *         сторона обязана превратить это в ошибку контента с позицией
     */
    static NumberRef parse(String raw) {
        String text = raw.trim();
        int star = text.indexOf('*');
        if (star >= 0) {
            String right = text.substring(star + 1).trim();
            if (!right.startsWith("@")) {
                // Собака, а не решётка: решётка в YAML начинает комментарий, и
                // «4 * #seals» без кавычек превратилось бы в «4 *».
                throw new NumberFormatException(
                        "справа от * ожидался счётчик вида @имя, получено \"" + right + "\"");
            }
            String counter = right.substring(1).trim();
            if (counter.isEmpty()) {
                throw new NumberFormatException("у счётчика нет имени");
            }
            return new Scaled(parseSimple(text.substring(0, star)), counter);
        }
        return parseSimple(text);
    }

    private static NumberRef parseSimple(String raw) {
        String text = raw.trim();
        if (text.startsWith("$")) {
            return new FromBalance(text.substring(1));
        }
        return new Literal(Double.parseDouble(text));
    }
}
