package ru.projectst.rpgcore.client;

import java.util.Map;

/**
 * Как выглядит эффект: вид, цвета и сроки.
 *
 * <p>Каталог — здесь, а не на сервере: сервер говорит «что и где» числами, мод
 * решает «как красиво». Поэтому новый эффект добавляется строкой сюда, и плагин
 * об этом не знает. Эффект, которого в каталоге нет, рисуется общим по своему
 * виду и цвету класса, — так же, как значок навыка без картинки рисуется ромбом
 * цвета класса: новый навык в контенте не должен требовать новой сборки мода.
 *
 * <p>Граница области у всех видов, где она есть, рисуется ровно по радиусу из
 * события. Цвета и сроки меняют только украшения вокруг неё.
 *
 * @param kind    вид: что именно рисуется
 * @param primary основной цвет (RGB); 0 — цвет класса
 * @param accent  цвет искр, таймера и блика (RGB); 0 — светлее основного
 * @param grow    за сколько тиков волна дорастает до радиуса
 * @param hold    сколько тиков граница держится чёткой линией
 * @param fade    за сколько тиков гаснет
 * @param runes   пояс рун внутри границы
 * @param motes   сколько искр, доля от обычного
 * @param size    размер ядра снаряда или вспышки, в блоках
 * @param motif   форма искр; {@code null} — мотив класса
 */
