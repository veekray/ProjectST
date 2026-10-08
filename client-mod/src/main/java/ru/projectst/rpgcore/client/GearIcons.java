package ru.projectst.rpgcore.client;

import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Силуэты пустых ячеек снаряжения: пиксель-арт прямо в коде.
 *
 * <p>Пустая ячейка обязана говорить, что в неё класть: тринадцать одинаковых
 * квадратов читаются как сундук, и кольцо приходится примерять во всё подряд.
 * Силуэт тусклый и гравированный — светлый контур по тёмной заливке, — чтобы
 * не спорить с настоящими вещами в соседних ячейках.
 *
 * <p>Не текстуры — по той же причине, что и значки навыков ({@link SkillIcons}):
 * текстура требует ресурспака, а он у игроков разный.
 */
public final class GearIcons {

    /** Сторона силуэта в пикселях. */
    public static final int SIZE = 12;

    /** Контур: светлее заливки — так силуэт читается вдавленным. */
    private static final int EDGE = 0xFF5A5341;
    /** Заливка: чуть светлее фона ячейки. */
    private static final int FILL = 0xFF2C2820;
    /** Блик: камень в кольце, щель забрала, пряжка. */
    private static final int GLINT = 0xFF7A7058;

    private static final Map<String, String[]> ICONS = Map.ofEntries(
            Map.entry("helmet", new String[] {
                    "....aaaa....",
                    "..aabbbbaa..",
                    ".abbbbbbbba.",
                    ".abbcbbbbba.",
                    "abbcbbbbbbba",
                    "ab........ba",
                    "abbbb..bbbba",
                    "abbbb..bbbba",
                    "abbbb..bbbba",
                    ".abbb..bbba.",
                    "..aab..baa..",
                    "............"}),
            Map.entry("chest", new String[] {
                    "..aa....aa..",
                    ".abba..abba.",
                    "abbbbaabbbba",
                    "abbbbbbbbbba",
                    "aabbbbbbbbaa",
                    ".abbbccbbba.",
                    ".abbbbbbbba.",
                    ".abbbccbbba.",
                    ".abbbbbbbba.",
                    ".abbbbbbbba.",
                    ".aabbbbbbaa.",
                    "...aaaaaa..."}),
            Map.entry("legs", new String[] {
                    ".aaaaaaaaaa.",
                    ".abbbbbbbba.",
                    ".abbccccbba.",
                    ".abbbbbbbba.",
                    ".abbbaabbba.",
                    ".abba..abba.",
                    ".abba..abba.",
                    ".abba..abba.",
                    ".abba..abba.",
                    ".abba..abba.",
                    ".aaaa..aaaa.",
                    "............"}),
            Map.entry("boots", new String[] {
                    "...aaaaa....",
                    "...abbba....",
                    "...abbba....",
                    "...abcba....",
                    "...abbba....",
                    "...abbba....",
                    "...abbbaaa..",
                    "..abbbbbbaa.",
                    ".abbbbbbbbba",
                    ".abbbbbbbbba",
                    ".aaaaaaaaaaa",
                    "............"}),
            Map.entry("offhand", new String[] {
                    ".aaaaaaaaaa.",
                    ".abbbbbbbba.",
                    ".abbbccbbba.",
                    ".abbbccbbba.",
                    ".abccccccba.",
                    ".abccccccba.",
                    ".abbbccbbba.",
                    ".abbbccbbba.",
                    "..abbccbba..",
                    "...abbbba...",
                    "....abba....",
                    ".....aa....."}),
            Map.entry("ring", new String[] {
                    ".....cc.....",
                    "....cbbc....",
                    ".....cc.....",
                    "...aabbaa...",
                    "..ab....ba..",
                    ".ab......ba.",
                    ".ab......ba.",
                    ".ab......ba.",
                    ".ab......ba.",
                    "..ab....ba..",
                    "...aabbaa...",
                    "............"}),
            Map.entry("amulet", new String[] {
                    ".a........a.",
                    ".a........a.",
                    "..a......a..",
                    "..a......a..",
                    "...a....a...",
                    "....aaaa....",
                    "....abba....",
                    "...abccba...",
                    "..abcbbcba..",
                    "...abccba...",
                    "....abba....",
                    ".....aa....."}),
            Map.entry("bracelet", new String[] {
                    "............",
                    "...aaaaaa...",
                    ".aab....baa.",
                    "ab........ba",
                    "ab........ba",
                    "abbaaaaaabba",
                    "abbbbbbbbbba",
                    ".abcbcbcbba.",
                    "..aabbbbaa..",
                    "....aaaa....",
                    "............",
                    "............"}),
            Map.entry("gloves", new String[] {
                    "...a.a.a....",
                    "..abababa...",
                    "..abababa...",
                    "..abababa.a.",
                    "..abbbbbbaba",
                    "..abbbbbbbba",
                    "..abbbbbbba.",
                    "..abbbbbba..",
                    "...abbbbba..",
                    "...accccca..",
                    "...accccca..",
                    "...aaaaaaa.."}),
            Map.entry("artifact", new String[] {
                    ".....aa.....",
                    "....abba....",
                    "...abccba...",
                    "..abcbbcba..",
                    ".abcbbbbcba.",
                    "abcbbbbbbcba",
                    ".abbbbbbbba.",
                    "..abbbbbba..",
                    "...abbbba...",
                    "....abba....",
                    ".....aa.....",
                    "............"}));

    private GearIcons() {
    }

    /**
     * Силуэт ячейки в точке (x, y).
     *
     * <p>Ячейка узнаётся по имени из протокола, а два кольца и три артефакта —
     * по началу имени: рисунок у них один, различает их место в окне.
     */
    public static void draw(GuiGraphics graphics, String cell, int x, int y) {
        String[] rows = ICONS.get(shape(cell));
        if (rows == null) {
            return;
        }
        paint(graphics, rows, x, y, EDGE, FILL, GLINT);
    }

    /** Тот же силуэт одним цветом: для значка закладки. */
    public static void drawMono(GuiGraphics graphics, String cell, int x, int y, int colour) {
        String[] rows = ICONS.get(shape(cell));
        if (rows == null) {
            return;
        }
        paint(graphics, rows, x, y, colour, colour, colour);
    }

    private static String shape(String cell) {
        if (cell.startsWith("ring")) {
            return "ring";
        }
        if (cell.startsWith("artifact")) {
            return "artifact";
        }
        return cell;
    }

    /**
     * Рисует сетку символов.
     *
     * <p>Подряд идущие пиксели одного цвета в строке сливаются в одну заливку:
     * силуэт — это десяток вызовов, а не полторы сотни.
     */
    private static void paint(GuiGraphics graphics, String[] rows, int x, int y,
                              int edge, int fill, int glint) {
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            int col = 0;
            while (col < line.length()) {
                char c = line.charAt(col);
                int end = col;
                while (end < line.length() && line.charAt(end) == c) {
                    end++;
                }
                int colour = switch (c) {
                    case 'a' -> edge;
                    case 'b' -> fill;
                    case 'c' -> glint;
                    default -> 0;
                };
                if (colour != 0) {
                    graphics.fill(x + col, y + row, x + end, y + row + 1, colour);
                }
                col = end;
            }
        }
    }
}
