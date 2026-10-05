package ru.projectst.rpgcore.mob;

/**
 * Навык моба.
 *
 * <p>Навык обязан быть служебным и без класса — то же правило, что у умений
 * предметов, и по той же причине: иначе моб ссылался бы на классовый навык
 * игрока, а значит на его баланс и его ступени.
 *
 * @param skillId навык
 * @param trigger что его запускает
 * @param chance  вероятность срабатывания в процентах; сто — всегда
 * @param intervalTicks для {@link MobTrigger#ON_INTERVAL} — как часто
 */
public record MobSkill(String skillId, MobTrigger trigger, double chance, int intervalTicks) {

    public MobSkill {
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("у навыка моба обязателен идентификатор");
        }
        if (trigger == null) {
            throw new IllegalArgumentException("у навыка моба обязателен триггер");
        }
        if (chance <= 0 || chance > 100) {
            throw new IllegalArgumentException("вероятность от единицы до ста: " + skillId);
        }
        if (trigger == MobTrigger.ON_INTERVAL && intervalTicks < 1) {
            throw new IllegalArgumentException("периодическому навыку нужен промежуток: "
                    + skillId);
        }
    }
}
