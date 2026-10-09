package ru.projectst.rpgcore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Звуки мода.
 *
 * <p>События лежат в {@code assets/rpgcore/sounds.json} и собраны из ванильных
 * звуковых событий: свои {@code .ogg} не синтезируются и не выдумываются — их
 * нет, и честнее собрать звук из настоящих, чем сделать фальшивый. Играются по
 * имени: менеджер звука ищет событие в {@code sounds.json} сам, поэтому
 * регистрировать {@code SoundEvent} не нужно — и записей в реестре нет.
 *
 * <p>Игрок с модом слышит звук сцены, а ванильный звук того же действия сервер
 * ему не шлёт: два звука одного удара — тот же дубль, что и две картинки.
 */
final class FxSounds {

    private static final RandomSource RANDOM = RandomSource.create();

    private FxSounds() {
    }

    /** Звук мода в точке мира. Неизвестное событие менеджер звука молча пропустит. */
    static void play(String event, double x, double y, double z, float volume, float pitch) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || event == null || event.isEmpty()) {
            return;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(RpgCoreClient.MOD_ID, event);
        client.getSoundManager().play(new SimpleSoundInstance(id, SoundSource.PLAYERS, volume,
                pitch, RANDOM, false, 0, SoundInstance.Attenuation.LINEAR, x, y, z, false));
    }
}
