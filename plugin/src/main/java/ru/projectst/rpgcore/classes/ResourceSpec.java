package ru.projectst.rpgcore.classes;

/**
 * Чем класс платит за навыки.
 *
 * <p>У мага это мана, у воина — сила духа, и это не два разных механизма, а
 * одно и то же с разными статами и названием. В старом стеке у них были
 * отдельные пути: мана считалась одним плагином, выносливость — другим, поэтому
 * «не хватило» выглядело по-разному и проверялось в разных местах.
 *
 * <p>Выносливость тоже описана здесь, но классу она не принадлежит: это общий
 * для всех запас, из которого платится врождённый рывок. Лежит рядом потому,
 * что механизм у неё тот же, а не потому, что ею можно платить за навыки
 * класса — этого как раз нельзя, и ClassDefLoader об этом скажет.
 *
 * @param display  как называть ресурс игроку
 * @param maxStat  стат запаса
 * @param regenStat стат восстановления в секунду
 */
public record ResourceSpec(String display, String maxStat, String regenStat) {

    public static final ResourceSpec MANA = new ResourceSpec("Мана", "max_mana", "mana_regen");

    /** Ресурс не магических классов: то же, что мана, другими статами. */
    public static final ResourceSpec SPIRIT =
            new ResourceSpec("Сила духа", "max_spirit", "spirit_regen");

    /** Общий запас всех игроков. Не ресурс класса: см. замечание выше. */
    public static final ResourceSpec STAMINA =
            new ResourceSpec("Выносливость", "max_stamina", "stamina_regen");

    public ResourceSpec {
        if (display == null || display.isBlank()) {
            throw new IllegalArgumentException("у ресурса обязательно название");
        }
        if (maxStat == null || maxStat.isBlank() || regenStat == null || regenStat.isBlank()) {
            throw new IllegalArgumentException("у ресурса обязательны статы запаса и реген");
        }
    }
}
