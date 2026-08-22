package dev.bookreports.command;

import dev.bookreports.book.BookBuilder;
import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.LocaleManager;
import dev.bookreports.session.ReportSession;
import dev.bookreports.session.SessionManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** {@code /report [player]} and {@code /report tool} — the player-facing entry points into the book flow. */
public final class ReportCommand implements CommandExecutor, TabCompleter {

    private final SessionManager sessions;
    private final Supplier<BookReportsConfig> config;
    private final LocaleManager locale;
    private final BookBuilder books;
    private final RateLimiter rateLimiter;

    public ReportCommand(SessionManager sessions, Supplier<BookReportsConfig> config, LocaleManager locale,
            BookBuilder books, RateLimiter rateLimiter) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.config = Objects.requireNonNull(config, "config");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.books = Objects.requireNonNull(books, "books");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(locale.get("command.player-only"));
            return true;
        }
        if (args.length == 1 && "tool".equalsIgnoreCase(args[0])) {
            giveTool(player);
            return true;
        }
        if (!rateLimiter.tryAcquire(player.getUniqueId())) {
            player.sendMessage(locale.get("report.rate-limited"));
            return true;
        }

        if (args.length == 0) {
            openTargetPicker(player);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(locale.get("report.target-not-found", Map.of("player", args[0])));
            return true;
        }
        beginSession(player, target);
        return true;
    }

    private void openTargetPicker(Player player) {
        List<? extends Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(candidate -> !candidate.getUniqueId().equals(player.getUniqueId()))
                .sorted(Comparator.comparing(Player::getName)).toList();
        books.openSelectTarget(player, candidates);
    }

    private void beginSession(Player player, Player target) {
        BookReportsConfig cfg = config.get();
        if (cfg.preventSelfReport() && target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(locale.get("report.self-report-blocked"));
            return;
        }
        ReportSession session = sessions.startWithTarget(player.getUniqueId(), target.getUniqueId());
        books.openTargetConfirm(player, session, target.getName());
    }

    private void giveTool(Player player) {
        BookReportsConfig cfg = config.get();
        if (!cfg.enableReportTool()) {
            player.sendMessage(locale.get("report.tool-disabled"));
            return;
        }
        if (!player.hasPermission("bookreports.report.tool")) {
            player.sendMessage(locale.get("command.no-permission"));
            return;
        }
        player.getInventory().addItem(ReportToolItems.create(locale));
    }

    /**
     * Suggests online player names (and {@code tool}) for {@code /report <partial>} — matching is still exact on
     * submit.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String partial = args[0].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>(Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> !(sender instanceof Player self) || !name.equalsIgnoreCase(self.getName()))
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial)).sorted(Comparator.naturalOrder())
                .toList());
        if ("tool".startsWith(partial)) {
            suggestions.add("tool");
        }
        return suggestions;
    }
}
