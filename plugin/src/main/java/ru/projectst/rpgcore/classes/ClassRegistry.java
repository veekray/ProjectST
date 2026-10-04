package ru.projectst.rpgcore.classes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Реестр классов. */
public final class ClassRegistry {

    public static final ClassRegistry EMPTY = new ClassRegistry(Map.of());

    private final Map<String, ClassDef> byId;

    public ClassRegistry(Map<String, ClassDef> byId) {
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    public Optional<ClassDef> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public boolean has(String id) {
        return byId.containsKey(id);
    }

    public int size() {
        return byId.size();
    }

    public Set<String> ids() {
        return byId.keySet();
    }

    public Iterable<ClassDef> all() {
        return byId.values();
    }
}
