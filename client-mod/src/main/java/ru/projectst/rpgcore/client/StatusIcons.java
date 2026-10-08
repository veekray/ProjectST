package ru.projectst.rpgcore.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Значки статусов: пиксель-арт девять на девять прямо в коде.
 *
 * <p>Не текстуры — по той же причине, что и значки навыков ({@link SkillIcons}):
 * текстура требует ресурспака, а он у игроков разный.
 *
 * <p>Статус без своего значка рисуется ромбом цвета категории с первой буквой
 * имени: новый статус в контенте не должен требовать пересборки мода, чтобы его
 * было видно. Похожие по смыслу статусы делят рисунок и различаются цветом —
 * кровотечение у берсерка и у ассасина одно и то же для того, кто истекает.
 */
public final class StatusIcons {

    /** Сторона рисунка в клетках. */
    public static final int GRID = 9;

    private record Icon(int dark, int main, int light, String[] rows) {
    }

    private static final Map<String, Icon> ICONS = new HashMap<>();

    // ------------------------------------------------------------ рисунки
    // a — тень, b — основной цвет, c — блик; точка — пусто.

    private static final String[] SPIRAL = {
            ".c.....c.", "..c...c..", "...bbb...", "..b...b..", "..b.c.b..",
            "..b...b..", "...bbb...", "..c...c..", ".c.....c."};
    private static final String[] ROOTS = {
            "....b....", "....b....", "...bbb...", "..b.b.b..", ".b..b..b.",
            "b..b.b..b", "..b...b..", ".a.....a.", "aaaaaaaaa"};
    private static final String[] HUSH = {
            ".bbbbbbb.", "b.......b", "b.c...c.b", "b..c.c..b", "b...c...b",
            "b..c.c..b", "b.c...c.b", ".bbbbbbb.", "..bb....."};
    private static final String[] COCOON = {
            "...aaa...", "..abbba..", ".abcccba.", ".abc.cba.", ".abc.cba.",
            ".abc.cba.", ".abcccba.", "..abbba..", "...aaa..."};
    private static final String[] BUBBLE = {
            "...bbb...", ".bb...bb.", ".b.....b.", "b...c...b", "b..ccc..b",
            "b...c...b", ".b.....b.", ".bb...bb.", "...bbb..."};
    private static final String[] SEAL = {
            "....c....", "...cbc...", "..cb.bc..", ".cb...bc.", "cb..a..bc",
            ".cb...bc.", "..cb.bc..", "...cbc...", "....c...."};
    private static final String[] BARK = {
            ".aaaaaaa.", "abcbbbcba", "abcbbbcba", "abbcbcbba", "abbcbcbba",
            "abcbbbcba", ".abcbcba.", "..abbba..", "...aaa..."};
    private static final String[] SKULL = {
            "..bbbbb..", ".bbbbbbb.", "bb.bbb.bb", "bb.bbb.bb", "bbbbbbbbb",
            ".bbb.bbb.", "..bbbbb..", "..b.b.b..", "........."};
    private static final String[] DAGGER = {
            "........c", ".......cb", "......cb.", ".....cb..", "a...cb...",
            "aa.cb....", ".aab.....", "aaaaa....", "aa.aa...."};
    private static final String[] CROSSED = {
            "c.......c", ".b.....b.", "..b...b..", "...b.b...", "....b....",
            "...b.b...", "..a...a..", ".a.....a.", "a.......a"};
    private static final String[] DROP = {
            "....b....", "....b....", "...bbb...", "..bbbbb..", ".bbbcbbb.",
            ".bbcbbbb.", ".bbbbbbb.", "..bbbbb..", "...aaa..."};
    private static final String[] COATED = {
            "........c", ".......cb", "......cb.", ".....cb..", "....cb...",
            "a..cb....", ".aab..b..", "aaaa.bbb.", "aa.a.bbb."};
    private static final String[] SCYTHE = {
            ".ccccc...", "c.....c..", ".....bb..", "....b.b..", "...b..b..",
            "..b...b..", ".b....b..", "......b..", "......b.."};
    private static final String[] HOOD = {
            "...aaa...", "..abbba..", ".abb.bba.", ".ab...ba.", ".abb.bba.",
            ".abbbbba.", "abbbbbbba", "abbbbbbba", "aaaaaaaaa"};
    private static final String[] RETURN = {
            "...ccc...", "..c...c..", ".c.....c.", "cc.....c.", "ccc....c.",
            ".......c.", "..b...c..", "...bcc...", "........."};
    private static final String[] AXE = {
            "..bbb....", ".bbbba...", "bbbbbaa..", ".bbba.a..", "..b...a..",
            "......a..", "......a..", "......a..", "......a.."};
    private static final String[] MASK = {
            ".........", ".bb...bb.", "bbbbbbbbb", "bb.bbb.bb", "bbbbbbbbb",
            ".bbbcbbb.", "..bbbbb..", "...b.b...", "........."};
    private static final String[] GRAVE = {
            "...bbb...", "..bbbbb..", ".bbbcbbb.", ".bbcccbb.", ".bbbcbbb.",
            ".bbbbbbb.", ".bbbbbbb.", "aaaaaaaaa", "........."};
    private static final String[] SIGHT = {
            "....c....", "..bbcbb..", ".b..c..b.", "b.......b", "cccc.cccc",
            "b.......b", ".b..c..b.", "..bbcbb..", "....c...."};
    private static final String[] CHEVRONS = {
            ".........", "b...b....", ".b...b...", "..b...b..", "...c...c.",
            "..b...b..", ".b...b...", "b...b....", "........."};
    private static final String[] EYE = {
            ".........", "...bbb...", ".bb...bb.", "b..ccc..b", "b..cac..b",
            "b..ccc..b", ".bb...bb.", "...bbb...", "........."};
    private static final String[] ARROW = {
            "........c", ".......c.", "b.....c..", ".b...c...", "..b.c....",
            "...c.....", "..c.b....", ".c...b...", "c.....b.."};
    private static final String[] PULSE = {
            ".........", "...b.....", "...bb....", "..b.b....", "bbb..b.bb",
            ".....b.b.", "......b..", "......b..", "........."};
    private static final String[] FLAME = {
            "....c....", "...cc....", "...cbc...", "..cbbc.c.", ".cbbbbcc.",
            ".cbbabbc.", "cbbaaabbc", ".cbaaabc.", "..ccccc.."};
    private static final String[] HOURGLASS = {
            "aaaaaaaaa", ".b.....b.", "..bcccb..", "...bcb...", "....b....",
            "...b.b...", "..b.c.b..", ".bcccccb.", "aaaaaaaaa"};
    private static final String[] PEAK = {
            "....c....", "...cbc...", "...b.b...", "..b...b..", "..b...b..",
            ".b.....b.", ".b..a..b.", "b..aaa..b", "bbbbbbbbb"};
    private static final String[] FANGS = {
            ".........", "bbbbbbbbb", "bcb...bcb", ".b.....b.", ".b.....b.",
            ".........", ".a.....a.", "aca...aca", "aaaaaaaaa"};
    private static final String[] FIST = {
            ".bbbbbb..", "bcbcbcbb.", "bbbbbbbb.", "bbbbbbbbb", "bbbbbbbbb",
            ".bbbbbbb.", "..bbbbb..", "..bbbbb..", "........."};
    private static final String[] CRACK = {
            "....b....", "....bb...", "...bb....", "..bb.....", "...bb....",
            "....bb...", "...bb....", "..bb.....", "..b......"};
    private static final String[] LOTUS = {
            ".........", "....c....", "...ccc...", ".c.ccc.c.", "ccbcccbcc",
            ".ccbbbcc.", "..ccccc..", ".........", "........."};
    private static final String[] TARGET = {
            "...bbb...", ".bb...bb.", ".b.ccc.b.", "b.c...c.b", "b.c.a.c.b",
            "b.c...c.b", ".b.ccc.b.", ".bb...bb.", "...bbb..."};
    private static final String[] TWIN = {
            ".a...b...", "aaa.bbb..", ".a...b...", "aaa.bbb..", "a.a.b.b..",
            "aaa.bbb..", ".a.a.b.b.", ".a.a.b.b.", "........."};
    private static final String[] BURST = {
            "b...b...b", ".b..b..b.", "..b.b.b..", "...ccc...", "bbbcacbbb",
            "...ccc...", "..b.b.b..", ".b..b..b.", "b...b...b"};
    private static final String[] BANG = {
            "...bbb...", "...bbb...", "...bbb...", "...bbb...", "....b....",
            "....b....", ".........", "...bbb...", "...bbb..."};
    private static final String[] SHIELD = {
            "aaaaaaaaa", "abbbcbbba", "abbbcbbba", "accccccca", "abbbcbbba",
            ".abbcbba.", ".abbcbba.", "..abcba..", "...aaa..."};
    private static final String[] CRACKED_SHIELD = {
            "aaaaaaaaa", "abbbcbbba", "abbc.bbba", "abbbc.bba", "abbc.bbba",
            ".abbcbba.", ".abbbcba.", "..abbba..", "...aaa..."};
    private static final String[] WALL = {
            ".........", "bbbabbbab", "aaaaaaaaa", "babbbabbb", "aaaaaaaaa",
            "bbbabbbab", "aaaaaaaaa", "babbbabbb", "........."};
    private static final String[] SPIKES = {
            "c.......c", ".aaaaaaa.", "cabbbbbac", ".abcccba.", "cabbcbbac",
            ".abbcbba.", "..abbba..", "...aaa...", "........."};
    private static final String[] TOWER = {
            "b.b.b.b.b", "bbbbbbbbb", ".bbbbbbb.", ".bb.c.bb.", ".bbbbbbb.",
            ".bbbbbbb.", ".bb...bb.", ".bb...bb.", "aaaaaaaaa"};
    private static final String[] CLAWS = {
            "b...b...b", ".b...b...", ".b...b..b", "..b...b.b", "..b...b..",
            "...b...b.", "...b...b.", "....c...c", "........."};
    private static final String[] PLANTED = {
            "....c....", "....b....", "....b....", "....b....", "....b....",
            "..aaaaa..", "....a....", "....a....", "...aaa..."};
    private static final String[] SNAIL = {
            ".........", "...bbbb..", "..b....b.", ".b..cc..b", ".b.c..b.b",
            ".b..bb..b", "aab....b.", "aaabbbb..", "........."};
    private static final String[] BROKEN = {
            "........b", ".......b.", "......b..", ".....b...", ".........",
            "...b.....", "..b......", "aaa......", ".a......."};
    private static final String[] BANNER = {
            "a........", "abbbbbb..", "abccccb..", "abbbbbbb.", "abccccb..",
            "abbbbbb..", "a........", "a........", "a........"};
    private static final String[] HALO = {
            "..ccccc..", ".c.....c.", "..ccbcc..", "....b....", "..bbbbb..",
            "....b....", "....b....", "....a....", "....a...."};
    private static final String[] DUST = {
            ".........", "...bb....", ".bbbbbb..", "bbcbbbbb.", "bbbbbcbbb",
            ".bbbbbbb.", "..c...c..", "c...c...c", "........."};
    private static final String[] PURSE = {
            "...a.a...", "....a....", "...bbb...", "..bbbbb..", ".bbbcbbb.",
            ".bbcccbb.", ".bbbcbbb.", "..bbbbb..", "........."};

