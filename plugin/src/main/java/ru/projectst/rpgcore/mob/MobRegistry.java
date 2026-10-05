package ru.projectst.rpgcore.mob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Реестр загруженных мобов и правил спавна. */
public final class MobRegistry {

    public static final MobRegistry EMPTY = new MobRegistry(Map.of(), List.of());

    private final Map<String, MobDef> byId;
    private final List<SpawnRule> rules;

    public MobRegistry(Map<String, MobDef> byId, List<SpawnRule> rules) {
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
        this.rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public Optional<MobDef> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public boolean has(String id) {
        return byId.containsKey(id);
    }

    public Iterable<MobDef> all() {
        return byId.values();
    }

    public Iterable<String> ids() {
        return byId.keySet();
    }

    public int size() {
        return byId.size();
    }

    public List<SpawnRule> rules() {
        return rules;
    }

    /** Правила, подходящие этому миру и типу: порядок как в файле. */
    public List<SpawnRule> rulesFor(String world, String entityType) {
        List<SpawnRule> out = new ArrayList<>();
        for (SpawnRule rule : rules) {
            if (rule.appliesTo(world, entityType)) {
                out.add(rule);
            }
        }
        return out;
    }
}
