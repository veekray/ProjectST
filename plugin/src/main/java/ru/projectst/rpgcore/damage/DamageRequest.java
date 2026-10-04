package ru.projectst.rpgcore.damage;

import java.util.Set;

/**
 * Запрос на нанесение урона. Единственная форма, в которой урон вообще может
 * быть нанесён: других путей нет, см. {@link DamageEngine}.
 *
 * @param base    базовый урон до всех множителей
 * @param school  школа
 * @param skillId откуда пришёл урон; нужен для отладки и для правил вроде
 *                «один навык не бьёт одну цель дважды за каст»
 * @param tags    произвольные метки: {@code dot}, {@code aoe}, {@code finisher}.
 *                Правила снижения и статусы могут на них смотреть
 */
public record DamageRequest(double base, DamageSchool school, String skillId, Set<String> tags) {

    public DamageRequest {
        if (base < 0) {
            throw new IllegalArgumentException("базовый урон не может быть отрицательным");
        }
        if (school == null) {
            throw new IllegalArgumentException("school обязателен");
        }
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("skillId обязателен: без него урон нельзя "
                    + "ни объяснить в отладке, ни ограничить правилом");
        }
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }

    public static DamageRequest of(double base, DamageSchool school, String skillId) {
        return new DamageRequest(base, school, skillId, Set.of());
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
}
