package ru.projectst.rpgcore.cast;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import ru.projectst.rpgcore.classes.ClassService;
import ru.projectst.rpgcore.classes.ResourceSpec;
import ru.projectst.rpgcore.stat.StatService;

/**
 * Расходуемый ресурс игроков: мана у мага, выносливость у плута.
 *
 * <p>Один механизм на оба, и названия статов приходят от класса. Максимум и
 * восстановление — статы, а не отдельные числа: иначе класс, предмет и навык
 * получили бы три разных способа влиять на запас, и однажды они разошлись бы.
 * Текущее значение живёт здесь, а не в {@link StatService}, потому что это не
 * стат, а расходуемое число.
 *
 * <p>Новый игрок начинает с полным запасом. Это сознательно: ноль при входе
 * выглядел бы как поломка.
 */
public final class ResourcePool {

    private final StatService stats;
    private final ClassService classes;
    private final Map<UUID, Double> current = new HashMap<>();

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

    public double max(UUID player) {
        return Math.max(0, stats.snapshot(player).get(specOf(player).maxStat()));
    }

    public double current(UUID player) {
        double max = max(player);
        Double value = current.get(player);
        if (value == null) {
            return max;
        }
        // Максимум мог упасть вместе со снятым бафом — запас за ним следует.
        return Math.min(value, max);
    }

    public boolean has(UUID player, double amount) {
        return current(player) >= amount;
    }

    /**
     * Списывает ману.
     *
     * @return {@code false}, если её не хватило; в этом случае ничего не списано
     */
    public boolean spend(UUID player, double amount) {
        double now = current(player);
        if (now < amount) {
            return false;
        }
        current.put(player, now - amount);
        return true;
    }

    public void restore(UUID player, double amount) {
        current.put(player, Math.min(max(player), current(player) + amount));
    }

    public void fill(UUID player) {
        current.put(player, max(player));
    }

    /**
     * Восстановление за прошедшее время.
     *
     * @param seconds сколько секунд прошло; стат задан в единицах за секунду
     */
    public void regenerate(UUID player, double seconds) {
        double rate = stats.snapshot(player).get(specOf(player).regenStat());
        if (rate > 0) {
            restore(player, rate * seconds);
        }
    }

    public void forget(UUID player) {
        current.remove(player);
    }

    public int trackedPlayers() {
        return current.size();
    }
}
