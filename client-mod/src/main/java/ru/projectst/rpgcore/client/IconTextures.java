package ru.projectst.rpgcore.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Рисованные значки из ресурсов мода.
 *
 * <p>Картинки лежат в самом jar мода ({@code assets/rpgcore/textures/gui}), и
 * игра грузит их сама: ресурспак игроку для этого не нужен. Значок берётся по
 * имени файла — навыка или статуса, — поэтому любую картинку можно заменить
 * нарисованной руками, не трогая код. Своих картинок у значка нет — рисуется
 * прежний пиксель-арт из кода: новый навык в контенте не должен требовать
 * художника, чтобы его было видно.
 *
 * <p>Есть файл или нет, спрашивается у игры один раз на имя: опрос ресурсов
 * каждый кадр для каждого значка стоил бы больше, чем сам рисунок.
 */
public final class IconTextures {

    /** Сторона картинки навыка: 32, рисунок в ромбе. */
    public static final int SKILL = 32;
    /** Сторона картинки статуса: тоже 32 и тоже ромб. */
    public static final int STATUS = 32;

    private static final Map<String, ResourceLocation> FOUND = new HashMap<>();
    private static final Map<String, Boolean> KNOWN = new HashMap<>();

    private IconTextures() {
    }

    /** Картинка значка или {@code null}, если её нет. */
    public static ResourceLocation find(String folder, String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        String key = folder + "/" + id;
        Boolean known = KNOWN.get(key);
        if (known == null) {
            ResourceLocation location = ResourceLocation.tryBuild(RpgCoreClient.MOD_ID,
                    "textures/gui/" + folder + "/" + id + ".png");
            known = location != null
                    && Minecraft.getInstance().getResourceManager().getResource(location).isPresent();
            KNOWN.put(key, known);
            if (known) {
                FOUND.put(key, location);
            }
        }
        return known ? FOUND.get(key) : null;
    }

    /** Рисует картинку целиком в квадрат со стороной {@code size}. */
    public static void draw(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size,
                            int textureSize) {
        RenderSystem.enableBlend();
        graphics.blit(texture, x, y, size, size, 0, 0, textureSize, textureSize, textureSize,
                textureSize);
        RenderSystem.disableBlend();
    }
}
