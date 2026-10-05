package ru.projectst.rpgcore.platform;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Требование клиентского мода.
 *
 * <p>Решение владельца проекта: без мода на сервер не пускать. Записано здесь,
 * чтобы было видно и цену этого решения — она в том, что любая поломка канала
 * становится поломкой входа. Поэтому проверка устроена так, чтобы её можно было
 * выключить, не пересобирая плагин, и чтобы она никогда не срабатывала молча.
 *
 * <p><b>Отсрочка обязательна.</b> Рукопожатие приходит не мгновенно: клиент
 * шлёт его после входа в мир, а на слабом соединении это несколько секунд.
 * Выгонять сразу означало бы выгонять половину тех, у кого мод есть.
 *
 * <p>Выключается в {@code config.yml}: {@code require-mod: false}. Это не
 * «настройка на всякий случай», а аварийный выход: если канал перестанет
 * доходить после обновления сервера, администратору нужен способ пустить людей
 * внутрь, не правя код.
 */
public final class ModGate {

    private final Plugin plugin;
    private final ClientLink link;
    private final boolean required;
    private final int graceTicks;

    public ModGate(Plugin plugin, ClientLink link, boolean required, int graceSeconds) {
        this.plugin = plugin;
        this.link = link;
        this.required = required;
        this.graceTicks = Math.max(20, graceSeconds * 20);
    }

    public boolean required() {
        return required;
    }

    /** Ставит проверку на игрока: через отсрочку он либо с модом, либо выходит. */
    public void watch(Player player) {
        if (!required) {
            return;
        }
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(id);
            if (online == null || link.modVersion(id).isPresent()) {
                return;
            }
            if (online.hasPermission("rpgcore.admin")) {
                // Администратора не выгоняем: именно ему чинить канал, если он
                // сломался, и делать это снаружи сервера неудобно.
                online.sendMessage(Component.text(
                        "Клиентский мод не отвечает. Вас пустили как администратора.",
                        NamedTextColor.GOLD));
                return;
            }
            online.kick(Component.text("Для игры нужен клиентский мод RpgCore.",
                            NamedTextColor.RED)
                    .appendNewline()
                    .append(Component.text("Скачайте его и положите в папку mods.",
                            NamedTextColor.GRAY)));
            plugin.getLogger().info("игрок " + online.getName()
                    + " вышел: мод не ответил за " + (graceTicks / 20) + " с");
        }, graceTicks);
    }
}
