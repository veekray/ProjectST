package ru.projectst.rpgcore.item;

import java.util.Locale;

/**
 * Редкость предмета: имя и цвет в одном месте.
 *
 * <p>Цветовые коды, повторённые в каждом предмете, разъезжаются первыми: часть
 * «легендарных» оказывается золотой, часть жёлтой, и чинить это приходится
 * поиском по всем файлам.
 *
 * @param id      идентификатор в нижнем регистре
 * @param display как называется игроку
 * @param color   цвет, которым выводится имя предмета и сама редкость
 */
public record Rarity(String id, String display, String color) {

    public static final Rarity COMMON = new Rarity("common", "Обычный", "GRAY");

    public Rarity {
        if (id == null || !id.equals(id.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("id редкости должен быть в нижнем регистре: " + id);
        }
        if (display == null || display.isBlank()) {
            throw new IllegalArgumentException("у редкости обязательно название: " + id);
        }
        if (color == null || color.isBlank()) {
            throw new IllegalArgumentException("у редкости обязателен цвет: " + id);
        }
    }
}
