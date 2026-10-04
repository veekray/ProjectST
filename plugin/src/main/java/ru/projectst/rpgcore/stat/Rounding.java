package ru.projectst.rpgcore.stat;

/** Как округлять итоговое значение стата после ограничения диапазоном. */
public enum Rounding {

    NONE {
        @Override
        public double apply(double value) {
            return value;
        }
    },
    FLOOR {
        @Override
        public double apply(double value) {
            return Math.floor(value);
        }
    },
    CEIL {
        @Override
        public double apply(double value) {
            return Math.ceil(value);
        }
    },
    NEAREST {
        @Override
        public double apply(double value) {
            return Math.rint(value);
        }
    };

    public abstract double apply(double value);
}
