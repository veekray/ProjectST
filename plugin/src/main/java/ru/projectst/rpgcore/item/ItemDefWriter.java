package ru.projectst.rpgcore.item;

import java.util.Map;
import ru.projectst.rpgcore.stat.StatOp;

/**
 * Пишет файл предмета — тот самый, который читает {@link ItemDefLoader}.
 *
 * <p><b>Зачем вообще писать файлы.</b> Верстак мог бы держать собранный предмет
 * в памяти, но тогда у предметов появилось бы два сорта: объявленные файлом и
 * существующие только до перезапуска. Два сорта означают два пути в загрузке, в
 * выдаче и в связывании, и однажды они разойдутся. Поэтому верстак пишет
 * обычный файл предмета: собранный в игре предмет ничем не отличается от
 * написанного руками, и править его дальше можно и так, и так.
 *
 * <p><b>Цена решения — расхождение с загрузчиком.</b> Писатель и читатель одного
 * формата живут в двух методах и могут разъехаться: новый ключ в загрузчике
 * писатель не узнает, переименованный — потеряет. Плата по этому счёту —
 * {@code ItemDefWriterTest}: он собирает объявление, пишет, читает обратно и
 * сверяет с исходным. Поэтому забытый ключ падает тестом, а не теряется у
 * администратора в верстаке.
 *
 * <p>Ключи выводятся только те, что нужны: нулевая модель, пустое описание и
 * отсутствующие требования не пишутся вовсе. Файл, в котором половина строк —
 * «ничего», читается хуже короткого.
 */
public final class ItemDefWriter {

    private ItemDefWriter() {
    }

    /** Файл предмета целиком, с переводами строк в конце каждой строки. */
    public static String write(ItemDef def) {
        StringBuilder out = new StringBuilder();
        out.append("# Собран верстаком: /rpg forge.\n");
        out.append("# Это обычный файл предмета — правьте руками свободно, верстак\n");
        out.append("# откроет его обратно и ничего из написанного не потеряет.\n\n");

        out.append("id: ").append(def.id()).append('\n');
        out.append("display: ").append(quote(def.display())).append('\n');
        out.append("material: ").append(def.material()).append('\n');
        out.append("rarity: ").append(def.rarityId()).append('\n');
        out.append("slot: ").append(def.slot().key()).append('\n');
        if (def.unbreakable()) {
            out.append("unbreakable: true\n");
        }
        if (def.modelData() > 0) {
            out.append("model-data: ").append(def.modelData()).append('\n');
        }

        if (!def.lore().isEmpty()) {
            out.append("\nlore:\n");
            for (String line : def.lore()) {
                out.append("  - ").append(quote(line)).append('\n');
            }
        }

        // any() значит «подходит любому», то есть требований нет; раздел нужен
        // в обратном случае.
        if (!def.requirement().any()) {
            out.append("\nrequires:\n");
            if (def.requirement().classId() != null) {
                out.append("  class: ").append(def.requirement().classId()).append('\n');
            }
            if (def.requirement().level() > 0) {
                out.append("  level: ").append(def.requirement().level()).append('\n');
            }
        }

        if (!def.stats().isEmpty()) {
            out.append("\nstats:\n");
            for (Map.Entry<String, ItemDef.ItemStatLine> entry : def.stats().entrySet()) {
                ItemDef.ItemStatLine line = entry.getValue();
                out.append("  ").append(entry.getKey()).append(": ");
                if (line.op() == StatOp.FLAT) {
                    // Короткая форма — тот же синтаксис с опущенным op, а не
                    // второй способ записать то же самое.
                    out.append(number(line.value())).append('\n');
                } else {
                    out.append("{ op: ").append(line.op().name().toLowerCase(java.util.Locale.ROOT))
                            .append(", value: ").append(number(line.value())).append(" }\n");
                }
            }
        }

        if (!def.abilities().isEmpty()) {
            out.append("\nabilities:\n");
            for (ItemAbility ability : def.abilities()) {
                out.append("  - { skill: ").append(ability.skillId())
                        .append(", trigger: ").append(trigger(ability.trigger()))
                        .append(" }\n");
            }
        }
        return out.toString();
    }

    /**
     * Имя триггера в файле.
     *
     * <p>Обратная сторона разбора в загрузчике. Switch без ветки по умолчанию
     * намеренно: новый триггер не скомпилируется, пока его здесь не назовут, —
     * иначе он молча записался бы как чужой.
     */
    private static String trigger(ItemTrigger trigger) {
        return switch (trigger) {
            case RIGHT_CLICK -> "right-click";
            case LEFT_CLICK -> "left-click";
            case SNEAK_RIGHT_CLICK -> "sneak-right-click";
            case ON_HIT -> "on-hit";
        };
    }

    /**
     * Строка в кавычках.
     *
     * <p>В кавычках всегда, даже когда можно без них: имя предмета пишет
     * человек, и в нём встретится и двоеточие, и решётка, и ведущий пробел —
     * всё то, от чего YAML без кавычек меняет смысл. Обратный слэш и кавычка
     * экранируются, переводы строк заменяются пробелом: многострочного имени
     * предмета не бывает.
     */
    private static String quote(String value) {
        String text = value == null ? "" : value.replace("\r", " ").replace("\n", " ");
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    /** Целое пишется целым: «12.0» в файле читается как опечатка. */
    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
