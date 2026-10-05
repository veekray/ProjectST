package ru.projectst.rpgcore.client;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Значки навыков: маленький пиксель-арт прямо в коде.
 *
 * <p>Не текстуры. Текстуры потребовали бы ресурспака, а он у игроков разный, и
 * половина увидела бы пустые квадраты. Девять на девять пикселей и три цвета —
 * этого хватает, чтобы навык узнавался в сетке, и не хватает, чтобы кто-нибудь
 * начал их перерисовывать.
 *
 * <p>Навык без своего значка рисуется ромбом класса, а не падает и не исчезает:
 * новый навык в контенте не должен требовать правки мода, чтобы его можно было
 * изучить.
 */
public final class SkillIcons {

    /** Значок: три цвета и сетка символов. */
    private record Icon(int dark, int main, int light, String[] rows) {
    }

    private static final Map<String, Icon> ICONS = new LinkedHashMap<>();

    /** Запасной цвет по началу идентификатора: маг синий, друид зелёный и так далее. */
    private static final Map<String, Integer> CLASS_COLOURS = Map.of(
            "mage", 0xFF5B8AD6,
            "druid", 0xFF4F8A3A,
            "warlock", 0xFF7F3A6B,
            "rogue", 0xFF5B5B6B,
            "item", 0xFFC9A227);

    private SkillIcons() {
    }

