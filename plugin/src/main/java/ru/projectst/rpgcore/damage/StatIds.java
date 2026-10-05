package ru.projectst.rpgcore.damage;

/**
 * Идентификаторы статов, которые знает конвейер урона.
 *
 * <p>Собраны в одном месте намеренно: пока они были строками по месту
 * использования, опечатка в имени стата означала «надбавка молча не
 * применяется». Теперь опечатка ловится компилятором, а отсутствие стата в
 * реестре — исключением при расчёте.
 */
public final class StatIds {

    /** Усиление эффектов: лечение и величины статусов. */
    public static final String EFFECT_POWER = "effect_power";

    /** Удлинение эффектов: длительность накладываемых статусов. */
    public static final String EFFECT_DURATION = "effect_duration";

    /** Запас здоровья. Переносится в ванильный атрибут игрока. */
    public static final String MAX_HEALTH = "max_health";

    /** Запас маны. */
    public static final String MAX_MANA = "max_mana";

    /** Восстановление маны в секунду. */
    public static final String MANA_REGEN = "mana_regen";

    /** Сокращение перезарядки в процентах; ограничено сверху в CastService. */
    public static final String COOLDOWN_REDUCTION = "cooldown_reduction";

    /** Общий усилитель урона навыков, поверх школьного. */
    public static final String SKILL_DAMAGE = "skill_damage";

    public static final String PHYSICAL_DAMAGE = "physical_damage";
    public static final String MAGIC_DAMAGE = "magic_damage";

    public static final String CRIT_CHANCE = "critical_strike_chance";
    public static final String CRIT_POWER = "critical_strike_power";

    public static final String DEFENSE = "defense";
    public static final String MAGIC_RESISTANCE = "magic_resistance";

    /** Процентное снижение любого урона. */
    public static final String DAMAGE_REDUCTION = "damage_reduction";

    private StatIds() {
    }
}
