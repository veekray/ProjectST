package ru.projectst.rpgcore.loader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Сборщик ошибок загрузки.
 *
 * <p>Ошибки собираются, а не выбрасываются исключением на первой же. Это
 * сознательно: когда правишь конфиг, полезно увидеть сразу все пять опечаток,
 * а не по одной за перезагрузку.
 */
public final class ContentErrors {

    private final List<ContentError> errors = new ArrayList<>();

    public void add(SourceRef at, String path, String what) {
        errors.add(new ContentError(at, path, what));
    }

    public void add(ContentError error) {
        errors.add(error);
    }

    public boolean isEmpty() {
        return errors.isEmpty();
    }

    public int count() {
        return errors.size();
    }

    public List<ContentError> all() {
        return Collections.unmodifiableList(errors);
    }
}
