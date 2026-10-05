package ru.projectst.rpgcore.convert;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Переносит объявления мобов MythicMobs в наш формат.
 *
 * <p><b>Конвертер не притворяется полным.</b> Он переносит то, что имеет прямое
 * соответствие: тип, имя, здоровье, урон, броню, опыт и дроп. Всё остальное —
 * список навыков со своими механиками, условиями и таргетерами — выписывается в
 * отчёт как «не перенесено», с указанием строки.
 *
 * <p>Так сделано намеренно. Конвертер, который молча пропускает непонятое,
 * оставляет моба без половины поведения, и обнаруживается это в бою. Конвертер,
 * который честно говорит «эти двенадцать навыков перенесите руками», экономит
 * ровно те часы, которые стоило экономить, и не создаёт ложного впечатления, что
 * перенос закончен.
 *
 * <p>Разбор здесь свой и намеренно простой: формат MythicMobs — это YAML, но
 * его содержимое строки вида {@code mechanic{a=1} @Targeter ?condition}, и
 * честный разбор этих строк означал бы написать его парсер целиком. Нам нужны
 * только ключи верхнего уровня, а до них достаточно отступов.
 */
public final class MobConverter {

    /** Что получилось: файлы на запись и отчёт о том, что не перенеслось. */
    public record Result(Map<String, String> files, List<String> report, int converted,
                         int skipped) {
    }

    private MobConverter() {
    }

    /**
     * Переносит один файл MythicMobs, в котором может быть несколько мобов.
     *
     * @param fileName имя файла, чтобы отчёт указывал на источник
     * @param text     содержимое
     */
    public static Result convert(String fileName, String text) {
        Map<String, String> files = new LinkedHashMap<>();
        List<String> report = new ArrayList<>();
        int converted = 0;
        int skipped = 0;

        List<String> lines = text.lines().toList();
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            // Объявление моба — ключ в самом начале строки, без отступа.
            if (!line.isBlank() && !line.startsWith(" ") && !line.startsWith("#")
                    && line.stripTrailing().endsWith(":")) {
                starts.add(i);
            }
        }

        for (int m = 0; m < starts.size(); m++) {
            int from = starts.get(m);
            int to = m + 1 < starts.size() ? starts.get(m + 1) : lines.size();
            String name = lines.get(from).strip();
            name = name.substring(0, name.length() - 1).trim();
            List<String> body = lines.subList(from + 1, to);

            Mob mob = read(body);
            if (mob.type == null) {
                report.add(fileName + ":" + (from + 1) + "  " + name
                        + " — нет ключа Type, пропущен целиком");
                skipped++;
                continue;
            }
            String id = identifier(name);
            files.put(id + ".yml", render(id, name, mob));
            converted++;

            for (String note : mob.notes) {
                report.add(fileName + "  " + name + " — " + note);
            }
        }

