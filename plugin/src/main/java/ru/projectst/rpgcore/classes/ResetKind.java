package ru.projectst.rpgcore.classes;

import java.util.Locale;
import java.util.Optional;

/**
 * Что именно обнулять игроку.
 *
 * <p>Набор закрытый и каждый пункт назван: «обнулить» без уточнения — это
 * команда, после которой администратор идёт извиняться. Здесь видно заранее, что
 * пропадёт, а что останется.
 */
public enum ResetKind {

    /** Всё: класс, изученное, слоты, уровень и опыт. Игрок как новый. */
    ALL("всё: класс, навыки, слоты, уровень и опыт"),

    /** Класс вместе с изученным и слотами; уровень и опыт остаются. */
    CLASS("класс, изученное и слоты; уровень и опыт остаются"),

    /**
     * Изученные навыки и их уровни. Потраченные очки возвращаются — иначе
     * «сброс навыков» означал бы наказание, а не пересборку сборки.
     */
    SKILLS("изученные навыки; потраченные очки возвращаются"),

    /** Только привязки слотов. */
    SLOTS("привязки слотов"),

    /** Уровень, опыт и свободные очки; изученное остаётся. */
    PROGRESS("уровень, опыт и свободные очки");

    private final String what;

    ResetKind(String what) {
        this.what = what;
    }

    /** Что именно пропадёт: показывается до выполнения. */
    public String what() {
        return what;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ResetKind> of(String raw) {
        for (ResetKind kind : values()) {
            if (kind.key().equalsIgnoreCase(raw)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