    static {
        // контроль и общие дебафы
        put("stun", 0xFF5A1A14, 0xFFC94A3D, 0xFFF0D070, SPIRAL);
        put("root", 0xFF1E3A1E, 0xFF6B8A3A, 0xFFA8D98A, ROOTS);
        put("silence", 0xFF2A2A3A, 0xFF8A8AA0, 0xFFC94A3D, HUSH);
        put("banish", 0xFF2A1A3A, 0xFF6B3A9E, 0xFFC79BFF, COCOON);
        put("shield", 0xFF1A2A5A, 0xFF4A7FC1, 0xFFBFD9FF, BUBBLE);
        put("slowed", 0xFF1A3A4A, 0xFF4A9EB0, 0xFFBFE8F0, SNAIL);
        put("exposed", 0xFF5A4A1A, 0xFFB08A2E, 0xFFF0E0A0, CRACKED_SHIELD);
        put("weakened", 0xFF3A1A3A, 0xFF8A4A9E, 0xFFD8B0F0, BROKEN);
        put("aiming", 0xFF3A3A3A, 0xFF8A8A7A, 0xFFE0E0D0, SIGHT);
        put("challenge", 0xFF5A1A14, 0xFFC94A3D, 0xFFF0D070, BANG);
        put("combat_mark", 0xFF3A3A3A, 0xFF9A9280, 0xFFE0D8C0, CROSSED);
        // яды, кровь, проклятия
        put("bleed", 0xFF4A0E0E, 0xFFA82020, 0xFFE07068, DROP);
        put("berserk_bleed", 0xFF4A0E0E, 0xFF8B1A1A, 0xFFC94A3D, DROP);
        put("venom", 0xFF1E3A1E, 0xFF5B8A2E, 0xFFC8E87A, DROP);
        put("rot", 0xFF3A2A14, 0xFF6B5A2A, 0xFFA8C060, DROP);
        put("curse", 0xFF2A1A2A, 0xFF7F3A6B, 0xFFD8A0C8, SKULL);
        put("reaper_brand", 0xFF2A1A2A, 0xFF5A3A5A, 0xFFC0B0C8, SCYTHE);
        put("quarry", 0xFF5A2A14, 0xFFC97A2A, 0xFFF0C050, SIGHT);
        put("karma_mark", 0xFF5A4A1A, 0xFFC9A227, 0xFFF0E0A0, TARGET);
        // маг, друид, колдун
        put("empowered", 0xFF2E3A6B, 0xFF5B8AD6, 0xFFBFE0FF, SEAL);
        put("bark_guard", 0xFF3A2A14, 0xFF7A5A2A, 0xFFC9A060, BARK);
        // плут, ассасин, трикстер
        put("ambush", 0xFF2A2A3A, 0xFF5B5B6B, 0xFFD0D0E0, DAGGER);
        put("venom_coat", 0xFF1E3A1E, 0xFF5B8A2E, 0xFFD0E0D0, COATED);
        put("veil", 0xFF1A1A2A, 0xFF4A4A6B, 0xFF9A9AC0, HOOD);
        put("riposte_window", 0xFF2A2A3A, 0xFF8A8AA0, 0xFFE0E0F0, RETURN);
        put("executioner", 0xFF3A1A1A, 0xFF8A2A2A, 0xFFE0C0C0, AXE);
        put("carnival", 0xFF3A1A4A, 0xFF7A4FD1, 0xFFF0C050, MASK);
        put("feigned", 0xFF2A2A2A, 0xFF7A7A72, 0xFFC8C8C0, GRAVE);
        put("dust_cover", 0xFF4A4030, 0xFF9A8A6A, 0xFFE0D8C0, DUST);
        put("stolen_power", 0xFF4A3A14, 0xFFB08A2E, 0xFFF0D070, PURSE);
        // охотник
        put("rush_full", 0xFF5A2A14, 0xFFC97A2A, 0xFFF0C050, CHEVRONS);
        put("sense", 0xFF3A2A14, 0xFF8A6A2E, 0xFFF0D070, EYE);
        put("deadly_hunt", 0xFF5A1A14, 0xFFC94A2A, 0xFFF0C050, ARROW);
        put("eagle_eye", 0xFF5A4A1A, 0xFFC9A227, 0xFF7FC1E0, EYE);
        put("thrill", 0xFF1E3A1E, 0xFF6FA84F, 0xFFC8F0A0, PULSE);
        // берсерк
        put("overheat", 0xFF5A1A0E, 0xFFE07A2A, 0xFFF0D070, FLAME);
        put("ignore_pain", 0xFF3A2A1A, 0xFF8A6A4A, 0xFFC94A3D, HOURGLASS);
        put("unshakable", 0xFF3A3A3A, 0xFF9A9A8A, 0xFFF0E0A0, PEAK);
        put("hunger", 0xFF4A0E0E, 0xFFE0D8C0, 0xFFC94A3D, FANGS);
        put("undying", 0xFF5A1A0E, 0xFFC94A2A, 0xFFF0D070, SKULL);
        // монах
        put("stance_press", 0xFF5A3A14, 0xFFC9A227, 0xFFF0E0A0, FIST);
        put("stance_breach", 0xFF3A1A14, 0xFFC94A3D, 0xFFF0C0A0, CRACK);
        put("stance_calm", 0xFF1A3A4A, 0xFF4A9EB0, 0xFFBFE8F0, LOTUS);
        put("phantom", 0xFF2A2A3A, 0xFF7A7AA0, 0xFFC0C0E0, TWIN);
        put("asura", 0xFF5A1A0E, 0xFFE07A2A, 0xFFF0D070, BURST);
        put("meditation", 0xFF1A3A4A, 0xFF6FC1C1, 0xFFE0F8F8, LOTUS);
        // рыцарь и воин
        put("guarded", 0xFF2A3A5A, 0xFF4A6B9E, 0xFFBFD0E8, SHIELD);
        put("shield_wall", 0xFF3A3A3A, 0xFF8A8A7A, 0xFFD8D8C8, WALL);
        put("retribution", 0xFF3A2A14, 0xFFC9A227, 0xFFF0E0A0, SPIKES);
        put("fortress", 0xFF3A3A3A, 0xFFB0B0A0, 0xFFF0E0A0, TOWER);
        put("shield_up", 0xFF3A3A3A, 0xFF8A8A7A, 0xFFE0E0D0, SHIELD);
        put("rage", 0xFF4A0E0E, 0xFFC94A3D, 0xFFF0C050, CLAWS);
        put("warrior_stance", 0xFF3A3A3A, 0xFFA39B88, 0xFFE8DCC0, PLANTED);
        put("banner_guard", 0xFF5A4A1A, 0xFFC9A227, 0xFFF0E0A0, BANNER);
        put("oath", 0xFF5A4A1A, 0xFFE8DCC0, 0xFFF0D070, HALO);
    }

