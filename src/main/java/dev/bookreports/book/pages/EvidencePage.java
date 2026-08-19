package dev.bookreports.book.pages;

import dev.bookreports.config.LocaleManager;
import dev.bookreports.session.ReportSession;
import dev.bookreports.util.ComponentUtil;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;

public final class EvidencePage {

    private EvidencePage() {
    }

    public static Book build(ReportSession session, LocaleManager locale) {
        Component page = locale.get("book.page.evidence.title").appendNewline().appendNewline()
                .append(ComponentUtil.clickable(locale.get("book.page.evidence.add"),
                        ComponentUtil.selectCommand(session.sessionId(), "evidence-add")))
                .appendNewline()
                .append(ComponentUtil.clickable(locale.get("book.page.evidence.skip"),
                        ComponentUtil.selectCommand(session.sessionId(), "evidence-skip")))
                .appendNewline().append(ComponentUtil.clickable(locale.get("book.page.target-confirm.cancel"),
                        ComponentUtil.selectCommand(session.sessionId(), "cancel")));

        return Book.book(locale.get("report.book-title"), Component.text("BookReports"), page);
    }
}
