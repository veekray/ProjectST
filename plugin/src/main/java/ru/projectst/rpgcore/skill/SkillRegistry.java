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

    /**
     * Врождённый навык: тот, что есть у каждого игрока с первого уровня.
     *
     * <p>Он один — это проверяет связывание. Поэтому здесь не список, а
     * «есть или нет»: список заставил бы каждого вызывающего придумывать, что
     * делать со вторым.
     */
    public Optional<SkillDef> innate() {
        for (SkillDef skill : byId.values()) {
            if (skill.innate()) {
                return Optional.of(skill);
            }
        }
        return Optional.empty();
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
