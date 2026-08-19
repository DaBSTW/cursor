package dev.bookreports.book.pages;

import dev.bookreports.config.LocaleManager;
import dev.bookreports.session.ReportSession;
import dev.bookreports.util.ComponentUtil;
import java.util.Map;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;

public final class TargetConfirmPage {

    private TargetConfirmPage() {
    }

    public static Book build(ReportSession session, String targetName, LocaleManager locale) {
        Component page = locale.get("book.page.target-confirm.title").appendNewline()
                .append(locale.get("book.page.target-confirm.subtitle", Map.of("player", targetName))).appendNewline()
                .appendNewline()
                .append(ComponentUtil.clickable(locale.get("book.page.target-confirm.continue"),
                        ComponentUtil.selectCommand(session.sessionId(), "confirm-target")))
                .appendNewline().append(ComponentUtil.clickable(locale.get("book.page.target-confirm.cancel"),
                        ComponentUtil.selectCommand(session.sessionId(), "cancel")));

        return Book.book(locale.get("report.book-title"), Component.text("BookReports"), page);
    }
}
