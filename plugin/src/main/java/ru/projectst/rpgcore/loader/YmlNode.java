package ru.projectst.rpgcore.loader;

import java.util.List;

/**
 * Узел разобранного документа, знающий своё место в файле.
 *
 * <p>Намеренно не используется {@code YamlConfiguration} из Bukkit: он не даёт
 * ни позиций, ни информации о том, какие ключи остались непрочитанными, а без
 * этих двух вещей требование «никаких тихих отказов» невыполнимо.
 */
public sealed interface YmlNode permits YmlNode.Scalar, YmlNode.Seq, YmlMap {

    SourceRef at();

    /** Одиночное значение. Хранится как текст, разбор по типу делает читатель. */
    record Scalar(SourceRef at, String raw) implements YmlNode {}

    /** Список. */
    record Seq(SourceRef at, List<YmlNode> items) implements YmlNode {}
}
