package ru.projectst.rpgcore.skill;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Реестр загруженных навыков. */
public final class SkillRegistry {

    public static final SkillRegistry EMPTY = new SkillRegistry(Map.of());

    private final Map<String, SkillDef> byId;

    public SkillRegistry(Map<String, SkillDef> byId) {
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    public Optional<SkillDef> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public boolean has(String id) {
        return byId.containsKey(id);
    }

    public int size() {
        return byId.size();
    }

    public Iterable<SkillDef> all() {
        return byId.values();
    }

    public Iterable<String> ids() {
        return byId.keySet();
    }
}
