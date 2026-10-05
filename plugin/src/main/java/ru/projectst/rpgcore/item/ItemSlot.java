package ru.projectst.rpgcore.item;

import java.util.Locale;

/**
 * Где предмет должен находиться, чтобы его статы считались.
 *
 * <p>Слот объявлен у предмета, а не угадывается по материалу. В прежнем стеке
 * тип предмета определял и слот, и поведение, из-за чего «кольцо» приходилось
 * выдавать за шлем, чтобы оно вообще что-то давало.
 */
public enum ItemSlot {

    /** В основной руке. */
    HAND,
    /** В левой руке. */
    OFFHAND,
    HELMET,
    CHEST,
    LEGS,
    BOOTS,
    /** Где угодно в инвентаре: талисманы и реликвии. */
    ANY;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
