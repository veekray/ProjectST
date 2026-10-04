package ru.projectst.rpgcore.status;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.loader.YmlDoc;
import ru.projectst.rpgcore.loader.YmlMap;

/**
 * Читает статусы из YAML и сразу проверяет граф конфликтов.
 *
 * <p>Ожидаемый вид файла:
 * <pre>
 * statuses:
 *   stun:
 *     category: control
 *     duration: 40
 *     stacking: refresh
 *     priority: 10
 *     exclusive: control
 *     tags: [hard]
 *   banish:
 *     category: immunity
 *     duration: 80
 *     priority: 100
 *     exclusive: control
 *     suppresses: [stun]
 * </pre>
 */
public final class StatusDefLoader {

    private StatusDefLoader() {
    }

    public static Optional<StatusRegistry> load(String file, String text, ContentErrors errors) {
        Optional<YmlDoc> parsed = YmlDoc.parse(file, text, errors);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        YmlDoc doc = parsed.get();

        Map<String, StatusDef> defs = new LinkedHashMap<>();
        Optional<YmlMap> section = doc.root().map("statuses");
        if (section.isPresent()) {
            YmlMap statuses = section.get();
            for (String id : statuses.keys()) {
                Optional<YmlMap> body = statuses.map(id);
                if (body.isEmpty()) {
                    continue;
                }
                read(id, body.get(), errors).ifPresent(def -> defs.put(def.id(), def));
            }
        }

        doc.finish();

        StatusRegistry registry = new StatusRegistry(defs);
        // Граф проверяется здесь же: смысла отдавать наружу реестр, про который
        // неизвестно, противоречив он или нет, нет никакого.
        registry.validate(doc.root().at(), errors);
        return Optional.of(registry);
    }

    /**
     * ВАЖНО: все ключи читаются до любой проверки и до любого возврата.
     *
     * <p>Иначе получается ложная диагностика. Досрочный выход оставляет
     * остальные ключи непрочитанными, и {@code YmlDoc.finish()} объявляет их
     * неизвестными — в ответ на отсутствие {@code category} пользователь видел
     * ещё и «duration: неизвестный ключ». Это ровно та категория вранья в
     * сообщениях, против которой написан весь модуль.
     */
    private static Optional<StatusDef> read(String id, YmlMap body, ContentErrors errors) {
        StatusCategory category = body.enumOf("category", StatusCategory.class, null);
        int duration = body.integer("duration", 1, 72_000, 40);
        int maxStacks = body.integer("max-stacks", 1, 99, 1);
        Stacking stacking = body.enumOf("stacking", Stacking.class, Stacking.REFRESH);
        int priority = body.integer("priority", -1000, 1000, 0);
        StatusCategory exclusive = body.enumOf("exclusive", StatusCategory.class, null);

        Set<String> suppresses = Set.copyOf(body.strings("suppresses"));
        Set<String> removes = Set.copyOf(body.strings("removes"));
        Set<String> blocks = Set.copyOf(body.strings("blocks"));
        Set<String> tags = Set.copyOf(body.strings("tags"));

        boolean ok = true;
        if (!id.equals(id.toLowerCase(Locale.ROOT))) {
            errors.add(body.at(), "statuses." + id,
                    "идентификатор статуса должен быть в нижнем регистре");
            ok = false;
        }
        if (category == null) {
            errors.add(body.at(), "statuses." + id, "обязательный ключ category отсутствует");
            ok = false;
        }
        if (maxStacks > 1 && stacking != Stacking.STACKS) {
            errors.add(body.at(), "statuses." + id,
                    "max-stacks больше 1 имеет смысл только при stacking: stacks");
            ok = false;
        }
        if (!ok) {
            return Optional.empty();
        }

        return Optional.of(new StatusDef(id, category, duration, maxStacks, stacking,
                priority, exclusive, suppresses, removes, blocks, tags));
    }
}
