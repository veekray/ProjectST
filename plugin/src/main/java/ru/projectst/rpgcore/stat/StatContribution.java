package ru.projectst.rpgcore.stat;

/**
 * Вклад одного источника в стат: то, что игрок чувствует, а не то, что
 * записано в файле.
 *
 * <p>«+40 к скорости» ничего не говорит: рейтинг превращается в проценты кривой,
 * и те же сорок дают разное в зависимости от того, сколько уже есть. Поэтому
 * вклад — это разница процентов эффекта с источником и без него, посчитанная
 * той же кривой, что и бой. У стата без кривой (запас здоровья) процентов нет,
 * и вклад остаётся в его единицах.
 *
 * <p>Знак — направление стата: защита упала — «−8%», скорость выросла — «+12%».
 * Не направление фразы эффекта: у защиты фраза перевёрнута («−60% получаемого
 * урона»), но под значком защиты «+8%» читалось бы как «защиты стало больше».
 *
 * @param amount  изменение: в процентах эффекта или в единицах стата
 * @param percent в процентах ли оно
 */
public record StatContribution(String statId, double amount, boolean percent) {

    /** Вклад источника: итог с ним против итога без него. */
    public static StatContribution of(StatDef def, double with, double without) {
        if (def.effect() == null) {
            return new StatContribution(def.id(), with - without, false);
        }
        return new StatContribution(def.id(),
                def.effect().percent(with) - def.effect().percent(without), true);
    }

    /**
     * Хорошо ли это игроку.
     *
     * <p>Все статы проекта устроены «больше — лучше»: у защит и снижения
     * перезарядки перевёрнута только фраза, а не польза. Стат, которого чем
     * больше, тем хуже, потребует своего признака в {@code stats.yml} — до тех
     * пор правило одно.
     */
    public boolean good() {
        return amount > 0;
    }

    /**
     * Число для показа: «+12%», «-8%», «+4»; пусто — показывать нечего.
     *
     * <p>Пусто, когда округлённый вклад нулевой: «+0%» не говорит ничего и
     * занимает место. Минус — обычный дефис: шрифт Minecraft знает не каждый
     * символ, а неизвестный рисует квадратом.
     */
    public String text() {
        double size = Math.abs(amount);
        String number = size >= 10 ? String.valueOf(Math.round(size))
                : trim(Math.round(size * 10) / 10.0);
        if (number.equals("0")) {
            return "";
        }
        return (amount > 0 ? "+" : "-") + number + (percent ? "%" : "");
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
