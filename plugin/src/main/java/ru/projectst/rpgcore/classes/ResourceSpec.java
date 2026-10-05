package ru.projectst.rpgcore.classes;

/**
 * Чем класс платит за навыки.
 *
 * <p>У мага это мана, у плута — выносливость, и это не два разных механизма, а
 * одно и то же с разными статами и названием. В старом стеке у них были
 * отдельные пути: мана считалась одним плагином, выносливость — другим, поэтому
 * «не хватило» выглядело по-разному и проверялось в разных местах.
 *
 * @param display  как называть ресурс игроку
 * @param maxStat  стат запаса
 * @param regenStat стат восстановления в секунду
 */
public record ResourceSpec(String display, String maxStat, String regenStat) {

    public static final ResourceSpec MANA = new ResourceSpec("Мана", "max_mana", "mana_regen");

    public ResourceSpec {
        if (display == null || display.isBlank()) {
            throw new IllegalArgumentException("у ресурса обязательно название");
        }
        if (maxStat == null || maxStat.isBlank() || regenStat == null || regenStat.isBlank()) {
            throw new IllegalArgumentException("у ресурса обязательны статы запаса и реген");
        }
    }
}
