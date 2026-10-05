package ru.projectst.rpgcore.skill;

import java.util.UUID;

/**
 * Призванное существо.
 *
 * <p>Владелец записан явно, и это главное. В старом стеке «свой» моб отличался
 * от чужого проверкой типа — все волки считались своими, поэтому зверь друида
 * получал лечение и урон от чужих друидов, а исправление сводилось к штампу
 * имени владельца в переменную моба при спавне, который ещё и опаздывал
 * относительно самого спавна.
 *
 * @param entityId сущность в мире
 * @param owner    кто призвал
 * @param tag      тип призванного: по нему навыки находят своих
 * @param expiresAtTick тик, после которого существо снимается
 * @param attacksEnemies само ищет врагов владельца
 */
public record Minion(UUID entityId, UUID owner, String tag, long expiresAtTick,
                     boolean attacksEnemies) {

    public Minion {
        if (entityId == null || owner == null) {
            throw new IllegalArgumentException("у призванного обязательны сущность и владелец");
        }
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("у призванного обязателен тег");
        }
    }

    public boolean ownedBy(UUID candidate) {
        return owner.equals(candidate);
    }

    public boolean expired(long now) {
        return now >= expiresAtTick;
    }
}