    static {
        // кольцо маны
        ICONS.put("mage_flow_loop", new Icon(0xFF3A2E6B, 0xFF6A5BD6, 0xFFBFB2FF, new String[] {
                "...bbb...",
                ".bb...bb.",
                ".b.....b.",
                "b...c...b",
                "b..ccc..b",
                "b...c...b",
                ".b.....b.",
                ".bb...bb.",
                "...bbb..."}));
        // разряд
        ICONS.put("mage_mana_bolt", new Icon(0xFF2E3A6B, 0xFF4F7FD6, 0xFFBFE0FF, new String[] {
                ".....bb..",
                "....bbc..",
                "...bbc...",
                "..bbcc...",
                ".bbbbbb..",
                "...ccbb..",
                "...cbb...",
                "..cbb....",
                "..bb....."}));
        // шаг в пустоту
        ICONS.put("mage_void_step", new Icon(0xFF2A1A3A, 0xFF6B3A9E, 0xFFC79BFF, new String[] {
                "..aaaaa..",
                ".abbbbba.",
                "abb...bba",
                "ab..c..ba",
                "ab.ccc.ba",
                "ab..c..ba",
                "abb...bba",
                ".abbbbba.",
                "..aaaaa.."}));
        // сгон
        ICONS.put("mage_herd", new Icon(0xFF2E3A6B, 0xFF5B8AD6, 0xFFBFD9FF, new String[] {
                "b.......b",
                ".b.....b.",
                "..b...b..",
                "...b.b...",
                "..ccbcc..",
                "...b.b...",
                "..b...b..",
                ".b.....b.",
                "b.......b"}));
        // россыпь
        ICONS.put("mage_scatter", new Icon(0xFF3A2E6B, 0xFF7F5BD6, 0xFFD9BFFF, new String[] {
                "c...c...c",
                ".b...b.b.",
                "..b..b.b.",
                "...b.b.b.",
                "....bbb..",
                "....ab...",
                "....ab...",
                "....ab...",
                "....aa..."}));
        // коллапс
        ICONS.put("mage_collapse", new Icon(0xFF3A1A4A, 0xFF8A3AC7, 0xFFF0C2FF, new String[] {
                "c.......c",
                ".b.....b.",
                "..bb.bb..",
                "...bbb...",
                "..bcccb..",
                "...bbb...",
                "..bb.bb..",
                ".b.....b.",
                "c.......c"}));
        // плющ
        ICONS.put("druid_poison_ivy", new Icon(0xFF1E3A1E, 0xFF4F8A3A, 0xFFA8D98A, new String[] {
                "....b....",
                "...bb....",
                "..bcb.cc.",
                "..bb.ccb.",
                "...bbbb..",
                ".cc.bb...",
                ".cbb.bb..",
                "...b..b..",
                "....b...."}));
        // споры
        ICONS.put("druid_life_spores", new Icon(0xFF1E3A2E, 0xFF4FA87F, 0xFFC2F0D9, new String[] {
                "..c...c..",
                ".ccc.ccc.",
                "..c...c..",
                "....b....",
                "...bbb...",
                "..bbbbb..",
                "...bbb...",
                ".c..b..c.",
                "ccc...ccc"}));
        // кора
        ICONS.put("druid_bark_guard", new Icon(0xFF3A2A14, 0xFF7F5B2E, 0xFFC7A06B, new String[] {
                ".aaaaaaa.",
                "abbbbbbba",
                "ab.cbc.ba",
                "abc.b.cba",
                "ab.cbc.ba",
                "abc.b.cba",
                "ab.cbc.ba",
                "abbbbbbba",
                ".aaaaaaa."}));
        // корни
        ICONS.put("druid_grasping_roots", new Icon(0xFF2A1E0F, 0xFF6B4A2A, 0xFFA87F4F, new String[] {
                "b.b...b.b",
                ".b.b.b.b.",
                ".b..b..b.",
                "..bbbbb..",
                "...bcb...",
                "..b.b.b..",
                ".b..b..b.",
                "b...b...b",
                "b...b...b"}));
        // зверь
        ICONS.put("druid_beast_call", new Icon(0xFF2A2014, 0xFF8A6B3A, 0xFFD9BF8A, new String[] {
                ".b.....b.",
                ".bb...bb.",
                ".bbbbbbb.",
                "bbcbbbcbb",
                "bbbbbbbbb",
                ".bbcccbb.",
                "..bbbbb..",
                "...b.b...",
                "..b...b.."}));
        // цвет бездны
        ICONS.put("druid_abyss_bloom", new Icon(0xFF2A143A, 0xFF6B2E7F, 0xFFD98AC7, new String[] {
                "....a....",
                ".c..b..c.",
                "..c.b.c..",
                "...cbc...",
                "abbbcbbba",
                "...cbc...",
                "..c.b.c..",
                ".c..b..c.",
                "....a...."}));
        // проклятый снаряд
        ICONS.put("warlock_cursed_bolt", new Icon(0xFF2A1A2A, 0xFF5B2E6B, 0xFFBF8AD9, new String[] {
                "..aaaaa..",
                ".abbbbba.",
                "ab.c.c.ba",
                "ab.....ba",
                "ab.ccc.ba",
                ".abbbbba.",
                "..a.b.a..",
                "...a.a...",
                "....a...."}));
        // метка жатвы
        ICONS.put("warlock_reap_mark", new Icon(0xFF2A1A2A, 0xFF7F3A6B, 0xFFE0A8C7, new String[] {
                "....b....",
                "...bbb...",
                "..b.c.b..",
                ".b..c..b.",
                "bbcccccbb",
                ".b..c..b.",
                "..b.c.b..",
                "...bbb...",
                "....b...."}));
        // пелена
        ICONS.put("warlock_dark_veil", new Icon(0xFF14141E, 0xFF3A3A5B, 0xFF8A8AB2, new String[] {
                "..bbbbb..",
                ".bbbbbbb.",
                "bbbcbcbbb",
                "bbbbbbbbb",
                ".bbbbbbb.",
                "..a.a.a..",
                ".a.a.a.a.",
                "..a.a.a..",
                ".a.....a."}));
        // оковы
        ICONS.put("warlock_despair_chains", new Icon(0xFF1E1E28, 0xFF5B5B6B, 0xFFB2B2C7, new String[] {
                "bb.......",
                "bcb......",
                ".bbb.....",
                "..bcb....",
                "...bbb...",
                "....bcb..",
                ".....bbb.",
                "......bcb",
                ".......bb"}));
        // переливание
        ICONS.put("warlock_transfusion", new Icon(0xFF2A1A2A, 0xFF8A3A5B, 0xFFE0A8BF, new String[] {
                ".bb...bb.",
                "bccb.bccb",
                "bcccbcccb",
                "bcccccccb",
                ".bcccccb.",
                "..bcccb..",
                "...bcb...",
                "....b....",
                "....a...."}));
        // кокон
        ICONS.put("warlock_agony_cocoon", new Icon(0xFF1E142A, 0xFF4F2E6B, 0xFFA88AD9, new String[] {
                "...bbb...",
                "..bcccb..",
                ".bc.b.cb.",
                ".bc.b.cb.",
                "bc..b..cb",
                ".bc.b.cb.",
                ".bc.b.cb.",
                "..bcccb..",
                "...bbb..."}));
        // рывок
        ICONS.put("rogue_dash", new Icon(0xFF1E1E1E, 0xFF5B5B5B, 0xFFC7C7C7, new String[] {
                ".........",
                "..c......",
                ".ccc.....",
                "ccccbbb..",
                ".ccc.bbbb",
                "..c..bbb.",
                ".....b...",
                "....b....",
                "........."}));
        // шаг призрака
        ICONS.put("rogue_ghost_step", new Icon(0xFF1E2228, 0xFF4F5B6B, 0xFFB2C2D9, new String[] {
                "...ccc...",
                "..ccccc..",
                ".cc.c.cc.",
                ".ccccccc.",
                ".ccccccc.",
                "..bbbbb..",
                "..b.b.b..",
                ".b..b..b.",
                "a...a...a"}));
        // удар из тени
        ICONS.put("rogue_shadow_strike", new Icon(0xFF14141A, 0xFF3A3A4F, 0xFFC7C7D9, new String[] {
                ".......cc",
                "......cc.",
                ".....cc..",
                "....cc...",
                "...cc....",
                "..bc.....",
                ".bbb.....",
                "abb......",
                "aa......."}));
        // веер клинков
        ICONS.put("rogue_fan_of_knives", new Icon(0xFF1E1E24, 0xFF5B5B6B, 0xFFD9D9E0, new String[] {
                "c...c...c",
                ".c..c..c.",
                "..c.c.c..",
                "...ccc...",
                "ccc.b.ccc",
                "...ccc...",
                "..c.c.c..",
                ".c..c..c.",
                "c...c...c"}));
        // отражение
        ICONS.put("rogue_mirror_image", new Icon(0xFF1E2428, 0xFF4F6B7F, 0xFFBFD9E0, new String[] {
                ".bbb.bbb.",
                "bcccbcccb",
                "bcccbcccb",
                "bcccbcccb",
                ".bbb.bbb.",
                "....a....",
                "...a.a...",
                "..a...a..",
                ".a.....a."}));
        // метка смерти
        ICONS.put("rogue_mark_of_death", new Icon(0xFF2A1414, 0xFF7F2E2E, 0xFFE0A8A8, new String[] {
                "..bbbbb..",
                ".bcccccb.",
                "bc.c.c.cb",
                "bcccccccb",
                "bc.ccc.cb",
                ".bcccccb.",
                "..bbbbb..",
                "...b.b...",
                "..b...b.."}));
    }

