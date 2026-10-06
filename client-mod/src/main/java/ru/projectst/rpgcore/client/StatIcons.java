package ru.projectst.rpgcore.client;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Значки и короткие имена статов.
 *
 * <p>Полное имя стата занимает полстроки: «Восстановление выносливости» рядом с
 * числом не оставляет места ни на что. В списке стоит значок и короткое имя, а
 * полное показывает подсказка — там оно никому не мешает.
 *
 * <p>Значки нарисованы здесь же, семь на семь, по той же причине, что и значки
 * навыков: картинка потребовала бы ресурспака или чужой графики в моде, а
 * растянутая под другой размер она превращается в мыло.
 *
 * <p>Стат без своего значка рисуется ромбом и показывает полное имя: новый стат
 * в <code>stats.yml</code> не должен требовать правки мода, чтобы его было
 * видно.
 */
public final class StatIcons {

    /** Значок: три цвета и сетка символов. */
    private record Icon(int dark, int main, int light, String[] rows) {
    }

    /** Сторона значка в пикселях. */
    public static final int SIZE = 7;

    private static final Map<String, Icon> ICONS = new LinkedHashMap<>();
    private static final Map<String, String> SHORT = new LinkedHashMap<>();

    private StatIcons() {
    }

    static {
        // ------------------------------------------------------------ урон
        SHORT.put("skill_damage", "Урон навыков");
        ICONS.put("skill_damage", new Icon(0xFF6B2E1A, 0xFFC94A2A, 0xFFF0C050, new String[] {
                "...c...",
                ".b.c.b.",
                "..ccc..",
                "cccccc.",
                "..ccc..",
                ".b.c.b.",
                "...c..."}));

        SHORT.put("physical_damage", "Физ. урон");
        ICONS.put("physical_damage", new Icon(0xFF5A4A2A, 0xFFB9B2A0, 0xFFF0ECE0,
                new String[] {
                        ".....cc",
                        "....cc.",
                        "...cc..",
                        "..cc...",
                        ".ac....",
                        "aaa....",
                        "b.a...."}));

        SHORT.put("magic_damage", "Маг. урон");
        ICONS.put("magic_damage", new Icon(0xFF2E2A6B, 0xFF5B6BD6, 0xFFBFC8FF, new String[] {
                "...b...",
                "..bcb..",
                ".bcccb.",
                "bcccccb",
                ".bcccb.",
                "..bcb..",
                "...b..."}));

        SHORT.put("critical_strike_chance", "Шанс крита");
        ICONS.put("critical_strike_chance", new Icon(0xFF5A1A14, 0xFFC94A3D, 0xFFF0D070,
                new String[] {
                        "..bbb..",
                        ".b...b.",
                        "b..c..b",
                        "b.ccc.b",
                        "b..c..b",
                        ".b...b.",
                        "..bbb.."}));

        SHORT.put("critical_strike_power", "Сила крита");
        ICONS.put("critical_strike_power", new Icon(0xFF5A1A14, 0xFFC94A3D, 0xFFF0D070,
                new String[] {
                        "b..b..b",
                        ".b.c.b.",
                        "..ccc..",
                        "bcccccb",
                        "..ccc..",
                        ".b.c.b.",
                        "b..b..b"}));

        // ------------------------------------------------------------ защита
        SHORT.put("physical_defense", "Физ. защита");
        ICONS.put("physical_defense", new Icon(0xFF2A3A5A, 0xFF4A6B9E, 0xFFBFD0E8, new String[] {
                "ccccccc",
                "bbbbbbb",
                ".bbbbb.",
                ".bbbbb.",
                "..bbb..",
                "..bbb..",
                "...b..."}));

        SHORT.put("magic_defense", "Маг. защита");
        ICONS.put("magic_defense", new Icon(0xFF2A2A5A, 0xFF6B5BD6, 0xFFD0C8FF, new String[] {
                "ccccccc",
                "bb.c.bb",
                ".bcccb.",
                ".bb.bb.",
                "..b.b..",
                "..bbb..",
                "...b..."}));

        SHORT.put("general_defense", "Общая защита");
        ICONS.put("general_defense", new Icon(0xFF3A3A3A, 0xFF8A8A7A, 0xFFD8D8C8, new String[] {
                "cc...cc",
                "ccbbbcc",
                ".bbbbb.",
                ".bcccb.",
                ".bbbbb.",
                ".bbbbb.",
                "..bbb.."}));

        // ------------------------------------------------------------ эффекты
        SHORT.put("effect_power", "Сила эффектов");
        ICONS.put("effect_power", new Icon(0xFF2A4A2A, 0xFF4F9E4A, 0xFFBFF0A0, new String[] {
                "..aaa..",
                "...a...",
                "..bbb..",
                ".bbbbb.",
                ".bcccb.",
                ".bbbbb.",
                "..bbb.."}));

        SHORT.put("effect_duration", "Длит. эффектов");
        ICONS.put("effect_duration", new Icon(0xFF5A4A2A, 0xFFC9A227, 0xFFF0E0A0, new String[] {
                "bbbbbbb",
                ".ccccc.",
                "..ccc..",
                "...c...",
                "..c.c..",
                ".ccccc.",
                "bbbbbbb"}));

        SHORT.put("cooldown_reduction", "Сниж. КД");
        ICONS.put("cooldown_reduction", new Icon(0xFF3A3A4A, 0xFF8A8AA0, 0xFFE0E0F0, new String[] {
                "..bbb..",
                ".b...bb",
                "b..c..b",
                "b..cc.b",
                "b.....b",
                ".b...b.",
                "..bbb.."}));

        // ------------------------------------------------------------ ресурсы
        SHORT.put("max_mana", "Запас маны");
        ICONS.put("max_mana", new Icon(0xFF1A2A5A, 0xFF2F6FA8, 0xFFA0D0F0, new String[] {
                "...b...",
                "...b...",
                "..bbb..",
                ".bbcbb.",
                "bbbcbbb",
                ".bbbbb.",
                "..bbb.."}));

        SHORT.put("mana_regen", "Реген маны");
        ICONS.put("mana_regen", new Icon(0xFF1A2A5A, 0xFF2F6FA8, 0xFFA0D0F0, new String[] {
                "...b...",
                "..bbb..",
                ".bbbbb.",
                "..bbb..",
                "...c...",
                "..ccc..",
                ".c.c.c."}));

        SHORT.put("max_health", "Здоровье");
        ICONS.put("max_health", new Icon(0xFF5A1414, 0xFF9E2B25, 0xFFE07068, new String[] {
                ".bb.bb.",
                "bcbbbbb",
                "bcbbbbb",
                "bbbbbbb",
                ".bbbbb.",
                "..bbb..",
                "...b..."}));

        SHORT.put("max_spirit", "Запас духа");
        ICONS.put("max_spirit", new Icon(0xFF3A2A5A, 0xFF7B52B0, 0xFFD8C0F0, new String[] {
                "..bbb..",
                ".bcccb.",
                "bc.c.cb",
                "bcccccb",
                "bc.c.cb",
                ".bcccb.",
                "..bbb.."}));

        SHORT.put("spirit_regen", "Реген духа");
        ICONS.put("spirit_regen", new Icon(0xFF3A2A5A, 0xFF7B52B0, 0xFFD8C0F0, new String[] {
                "...b...",
                "..bbb..",
                ".bcccb.",
                "..bbb..",
                "...c...",
                "..ccc..",
                ".c.c.c."}));

        SHORT.put("max_stamina", "Запас сил");
        ICONS.put("max_stamina", new Icon(0xFF5A4A1A, 0xFFC9A227, 0xFFF0E0A0, new String[] {
                "....cc.",
                "...cc..",
                "..cc...",
                ".ccccc.",
                "...cc..",
                "..cc...",
                ".cc...."}));

        SHORT.put("stamina_regen", "Реген сил");
        ICONS.put("stamina_regen", new Icon(0xFF5A4A1A, 0xFFC9A227, 0xFFF0E0A0, new String[] {
                "...cc..",
                "..cc...",
                ".ccccc.",
                "...cc..",
                "...b...",
                "..bbb..",
                ".b.b.b."}));
    }

    /** Короткое имя для списка; если своего нет — полное, как прислал сервер. */
    public static String shortName(String statId, String display) {
        return SHORT.getOrDefault(statId, display);
    }

    /** Рисует значок стата размером {@link #SIZE} на {@link #SIZE}. */
    public static void draw(GuiGraphics graphics, String statId, int x, int y) {
        Icon icon = ICONS.get(statId);
        if (icon == null) {
            // Своего значка нет: ромб бронзой. Видно, что стат есть, и видно,
            // что художник до него не дошёл.
            RpgStyle.pip(graphics, x, y, SIZE, true, RpgStyle.EDGE);
            return;
        }
        for (int row = 0; row < SIZE; row++) {
            String line = icon.rows()[row];
            for (int column = 0; column < SIZE && column < line.length(); column++) {
                int colour = switch (line.charAt(column)) {
                    case 'a' -> icon.dark();
                    case 'b' -> icon.main();
                    case 'c' -> icon.light();
                    default -> 0;
                };
                if (colour != 0) {
                    graphics.fill(x + column, y + row, x + column + 1, y + row + 1, colour);
                }
            }
        }
    }
}
