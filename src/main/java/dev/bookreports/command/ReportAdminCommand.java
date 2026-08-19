package dev.bookreports.command;

import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.LocaleManager;
import dev.bookreports.gui.ReportDetailView;
import dev.bookreports.gui.ReportQueueView;
import dev.bookreports.integration.punishment.PunishmentBridge;
import dev.bookreports.service.ReportService;
import dev.bookreports.storage.dao.ReportDao;
import dev.bookreports.storage.model.Report;
import dev.bookreports.storage.model.ReportStatus;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** {@code /reportadmin list|view|claim|resolve|history|notifications} — works from a player's GUI or the console. */
public final class ReportAdminCommand implements CommandExecutor {

    private final Plugin plugin;
    private final LocaleManager locale;
    private final ReportService reportService;
    private final ReportDao reportDao;
    private final Supplier<BookReportsConfig> config;
    private final StaffNotificationService notifications;
    private final Optional<PunishmentBridge> punishmentBridge;
    private final Executor executor;

    public ReportAdminCommand(Plugin plugin, LocaleManager locale, ReportService reportService, ReportDao reportDao,
            Supplier<BookReportsConfig> config, StaffNotificationService notifications,
            Optional<PunishmentBridge> punishmentBridge, Executor executor) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.reportService = Objects.requireNonNull(reportService, "reportService");
        this.reportDao = Objects.requireNonNull(reportDao, "reportDao");
        this.config = Objects.requireNonNull(config, "config");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.punishmentBridge = Objects.requireNonNull(punishmentBridge, "punishmentBridge");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || "list".equalsIgnoreCase(args[0])) {
            list(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "view" -> view(sender, args);
            case "teleport" -> teleport(sender, args);
            case "claim" -> claim(sender, args);
            case "resolve" -> resolve(sender, args);
            case "history" -> history(sender, args);
            case "notifications" -> notificationsToggle(sender, args);
            default -> sender.sendMessage(locale.get("command.usage",
                    Map.of("usage", "/reportadmin <list|view|claim|resolve|history|notifications>")));
        }
        return true;
    }

    private void list(CommandSender sender) {
        if (sender instanceof Player player) {
            openQueue(player);
            return;
        }
        reportService.getQueue(ReportStatus.PENDING, 0, 20).whenComplete((reports, error) -> {
            if (error != null) {
                sender.sendMessage(locale.get("error.generic"));
                return;
            }
            for (Report report : reports) {
                sender.sendMessage(summaryLine(report));
            }
        });
    }

    private void openQueue(Player player) {
        ReportQueueView[] queueRef = new ReportQueueView[1];
        ReportQueueView queue = new ReportQueueView(plugin, player, reportDao, config, locale, report -> {
            ReportDetailView detail = new ReportDetailView(plugin, player, locale, reportService, punishmentBridge,
                    config, report, () -> queueRef[0].open(0));
            detail.open();
        });
        queueRef[0] = queue;
        queue.open(0);
    }

    private void view(CommandSender sender, String[] args) {
        parseId(sender, args).ifPresent(id -> reportService.getReportById(id).whenComplete((found, error) -> {
            if (error != null || found.isEmpty()) {
                sender.sendMessage(locale.get("error.generic"));
                return;
            }
            sender.sendMessage(summaryLine(found.get()));
        }));
    }

    private void teleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(locale.get("command.player-only"));
            return;
        }
        parseId(sender, args).ifPresent(id -> reportService.getReportById(id).whenComplete((found, error) -> {
            if (error != null || found.isEmpty()) {
                sender.sendMessage(locale.get("error.generic"));
                return;
            }
            Player target = Bukkit.getPlayer(found.get().targetUuid());
            if (target == null) {
                sender.sendMessage(locale.get("report.target-offline"));
                return;
            }
            // This callback runs on the report service's worker executor — teleporting must happen on the main thread.
            Bukkit.getScheduler().runTask(plugin, () -> player.teleport(target.getLocation()));
        }));
    }

    private void claim(CommandSender sender, String[] args) {
        UUID reviewer = reviewerUuid(sender);
        parseId(sender, args).ifPresent(id -> reportService.claim(id, reviewer).whenComplete((claimed, error) -> {
            if (error != null || !Boolean.TRUE.equals(claimed)) {
                sender.sendMessage(locale.get("staff.claim.already-claimed", Map.of("reviewer", "?")));
                return;
            }
            sender.sendMessage(locale.get("staff.claim.success", Map.of("ticket_id", args[1])));
        }));
    }

    private void resolve(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bookreports.staff.resolve")) {
            sender.sendMessage(locale.get("command.no-permission"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(locale.get("command.usage",
                    Map.of("usage", "/reportadmin resolve <id> <action|reject|duplicate|false>")));
            return;
        }
        UUID reviewer = reviewerUuid(sender);
        parseId(sender, args).ifPresent(id -> dispatchResolve(sender, id, args[2].toLowerCase(Locale.ROOT), reviewer));
    }

    private void dispatchResolve(CommandSender sender, long id, String action, UUID reviewer) {
        switch (action) {
            case "action" -> completeResolve(sender, id,
                    reportService.resolve(id, ReportStatus.RESOLVED_ACTION, reviewer, "EXTERNAL_ACTION_APPLIED"));
            case "reject" -> completeResolve(sender, id,
                    reportService.resolve(id, ReportStatus.RESOLVED_REJECTED, reviewer, "NO_EVIDENCE"));
            case "duplicate" -> completeResolve(sender, id,
                    reportService.resolve(id, ReportStatus.RESOLVED_DUPLICATE, reviewer, "DUPLICATE"));
            case "false" -> completeResolve(sender, id, reportService.markFalse(id, reviewer, "REVIEWED_FALSE"));
            default ->
                sender.sendMessage(locale.get("command.usage", Map.of("usage", "action|reject|duplicate|false")));
        }
    }

    private void completeResolve(CommandSender sender, long id, CompletableFuture<Boolean> future) {
        future.whenComplete((updated, error) -> {
            if (error != null || !Boolean.TRUE.equals(updated)) {
                sender.sendMessage(locale.get("staff.resolve.already-resolved"));
                return;
            }
            sender.sendMessage(
                    locale.get("staff.resolve.success", Map.of("ticket_id", String.valueOf(id), "resolution", "OK")));
        });
    }

    private void history(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(locale.get("command.usage", Map.of("usage", "/reportadmin history <player>")));
            return;
        }
        String name = args[1];
        executor.execute(() -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(name);
            reportService.getReportHistory(target.getUniqueId()).whenComplete((history, error) -> {
                if (error != null) {
                    sender.sendMessage(locale.get("error.generic"));
                    return;
                }
                sender.sendMessage(locale.get("staff.history.title", Map.of("player", name)));
                if (history.isEmpty()) {
                    sender.sendMessage(locale.get("staff.history.empty"));
                }
                for (Report report : history) {
                    sender.sendMessage(summaryLine(report));
                }
            });
        });
    }

    private void notificationsToggle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length < 2 || !"toggle".equalsIgnoreCase(args[1])) {
            sender.sendMessage(locale.get("command.usage", Map.of("usage", "/reportadmin notifications toggle")));
            return;
        }
        if (!player.hasPermission("bookreports.staff.notify")) {
            sender.sendMessage(locale.get("command.no-permission"));
            return;
        }
        notifications.toggle(player);
    }

    private Component summaryLine(Report report) {
        return Component.text("#" + report.id() + " " + report.targetName() + " — " + report.categoryId() + " ["
                + report.priority() + "] " + report.status());
    }

    private Optional<Long> parseId(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(locale.get("command.usage", Map.of("usage", "/reportadmin " + args[0] + " <id>")));
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(args[1]));
        } catch (NumberFormatException e) {
            sender.sendMessage(locale.get("command.usage", Map.of("usage", "<id> must be a number")));
            return Optional.empty();
        }
    }

    private UUID reviewerUuid(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getUniqueId();
        }
        // Console can still claim/resolve on behalf of "the server" — SPECS.md §5.1 calls this out
        // explicitly for scripting, so a nil UUID stands in for a non-player reviewer.
        return new UUID(0, 0);
    }
}
