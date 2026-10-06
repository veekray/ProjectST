package ru.projectst.rpgcore.platform.gui;

import ru.projectst.rpgcore.cast.CastService;
import ru.projectst.rpgcore.classes.ClassRegistry;
import ru.projectst.rpgcore.classes.ClassService;
import ru.projectst.rpgcore.skill.SkillRegistry;
import ru.projectst.rpgcore.stat.StatRegistry;
import ru.projectst.rpgcore.stat.StatService;
import ru.projectst.rpgcore.status.StatusService;

/**
 * Всё, что интерфейсу нужно от плагина.
 *
 * <p>Снаряжение и артефакты здесь потому, что окно артефактов их меняет: вынутый
 * артефакт обязан перестать считаться при том же нажатии, а не через секунду по
 * таймеру.
 *
 * <p>Одна передача вместо восьми полей в каждом экране. Интерфейс сам ничего не
 * решает: он спрашивает те же сервисы, что и команды, и показывает их ответы.
 * Поэтому экран не может разойтись с правилами — других правил у него нет.
 */
public record MenuContext(ClassRegistry classes, SkillRegistry skills, StatRegistry stats,
                          ClassService playerClasses, CastService casts,
                          StatService statValues, StatusService statuses,
                          ru.projectst.rpgcore.platform.ArtifactSlots artifacts,
                          ru.projectst.rpgcore.platform.RpgItems items,
                          ru.projectst.rpgcore.platform.EquipmentWatcher equipment) {
}
