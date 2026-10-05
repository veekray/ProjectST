package ru.projectst.rpgcore.skill;

import java.util.UUID;

/**
 * Область в мире, оставленная навыком: печать мага, круг, ловушка.
 *
 * <p>Своя сущность, а не армор-стенд с метаданными. В старом стеке зоны
 * приходилось изображать невидимыми мобами, и это стоило дорого: такой «моб»
 * отбрасывался выборкой целей на двух разных стадиях — сначала на поиске
 * ({@code livingonly} по умолчанию), потом фильтром — и чтобы его увидеть,
 * нужно было знать четыре флага. Здесь зона не является целью ничего, пока её
 * не спросили по тегу.
 *
 * @param id        свой идентификатор: по нему зона снимается
 * @param tag       тип зоны, по которому её ищут навыки
 * @param owner     кто поставил; свои и чужие зоны различаются явно
 * @param center    центр
 * @param radius    радиус
 * @param expiresAtTick тик, после которого зоны нет
 * @param particle  чем рисуется; {@code null} — невидимая. Единственная
 *                  presentation-деталь в модели, и осознанно: зона, которую
 *                  игрок не видит, — это ловушка, а не механика
 * @param onEnter   навык, который выполняется на вошедшего; {@code null} — нет
 * @param onTick    навык, который зона выполняет сама; {@code null} — нет
 * @param tickInterval промежуток между тиками зоны
 */
public record Zone(UUID id, String tag, UUID owner, Position center, double radius,
                   long expiresAtTick, String particle, String onEnter, String onTick,
                   int tickInterval) {

    public Zone(UUID id, String tag, UUID owner, Position center, double radius,
                long expiresAtTick, String particle) {
        this(id, tag, owner, center, radius, expiresAtTick, particle, null, null, 0);
    }

    public Zone {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("у зоны обязателен тег");
        }
        if (radius <= 0) {
            throw new IllegalArgumentException("радиус зоны должен быть положительным");
        }
    }

    /** Лежит ли точка внутри зоны. Сравнение в одном мире, разумеется. */
    public boolean contains(Position point) {
        return center.worldId().equals(point.worldId())
                && center.distanceTo(point) <= radius;
    }

    public boolean expired(long now) {
        return now >= expiresAtTick;
    }

    public boolean ownedBy(UUID candidate) {
        return owner != null && owner.equals(candidate);
    }
}
