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

    /**
     * Запасной цвет по началу идентификатора: маг синий, друид зелёный и так
     * далее. Через ofEntries, а не of: у короткой формы потолок в десять пар, и
     * одиннадцатый класс её молча не переполняет — он её не компилирует.
     */
    private static final Map<String, Integer> CLASS_COLOURS = Map.ofEntries(
            Map.entry("mage", 0xFF5B8AD6),
            Map.entry("druid", 0xFF4F8A3A),
            Map.entry("warlock", 0xFF7F3A6B),
            Map.entry("rogue", 0xFF5B5B6B),
            Map.entry("assassin", 0xFF4A3A5B),
            Map.entry("trickster", 0xFF7A4FD1),
            Map.entry("hunter", 0xFF8A6A2E),
            Map.entry("berserker", 0xFF8A2A1E),
            Map.entry("striker", 0xFFC9A227),
            Map.entry("knight", 0xFFB0B0C0),
            Map.entry("warrior", 0xFFC9544A),
            Map.entry("item", 0xFFC9A227));

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

        // ------------------------------------------------------------ ассасин
        // клинок из ниоткуда: кинжал, выходящий из разрыва
        ICONS.put("assassin_nowhere_blade", new Icon(0xFF2A1A3A, 0xFF6B5A8A, 0xFFE8E0F0,
                new String[] {
                        "a.....cc.",
                        ".a...cc..",
                        "..a.cc...",
                        "...bcc...",
                        "..bbc....",
                        ".bba.....",
                        "bb..a....",
                        "b....a...",
                        "......a.."}));
        // растворение: силуэт, рассыпающийся в точки
        ICONS.put("assassin_dissolve", new Icon(0xFF1A1A24, 0xFF4A4A5B, 0xFFA09AB0,
                new String[] {
                        "...bbb...",
                        "...bbb...",
                        "..bbbbb..",
                        "..bbbbb..",
                        ".b.bbb.c.",
                        "...b.b..c",
                        "..c.b.c..",
                        "..b...c..",
                        ".c.....c."}));
        // клеймо жнеца: печать с косой чертой
        ICONS.put("assassin_reaper_brand", new Icon(0xFF2A0A1E, 0xFF8A2A5B, 0xFFE08AC0,
                new String[] {
                        "..bbbbb..",
                        ".b.....b.",
                        "b....cc.b",
                        "b...cc..b",
                        "b..cc...b",
                        "b.cc....b",
                        "b.......b",
                        ".b.....b.",
                        "..bbbbb.."}));
        // вскрытие артерии: порез и капли
        ICONS.put("assassin_arterial_cut", new Icon(0xFF4A0A0A, 0xFFB02A24, 0xFFF08078,
                new String[] {
                        ".......cc",
                        "......cc.",
                        ".....cc..",
                        "....cc...",
                        "...cc....",
                        "..cc.....",
                        ".cc..b.b.",
                        "cc..bbbb.",
                        "....b.b.."}));
        // удавка: петля с хвостом
        ICONS.put("assassin_garrote", new Icon(0xFF2A2418, 0xFF8A7A5B, 0xFFE0D4B0,
                new String[] {
                        "..bbbbb..",
                        ".b.....b.",
                        "b.......b",
                        "b.......b",
                        ".b.....b.",
                        "..bb.bb..",
                        "...c.c...",
                        "...c.c...",
                        "...ccc..."}));
        // трупный яд: флакон с черепом
        ICONS.put("assassin_corpse_venom", new Icon(0xFF1A2A0E, 0xFF4E7A1E, 0xFFBCE88A,
                new String[] {
                        "...aaa...",
                        "....a....",
                        "..bbbbb..",
                        ".bbbbbbb.",
                        ".bcb.bcb.",
                        ".bbbbbbb.",
                        ".bb.c.bb.",
                        ".bbbbbbb.",
                        "..bbbbb.."}));
        // сход в тень: силуэт в клубах
        ICONS.put("assassin_shadowmeld", new Icon(0xFF140F1C, 0xFF3A3448, 0xFF8A82A0,
                new String[] {
                        ".c.....c.",
                        "c..bbb..c",
                        ".c.bbb.c.",
                        "..bbbbb..",
                        "c.bbbbb.c",
                        ".cbbbbbc.",
                        "c..bbb..c",
                        ".c.....c.",
                        "c.......c"}));
        // каскад теней: три прыжка зигзагом
        ICONS.put("assassin_shadow_cascade", new Icon(0xFF1A1428, 0xFF5B4A8A, 0xFFC0B0F0,
                new String[] {
                        "cc.......",
                        ".cc......",
                        "..cb.....",
                        "...bcc...",
                        "....bcc..",
                        ".....cb..",
                        "......bcc",
                        ".......cc",
                        "........c"}));
        // ответный клинок: скрещённые лезвия
        ICONS.put("assassin_riposte", new Icon(0xFF2A2A2A, 0xFF8A8A9A, 0xFFF0F0F8,
                new String[] {
                        "c.......c",
                        ".c.....c.",
                        "..c...c..",
                        "...c.c...",
                        "....b....",
                        "...c.c...",
                        "..c...c..",
                        ".a.....a.",
                        "a.......a"}));
        // час палача: череп в кольце
        ICONS.put("assassin_executioners_hour", new Icon(0xFF2A0A0A, 0xFFB02A24, 0xFFF0E8D8,
                new String[] {
                        "..bbbbb..",
                        ".b.....b.",
                        "b.ccccc.b",
                        "b.c.c.c.b",
                        "b.ccccc.b",
                        "b..c.c..b",
                        "b.ccccc.b",
                        ".b.....b.",
                        "..bbbbb.."}));

        // ------------------------------------------------------------ трикстер
        // раскол: три силуэта
        ICONS.put("trickster_split", new Icon(0xFF2A1A4A, 0xFF7A4FD1, 0xFFD8C0FF,
                new String[] {
                        ".b..c..b.",
                        ".b..c..b.",
                        "bbb.c.bbb",
                        "bbbcccbbb",
                        "bbbcccbbb",
                        ".b.ccc.b.",
                        ".b.ccc.b.",
                        ".b..c..b.",
                        "....c...."}));
        // подмена: две стрелки навстречу
        ICONS.put("trickster_sleight", new Icon(0xFF2A1A4A, 0xFF7A4FD1, 0xFFD8C0FF,
                new String[] {
                        "..c...b..",
                        ".ccc.bbb.",
                        "ccccc.b..",
                        "..c...b..",
                        ".........",
                        "..c...b..",
                        "..c.bbbbb",
                        ".ccc.bbb.",
                        "..c...b.."}));
        // ножевой шквал: ножи во все стороны
        ICONS.put("trickster_knife_storm", new Icon(0xFF2A2A3A, 0xFF9A9AB0, 0xFFF0F0F8,
                new String[] {
                        "c...c...c",
                        ".c..b..c.",
                        "..c.b.c..",
                        "...cbc...",
                        "cbbbcbbbc",
                        "...cbc...",
                        "..c.b.c..",
                        ".c..b..c.",
                        "c...c...c"}));
        // пыль в глаза: облако и зажмуренный глаз
        ICONS.put("trickster_blinding_dust", new Icon(0xFF3A3028, 0xFF8A7A62, 0xFFE0D0B0,
                new String[] {
                        "..bb.bb..",
                        ".bbbbbbb.",
                        "bbbbbbbbb",
                        "bbcccccbb",
                        "bc.ccc.cb",
                        "bbcccccbb",
                        "bbbbbbbbb",
                        ".bbbbbbb.",
                        "..bb.bb.."}));
        // карманник: кошель с рукой
        ICONS.put("trickster_pickpocket", new Icon(0xFF3A2A14, 0xFF8A6A2E, 0xFFE8C86A,
                new String[] {
                        "..c...c..",
                        "..c.c.c..",
                        "..ccccc..",
                        "...bbb...",
                        "..bbbbb..",
                        ".bbbbbbb.",
                        ".bbcccbb.",
                        ".bbbbbbb.",
                        "..bbbbb.."}));
        // нестабильные копии: силуэт во вспышке
        ICONS.put("trickster_unstable_doubles", new Icon(0xFF4A1A0A, 0xFFC94A2A, 0xFFF0D070,
                new String[] {
                        "b...c...b",
                        ".b.ccc.b.",
                        "..ccccc..",
                        ".ccbbbcc.",
                        "cccbbbccc",
                        ".ccbbbcc.",
                        "..ccccc..",
                        ".b.ccc.b.",
                        "b...c...b"}));
        // ложная смерть: череп с подмигиванием
        ICONS.put("trickster_feign_death", new Icon(0xFF2A2A2A, 0xFF8A8A8A, 0xFFF0F0E8,
                new String[] {
                        "..ccccc..",
                        ".ccccccc.",
                        "cc.ccc.cc",
                        "ca.ccc.ac",
                        "ccccccccc",
                        "cc.c.c.cc",
                        ".ccccccc.",
                        "..c.c.c..",
                        "...ccc..."}));
        // перетасовка: фигуры, разлетающиеся по углам
        ICONS.put("trickster_shuffle", new Icon(0xFF2A1A4A, 0xFF7A4FD1, 0xFFD8C0FF,
                new String[] {
                        "bb.....bb",
                        "bb..c..bb",
                        "...ccc...",
                        "..ccccc..",
                        ".ccc.ccc.",
                        "..ccccc..",
                        "...ccc...",
                        "bb..c..bb",
                        "bb.....bb"}));
        // кукловод: крестовина с нитями
        ICONS.put("trickster_puppeteer", new Icon(0xFF2A1A4A, 0xFF7A4FD1, 0xFFD8C0FF,
                new String[] {
                        "ccccccccc",
                        "..c...c..",
                        "..c...c..",
                        "..c...c..",
                        ".bbb.bbb.",
                        "bbbbbbbbb",
                        ".bbb.bbb.",
                        "..b...b..",
                        ".bb...bb."}));
        // карнавал: маска
        ICONS.put("trickster_carnival", new Icon(0xFF3A1A4A, 0xFFB04FD1, 0xFFF0D070,
                new String[] {
                        "c.......c",
                        ".cbbbbbc.",
                        "cbbbbbbbc",
                        "bb.bbb.bb",
                        "b.c.b.c.b",
                        "bb.bbb.bb",
                        ".bbbbbbb.",
                        "..bbbbb..",
                        "...ccc..."}));

        // ------------------------------------------------------------ охотник
        // клеймо добычи: прицел
        ICONS.put("hunter_quarry_mark", new Icon(0xFF4A2A0A, 0xFFC98A2A, 0xFFF0E0A0,
                new String[] {
                        "....c....",
                        "..bbbbb..",
                        ".b..c..b.",
                        "b...c...b",
                        "cccc.cccc",
                        "b...c...b",
                        ".b..c..b.",
                        "..bbbbb..",
                        "....c...."}));
        // отбойный выстрел: стрела и волна позади
        ICONS.put("hunter_repel_shot", new Icon(0xFF3A3028, 0xFF8A7A5B, 0xFFF0E8D0,
                new String[] {
                        ".........",
                        "b........",
                        ".b..c....",
                        "..b..c...",
                        "bbbcccccc",
                        "..b..c...",
                        ".b..c....",
                        "b........",
                        "........."}));
        // отход егеря: капкан с зубьями
        ICONS.put("hunter_disengage", new Icon(0xFF2A2A2A, 0xFF8A8A9A, 0xFFE0E0E8,
                new String[] {
                        ".........",
                        "c.c.c.c.c",
                        ".c.c.c.c.",
                        "..bbbbb..",
                        ".bbbbbbb.",
                        "..bbbbb..",
                        ".c.c.c.c.",
                        "c.c.c.c.c",
                        "........."}));
        // сигнальный выстрел: вспышка
        ICONS.put("hunter_flare_shot", new Icon(0xFF4A3A0A, 0xFFC9A227, 0xFFFFF0B0,
                new String[] {
                        "c...c...c",
                        ".c..c..c.",
                        "..c.c.c..",
                        "...ccc...",
                        "ccccbcccc",
                        "...ccc...",
                        "..c.c.c..",
                        ".c..c..c.",
                        "c...c...c"}));
        // ливень стрел: стрелы сверху
        ICONS.put("hunter_arrow_downpour", new Icon(0xFF2A3A4A, 0xFF6A8AA8, 0xFFD0E8F0,
                new String[] {
                        "c..c..c..",
                        "c..c..c.c",
                        "b..b..b.b",
                        "b..b..b.b",
                        ".b..b..b.",
                        ".b..b..b.",
                        "..b..b..b",
                        "..c..c..c",
                        "ccccccccc"}));
        // гнилая стрела: наконечник с каплей
        ICONS.put("hunter_rot_arrow", new Icon(0xFF1A2A0A, 0xFF5E7A2A, 0xFFBCD88A,
                new String[] {
                        ".....ccc.",
                        "....cc.c.",
                        "...cc.cc.",
                        "..cc.....",
                        ".cc......",
                        "cc...b...",
                        "....bbb..",
                        "....bbb..",
                        ".....b..."}));
        // чутьё: глаз с лучами
        ICONS.put("hunter_sense", new Icon(0xFF0A2A3A, 0xFF2E7F9F, 0xFFBCE8F0,
                new String[] {
                        "c...c...c",
                        ".c.....c.",
                        "..bbbbb..",
                        ".bbcccbb.",
                        "cbcc.ccbc",
                        ".bbcccbb.",
                        "..bbbbb..",
                        ".c.....c.",
                        "c...c...c"}));
        // выстрел на поражение: арбалет на прицеле
        ICONS.put("hunter_killshot", new Icon(0xFF3A1A0A, 0xFFB04A2A, 0xFFF0D070,
                new String[] {
                        "b.......b",
                        ".b.....b.",
                        "..ccccc..",
                        "..c...c..",
                        "ccc.b.ccc",
                        "..c...c..",
                        "..ccccc..",
                        ".b.....b.",
                        "b.......b"}));
        // смертельная охота: рога
        ICONS.put("hunter_deadly_hunt", new Icon(0xFF3A2A0A, 0xFFC9A227, 0xFFF0E0A0,
                new String[] {
                        "c.......c",
                        "cc.....cc",
                        "c.c...c.c",
                        "c..c.c..c",
                        ".c..b..c.",
                        "..bbbbb..",
                        "..b.b.b..",
                        "..bbbbb..",
                        "...bbb..."}));

        // ------------------------------------------------------------ плут: добор
        // пронзающий удар: клинок сквозь строй
        ICONS.put("rogue_piercing_blow", new Icon(0xFF4A1A14, 0xFFB03A2A, 0xFFF0C0A0,
                new String[] {
                        ".........",
                        "b.......b",
                        ".b.....b.",
                        "..cccccc.",
                        "ccccccc..",
                        "..cccccc.",
                        ".b.....b.",
                        "b.......b",
                        "........."}));
        // дымовая завеса: граната в облаке
        ICONS.put("rogue_smoke_screen", new Icon(0xFF2A2A2A, 0xFF6A6A6A, 0xFFC0C0C0,
                new String[] {
                        "..c...c..",
                        ".ccc.ccc.",
                        "ccccccccc",
                        ".cc...cc.",
                        "...bbb...",
                        "..bbbbb..",
                        "..bbbbb..",
                        "...bbb...",
                        "..c...c.."}));
        // орлиное зрение: глаз в кольце
        ICONS.put("rogue_eagle_eye", new Icon(0xFF0A2A3A, 0xFF2E7F9F, 0xFFD8F0F8,
                new String[] {
                        "...bbb...",
                        ".bb...bb.",
                        "b.......b",
                        "b..ccc..b",
                        "b.cc.cc.b",
                        "b..ccc..b",
                        "b.......b",
                        ".bb...bb.",
                        "...bbb..."}));
        // кураж: крыло и искры
        ICONS.put("rogue_thrill", new Icon(0xFF4A3A0A, 0xFFC9A227, 0xFFF0E8B0,
                new String[] {
                        "......c..",
                        ".....cc..",
                        "....ccc..",
                        "...cccc..",
                        "..ccccc..",
                        ".bbbbbb..",
                        "..bbbb...",
                        "...bb..c.",
                        "..c....c."}));

        // ------------------------------------------------------------ берсерк
        ICONS.put("berserker_blood_slash", new Icon(0xFF4A0A0A, 0xFFB02A24, 0xFFF08078,
                new String[] {
                        "......ccc",
                        ".....cc..",
                        "....cc...",
                        "...cc....",
                        "..cc.....",
                        ".cc..b.b.",
                        "cc..bbbb.",
                        "....b.b..",
                        "....b...."}));
        ICONS.put("berserker_quake", new Icon(0xFF2A2A2A, 0xFF7A6A5A, 0xFFC0B0A0,
                new String[] {
                        "....b....",
                        "...bbb...",
                        "..bbbbb..",
                        ".bbbbbbb.",
                        "ccccccccc",
                        "c.c...c.c",
                        ".c.c.c.c.",
                        "..c.c.c..",
                        "...c.c..."}));
        ICONS.put("berserker_chain_hook", new Icon(0xFF2A2A2A, 0xFF8A8A9A, 0xFFE0E0E8,
                new String[] {
                        "bb.......",
                        ".bb......",
                        "..bb.....",
                        "...bb....",
                        "....cc...",
                        ".....c.c.",
                        ".....c..c",
                        "......cc.",
                        "........."}));
        ICONS.put("berserker_carnage_dash", new Icon(0xFF4A0A0A, 0xFFB02A24, 0xFFF0A080,
                new String[] {
                        "b...c....",
                        ".b..c..cc",
                        "..b.c.cc.",
                        "bbbccccc.",
                        "..b.c.cc.",
                        ".b..c..cc",
                        "b...c....",
                        ".........",
                        "........."}));
        ICONS.put("berserker_ignore_pain", new Icon(0xFF2A2A2A, 0xFF6A6A6A, 0xFFC0C0C0,
                new String[] {
                        "bbb...bbb",
                        "bbbbbbbbb",
                        ".bbbbbbb.",
                        ".bbcccbb.",
                        ".bbbcbbb.",
                        ".bbbcbbb.",
                        "..bbbbb..",
                        "..bbbbb..",
                        "...bbb..."}));
        ICONS.put("berserker_savage_roar", new Icon(0xFF4A2A0A, 0xFFC9892A, 0xFFF0E0A0,
                new String[] {
                        "c.......c",
                        ".c.bbb.c.",
                        "..bb.bb..",
                        "cbbbbbbbc",
                        ".b.bbb.b.",
                        "cbbbbbbbc",
                        "..bb.bb..",
                        ".c.bbb.c.",
                        "c.......c"}));
        ICONS.put("berserker_insatiable_hunger", new Icon(0xFF4A0A0A, 0xFFB02A24, 0xFFF08078,
                new String[] {
                        ".bb.bb...",
                        "bbbbbbb..",
                        "bbbbbbb.c",
                        ".bbbbb.c.",
                        "..bbb.c..",
                        "...b.c...",
                        "....c....",
                        "...c.....",
                        "..c......"}));
        ICONS.put("berserker_death_spin", new Icon(0xFF2A2A3A, 0xFF8A8A9A, 0xFFF0F0F8,
                new String[] {
                        "..ccccc..",
                        ".c.....c.",
                        "c..bbb..c",
                        "c.bbbbb.c",
                        "c.bbbbb.c",
                        "c.bbbbb.c",
                        "c..bbb..c",
                        ".c.....c.",
                        "..ccccc.."}));
        ICONS.put("berserker_execution", new Icon(0xFF2A1A0A, 0xFF8A6A3A, 0xFFE0E0E8,
                new String[] {
                        "...ccccc.",
                        "..cccccc.",
                        ".ccccccc.",
                        ".ccccc...",
                        "...b.....",
                        "..b......",
                        ".b.......",
                        "b........",
                        "........."}));
        ICONS.put("berserker_undying_rage", new Icon(0xFF4A0A0A, 0xFFC94A1A, 0xFFF0C050,
                new String[] {
                        "c...c...c",
                        ".c.ccc.c.",
                        "..ccccc..",
                        ".cc.b.cc.",
                        "cc.bbb.cc",
                        ".cc.b.cc.",
                        "..ccccc..",
                        ".c.ccc.c.",
                        "c...c...c"}));

        // ------------------------------------------------------------ страйкер
        ICONS.put("striker_soaring_fist", new Icon(0xFF3A2A0A, 0xFFC9A227, 0xFFF0E8B0,
                new String[] {
                        ".........",
                        "c........",
                        ".c...bbb.",
                        "..c.bbbbb",
                        "ccccbbbbb",
                        "..c.bbbbb",
                        ".c...bbb.",
                        "c........",
                        "........."}));
        ICONS.put("striker_gale_palm", new Icon(0xFF2A3A3A, 0xFF7AB0B0, 0xFFD8F0F0,
                new String[] {
                        "c........",
                        ".cc...bb.",
                        "..cc.bbbb",
                        "...ccbbbb",
                        "....cbbbb",
                        "...ccbbbb",
                        "..cc.bbbb",
                        ".cc...bb.",
                        "c........"}));
        ICONS.put("striker_sweep_kick", new Icon(0xFF3A2A1A, 0xFF8A6A3A, 0xFFE0C080,
                new String[] {
                        "....bb...",
                        "....bb...",
                        "...bbb...",
                        "...bbb...",
                        "..bbbb...",
                        ".ccbbb...",
                        "cc.......",
                        "c........",
                        "........."}));
        ICONS.put("striker_wind_flurry", new Icon(0xFF2A3A3A, 0xFF9AC0C0, 0xFFF0F8F8,
                new String[] {
                        "c.c.c.c.c",
                        ".........",
                        "cccccccc.",
                        ".........",
                        "c.c.c.c.c",
                        ".........",
                        "cccccccc.",
                        ".........",
                        "c.c.c.c.c"}));
        ICONS.put("striker_chi_blast", new Icon(0xFF2A2A4A, 0xFF6A8AD0, 0xFFD8E8FF,
                new String[] {
                        "....c....",
                        "..cbbbc..",
                        ".cbbbbbc.",
                        "cbbcccbbc",
                        "cbbcccbbc",
                        "cbbcccbbc",
                        ".cbbbbbc.",
                        "..cbbbc..",
                        "....c...."}));
        ICONS.put("striker_rising_dragon", new Icon(0xFF3A2A0A, 0xFFC9892A, 0xFFF0E0A0,
                new String[] {
                        "....c....",
                        "...ccc...",
                        "..ccccc..",
                        "....c....",
                        "....c....",
                        "..bbbbb..",
                        ".bbbbbbb.",
                        ".bbbbbbb.",
                        "..bbbbb.."}));
        ICONS.put("striker_touch_of_karma", new Icon(0xFF2A0A3A, 0xFF8A3AC0, 0xFFE0C0F0,
                new String[] {
                        "...ccc...",
                        "..c...c..",
                        ".c.bbb.c.",
                        "c.bbbbb.c",
                        "c.bbbbb.c",
                        "c.bbbbb.c",
                        ".c.bbb.c.",
                        "..c...c..",
                        "...ccc..."}));
        ICONS.put("striker_phantom_step", new Icon(0xFF2A2A3A, 0xFF9A9AB0, 0xFFF0F0F8,
                new String[] {
                        ".........",
                        "..bbb.c..",
                        ".bbbbb.c.",
                        ".bbbbb..c",
                        "..bbb...c",
                        "...b...c.",
                        "..b.b.c..",
                        ".b...bc..",
                        "........."}));
        ICONS.put("striker_zen_meditation", new Icon(0xFF1A3A3A, 0xFF4AA0A0, 0xFFD0F0F0,
                new String[] {
                        "....c....",
                        "...ccc...",
                        "....c....",
                        "..bbbbb..",
                        ".bbbbbbb.",
                        "bb.bbb.bb",
                        "b..bbb..b",
                        ".bbbbbbb.",
                        "ccccccccc"}));
        ICONS.put("striker_asura_mode", new Icon(0xFF3A0A0A, 0xFFC94A1A, 0xFFF0C050,
                new String[] {
                        "c.c...c.c",
                        ".ccc.ccc.",
                        "..bbbbb..",
                        ".bb.b.bb.",
                        "cbbbbbbbc",
                        ".bbbbbbb.",
                        "..bb.bb..",
                        ".ccc.ccc.",
                        "c.c...c.c"}));

        // ------------------------------------------------------------ рыцарь
        ICONS.put("knight_challenge", new Icon(0xFF3A1A1A, 0xFFB03A32, 0xFFF0E0C0,
                new String[] {
                        "....c....",
                        "...ccc...",
                        "..cc.cc..",
                        ".cc...cc.",
                        "cc.....cc",
                        ".bb...bb.",
                        "..bb.bb..",
                        "...bbb...",
                        "....b...."}));
        ICONS.put("knight_war_cry", new Icon(0xFF3A2A1A, 0xFF8A6A3A, 0xFFF0D898,
                new String[] {
                        "c.......c",
                        ".c.bbb.c.",
                        "..bbbbb..",
                        "c.bb.bb.c",
                        ".bbbbbbb.",
                        "c.bb.bb.c",
                        "..bbbbb..",
                        ".c.bbb.c.",
                        "c.......c"}));
        ICONS.put("knight_shockwave", new Icon(0xFF2A2A2A, 0xFF8A8A9A, 0xFFF0F0F8,
                new String[] {
                        "c........",
                        ".c.......",
                        "..cc.....",
                        "...ccc...",
                        "bbbbccccc",
                        "...ccc...",
                        "..cc.....",
                        ".c.......",
                        "c........"}));
        ICONS.put("knight_protector", new Icon(0xFF1A2A3A, 0xFF4A7AB0, 0xFFD0E8F8,
                new String[] {
                        "ccccccccc",
                        "cbbbbbbbc",
                        "cb.bbb.bc",
                        "cbbbbbbbc",
                        ".cbbbbbc.",
                        ".cbbbbbc.",
                        "..cbbbc..",
                        "...ccc...",
                        "....c...."}));
        ICONS.put("knight_shield_wall", new Icon(0xFF2A2A2A, 0xFF7A7A8A, 0xFFE0E0E8,
                new String[] {
                        "bbb.bbb.b",
                        "bbb.bbb.b",
                        ".bbb.bbb.",
                        ".bbb.bbb.",
                        "bbb.bbb.b",
                        "bbb.bbb.b",
                        ".bbb.bbb.",
                        ".bbb.bbb.",
                        "ccccccccc"}));
        ICONS.put("knight_shield_bash", new Icon(0xFF2A2A2A, 0xFF8A8A9A, 0xFFF0D070,
                new String[] {
                        "..bbbbb..",
                        ".bbbbbbb.",
                        "bbbbbbbbb",
                        "bbb...bbb",
                        "bb.ccc.bb",
                        "bbb...bbb",
                        ".bbbbbbb.",
                        "..bbbbb..",
                        "...ccc..."}));
        ICONS.put("knight_retribution", new Icon(0xFF3A2A0A, 0xFFC9A227, 0xFFF0E8B0,
                new String[] {
                        "c.......c",
                        ".c.....c.",
                        "..cbbbc..",
                        ".cbbbbbc.",
                        "cbbbcbbbc",
                        ".cbbbbbc.",
                        "..cbbbc..",
                        ".c.....c.",
                        "c.......c"}));
        ICONS.put("knight_banner", new Icon(0xFF2A1A0A, 0xFF8A3A32, 0xFFF0E0C0,
                new String[] {
                        ".ccccccc.",
                        ".cbbbbbc.",
                        ".cb.b.bc.",
                        ".cbbbbbc.",
                        ".cbbbbbc.",
                        ".c.ccc.c.",
                        "...ccc...",
                        "...ccc...",
                        "...ccc..."}));
        ICONS.put("knight_judgment", new Icon(0xFF3A3A0A, 0xFFC9C92A, 0xFFF8F8D0,
                new String[] {
                        "....c....",
                        "ccccccccc",
                        "c...c...c",
                        "ccc.c.ccc",
                        ".c..c..c.",
                        "....c....",
                        "...bbb...",
                        "..bbbbb..",
                        ".bbbbbbb."}));
        ICONS.put("knight_absolute_fortress", new Icon(0xFF2A2A3A, 0xFF8A8AA0, 0xFFF0E0A0,
                new String[] {
                        "c.c.c.c.c",
                        "ccccccccc",
                        "cbbbbbbbc",
                        "cb.bbb.bc",
                        "cbbbbbbbc",
                        "cb.bbb.bc",
                        "cbbbbbbbc",
                        "ccccccccc",
                        "c.c.c.c.c"}));

        // ------------------------------------------------------------ воин
        ICONS.put("warrior_charge", new Icon(0xFF3A2A1A, 0xFF9A7A4A, 0xFFF0D8A0,
                new String[] {
                        ".........",
                        "c........",
                        ".c....bb.",
                        "..c..bbbb",
                        "ccccbbbbb",
                        "..c..bbbb",
                        ".c....bb.",
                        "c........",
                        "........."}));
        ICONS.put("warrior_cleave", new Icon(0xFF2A2A2A, 0xFF8A8A9A, 0xFFF0F0F8,
                new String[] {
                        "......ccc",
                        "....ccc..",
                        "..ccc....",
                        ".cc......",
                        "cc.......",
                        ".cc......",
                        "..ccc....",
                        "....ccc..",
                        "......ccc"}));
        ICONS.put("warrior_shield_up", new Icon(0xFF2A2A3A, 0xFF7A7A9A, 0xFFE0E0F0,
                new String[] {
                        "...ccc...",
                        "..ccccc..",
                        "bbbbbbbbb",
                        "bbbbbbbbb",
                        ".bbbbbbb.",
                        ".bbbbbbb.",
                        "..bbbbb..",
                        "...bbb...",
                        "....b...."}));
        ICONS.put("warrior_shield_charge", new Icon(0xFF2A2A3A, 0xFF7A7A9A, 0xFFF0D070,
                new String[] {
                        "c........",
                        ".c..bbbbb",
                        "..c.bbbbb",
                        "ccc.bbbbb",
                        "..c..bbbb",
                        "ccc.bbbbb",
                        "..c.bbbbb",
                        ".c..bbbbb",
                        "c........"}));
        ICONS.put("warrior_spin", new Icon(0xFF2A2A2A, 0xFF9A9AA8, 0xFFF0F0F8,
                new String[] {
                        "..ccccc..",
                        ".c.....c.",
                        "c...b...c",
                        "c..bbb..c",
                        "c.bbbbb.c",
                        "c..bbb..c",
                        "c...b...c",
                        ".c.....c.",
                        "..ccccc.."}));
        ICONS.put("warrior_challenge", new Icon(0xFF3A1A1A, 0xFF9A3A32, 0xFFF0D8B0,
                new String[] {
                        "....c....",
                        "...ccc...",
                        "..cc.cc..",
                        ".cc...cc.",
                        "..bbbbb..",
                        ".bb...bb.",
                        "..bb.bb..",
                        "...bbb...",
                        "....b...."}));
        ICONS.put("warrior_rage", new Icon(0xFF4A0A0A, 0xFFC94A3D, 0xFFF0A890,
                new String[] {
                        "..b...b..",
                        ".bb...bb.",
                        "..bb.bb..",
                        "...ccc...",
                        "..ccccc..",
                        "..ccccc..",
                        "...ccc...",
                        "..bb.bb..",
                        ".b.....b."}));
        ICONS.put("warrior_combo", new Icon(0xFF3A3A1A, 0xFFC9C92A, 0xFFF8F8C0,
                new String[] {
                        "b........",
                        ".b.......",
                        "..b......",
                        "c........",
                        ".c.......",
                        "..c......",
                        "cc.......",
                        ".cc......",
                        "..ccc...."}));
        ICONS.put("warrior_stance", new Icon(0xFF2A2A2A, 0xFF6A6A7A, 0xFFE0E0E8,
                new String[] {
                        "c.......c",
                        ".c.bbb.c.",
                        "..bbbbb..",
                        ".bbbbbbb.",
                        "cbbbbbbbc",
                        ".bbbbbbb.",
                        "..bbbbb..",
                        ".c.bbb.c.",
                        "c.......c"}));
        ICONS.put("warrior_war_cry", new Icon(0xFF3A2A0A, 0xFFC9892A, 0xFFF0E0A0,
                new String[] {
                        "c.......c",
                        ".c.....c.",
                        "..bbbbb..",
                        "c.bb.bb.c",
                        ".bbbbbbb.",
                        "c.bb.bb.c",
                        "..bbbbb..",
                        ".c.....c.",
                        "c.......c"}));
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
