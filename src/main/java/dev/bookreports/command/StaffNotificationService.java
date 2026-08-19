package dev.bookreports.command;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.bookreports.api.event.ReportCreatedEvent;
import dev.bookreports.api.event.ReportResolvedEvent;
import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.LocaleManager;
import dev.bookreports.storage.dao.StaffPrefsDao;
import dev.bookreports.storage.model.Priority;
import dev.bookreports.storage.model.Report;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Live in-game alerts (SPECS.md §5.4): staff with {@code bookreports.staff.notify} get pinged on a new {@code HIGH}
 * priority report, and the original reporter gets a generic outcome message once it's resolved.
 *
 * <p>
 * The per-player toggle is cached in memory (warmed on join, dropped on quit) so an event firing on the main thread
 * never blocks on a database read — see CODESTYLE.md §7.
 */
public final class StaffNotificationService implements Listener {

    private final Supplier<BookReportsConfig> config;
    private final LocaleManager locale;
    private final StaffPrefsDao staffPrefsDao;
    private final Executor executor;
    private final Logger logger;
    private final Cache<UUID, Boolean> notificationsEnabled = Caffeine.newBuilder().maximumSize(10_000)
            .expireAfterAccess(Duration.ofHours(2)).build();

    public StaffNotificationService(Supplier<BookReportsConfig> config, LocaleManager locale,
            StaffPrefsDao staffPrefsDao, Executor executor, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.staffPrefsDao = Objects.requireNonNull(staffPrefsDao, "staffPrefsDao");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        executor.execute(() -> {
            boolean enabled = staffPrefsDao.notificationsEnabled(id);
            notificationsEnabled.put(id, enabled);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        notificationsEnabled.invalidate(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onReportCreated(ReportCreatedEvent event) {
        Report report = event.report();
        if (report.priority() != Priority.HIGH) {
            return;
        }
        Map<String, String> placeholders = Map.of("player", report.targetName(), "category", report.categoryId());
        Component alert = locale.get("staff.notify.high-priority", placeholders).append(Component.space())
                .append(locale.get("staff.notify.view")
                        .clickEvent(ClickEvent.runCommand("/reportadmin view " + report.id())))
                .append(Component.space()).append(locale.get("staff.notify.teleport")
                        .clickEvent(ClickEvent.runCommand("/reportadmin teleport " + report.id())));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("bookreports.staff.notify") && isEnabledFor(staff.getUniqueId())) {
                staff.sendMessage(alert);
                staff.sendActionBar(locale.get("staff.notify.high-priority", placeholders));
                playAlertSound(staff);
            }
        }
    }

    @EventHandler
    public void onReportResolved(ReportResolvedEvent event) {
        Player reporter = Bukkit.getPlayer(event.report().reporterUuid());
        if (reporter != null) {
            reporter.sendMessage(
                    locale.get("report.resolved", Map.of("ticket_id", String.valueOf(event.report().id()))));
        }
    }

    /** Toggles the calling player's preference, updating the cache immediately and the DB asynchronously. */
    public void toggle(Player player) {
        UUID id = player.getUniqueId();
        boolean newValue = !isEnabledFor(id);
        notificationsEnabled.put(id, newValue);
        executor.execute(() -> staffPrefsDao.setNotificationsEnabled(id, newValue));
        player.sendMessage(locale.get(newValue ? "staff.notifications.enabled" : "staff.notifications.disabled"));
    }

    private boolean isEnabledFor(UUID playerId) {
        Boolean cached = notificationsEnabled.getIfPresent(playerId);
        return cached == null || cached;
    }

    private void playAlertSound(Player staff) {
        try {
            staff.playSound(staff.getLocation(), Sound.valueOf(config.get().staff().alertSound()), 1f, 1f);
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Invalid staff.alert-sound in config.yml: " + config.get().staff().alertSound(),
                    e);
        }
    }
}