    /**
     * Рисует значок навыка в ромбе.
     *
     * <p>Ромб, а не квадрат: так просил заказчик, и так значки не сливаются с
     * ванильными слотами инвентаря, которые всегда квадратные.
     *
     * @param size   сторона ромба по диагонали
     * @param bright цветной или приглушённый: приглушённый значит «не изучен»
     */
    public static void draw(GuiGraphics graphics, String skillId, int x, int y, int size,
                            boolean bright) {
        drawFrame(graphics, x, y, size, bright, classColour(skillId));

        Icon icon = ICONS.get(skillId);
        int pixel = Math.max(1, (size - 8) / 9);
        int glyphX = x + (size - pixel * 9) / 2;
        int glyphY = y + (size - pixel * 9) / 2;

        if (icon == null) {
            // Своего значка нет: рисуем точку цветом класса. Видно, что навык
            // есть, и видно, что художник до него не дошёл.
            int colour = shade(classColour(skillId), bright);
            graphics.fill(glyphX + pixel * 3, glyphY + pixel * 3,
                    glyphX + pixel * 6, glyphY + pixel * 6, colour);
            return;
        }

        for (int row = 0; row < 9; row++) {
            String line = icon.rows()[row];
            for (int column = 0; column < 9; column++) {
                int colour = switch (line.charAt(column)) {
                    case 'a' -> icon.dark();
                    case 'b' -> icon.main();
                    case 'c' -> icon.light();
                    default -> 0;
                };
                if (colour == 0) {
                    continue;
                }
                int px = glyphX + column * pixel;
                int py = glyphY + row * pixel;
                graphics.fill(px, py, px + pixel, py + pixel, shade(colour, bright));
            }
        }
    }

