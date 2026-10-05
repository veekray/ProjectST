package ru.projectst.rpgcore.mob;

import java.util.Collection;
import ru.projectst.rpgcore.item.ItemRegistry;
import ru.projectst.rpgcore.loader.ContentErrors;
import ru.projectst.rpgcore.loader.SourceRef;
import ru.projectst.rpgcore.skill.SkillDef;
import ru.projectst.rpgcore.skill.SkillRegistry;
import ru.projectst.rpgcore.stat.StatRegistry;

/**
 * Связывание мобов: статы, навыки, дроп и правила спавна.
 *
 * <p>Правило про навыки то же, что у предметов: моб ссылается на служебный навык
 * без класса. Иначе моб запускал бы классовый навык игрока — вместе с его
 * балансом, его ступенями и его стоимостью маны, которой у моба нет.
 */
public final class MobLinker {

    private MobLinker() {
    }

    public static void link(Collection<MobDef> mobs, MobRegistry registry,
                            StatRegistry stats, SkillRegistry skills, ItemRegistry items,
                            ContentErrors errors) {
        for (MobDef mob : mobs) {
            SourceRef where = SourceRef.ofFile(mob.id() + ".yml");

            for (String statId : mob.stats().keySet()) {
                if (!stats.has(statId)) {
                    errors.add(where, "stats." + statId,
                            "ссылка на необъявленный стат \"" + statId + "\"");
                }
            }

            for (int i = 0; i < mob.skills().size(); i++) {
                MobSkill skill = mob.skills().get(i);
                String path = "skills[" + i + "].skill";
                var def = skills.find(skill.skillId());
                if (def.isEmpty()) {
                    errors.add(where, path,
                            "ссылка на несуществующий навык \"" + skill.skillId() + "\"");
                    continue;
                }
                SkillDef found = def.get();
                if (!found.internal()) {
                    errors.add(where, path, "навык моба должен быть служебным (internal: true),"
                            + " иначе моб запускает классовый навык игрока");
                }
                if (!found.classId().isBlank()) {
                    errors.add(where, path, "навык моба принадлежит классу " + found.classId()
                            + "; у навыка моба класса быть не должно");
                }
            }

            for (int i = 0; i < mob.drops().size(); i++) {
                MobDrop drop = mob.drops().get(i);
                if (drop.custom() && !items.has(drop.itemId())) {
                    errors.add(where, "drops[" + i + "].item",
                            "ссылка на несуществующий предмет \"" + drop.itemId() + "\"");
                }
            }
        }

        for (int i = 0; i < registry.rules().size(); i++) {
            SpawnRule rule = registry.rules().get(i);
            if (!registry.has(rule.mobId())) {
                errors.add(SourceRef.ofFile("spawn.yml"), "rules[" + i + "].mob",
                        "правило спавна ссылается на несуществующего моба \""
                                + rule.mobId() + "\"");
            }
        }
    }
}
