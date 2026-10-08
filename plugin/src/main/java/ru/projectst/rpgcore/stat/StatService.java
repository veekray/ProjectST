package ru.projectst.rpgcore.stat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Надбавки, базы и кеш снимков на игрока.
 *
 * <p>Главное требование SPEC к этому классу: <b>единственный путь сброса кеша</b>.
 * Любой изменяющий метод сам зовёт {@link #invalidate(UUID)}, и больше нигде кеш
 * не чистится. В старом стеке статы пересчитывались из десятка мест, и половина
 * расхождений «в меню одно, в бою другое» была именно пропущенным сбросом.
 *
 * <p>Надбавки сгруппированы по источнику: предмет, статус, класс. Снятие идёт
 * источником целиком, а не перебором отдельных надбавок — иначе при снятии
 * предмета пришлось бы помнить, что именно он давал.
 *
 * <p>Bukkit здесь не нужен: игрок обозначается {@link UUID}. Поэтому класс
 * проверяется юнит-тестами.
 */
public final class StatService {

    private final StatEngine engine;

    /** игрок → источник → надбавки этого источника */
    private final Map<UUID, Map<String, List<StatModifier>>> modifiers = new HashMap<>();

    /** игрок → стат → база от класса и уровня */
    private final Map<UUID, Map<String, Double>> bases = new HashMap<>();

    private final Map<UUID, StatSnapshot> cache = new HashMap<>();

    public StatService(StatEngine engine) {
        this.engine = engine;
    }

    /**
     * Ставит надбавки источника, заменяя его прежние целиком.
     *
     * @param source имя источника: {@code item:main_hand}, {@code status:warlock_wither}
     */
    /**
     * Доля, в которую превращается стат игрока: 0.29 — двадцать девять процентов.
     *
     * <p>Ею пользуются все, кто читает процентные статы: перезарядка, вампиризм,
     * скорость, радиус, сила эффектов. Считать их делением на сто по месту
     * означало бы, что кривая рейтинга применяется где-то, а где-то нет.
     */
    public double share(UUID player, String statId) {
        return engine.share(statId, snapshot(player).getOrZero(statId));
    }

    /** Знает ли движок такой стат: см. {@link StatEngine#knows(String)}. */
    public boolean knows(String statId) {
        return engine.knows(statId);
    }

    public void setSource(UUID player, String source, Collection<StatModifier> values) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source обязателен");
        }
        modifiers.computeIfAbsent(player, k -> new LinkedHashMap<>())
                .put(source, List.copyOf(values));
        invalidate(player);
    }

    /** Снимает все надбавки источника. */
    public void removeSource(UUID player, String source) {
        Map<String, List<StatModifier>> bySource = modifiers.get(player);
        if (bySource != null && bySource.remove(source) != null) {
            invalidate(player);
        }
    }

    /** База стата от класса и уровня. */
    public void setBase(UUID player, String statId, double value) {
        bases.computeIfAbsent(player, k -> new LinkedHashMap<>()).put(statId, value);
        invalidate(player);
    }

    /** Снимок. Считается при промахе кеша, дальше отдаётся тот же объект. */
    public StatSnapshot snapshot(UUID player) {
        StatSnapshot cached = cache.get(player);
        if (cached != null) {
            return cached;
        }
        List<StatModifier> all = new ArrayList<>();
        Map<String, List<StatModifier>> bySource = modifiers.get(player);
        if (bySource != null) {
            for (List<StatModifier> list : bySource.values()) {
                all.addAll(list);
            }
        }
        Map<String, Double> playerBases = bases.getOrDefault(player, Map.of());
        StatSnapshot fresh = engine.computeAll(playerBases, all);
        cache.put(player, fresh);
        return fresh;
    }

    /**
     * Значение стата так, будто одного источника нет.
     *
     * <p>Нужно показу вклада: «сколько даёт именно эта Пелена» — это разница
     * между итогом и итогом без неё, посчитанная тем же движком в том же
     * порядке. Вычитать надбавку из итога по месту нельзя: проценты и множители
     * применяются после сложения, и простая разница соврала бы.
     */
    public double valueWithout(UUID player, String statId, String source) {
        List<StatModifier> rest = new ArrayList<>();
        Map<String, List<StatModifier>> bySource = modifiers.get(player);
        if (bySource != null) {
            bySource.forEach((name, list) -> {
                if (!name.equals(source)) {
                    rest.addAll(list);
                }
            });
        }
        return engine.computeAll(bases.getOrDefault(player, Map.of()), rest).getOrZero(statId);
    }

    /** Единственный путь сброса кеша. Публичный: его зовут внешние события. */
    public void invalidate(UUID player) {
        cache.remove(player);
    }

    /** Полностью забыть игрока: выход с сервера. */
    public void forget(UUID player) {
        modifiers.remove(player);
        bases.remove(player);
        cache.remove(player);
    }

    /** Имена активных источников: нужно команде отладки. */
    public Collection<String> sources(UUID player) {
        Map<String, List<StatModifier>> bySource = modifiers.get(player);
        return bySource == null ? List.of() : List.copyOf(bySource.keySet());
    }
}
