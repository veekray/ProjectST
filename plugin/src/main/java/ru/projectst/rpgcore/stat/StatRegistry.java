package ru.projectst.rpgcore.stat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Реестр определений статов.
 *
 * <p>Создаётся один раз при загрузке и дальше только читается. Статического
 * доступа нет намеренно: реестр передаётся туда, где нужен, иначе в тестах
 * пришлось бы поднимать глобальное состояние.
 */
public final class StatRegistry {

    private final Map<String, StatDef> byId;

    public StatRegistry(Map<String, StatDef> byId) {
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    public Optional<StatDef> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public boolean has(String id) {
        return byId.containsKey(id);
    }

    public int size() {
        return byId.size();
    }

    public Iterable<StatDef> all() {
        return byId.values();
    }
}
