package ru.projectst.rpgcore.item;

/**
 * Умение предмета: навык, который он запускает.
 *
 * <p>Навык обязан быть служебным и без класса: предмет не должен давать доступ к
 * классовому навыку в обход изучения. Это проверяет связывание, а не рантайм.
 *
 * @param skillId навык, который выполняется
 * @param trigger что его запускает
 */
public record ItemAbility(String skillId, ItemTrigger trigger) {

    public ItemAbility {
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("у умения предмета обязателен навык");
        }
        if (trigger == null) {
            throw new IllegalArgumentException("у умения предмета обязателен триггер");
        }
    }
}
