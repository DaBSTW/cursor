package dev.bookreports.command.internal;

import dev.bookreports.book.BookBuilder;
import dev.bookreports.config.LocaleManager;
import dev.bookreports.session.ReportSession;
import dev.bookreports.session.SessionManager;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** {@code /breport:target <uuid>} — starts a new report session from the online-player picker (SPECS.md §4.1). */
public final class SelectTargetCommand implements CommandExecutor, TabCompleter {

    private final SessionManager sessions;
    private final LocaleManager locale;
    private final BookBuilder books;

    public SelectTargetCommand(SessionManager sessions, LocaleManager locale, BookBuilder books) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.books = Objects.requireNonNull(books, "books");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return true;
        }
        UUID targetId;
        try {
            targetId = UUID.fromString(args[0]);
        } catch (IllegalArgumentException e) {
            return true;
        }

        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            player.sendMessage(locale.get("report.target-offline"));
            return true;
        }

        ReportSession session = sessions.startWithTarget(player.getUniqueId(), targetId);
        books.openTargetConfirm(player, session, target.getName());
        return true;
    }

    /** SPECS.md §13: this internal command must not tab-complete — it only ever runs via a book's click link. */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
