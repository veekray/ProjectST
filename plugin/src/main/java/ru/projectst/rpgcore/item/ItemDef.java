package ru.projectst.rpgcore.item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ru.projectst.rpgcore.stat.StatModifier;
import ru.projectst.rpgcore.stat.StatOp;

/**
 * Предмет как объявление.
 *
 * <p>В файле лежит, чем предмет выглядит, что даёт, кому разрешён и что умеет. В
 * мире живёт обычный ванильный предмет с нашей меткой — см. {@code platform}.
 *
 * @param id         идентификатор в нижнем регистре, он же имя файла
 * @param display    имя для игрока, без цветовых кодов: цвет даёт редкость
 * @param material   ванильный материал
 * @param modelData  значение custom model data; ноль — не задавать
 * @param lore       строки описания, как написаны в файле
 * @param rarityId   редкость
 * @param slot       где предмет должен быть, чтобы его статы считались
 * @param stats      надбавки к статам: ключ стата → надбавка
 * @param requirement кому разрешён
 * @param abilities  умения
 * @param unbreakable не ломается
 */
public record ItemDef(String id, String display, String material, int modelData,
                      List<String> lore, String rarityId, ItemSlot slot,
                      Map<String, ItemStatLine> stats, ItemRequirement requirement,
                      List<ItemAbility> abilities, boolean unbreakable) {

    public ItemDef {
        if (id == null || !id.equals(id.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("id предмета должен быть в нижнем регистре: " + id);
        }
        if (material == null || material.isBlank()) {
            throw new IllegalArgumentException("у предмета обязателен материал: " + id);
        }
        if (slot == null) {
            throw new IllegalArgumentException("у предмета обязателен слот: " + id);
        }
        lore = lore == null ? List.of() : List.copyOf(lore);
        stats = Collections.unmodifiableMap(new LinkedHashMap<>(
                stats == null ? Map.of() : stats));
        abilities = abilities == null ? List.of() : List.copyOf(abilities);
        requirement = requirement == null ? ItemRequirement.NONE : requirement;
        rarityId = rarityId == null || rarityId.isBlank() ? Rarity.COMMON.id() : rarityId;
    }

    /**
     * Надбавки предмета как источник для {@code StatService}.
     *
     * <p>Имя источника включает слот: два разных предмета в двух слотах не
     * вытесняют друг друга, а один и тот же слот не накапливает надбавки от
     * снятых предметов.
     */
    public List<StatModifier> modifiers(String source) {
        List<StatModifier> out = new java.util.ArrayList<>();
        for (Map.Entry<String, ItemStatLine> entry : stats.entrySet()) {
            out.add(new StatModifier(entry.getKey(), entry.getValue().op(),
                    entry.getValue().value(), source));
        }
        return out;
    }

    /** Надбавка предмета к одному стату. */
    public record ItemStatLine(StatOp op, double value) {

        public ItemStatLine {
            if (op == null) {
                throw new IllegalArgumentException("у надбавки обязателен способ применения");
            }
        }
    }
}
