package ru.projectst.rpgcore.status;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.loader.SourceRef;

/**
 * Реестр статусов. Помимо хранения проверяет граф конфликтов на противоречия.
 *
 * <p>Проверка при загрузке, а не при первом столкновении в бою, — это и есть
 * требование «никаких тихих отказов», перенесённое на отношения. Ссылка на
 * несуществующий статус или взаимное подавление обнаруживаются до того, как
 * игрок увидит их как «стан иногда не работает».
 */
public final class StatusRegistry {

    private final Map<String, StatusDef> byId;

    public StatusRegistry(Map<String, StatusDef> byId) {
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    public Optional<StatusDef> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Определение статуса. Отсутствие — ошибка программиста, не контента. */
    public StatusDef require(String id) {
        StatusDef def = byId.get(id);
        if (def == null) {
            throw new IllegalArgumentException("статус не объявлен: " + id);
        }
        return def;
    }

    public boolean has(String id) {
        return byId.containsKey(id);
    }

    public int size() {
        return byId.size();
    }

    public Iterable<StatusDef> all() {
        return byId.values();
    }

    /**
     * Проверяет граф отношений. Ошибки пишутся в сборщик, чтобы их показала
     * одна команда {@code /rpg validate}, а не серия перезагрузок.
     *
     * @param where откуда взялись определения: нужно для текста ошибки
     */
    public void validate(SourceRef where, ContentErrors errors) {
        for (StatusDef def : byId.values()) {
            // ссылки в пустоту
            for (String ref : def.referencedIds()) {
                if (!byId.containsKey(ref)) {
                    errors.add(where, "statuses." + def.id(),
                            "ссылка на несуществующий статус \"" + ref + "\"");
                }
            }
            // сам себя
            if (def.suppresses().contains(def.id())) {
                errors.add(where, "statuses." + def.id(), "статус подавляет сам себя");
            }
            if (def.blocks().contains(def.id())) {
                errors.add(where, "statuses." + def.id(), "статус блокирует сам себя");
            }
            if (def.removes().contains(def.id())) {
                errors.add(where, "statuses." + def.id(),
                        "статус удаляет сам себя: для повторного наложения есть stacking");
            }
        }
        reportSuppressCycles(where, errors);
    }

    /**
     * Цикл в подавлении означает неопределённость: если A подавляет B, а B
     * подавляет A, то при обоих активных ни один не действует либо действует
     * тот, кто наложен позже, — поведение зависит от порядка, то есть ровно то,
     * от чего затевался проект. Поэтому цикл — ошибка.
     *
     * <p>Взаимная блокировка, в отличие от подавления, законна: кто успел
     * первым, тот и стоит, и это предсказуемо.
     */
    private void reportSuppressCycles(SourceRef where, ContentErrors errors) {
        Set<String> reported = new HashSet<>();
        for (String start : byId.keySet()) {
            Optional<String> cycle = findCycleFrom(start);
            if (cycle.isPresent() && reported.add(cycle.get())) {
                errors.add(where, "statuses." + start,
                        "взаимное подавление: " + cycle.get());
            }
        }
    }

    /** Поиск в глубину по графу подавления. Возвращает описание цикла, если он есть. */
    private Optional<String> findCycleFrom(String start) {
        Deque<String> path = new ArrayDeque<>();
        Set<String> onPath = new LinkedHashSet<>();
        return dfs(start, path, onPath, new HashSet<>());
    }

    private Optional<String> dfs(String current, Deque<String> path,
                                 Set<String> onPath, Set<String> done) {
        if (onPath.contains(current)) {
            StringBuilder sb = new StringBuilder();
            for (String node : onPath) {
                sb.append(node).append(" -> ");
            }
            return Optional.of(sb + current);
        }
        if (!done.add(current)) {
            return Optional.empty();
        }
        StatusDef def = byId.get(current);
        if (def == null) {
            return Optional.empty();
        }
        onPath.add(current);
        path.push(current);
        for (String next : def.suppresses()) {
            Optional<String> found = dfs(next, path, onPath, done);
            if (found.isPresent()) {
                return found;
            }
        }
        path.pop();
        onPath.remove(current);
        return Optional.empty();
    }
}