    /**
     * Кольцо перезарядки вокруг ромба.
     *
     * <p>Кольцо, а не полоска сбоку: в бою взгляд уже на значке, и возвращать
     * его к отдельной шкале — лишнее движение. Готовый навык горит зелёным
     * целиком, и это видно боковым зрением, не читая ни чисел, ни краёв.
     *
     * <p>Заполняется снизу вверх: так же, как наливается всё остальное на
     * экране. Пойди оно по кругу, пришлось бы помнить, откуда круг начинается.
     *
     * @param share сколько перезарядки уже прошло, от нуля до единицы
     */
    public static void ring(GuiGraphics graphics, int x, int y, int size, double share,
                            boolean ready) {
        int outer = size + 4;
        int originX = x - 2;
        int originY = y - 2;
        int half = outer / 2;
        int lit = (int) Math.round(outer * Math.clamp(share, 0, 1));
        int from = outer - lit;

        int on = ready ? 0xFF5FAE3F : 0xFFC9A227;
        int off = 0xFF2A2018;
        for (int row = 0; row < outer; row++) {
            int spread = half - Math.abs(row - half);
            int leftEdge = originX + half - spread;
            int rightEdge = originX + half + spread + 1;
            int colour = row >= from ? on : off;
            graphics.fill(leftEdge - 1, originY + row, leftEdge + 2, originY + row + 1, colour);
            graphics.fill(rightEdge - 2, originY + row, rightEdge + 1, originY + row + 1, colour);
        }
    }

    /** Ромбовидная рамка с заливкой. */
    private static void drawFrame(GuiGraphics graphics, int x, int y, int size,
                                  boolean bright, int accent) {
        int half = size / 2;
        for (int row = 0; row < size; row++) {
            int spread = half - Math.abs(row - half);
            int from = x + half - spread;
            int to = x + half + spread + 1;
            graphics.fill(from, y + row, to, y + row + 1,
                    bright ? 0xF01C1510 : 0xC0120E0A);
            // Края ромба: две точки на строку, этого достаточно для контура.
            int edge = bright ? accent : 0xFF4A4036;
            graphics.fill(from, y + row, from + 1, y + row + 1, edge);
            graphics.fill(to - 1, y + row, to, y + row + 1, edge);
        }
    }

    /** Приглушение: не изученный навык виден, но явно не включён. */
    private static int shade(int colour, boolean bright) {
        if (bright) {
            return colour;
        }
        int r = (colour >> 16) & 0xFF;
        int g = (colour >> 8) & 0xFF;
        int b = colour & 0xFF;
        int grey = (int) (0.3 * r + 0.59 * g + 0.11 * b) / 2;
        return 0xC0000000 | (grey << 16) | (grey << 8) | grey;
    }

    private static int classColour(String skillId) {
        int underscore = skillId.indexOf('_');
        String prefix = underscore < 0 ? skillId : skillId.substring(0, underscore);
        return CLASS_COLOURS.getOrDefault(prefix, RpgStyle.EDGE);
    }
}
