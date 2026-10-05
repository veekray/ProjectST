package ru.projectst.rpgcore.item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Реестр загруженных предметов и редкостей. */
public final class ItemRegistry {

    public static final ItemRegistry EMPTY = new ItemRegistry(Map.of(), Map.of());

    private final Map<String, ItemDef> items;
    private final Map<String, Rarity> rarities;

    public ItemRegistry(Map<String, ItemDef> items, Map<String, Rarity> rarities) {
        this.items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
        this.rarities = Collections.unmodifiableMap(new LinkedHashMap<>(rarities));
    }

    public Optional<ItemDef> find(String id) {
        return Optional.ofNullable(items.get(id));
    }

    public boolean has(String id) {
        return items.containsKey(id);
    }

    public Iterable<ItemDef> all() {
        return items.values();
    }

    public Iterable<String> ids() {
        return items.keySet();
    }

    public int size() {
        return items.size();
    }

    /**
     * Редкость предмета.
     *
     * <p>Необъявленная редкость — обычная, а не ошибка в рантайме: ошибку уже
     * назвало связывание, и ронять из-за неё выдачу предмета незачем.
     */
    public Rarity rarity(String id) {
        return rarities.getOrDefault(id, Rarity.COMMON);
    }

    public boolean hasRarity(String id) {
        return rarities.containsKey(id);
    }

    public Iterable<Rarity> rarities() {
        return rarities.values();
    }
}
