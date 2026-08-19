package dev.bookreports.book.pages;

import dev.bookreports.config.LocaleManager;
import dev.bookreports.config.ReportCategory;
import dev.bookreports.session.ReportSession;
import dev.bookreports.util.ComponentUtil;
import java.util.Locale;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;

public final class SubReasonPage {

    private SubReasonPage() {
    }

    public static Book build(ReportSession session, ReportCategory category, LocaleManager locale) {
        Component page = locale.get("book.page.subreason.title").appendNewline().appendNewline();
        for (String subReason : category.subReasons()) {
            Component label = Component.text(capitalize(subReason));
            page = page
                    .append(ComponentUtil.clickable(label,
                            ComponentUtil.selectCommand(session.sessionId(), "subreason:" + subReason)))
                    .appendNewline();
        }
        page = page.append(ComponentUtil.clickable(locale.get("book.page.target-confirm.cancel"),
                ComponentUtil.selectCommand(session.sessionId(), "cancel")));

        return Book.book(locale.get("report.book-title"), Component.text("BookReports"), page);
    }

    private static String capitalize(String raw) {
        if (raw.isEmpty()) {
            return raw;
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1).toLowerCase(Locale.ROOT);
    }
}
