package ru.projectst.rpgcore.platform.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Ввод строки с клавиатуры: окно закрывается, игрок пишет в чат, окно
 * возвращается.
 *
 * <p>Нужен тому, что из ячеек не выбирается. Материалов в игре больше тысячи, а
 * имя предмета и строка описания — вообще произвольный текст: кнопка «следующий
 * материал» означала бы тысячу нажатий, а наковальня — отдельный экран, который
 * всё равно пришлось бы читать из чата.
 *
 * <p><b>Ответ обрабатывается в основном потоке.</b> Событие чата приходит в
 * своём, а всё остальное — инвентари, реестры, запись файла — живёт в основном.
 * Правка контента из чужого потока даёт поломку, которую потом ищут по стектрейсу
 * без всякой связи с чатом.
 *
 * <p><b>Сообщение не уходит в чат.</b> Игрок отвечает верстаку, а не серверу:
 * «DIAMOND_SWORD» в общем чате выглядит как случайно нажатый Enter.
 *
 * <p>Отмена названа словом и работает всегда: ожидание ввода, из которого нельзя
 * выйти, — это зависший интерфейс.
 */
public final class ChatPrompt implements Listener {

    /** Слово отмены. Одно и то же и в подсказке, и в проверке. */
    public static final String CANCEL = "отмена";

    private record Pending(Consumer<String> answered, Runnable cancelled) {
    }

    private final Plugin plugin;
    private final Map<UUID, Pending> waiting = new ConcurrentHashMap<>();

    public ChatPrompt(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Спрашивает строку.
     *
     * @param question что спросить: пишется игроку как есть
     * @param hint     чем помочь — пример или нынешнее значение; пусто — ничем
     * @param answered что сделать с ответом, в основном потоке
     * @param cancelled что сделать при отмене, в основном потоке
     */
    public void ask(Player player, String question, String hint,
                    Consumer<String> answered, Runnable cancelled) {
        player.closeInventory();
        waiting.put(player.getUniqueId(), new Pending(answered, cancelled));
        player.sendMessage(Component.text(question, NamedTextColor.GOLD));
        if (hint != null && !hint.isBlank()) {
            player.sendMessage(Component.text(hint, NamedTextColor.GRAY));
        }
        player.sendMessage(Component.text("Напишите в чат ответ или «" + CANCEL + "»",
                NamedTextColor.DARK_GRAY));
    }

    /** Ждёт ли этот игрок ввода: по этому видно, что чат перехватывать есть зачем. */
    public boolean waitingFor(UUID player) {
        return waiting.containsKey(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Pending pending = waiting.remove(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        // Сообщение не доходит до чата: это ответ верстаку.
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase(CANCEL)) {
                pending.cancelled().run();
                return;
            }
            pending.answered().accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Ожидание не должно переживать игрока: вернувшийся не помнит вопроса.
        waiting.remove(event.getPlayer().getUniqueId());
    }
}
