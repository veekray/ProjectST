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

    /**
     * Доля, в которую превращается значение стата.
     *
     * <p>Необъявленный стат считается процентами напрямую: конвейер читает и те
     * статы, которых сервер может не объявлять, и ронять бой из-за этого
     * незачем. Одно место на весь проект — чтобы меню и бой не разошлись.
     */
    public double share(String id, double value) {
        StatDef def = byId.get(id);
        return def == null ? value / 100.0 : def.share(value);
    }

    /** Процент, в который превращается значение стата. */
    public double percent(String id, double value) {
        StatDef def = byId.get(id);
        return def == null ? value : def.percent(value);
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
