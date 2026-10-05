package ru.projectst.rpgcore.craft;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Реестр загруженных рецептов. */
public final class RecipeRegistry {

    public static final RecipeRegistry EMPTY = new RecipeRegistry(Map.of());

    private final Map<String, RecipeDef> byId;

    public RecipeRegistry(Map<String, RecipeDef> byId) {
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    public Optional<RecipeDef> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Iterable<RecipeDef> all() {
        return byId.values();
    }

    public int size() {
        return byId.size();
    }
}
