package ru.projectst.rpgcore.cast;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import ru.projectst.rpgcore.classes.ClassService;
import ru.projectst.rpgcore.classes.ResourceSpec;
import ru.projectst.rpgcore.stat.StatService;

/**
 * Расходуемые запасы игрока: ресурс класса и выносливость.
 *
 * <p>Два запаса, один механизм. Ресурс класса объявлен в файле класса: мана у
 * магов, сила духа у остальных. Выносливость есть у всех и ни от какого класса
 * не зависит.
 *
 * <p><b>Почему выносливость отдельно.</b> Из неё платится врождённый рывок,
 * который есть у каждого игрока с первого уровня. Будь она ресурсом класса,
 * рывок соревновался бы с навыками за одно и то же число, и «не хватило на
 * рывок» означало бы «нечем ударить» — то есть движение покупалось бы уроном.
 * Поэтому класс не может объявить её своим ресурсом, и об этом скажет
 * {@code ClassDefLoader}, а не игрок, которому нечем отступить.
 *
 * <p>Максимум и восстановление — статы, а не отдельные числа: иначе класс,
 * предмет и навык получили бы три разных способа влиять на запас, и однажды они
 * разошлись бы. Текущее значение живёт здесь, а не в {@link StatService}, потому
 * что это не стат, а расходуемое число.
 *
 * <p>Новый игрок начинает с полным запасом. Это сознательно: ноль при входе
 * выглядел бы как поломка.
 */
public final class ResourcePool {

    private final StatService stats;
    private final ClassService classes;

    /**
     * Текущие запасы по игроку.
     *
     * <p>Ключ вложенной карты — стат запаса, то есть сам вид ресурса. Не
     * название и не номер: стат — единственное, что у вида ресурса есть
     * наверняка, и по нему же считается максимум.
     */
    private final Map<UUID, Map<String, Double>> current = new HashMap<>();

    public ResourcePool(StatService stats, ClassService classes) {
        this.stats = stats;
        this.classes = classes;
    }

    /** Чем платит этот игрок; без класса — мана. */
    public ResourceSpec specOf(UUID player) {
        return classes.classOf(player).map(def -> def.resource()).orElse(ResourceSpec.MANA);
    }

    public String displayName(UUID player) {
        return specOf(player).display();
    }

    // ------------------------------------------------------------------ ресурс класса

    public double max(UUID player) {
        return max(player, specOf(player));
    }

    public double current(UUID player) {
        return current(player, specOf(player));
    }

    public boolean has(UUID player, double amount) {
        return has(player, specOf(player), amount);
    }

    /**
     * Списывает ресурс класса.
     *
     * @return {@code false}, если его не хватило; в этом случае ничего не списано
     */
    public boolean spend(UUID player, double amount) {
        return spend(player, specOf(player), amount);
    }

    public void restore(UUID player, double amount) {
        restore(player, specOf(player), amount);
    }

    // ------------------------------------------------------------------ любой запас

    public double max(UUID player, ResourceSpec spec) {
        return Math.max(0, stats.snapshot(player).get(spec.maxStat()));
    }

    public double current(UUID player, ResourceSpec spec) {
        double max = max(player, spec);
        Double value = current.getOrDefault(player, Map.of()).get(spec.maxStat());
        if (value == null) {
            return max;
        }
        // Максимум мог упасть вместе со снятым бафом — запас за ним следует.
        return Math.min(value, max);
    }

    public boolean has(UUID player, ResourceSpec spec, double amount) {
        return current(player, spec) >= amount;
    }

    /**
     * Списывает из названного запаса.
     *
     * @return {@code false}, если его не хватило; в этом случае ничего не списано
     */
    public boolean spend(UUID player, ResourceSpec spec, double amount) {
        double now = current(player, spec);
        if (now < amount) {
            return false;
        }
        put(player, spec, now - amount);
        return true;
    }

    public void restore(UUID player, ResourceSpec spec, double amount) {
        put(player, spec, Math.min(max(player, spec), current(player, spec) + amount));
    }

    // ------------------------------------------------------------------ оба сразу

    /** Наполняет оба запаса: и ресурс класса, и выносливость. */
    public void fill(UUID player) {
        put(player, specOf(player), max(player));
        put(player, ResourceSpec.STAMINA, max(player, ResourceSpec.STAMINA));
    }

    /**
     * Восстановление за прошедшее время — обоих запасов.
     *
     * <p>Оба в одном методе намеренно: два таймера с разным шагом означали бы,
     * что «в секунду» для маны и для выносливости — разные секунды.
     *
     * @param seconds сколько секунд прошло; статы заданы в единицах за секунду
     */
    public void regenerate(UUID player, double seconds) {
        regenerate(player, specOf(player), seconds);
        regenerate(player, ResourceSpec.STAMINA, seconds);
    }

    private void regenerate(UUID player, ResourceSpec spec, double seconds) {
        double rate = stats.snapshot(player).get(spec.regenStat());
        if (rate > 0) {
            restore(player, spec, rate * seconds);
        }
    }

    public void forget(UUID player) {
        current.remove(player);
    }

    public int trackedPlayers() {
        return current.size();
    }

    private void put(UUID player, ResourceSpec spec, double value) {
        current.computeIfAbsent(player, p -> new HashMap<>()).put(spec.maxStat(), value);
    }
}
