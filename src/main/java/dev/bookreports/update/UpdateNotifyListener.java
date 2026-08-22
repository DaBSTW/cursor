package dev.bookreports.update;

import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.LocaleManager;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Tells an op on join that a newer BookReports version exists — opt-out via {@code update-checker.notify-ops-on-join}.
 */
public final class UpdateNotifyListener implements Listener {

    private final UpdateChecker checker;
    private final LocaleManager locale;
    private final Supplier<BookReportsConfig> config;

    public UpdateNotifyListener(UpdateChecker checker, LocaleManager locale, Supplier<BookReportsConfig> config) {
        this.checker = Objects.requireNonNull(checker, "checker");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.config = Objects.requireNonNull(config, "config");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!config.get().updateChecker().notifyOpsOnJoin() || !checker.updateAvailable()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("bookreports.admin")) {
            return;
        }
        player.sendMessage(locale.get("update.notify", Map.of("latest", checker.latestKnownVersion().orElse("?"),
                "current", checker.currentVersion(), "url", checker.releasesPageUrl())));
    }
}
