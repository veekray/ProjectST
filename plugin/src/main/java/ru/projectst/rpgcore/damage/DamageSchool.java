package ru.projectst.rpgcore.damage;

/**
 * Школа урона. Определяет, какой стат атакующего его усиливает и какой стат
 * цели его снижает.
 *
 * <p>Намеренно короткий список. В старом стеке типов урона было девять, из них
 * половина не влияла ни на что, а один (MAGICAL) вообще не существовал, хотя
 * выглядел правдоподобно — и тихо ломал каждый навык, где был написан.
 */
public enum DamageSchool {

    /** Физический: усиливается physical_damage, снижается defense. */
    PHYSICAL("physical_damage", "defense"),

    /** Магический: усиливается magic_damage, снижается magic_resistance. */
    MAGIC("magic_damage", "magic_resistance"),

    /** Чистый: не усиливается и не снижается ничем. Для добивающих эффектов. */
    TRUE(null, null);

    private final String offenseStat;
    private final String defenseStat;

    DamageSchool(String offenseStat, String defenseStat) {
        this.offenseStat = offenseStat;
        this.defenseStat = defenseStat;
    }

    /** Стат атакующего, усиливающий эту школу, либо {@code null}. */
    public String offenseStat() {
        return offenseStat;
    }

    /** Стат цели, снижающий эту школу, либо {@code null}. */
    public String defenseStat() {
        return defenseStat;
    }

    public boolean mitigable() {
        return defenseStat != null;
    }
}