public record FxStyle(Kind kind, int primary, int accent, int grow, int hold, int fade,
                      boolean runes, float motes, float size, FxDraw.Tex motif) {

    /** Стиль с искрами класса: так записано почти всё в каталоге. */
    public FxStyle(Kind kind, int primary, int accent, int grow, int hold, int fade,
                   boolean runes, float motes, float size) {
        this(kind, primary, accent, grow, hold, fade, runes, motes, size, null);
    }

    /** Что рисуется. Один вид — одна функция в {@link FxKinds}. */
    public enum Kind {
        /** Зона на земле: граница, дуга срока, заливка. */
        ZONE,
        /** Волна от центра до границы; граница держится. */
        WAVE,
        /** Кольцо, стягивающееся в центр: печать съедена. */
        IMPLODE,
        /** Круг удара, который ещё не ударил. */
        TELEGRAPH,
        /** Сектор конуса: дуга, кромки, заливка. */
        CONE,
        /** Вспышка в точке. */
        FLASH,
        /** Снаряд: ядро и шлейф. */
        BOLT,
        /** След перемещения. */
        TRAIL,
        /** Попадание по цели. */
        HIT,
        /** Поднимается от ног: бафф, лечение, усиление. */
        RISE,
        /** Оседает на цель сверху: проклятие, метка, ослабление. */
        SINK
    }

    // ------------------------------------------------------------------ облик классов

    /**
     * Облик класса: основной цвет, блик и форма искр.
     *
     * <p>Свои цвета, а не цвета значков: значок лежит на тёмном поле меню, а
     * эффект светится поверх мира сложением. Приглушённый серо-синий плута на
     * значке читается, а в свечении пропадает — поэтому здесь насыщеннее.
     * Оттенок тот же, что у значков, чтобы класс узнавался одинаково и в меню,
     * и в бою.
     */
    record Look(int primary, int accent, FxDraw.Tex motif) {
    }

    private static final Map<String, Look> LOOKS = Map.ofEntries(
            // Аркана: синие звёзды.
            Map.entry("mage", new Look(0x7A86FF, 0xC9B8FF, FxDraw.Tex.SPARK)),
            // Скверна: лиловая дымка с ядовито-зелёным бликом.
            Map.entry("warlock", new Look(0xB04AE0, 0x9CFF6A, FxDraw.Tex.WISP)),
            // Природа: листья в солнечном свете.
            Map.entry("druid", new Look(0x5CD65A, 0xE8F59A, FxDraw.Tex.LEAF)),
            // Сталь: бирюзовые осколки-клинки.
            Map.entry("rogue", new Look(0x5FB8D0, 0xE6F6FF, FxDraw.Tex.SHARD)),
            // Тень: фиолетовая дымка с кровавым бликом.
            Map.entry("assassin", new Look(0x7B3BD6, 0xFF4D7A, FxDraw.Tex.WISP)),
            // Иллюзия: переливчатые звёзды.
            Map.entry("trickster", new Look(0xC86BFF, 0x7AF0FF, FxDraw.Tex.STAR)),
            // Охота: янтарные наконечники.
            Map.entry("hunter", new Look(0xE0A83A, 0xC9F27A, FxDraw.Tex.CHEVRON)),
            // Ярость: кровь и угли.
            Map.entry("berserker", new Look(0xE0321E, 0xFFB347, FxDraw.Tex.EMBER)),
            // Гроза: жёлтые молнии.
            Map.entry("striker", new Look(0xFFD23A, 0xBFF6FF, FxDraw.Tex.ZAP)),
            // Свет: бледное золото, белые звёзды.
            Map.entry("knight", new Look(0xF0D58A, 0xFFFFFF, FxDraw.Tex.SPARK)),
            // Сталь и огонь: оранжевые угли.
            Map.entry("warrior", new Look(0xFF7A3A, 0xFFE08A, FxDraw.Tex.EMBER)),
            // Предметы: золото.
            Map.entry("item", new Look(0xE8C25A, 0xFFF2C0, FxDraw.Tex.SPARK)),
            // Мобы: болезненная зелень — чужое видно сразу.
            Map.entry("mob", new Look(0x8FD14A, 0xE4FF8A, FxDraw.Tex.WISP)));

    /** Ничей навык: врождённый рывок. Светлое золото интерфейса, дымка. */
    private static final Look NEUTRAL = new Look(0xE6D7A8, 0xFFFFFF, FxDraw.Tex.WISP);

    /**
     * Чей эффект: класс из события, а без него — по началу идентификатора.
     *
     * <p>Вспомогательный навык (капкан охотника, взрыв трупа у ловкача) может
     * быть записан без класса, и сервер пришлёт пустой класс. Эффект
     * {@code hunter_flash} при этом всё равно охотничий: нарисовать его
     * нейтральным золотом значило бы потерять класс там, где его видно.
     */
    static String owner(String classId, String fx) {
        if (classId != null && !classId.isEmpty()) {
            return classId;
        }
        if (fx == null) {
            return "";
        }
        int cut = fx.indexOf('_');
        String prefix = cut > 0 ? fx.substring(0, cut) : fx;
        return LOOKS.containsKey(prefix) ? prefix : "";
    }

    static Look look(String classId) {
        return LOOKS.getOrDefault(classId == null ? "" : classId, NEUTRAL);
    }

    // ------------------------------------------------------------------ маг

    /** Синевато-фиолетовая магия: тот же синий, что у значков мага, сдвинутый к аркане. */
    private static final int ARCANE = 0x7A86FF;
    private static final int ARCANE_LIGHT = 0xC9B8FF;
    private static final int VOID = 0xB15CFF;
    private static final int VOID_LIGHT = 0xF0C8FF;

    private static final Map<String, FxStyle> CATALOG = Map.ofEntries(
            // Печать: зона на земле. Граница чёткая, руны медленно ходят по кругу.
            Map.entry("mage_seal", new FxStyle(Kind.ZONE, ARCANE, ARCANE_LIGHT, 0, 0, 8, true, 1f, 0)),
            // Ядро съело печать: кольцо стягивается в мага и вспыхивает столбом.
            Map.entry("mage_seal_eat", new FxStyle(Kind.IMPLODE, VOID, VOID_LIGHT, 8, 0, 6, false, 1f, 1.2f)),
            // Петля: волна до радиуса толчка, граница держится, чтобы её прочитали.
            Map.entry("mage_flow_wave", new FxStyle(Kind.WAVE, ARCANE, ARCANE_LIGHT, 6, 8, 6, false, 1f, 0)),
            Map.entry("mage_flow_wave_strong", new FxStyle(Kind.WAVE, VOID, VOID_LIGHT, 6, 10, 8, true, 1.6f, 0)),
            // Разряд: светящееся ядро со шлейфом.
            Map.entry("mage_bolt", new FxStyle(Kind.BOLT, ARCANE, ARCANE_LIGHT, 0, 0, 6, false, 1f, 0.45f)),
            Map.entry("mage_bolt_strong", new FxStyle(Kind.BOLT, VOID, VOID_LIGHT, 0, 0, 8, false, 1.6f, 0.7f)),
            Map.entry("mage_bolt_hit", new FxStyle(Kind.WAVE, ARCANE, ARCANE_LIGHT, 4, 3, 5, false, 0.8f, 0)),
            Map.entry("mage_bolt_hit_strong", new FxStyle(Kind.WAVE, VOID, VOID_LIGHT, 4, 3, 6, false, 1.2f, 0)),
            // Россыпь: мелкие осколки-глифы, печать в точке падения.
            Map.entry("mage_scatter_shard", new FxStyle(Kind.BOLT, ARCANE, ARCANE_LIGHT, 0, 0, 5, false, 0.6f, 0.3f)),
            // Шаг в пустоту: лента от старта до прибытия, гаснет за полсекунды.
            Map.entry("mage_void_trail", new FxStyle(Kind.TRAIL, VOID, VOID_LIGHT, 0, 4, 12, false, 1f, 0.55f)),
            Map.entry("mage_void_trail_strong", new FxStyle(Kind.TRAIL, VOID, VOID_LIGHT, 0, 6, 14, false, 1.6f, 0.8f)),
            Map.entry("mage_void_wave", new FxStyle(Kind.WAVE, VOID, VOID_LIGHT, 5, 8, 6, false, 1.2f, 0)),
            // Сгон: сектор ровно по радиусу и углу выборки, искры текут к печати.
            Map.entry("mage_herd_cone", new FxStyle(Kind.CONE, ARCANE, ARCANE_LIGHT, 4, 10, 8, false, 1f, 0)),
            Map.entry("mage_herd_cone_strong", new FxStyle(Kind.CONE, VOID, VOID_LIGHT, 4, 12, 8, false, 1.4f, 0)),
            Map.entry("mage_root_snap", new FxStyle(Kind.IMPLODE, VOID, VOID_LIGHT, 6, 0, 6, false, 1f, 0.8f)),
            // Коллапс: круг удара телеграфирует секунду, затем схлопывается волной.
            Map.entry("mage_collapse_field", new FxStyle(Kind.TELEGRAPH, VOID, VOID_LIGHT, 0, 20, 4, true, 1.4f, 0)),
            Map.entry("mage_collapse_blast", new FxStyle(Kind.WAVE, VOID, VOID_LIGHT, 5, 6, 10, false, 2f, 0)),
            // ---------------------------------------------------------- роли классов
            //
            // Цвет и форма искр — из облика класса (primary и accent здесь 0).
            // Роль решает геометрию: _wave — граница области, _aura — круг
            // вокруг себя, _rise — бафф и лечение, _sink — то, что легло на
            // цель, _flash/_blast/_puff — удар, взрыв, лёгкий всплеск, _bolt —
            // снаряд, _zone — зона на земле.
            // Аркана: руны и ровный свет.
            Map.entry("mage_flash", new FxStyle(Kind.FLASH, 0, 0, 2, 2, 6, false, 1f, 0.8f)),
            Map.entry("mage_puff", new FxStyle(Kind.FLASH, 0, 0, 2, 1, 5, false, 0.6f, 0.5f)),
            // Скверна: руны, дымка гуще и тянется дольше.
            Map.entry("warlock_bolt", new FxStyle(Kind.BOLT, 0, 0, 0, 0, 6, false, 1.1f, 0.45f)),
            Map.entry("warlock_flash", new FxStyle(Kind.FLASH, 0, 0, 2, 2, 6, false, 1.1f, 0.8f)),
            Map.entry("warlock_rise", new FxStyle(Kind.RISE, 0, 0, 4, 8, 10, true, 1.1f, 0)),
            Map.entry("warlock_sink", new FxStyle(Kind.SINK, 0, 0, 4, 10, 14, false, 1.1f, 0)),
            Map.entry("warlock_wave", new FxStyle(Kind.WAVE, 0, 0, 7, 8, 9, true, 1.32f, 0)),
            Map.entry("warlock_zone", new FxStyle(Kind.ZONE, 0, 0, 0, 0, 8, true, 1.1f, 0)),
            // Природа: без рун, листьев много — поле живое.
            Map.entry("druid_aura", new FxStyle(Kind.WAVE, 0, 0, 5, 6, 9, false, 1.82f, 0)),
            Map.entry("druid_flash", new FxStyle(Kind.FLASH, 0, 0, 2, 2, 6, false, 1.3f, 0.8f)),
            Map.entry("druid_puff", new FxStyle(Kind.FLASH, 0, 0, 2, 1, 5, false, 0.78f, 0.5f)),
            Map.entry("druid_rise", new FxStyle(Kind.RISE, 0, 0, 4, 8, 10, false, 1.3f, 0)),
            Map.entry("druid_sink", new FxStyle(Kind.SINK, 0, 0, 4, 10, 10, false, 1.3f, 0)),
            Map.entry("druid_wave", new FxStyle(Kind.WAVE, 0, 0, 6, 8, 6, false, 1.56f, 0)),
            Map.entry("druid_zone", new FxStyle(Kind.ZONE, 0, 0, 0, 0, 8, false, 1.3f, 0)),
            // Сталь: коротко и чётко, без рун.
            Map.entry("rogue_bolt", new FxStyle(Kind.BOLT, 0, 0, 0, 0, 6, false, 1f, 0.5f)),
            Map.entry("rogue_flash", new FxStyle(Kind.FLASH, 0, 0, 2, 2, 6, false, 1f, 0.8f)),
            Map.entry("rogue_puff", new FxStyle(Kind.FLASH, 0, 0, 2, 1, 5, false, 0.6f, 0.5f)),
            Map.entry("rogue_rise", new FxStyle(Kind.RISE, 0, 0, 4, 8, 10, false, 1f, 0)),
            Map.entry("rogue_sink", new FxStyle(Kind.SINK, 0, 0, 4, 10, 10, false, 1f, 0)),
            Map.entry("rogue_wave", new FxStyle(Kind.WAVE, 0, 0, 4, 8, 6, false, 1.2f, 0)),
            Map.entry("rogue_zone", new FxStyle(Kind.ZONE, 0, 0, 0, 0, 8, false, 1f, 0)),
            // Тень: скупо, искр меньше, гаснет медленно.
            Map.entry("assassin_blast", new FxStyle(Kind.FLASH, 0, 0, 3, 3, 9, false, 2.7f, 2.2f)),
            Map.entry("assassin_flash", new FxStyle(Kind.FLASH, 0, 0, 2, 2, 6, false, 0.9f, 0.8f)),
            Map.entry("assassin_rise", new FxStyle(Kind.RISE, 0, 0, 4, 8, 10, false, 0.9f, 0)),
            Map.entry("assassin_sink", new FxStyle(Kind.SINK, 0, 0, 4, 10, 14, false, 0.9f, 0)),
            Map.entry("assassin_wave", new FxStyle(Kind.WAVE, 0, 0, 7, 8, 9, false, 1.08f, 0)),
            // Иллюзия: руны и россыпь звёзд.
            Map.entry("trickster_wave", new FxStyle(Kind.WAVE, 0, 0, 6, 8, 6, true, 1.56f, 0)),
            Map.entry("trickster_blast", new FxStyle(Kind.FLASH, 0, 0, 3, 3, 9, false, 3.9f, 2.2f)),
            Map.entry("trickster_flash", new FxStyle(Kind.FLASH, 0, 0, 2, 2, 6, false, 1.3f, 0.8f)),
            Map.entry("trickster_puff", new FxStyle(Kind.FLASH, 0, 0, 2, 1, 5, false, 0.78f, 0.5f)),
            Map.entry("trickster_rise", new FxStyle(Kind.RISE, 0, 0, 4, 8, 10, true, 1.3f, 0)),
            Map.entry("trickster_sink", new FxStyle(Kind.SINK, 0, 0, 4, 10, 10, false, 1.3f, 0)),
            // Охота: тонкие быстрые снаряды, наконечники.
            Map.entry("hunter_blast", new FxStyle(Kind.FLASH, 0, 0, 3, 3, 9, false, 3f, 2.2f)),
            Map.entry("hunter_bolt", new FxStyle(Kind.BOLT, 0, 0, 0, 0, 6, false, 1f, 0.3f)),
            Map.entry("hunter_flash", new FxStyle(Kind.FLASH, 0, 0, 2, 2, 6, false, 1f, 0.8f)),
            Map.entry("hunter_puff", new FxStyle(Kind.FLASH, 0, 0, 2, 1, 5, false, 0.6f, 0.5f)),
            Map.entry("hunter_rise", new FxStyle(Kind.RISE, 0, 0, 4, 8, 10, false, 1f, 0)),
            Map.entry("hunter_sink", new FxStyle(Kind.SINK, 0, 0, 4, 10, 10, false, 1f, 0)),
            Map.entry("hunter_wave", new FxStyle(Kind.WAVE, 0, 0, 6, 8, 6, false, 1.2f, 0)),
            // Ярость: углей больше всех, вспышки резкие.
            Map.entry("berserker_bolt", new FxStyle(Kind.BOLT, 0, 0, 0, 0, 6, false, 1.5f, 0.35f)),
            Map.entry("berserker_flash", new FxStyle(Kind.FLASH, 0, 0, 2, 2, 6, false, 1.5f, 0.8f)),
            Map.entry("berserker_puff", new FxStyle(Kind.FLASH, 0, 0, 2, 1, 5, false, 0.9f, 0.5f)),
            Map.entry("berserker_rise", new FxStyle(Kind.RISE, 0, 0, 4, 8, 10, false, 1.5f, 0)),
            Map.entry("berserker_wave", new FxStyle(Kind.WAVE, 0, 0, 4, 8, 6, false, 1.8f, 0)),
            // Гроза: быстрые волны, молнии.
            Map.entry("striker_bolt", new FxStyle(Kind.BOLT, 0, 0, 0, 0, 6, false, 1.3f, 0.55f)),
            Map.entry("striker_flash", new FxStyle(Kind.FLASH, 0, 0, 2, 2, 6, false, 1.3f, 0.8f)),
            Map.entry("striker_puff", new FxStyle(Kind.FLASH, 0, 0, 2, 1, 5, false, 0.78f, 0.5f)),
            Map.entry("striker_rise", new FxStyle(Kind.RISE, 0, 0, 4, 8, 10, false, 1.3f, 0)),
            Map.entry("striker_sink", new FxStyle(Kind.SINK, 0, 0, 4, 10, 10, false, 1.3f, 0)),
            Map.entry("striker_wave", new FxStyle(Kind.WAVE, 0, 0, 4, 8, 6, false, 1.56f, 0)),
            // Свет: руны и долгие ровные круги.
            Map.entry("knight_blast", new FxStyle(Kind.FLASH, 0, 0, 3, 3, 9, false, 3.3f, 2.2f)),
            Map.entry("knight_flash", new FxStyle(Kind.FLASH, 0, 0, 2, 2, 6, false, 1.1f, 0.8f)),
            Map.entry("knight_rise", new FxStyle(Kind.RISE, 0, 0, 4, 8, 10, true, 1.1f, 0)),
            Map.entry("knight_sink", new FxStyle(Kind.SINK, 0, 0, 4, 10, 14, false, 1.1f, 0)),
            Map.entry("knight_wave", new FxStyle(Kind.WAVE, 0, 0, 7, 8, 9, true, 1.32f, 0)),
            // Сталь и огонь: угли, широкие волны.
            Map.entry("warrior_aura", new FxStyle(Kind.WAVE, 0, 0, 5, 6, 9, false, 1.68f, 0)),
            Map.entry("warrior_flash", new FxStyle(Kind.FLASH, 0, 0, 2, 2, 6, false, 1.2f, 0.8f)),
            Map.entry("warrior_puff", new FxStyle(Kind.FLASH, 0, 0, 2, 1, 5, false, 0.72f, 0.5f)),
            Map.entry("warrior_rise", new FxStyle(Kind.RISE, 0, 0, 4, 8, 10, false, 1.2f, 0)),
            Map.entry("warrior_sink", new FxStyle(Kind.SINK, 0, 0, 4, 10, 10, false, 1.2f, 0)),
            Map.entry("warrior_wave", new FxStyle(Kind.WAVE, 0, 0, 6, 8, 6, false, 1.44f, 0)),
            // Предметы: золото, скромнее навыков класса.
            Map.entry("item_bolt", new FxStyle(Kind.BOLT, 0, 0, 0, 0, 6, false, 0.9f, 0.4f)),
            Map.entry("item_flash", new FxStyle(Kind.FLASH, 0, 0, 2, 2, 6, false, 0.9f, 0.8f)),
            // Мобы: болезненная зелень.
            Map.entry("mob_flash", new FxStyle(Kind.FLASH, 0, 0, 2, 2, 6, false, 1f, 0.8f)),
            Map.entry("mob_wave", new FxStyle(Kind.WAVE, 0, 0, 6, 8, 6, false, 1.2f, 0)),
            // Рывок: светлая дымка, ничей цвет.
            Map.entry("dash_puff", new FxStyle(Kind.FLASH, 0, 0, 2, 1, 5, false, 0.48f, 0.5f)),
            // Попадание и крит — общие для всех классов, цвет от класса.
            Map.entry("hit", new FxStyle(Kind.HIT, 0, 0, 0, 0, 6, false, 1f, 0.6f)),
            Map.entry("crit", new FxStyle(Kind.HIT, 0, 0xFFE9A8, 0, 0, 10, false, 2.5f, 1.6f)));

    /** Общие эффекты по виду: для идентификатора, которого нет в каталоге. */
    private static final Map<Kind, FxStyle> GENERIC = Map.of(
            Kind.ZONE, new FxStyle(Kind.ZONE, 0, 0, 0, 0, 8, false, 0.6f, 0),
            Kind.WAVE, new FxStyle(Kind.WAVE, 0, 0, 6, 8, 6, false, 0.8f, 0),
            Kind.CONE, new FxStyle(Kind.CONE, 0, 0, 4, 10, 8, false, 0.6f, 0),
            Kind.FLASH, new FxStyle(Kind.FLASH, 0, 0, 3, 2, 6, false, 0.6f, 0.8f),
            Kind.BOLT, new FxStyle(Kind.BOLT, 0, 0, 0, 0, 6, false, 0.8f, 0.4f),
            Kind.TRAIL, new FxStyle(Kind.TRAIL, 0, 0, 0, 4, 12, false, 0.8f, 0.5f),
            Kind.RISE, new FxStyle(Kind.RISE, 0, 0, 4, 8, 10, false, 1f, 0),
            Kind.SINK, new FxStyle(Kind.SINK, 0, 0, 4, 8, 10, false, 1f, 0));

    /**
     * Стиль эффекта по идентификатору.
     *
     * @param fallback вид общего эффекта, если идентификатора нет в каталоге
     */
    public static FxStyle of(String fx, Kind fallback) {
        FxStyle known = fx == null ? null : CATALOG.get(fx);
        if (known != null) {
            return known;
        }
        return GENERIC.getOrDefault(fallback, GENERIC.get(Kind.FLASH));
    }

    /** Есть ли у мода свой эффект: нужно тесту контента и отладке. */
    public static boolean known(String fx) {
        return CATALOG.containsKey(fx);
    }

    /** Основной цвет с учётом класса, ARGB с полной непрозрачностью. */
    public int primaryFor(String classId) {
        int rgb = primary != 0 ? primary : look(classId).primary();
        return 0xFF000000 | rgb;
    }

    /**
     * Цвет блика: свой, а если в стиле нет ни своего блика, ни своего
     * основного — блик класса.
     *
     * <p>Стиль со своим основным цветом и без блика высветляет основной к
     * белому: блик чужого класса рядом с ним был бы случайным сочетанием.
     */
    public int accentFor(String classId) {
        if (accent != 0) {
            return 0xFF000000 | accent;
        }
        if (primary == 0) {
            return 0xFF000000 | look(classId).accent();
        }
        int base = primaryFor(classId);
        int r = ((base >> 16) & 0xFF) + 255 >> 1;
        int g = ((base >> 8) & 0xFF) + 255 >> 1;
        int b = (base & 0xFF) + 255 >> 1;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Форма искр: своя у стиля или мотив класса. */
    public FxDraw.Tex motifFor(String classId) {
        return motif != null ? motif : look(classId).motif();
    }
}
