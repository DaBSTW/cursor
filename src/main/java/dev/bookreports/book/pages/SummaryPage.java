package dev.bookreports.book.pages;

import dev.bookreports.config.LocaleManager;
import dev.bookreports.config.ReportCategory;
import dev.bookreports.session.ReportSession;
import dev.bookreports.util.ComponentUtil;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class SummaryPage {

    private SummaryPage() {
    }

    public static Book build(ReportSession session, ReportCategory category, String targetName,
            Optional<Duration> cooldownRemaining, LocaleManager locale) {
        Component categoryDisplay = MiniMessage.miniMessage().deserialize(category.display());
        Component page = locale.get("book.page.summary.title").appendNewline()
                .append(locale.get("book.page.summary.target", Map.of("player", targetName))).appendNewline()
                .append(locale.get("book.page.summary.category").append(Component.space()).append(categoryDisplay))
                .appendNewline();

        if (session.subReasonId() != null) {
            page = page.append(locale.get("book.page.summary.subreason", Map.of("sub_reason", session.subReasonId())))
                    .appendNewline();
        }

        String evidence = session.evidenceText() != null ? session.evidenceText() : "-";
        page = page.append(locale.get("book.page.summary.evidence", Map.of("evidence", evidence))).appendNewline()
                .appendNewline();

        if (cooldownRemaining.isPresent()) {
            page = page.append(locale.get("book.page.summary.confirm-disabled",
                    Map.of("cooldown", formatDuration(cooldownRemaining.get()))));
        } else {
            page = page.append(ComponentUtil.clickable(locale.get("book.page.summary.confirm"),
                    ComponentUtil.selectCommand(session.sessionId(), "confirm-submit")));
        }
        page = page.appendNewline().append(ComponentUtil.clickable(locale.get("book.page.summary.cancel"),
                ComponentUtil.selectCommand(session.sessionId(), "cancel")));

        return Book.book(locale.get("report.book-title"), Component.text("BookReports"), page);
    }

    private static String formatDuration(Duration duration) {
        return Math.max(1, duration.toSeconds()) + "s";
    }
}
