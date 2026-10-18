package dev.pregen.progress;

import dev.pregen.api.TaskProgress;
import dev.pregen.config.SettingsHolder;
import dev.pregen.i18n.Arg;
import dev.pregen.i18n.MessageKey;
import dev.pregen.i18n.MessageService;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Per-player boss bars showing every task, one bar per world, titled in each viewer's language. Only players who ran
 * {@code /pregen watch} see them; quitting drops them from the watcher set.
 */
public final class BossBarDisplay {

    private final Server server;
    private final MessageService messages;
    private final SettingsHolder settings;
    private final Map<UUID, Map<String, BossBar>> bars = new ConcurrentHashMap<>();

    public BossBarDisplay(Server server, MessageService messages, SettingsHolder settings) {
        this.server = server;
        this.messages = messages;
        this.settings = settings;
    }

    /** Toggles the bars for a player; returns true when they are now shown. */
    public boolean toggle(Player player) {
        Map<String, BossBar> removed = bars.remove(player.getUniqueId());
        if (removed != null) {
            removed.values().forEach(player::hideBossBar);
            return false;
        }
        bars.put(player.getUniqueId(), new ConcurrentHashMap<>());
        return true;
    }

    public void forget(UUID playerId) {
        bars.remove(playerId);
    }

    /** Refreshes every watcher's bars from the given snapshots. Called once per second. */
    public void update(Collection<TaskProgress> progress) {
        Set<String> worlds = new HashSet<>();
        progress.forEach(snapshot -> worlds.add(snapshot.worldName()));
        for (Iterator<Map.Entry<UUID, Map<String, BossBar>>> it = bars.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Map<String, BossBar>> entry = it.next();
            Player player = server.getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            Map<String, BossBar> playerBars = entry.getValue();
            playerBars.entrySet().removeIf(bar -> {
                boolean stale = !worlds.contains(bar.getKey());
                if (stale) {
                    player.hideBossBar(bar.getValue());
                }
                return stale;
            });
            for (TaskProgress snapshot : progress) {
                BossBar bar = playerBars.computeIfAbsent(snapshot.worldName(), world -> createBar());
                bar.name(title(player, snapshot));
                bar.progress((float) Math.max(0.0, Math.min(1.0, snapshot.percent() / 100.0)));
                player.showBossBar(bar);
            }
        }
    }

    /** Hides every bar; called on shutdown. */
    public void clear() {
        bars.forEach((playerId, playerBars) -> {
            Player player = server.getPlayer(playerId);
            if (player != null) {
                playerBars.values().forEach(player::hideBossBar);
            }
        });
        bars.clear();
    }

    private BossBar createBar() {
        return BossBar.bossBar(Component.empty(), 0f, settings.get().progress().bossBarColor(),
                settings.get().progress().bossBarOverlay());
    }

    private Component title(Player player, TaskProgress snapshot) {
        return messages.render(player, MessageKey.BOSSBAR_TITLE, Arg.text("world", snapshot.worldName()),
                Arg.decimal("percent", snapshot.percent()), Arg.decimal("rate", snapshot.chunksPerSecond()),
                Arg.duration("eta", snapshot.etaSeconds()));
    }
}
