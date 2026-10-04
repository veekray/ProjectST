package ru.projectst.rpgcore.balance;

/**
 * Значение баланса: либо константа, либо кривая по уровню.
 *
 * <p>Существует, чтобы числа жили отдельно от логики навыка. Правка урона не
 * должна требовать открытия файла со скиллом — иначе балансировка превращается
 * в редактирование поведения, а это разные занятия с разной ценой ошибки.
 */
public sealed interface BalanceValue {

    /** Значение на данном уровне навыка. Уровни считаются с единицы. */
    double at(int level);

    /** Одно и то же число на всех уровнях. */
    record Constant(double value) implements BalanceValue {
        @Override
        public double at(int level) {
            return value;
        }
    }

    /**
     * Линейная кривая: {@code base + perLevel * (level - 1)}, ограниченная
     * диапазоном.
     *
     * <p>Ограничение нужно для убывающих кривых вроде перезарядки: без нижней
     * границы на высоких уровнях она ушла бы в ноль и ниже.
     */
    record Curve(double base, double perLevel, double min, double max) implements BalanceValue {

        public Curve {
            if (min > max) {
                throw new IllegalArgumentException("min больше max в кривой баланса");
            }
        }

        @Override
        public double at(int level) {
            double raw = base + perLevel * (Math.max(1, level) - 1);
            return Math.clamp(raw, min, max);
        }
    }
}