        if (converted == 0 && skipped == 0) {
            report.add(fileName + " — объявлений мобов не найдено");
        }
        return new Result(files, report, converted, skipped);
    }

    /** Разобранный моб: только то, что имеет прямое соответствие. */
    private static final class Mob {
        String type;
        String display;
        Double health;
        Double damage;
        Double armor;
        Integer experience;
        final List<String> drops = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
    }

    private static Mob read(List<String> body) {
        Mob mob = new Mob();
        String section = null;
        int skillCount = 0;

        for (String raw : body) {
            if (raw.isBlank() || raw.strip().startsWith("#")) {
                continue;
            }
            int indent = raw.length() - raw.stripLeading().length();
            String line = raw.strip();

            if (indent <= 2 && line.endsWith(":") && !line.contains(" ")) {
                section = line.substring(0, line.length() - 1).toLowerCase(Locale.ROOT);
                continue;
            }
            // Элемент списка разбирается раньше ключа: строка навыка
            // «- projectile{onHit=Boom} @target ~onTimer:100» содержит
            // двоеточие и иначе прочиталась бы как ключ верхнего уровня —
            // навык исчез бы из подсчёта, то есть молча.
            if (line.startsWith("-")) {
                if ("skills".equals(section)) {
                    skillCount++;
                } else if ("drops".equals(section)) {
                    mob.drops.add(line.substring(1).trim());
                }
                continue;
            }
            if (indent <= 2 && line.contains(":")) {
                section = null;
                String key = line.substring(0, line.indexOf(':')).trim()
                        .toLowerCase(Locale.ROOT);
                String value = line.substring(line.indexOf(':') + 1).trim();
                switch (key) {
                    case "type", "mobtype" -> mob.type = clean(value).toUpperCase(Locale.ROOT);
                    case "display" -> mob.display = clean(value);
                    case "health" -> mob.health = number(value);
                    case "damage" -> mob.damage = number(value);
                    case "armor" -> mob.armor = number(value);
                    default -> {
                        if (!key.isEmpty() && !value.isEmpty()) {
                            mob.notes.add("ключ " + key + " не перенесён");
                        }
                    }
                }
                continue;
            }

            if ("options".equals(section) && line.contains(":")) {
                String key = line.substring(0, line.indexOf(':')).trim()
                        .toLowerCase(Locale.ROOT);
                String value = line.substring(line.indexOf(':') + 1).trim();
                if ("movementspeed".equals(key)) {
                    mob.notes.add("скорость перемещения не перенесена: " + value);
                } else if ("alwaysshowname".equals(key)) {
                    // Имя у нас видно всегда: отдельного ключа нет.
                    continue;
                } else {
                    mob.notes.add("настройка " + key + " не перенесена");
                }
            }
        }

        if (skillCount > 0) {
            mob.notes.add("навыков не перенесено: " + skillCount
                    + " (механики, условия и таргетеры переносятся руками)");
        }
        return mob;
    }

    /** Строка нашего файла моба. */
    private static String render(String id, String originalName, Mob mob) {
        StringBuilder out = new StringBuilder();
        out.append("# Перенесено конвертером из MythicMobs: ").append(originalName).append('\n');
        out.append("#\n");
        out.append("# Проверьте числа и допишите навыки: конвертер переносит только то, что\n");
        out.append("# имеет прямое соответствие, а остальное перечислено в отчёте.\n\n");
        out.append("id: ").append(id).append('\n');
        out.append("display: \"").append(mob.display == null ? originalName : mob.display)
                .append("\"\n");
        out.append("type: ").append(mob.type).append('\n');
        out.append("health: ").append(trim(mob.health == null ? 20 : mob.health)).append('\n');
        if (mob.damage != null && mob.damage > 0) {
            out.append("damage: ").append(trim(mob.damage)).append('\n');
        }
        if (mob.experience != null) {
            out.append("experience: ").append(mob.experience).append('\n');
        }
        if (mob.armor != null && mob.armor > 0) {
            out.append('\n');
            out.append("# Броня MythicMobs перенесена в стат защиты: у нас её считает тот же\n");
            out.append("# конвейер урона, что защиту игрока.\n");
            out.append("stats:\n");
            out.append("  defense: ").append(trim(mob.armor)).append('\n');
        }
        if (!mob.drops.isEmpty()) {
            out.append('\n');
            out.append("# Дроп перенесён построчно. Таблицы дропа MythicMobs здесь не\n");
            out.append("# поддерживаются: строка — материал, количество и шанс.\n");
            out.append("drops:\n");
            for (String drop : mob.drops) {
                Drop parsed = parseDrop(drop);
                if (parsed == null) {
                    out.append("  # не разобрано: ").append(drop).append('\n');
                    continue;
                }
                out.append("  - { material: ").append(parsed.material)
                        .append(", min: ").append(parsed.min)
                        .append(", max: ").append(parsed.max)
                        .append(", chance: ").append(trim(parsed.chance))
                        .append(" }\n");
            }
        }
        return out.toString();
    }

    private record Drop(String material, int min, int max, double chance) {
    }

    /** Разбирает строку дропа вида {@code STRING 1-3 0.6}. */
    private static Drop parseDrop(String raw) {
        String[] parts = raw.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return null;
        }
        String material = parts[0].toUpperCase(Locale.ROOT);
        if (material.contains("{") || material.contains(":")) {
            return null; // таблица дропа или предмет другого плагина
        }
        int min = 1;
        int max = 1;
        double chance = 100;
        if (parts.length > 1 && parts[1].contains("-")) {
            String[] range = parts[1].split("-");
            min = (int) Math.max(1, number(range[0]) == null ? 1 : number(range[0]));
            max = (int) Math.max(min, number(range[1]) == null ? min : number(range[1]));
        } else if (parts.length > 1) {
            Double one = number(parts[1]);
            if (one != null) {
                min = (int) Math.max(1, one);
                max = min;
            }
        }
        if (parts.length > 2) {
            Double value = number(parts[2]);
            if (value != null) {
                // MythicMobs пишет шанс долей единицы, у нас — процентами.
                chance = value <= 1 ? value * 100 : value;
            }
        }
        return new Drop(material, min, Math.min(64, max), Math.min(100, Math.max(0.01, chance)));
    }

    private static String identifier(String name) {
        String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        id = id.replaceAll("_+", "_").replaceAll("^_|_$", "");
        return id.isEmpty() ? "converted_mob" : id;
    }

    private static String clean(String value) {
        String text = value.trim();
        if (text.length() > 1 && (text.startsWith("'") && text.endsWith("'")
                || text.startsWith("\"") && text.endsWith("\""))) {
            text = text.substring(1, text.length() - 1);
        }
        return text;
    }

    private static Double number(String value) {
        try {
            return Double.parseDouble(clean(value).replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
