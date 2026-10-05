package ru.projectst.rpgcore.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
import ru.projectst.rpgcore.net.Protocol;

/**
 * Клавиши навыков и меню.
 *
 * <p>Каждый слот — отдельная настраиваемая клавиша в ванильных настройках
 * управления, в своей категории. Игрок меняет их там же, где меняет всё
 * остальное, и конфликт с чужой клавишей Minecraft покажет сам — своего списка
 * привязок у мода нет намеренно: он бы не знал про чужие моды.
 *
 * <p>По умолчанию клавиши <b>не назначены</b>. Это сознательно: любая цифра уже
 * занята хотбаром, любая буква — чьим-нибудь модом, а мод, который молча
 * перехватывает чужую клавишу, выясняется в бою. Игрок назначает их один раз
 * сам, а до тех пор работает серверная раскладка Shift и цифра — она есть и у
 * тех, у кого мода нет вовсе.
 */
@EventBusSubscriber(modid = RpgCoreClient.MOD_ID, value = Dist.CLIENT)
public final class RpgKeys {

    /** Сколько слотов поддерживается клавишами. Столько же, сколько у классов. */
    public static final int SLOTS = 6;

    private static final String CATEGORY = "key.categories.rpgcore";

    private static final KeyMapping[] SLOT_KEYS = new KeyMapping[SLOTS];
    private static KeyMapping menuKey;

    private RpgKeys() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        for (int i = 0; i < SLOTS; i++) {
            SLOT_KEYS[i] = new KeyMapping("key.rpgcore.slot" + (i + 1),
                    InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
            event.register(SLOT_KEYS[i]);
        }
        menuKey = new KeyMapping("key.rpgcore.menu", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K, CATEGORY);
        event.register(menuKey);
    }

    /**
     * Обработка нажатий.
     *
     * <p>Отдельный класс намеренно: регистрация клавиш и их чтение живут на
     * разных шинах событий, и держать их рядом означало бы объяснять это каждому,
     * кто сюда заглянет.
     */
    @EventBusSubscriber(modid = RpgCoreClient.MOD_ID, value = Dist.CLIENT)
    public static final class Handler {

        private Handler() {
        }

        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.screen != null) {
                return;
            }
            for (int i = 0; i < SLOTS; i++) {
                KeyMapping key = SLOT_KEYS[i];
                if (key == null) {
                    continue;
                }
                // consumeClick, а не isDown: зажатая клавиша не должна слать
                // просьбу каждый тик. Перезарядку считает сервер, но сорок
                // отказов в секунду — это сорок строк в строке действия.
                while (key.consumeClick()) {
                    ActionPayload.send(Protocol.Action.CAST_SLOT, i + 1, "");
                }
            }
            if (menuKey != null) {
                while (menuKey.consumeClick()) {
                    ActionPayload.send(Protocol.Action.REFRESH_MENU);
                    client.setScreen(new CharacterScreen());
                }
            }
        }
    }
}
