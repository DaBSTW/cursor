package dev.bookreports.command;

import dev.bookreports.book.BookBuilder;
import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.LocaleManager;
import dev.bookreports.session.ReportSession;
import dev.bookreports.session.SessionManager;
import dev.bookreports.update.UpdateChecker;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Right-clicking a player with the report-tool item (SPECS.md §4.6) is equivalent to {@code /report <player>}. */
public final class ReportToolListener implements Listener {

    private final Supplier<BookReportsConfig> config;
    private final SessionManager sessions;
    private final LocaleManager locale;
    private final BookBuilder books;
    private final RateLimiter rateLimiter;
    private final UpdateChecker updateChecker;

    public ReportToolListener(Supplier<BookReportsConfig> config, SessionManager sessions, LocaleManager locale,
            BookBuilder books, RateLimiter rateLimiter, UpdateChecker updateChecker) {
        this.config = Objects.requireNonNull(config, "config");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.books = Objects.requireNonNull(books, "books");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.updateChecker = Objects.requireNonNull(updateChecker, "updateChecker");
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player target)) {
            return;
        }
        Player player = event.getPlayer();
        BookReportsConfig cfg = config.get();
        if (!cfg.enableReportTool() || !ReportToolItems.isReportTool(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);

        // Same lock as /report — see ReportCommand.
        if (updateChecker.serviceLocked() && !player.hasPermission("bookreports.admin")) {
            player.sendMessage(locale.get("report.service-unavailable"));
            return;
        }
        if (!rateLimiter.tryAcquire(player.getUniqueId())) {
            player.sendMessage(locale.get("report.rate-limited"));
            return;
        }
        if (cfg.preventSelfReport() && target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(locale.get("report.self-report-blocked"));
            return;
        }

        ReportSession session = sessions.startWithTarget(player.getUniqueId(), target.getUniqueId());
        books.openTargetConfirm(player, session, target.getName());
    }
}
