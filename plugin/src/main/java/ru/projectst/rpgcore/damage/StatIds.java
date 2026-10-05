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

    /**
     * Защита от физического урона.
     *
     * <p>Все три защиты — <b>рейтинг</b>, а не проценты. Сколько процентов он
     * снимает, считает {@code DamageEngine} по кривой насыщения: сто рейтинга
     * режут половину, двести — две трети, четыреста — четыре пятых. Каждый
     * следующий пункт стоит столько же, а даёт меньше, и полной неуязвимости
     * не бывает ни при каком значении.
     */
    public static final String PHYSICAL_DEFENSE = "physical_defense";

    /** Защита от магического урона. Тот же рейтинг, та же кривая. */
    public static final String MAGIC_DEFENSE = "magic_defense";

    /**
     * Общая защита: действует поверх школьной.
     *
     * <p>Два слоя не складываются, а перемножаются: по половине от каждого
     * дают три четверти вместе, а не всё. Сложение привело бы к тому, что
     * сумма двух защит обнуляет удар, и дальше пришлось бы ставить потолок —
     * ровно тот костыль, от которого уходит кривая.
     */
    public static final String GENERAL_DEFENSE = "general_defense";

    /**
     * Шанс не получить удар вовсе, в процентах.
     *
     * <p>Отдельно от снижения урона намеренно: снижение режет каждый удар
     * понемногу и предсказуемо, уклонение изредка убирает удар целиком. Одно
     * число вместо двух стёрло бы разницу, на которой стоят плут и его
     * подклассы.
     */
    public static final String DODGE_RATING = "dodge_rating";

    /** Получаемое лечение в процентах; отрицательное — анти-хил. */
    public static final String INCOMING_HEALING = "incoming_healing";

    /** Скорость передвижения в процентах сверх обычной. */
    public static final String MOVEMENT_SPEED = "movement_speed";

    /** Скорость атаки в процентах сверх обычной. */
    public static final String ATTACK_SPEED = "attack_speed";

    /** Доля нанесённого урона, возвращаемая здоровьем. */
    public static final String LIFESTEAL = "lifesteal";

    /**
     * Радиус площадных навыков в процентах сверх объявленного.
     *
     * <p>Читается в одном месте — там, где шаг выбирает цели, — и этого
     * достаточно: зоны и взрывы выбирают цели тем же способом. Дальность
     * снарядов он намеренно не трогает: «шире» и «дальше» это разные вещи, и
     * один стат, делающий оба, нельзя было бы настроить ни под то, ни под
     * другое.
     */
    public static final String SKILL_RADIUS = "skill_radius";

    private StatIds() {
    }
}