    private StatusIcons() {
    }

    private static void put(String id, int dark, int main, int light, String[] rows) {
        ICONS.put(id, new Icon(dark, main, light, rows));
    }

    /**
     * Рисует значок статуса в квадрате со стороной {@code size}.
     *
     * @param fallback цвет запасного ромба — цвет категории
     * @param display  имя статуса: его первая буква идёт на запасной ромб
     */
    public static void draw(GuiGraphics graphics, String statusId, String display, int x, int y,
                            int size, int fallback) {
        // Рисованная картинка, если есть; иначе — пиксель-арт ниже.
        var texture = IconTextures.find("statuses", statusId);
        if (texture != null) {
            IconTextures.draw(graphics, texture, x, y, size, IconTextures.STATUS);
            return;
        }
        Icon icon = ICONS.get(statusId);
        // Рисунок вписан в ромб: квадрат девять на девять помещается в нём целиком
        // только примерно в половину стороны.
        int pixel = Math.max(1, size / (GRID * 2));
        int glyphX = x + (size - pixel * GRID) / 2;
        int glyphY = y + (size - pixel * GRID) / 2;
        if (icon == null) {
            RpgStyle.pip(graphics, glyphX, glyphY, pixel * GRID, true, fallback);
            String letter = display.isEmpty() ? "?" : display.substring(0, 1).toUpperCase();
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, Component.literal(letter),
                    x + size / 2 - font.width(letter) / 2 + 1, y + (size - 8) / 2 + 1,
                    0xFF0B0B0D, false);
            return;
        }
        for (int row = 0; row < GRID; row++) {
            String line = icon.rows()[row];
            int column = 0;
            while (column < GRID) {
                char c = line.charAt(column);
                int end = column;
                while (end < GRID && line.charAt(end) == c) {
                    end++;
                }
                int colour = switch (c) {
                    case 'a' -> icon.dark();
                    case 'b' -> icon.main();
                    case 'c' -> icon.light();
                    default -> 0;
                };
                if (colour != 0) {
                    graphics.fill(glyphX + column * pixel, glyphY + row * pixel,
                            glyphX + end * pixel, glyphY + (row + 1) * pixel, colour);
                }
                column = end;
            }
        }
    }

    /** Есть ли у статуса свой рисунок. Для проверки, что художник никого не забыл. */
    public static boolean has(String statusId) {
        return ICONS.containsKey(statusId);
    }
}
