package ru.projectst.rpgcore.item;

import java.util.Collection;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.loader.SourceRef;
import ru.projectst.rpgcore.skill.SkillDef;
import ru.projectst.rpgcore.skill.SkillRegistry;
import ru.projectst.rpgcore.stat.StatRegistry;

/**
 * Связывание предметов: статы, редкости, классы и навыки умений.
 *
 * <p>Самая важная проверка здесь — последняя. Умение предмета обязано ссылаться
 * на служебный навык без класса, иначе предмет давал бы доступ к классовому
 * навыку в обход изучения: надел посох — получил «Коллапс» пятой ступени. Такую
 * дыру находят не в ревью, а на сервере, через неделю.
 */
public final class ItemLinker {

    private ItemLinker() {
    }

    public static void link(Collection<ItemDef> items, ItemRegistry registry,
                            StatRegistry stats, SkillRegistry skills,
                            Collection<String> classIds, ContentErrors errors) {
        for (ItemDef item : items) {
            SourceRef where = SourceRef.ofFile(item.id() + ".yml");

            for (String statId : item.stats().keySet()) {
                if (!stats.has(statId)) {
                    errors.add(where, "stats." + statId,
                            "ссылка на необъявленный стат \"" + statId + "\"");
                }
            }

            if (!registry.hasRarity(item.rarityId())) {
                errors.add(where, "rarity",
                        "ссылка на необъявленную редкость \"" + item.rarityId() + "\"");
            }

            String required = item.requirement().classId();
            if (required != null && !classIds.isEmpty() && !classIds.contains(required)) {
                errors.add(where, "requires.class",
                        "ссылка на несуществующий класс \"" + required + "\"");
            }

            for (int i = 0; i < item.abilities().size(); i++) {
                ItemAbility ability = item.abilities().get(i);
                String path = "abilities[" + i + "].skill";
                var skill = skills.find(ability.skillId());
                if (skill.isEmpty()) {
                    errors.add(where, path,
                            "ссылка на несуществующий навык \"" + ability.skillId() + "\"");
                    continue;
                }
                SkillDef def = skill.get();
                if (!def.internal()) {
                    errors.add(where, path, "умение предмета должно ссылаться на служебный навык"
                            + " (internal: true), иначе предмет даёт классовый навык в обход"
                            + " изучения");
                }
                if (!def.classId().isBlank()) {
                    errors.add(where, path, "навык умения предмета принадлежит классу "
                            + def.classId() + "; у навыка предмета класса быть не должно");
                }
            }
        }
    }
}
