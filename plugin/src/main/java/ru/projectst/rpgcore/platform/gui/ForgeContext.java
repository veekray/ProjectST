package ru.projectst.rpgcore.platform.gui;

import ru.projectst.rpgcore.platform.ContentService;
import ru.projectst.rpgcore.platform.EquipmentWatcher;
import ru.projectst.rpgcore.platform.ItemForge;
import ru.projectst.rpgcore.platform.RecipeRegistrar;
import ru.projectst.rpgcore.platform.RpgItems;

/**
 * Всё, что нужно верстаку.
 *
 * <p>Отдельно от {@link MenuContext} намеренно. Тот — про показ посчитанного:
 * реестры в нём взяты один раз при запуске, и этого хватает, потому что показ
 * ничего не меняет. Верстак контент <b>правит</b> и поэтому обязан спрашивать
 * {@link ContentService} каждый раз: после сохранения реестры заменяются
 * целиком, и экран со старым реестром показывал бы предмет, которого в игре
 * уже нет — или не показывал бы тот, что есть.
 */
public record ForgeContext(ItemForge forge, ContentService content, RpgItems items,
                           EquipmentWatcher equipment, RecipeRegistrar recipes,
                           ChatPrompt prompt) {
}
