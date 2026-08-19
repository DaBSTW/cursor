package dev.bookreports.book.pages;

import dev.bookreports.config.LocaleManager;
import dev.bookreports.storage.model.Report;
import java.util.Map;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;

public final class ResultPage {

    private ResultPage() {
    }

    public static Book build(Report report, LocaleManager locale) {
        Component page = locale.get("book.page.result.title").appendNewline()
                .append(locale.get("book.page.result.ticket", Map.of("ticket_id", String.valueOf(report.id()))));

        return Book.book(locale.get("report.book-title"), Component.text("BookReports"), page);
    }
}
