package dev.pregen.progress;

import dev.pregen.i18n.Arg;
import dev.pregen.i18n.MessageKey;
import dev.pregen.i18n.MessageService;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/** Sends lifecycle announcements to the console and to online players holding {@code pregen.notify}. */
public final class Notifier {

    public static final String NOTIFY_PERMISSION = "pregen.notify";

    private final Server server;
    private final MessageService messages;

    public Notifier(Server server, MessageService messages) {
        this.server = server;
        this.messages = messages;
    }

    public void broadcast(MessageKey key, Arg... args) {
        console(key, args);
        for (Player player : server.getOnlinePlayers()) {
            if (player.hasPermission(NOTIFY_PERMISSION)) {
                messages.send(player, key, args);
            }
        }
    }

    public void console(MessageKey key, Arg... args) {
        messages.send(server.getConsoleSender(), key, args);
    }
}
