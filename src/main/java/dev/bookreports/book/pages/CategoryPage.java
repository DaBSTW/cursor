package dev.bookreports.book.pages;

import dev.bookreports.config.LocaleManager;
import dev.bookreports.config.ReportCategory;
import dev.bookreports.session.ReportSession;
import dev.bookreports.util.ComponentUtil;
import java.util.Collection;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class CategoryPage {

    private CategoryPage() {
    }

    public static Book build(ReportSession session, Collection<ReportCategory> categories, LocaleManager locale) {
        Component page = locale.get("book.page.category.title").appendNewline().appendNewline();
        for (ReportCategory category : categories) {
            Component display = MiniMessage.miniMessage().deserialize(category.display());
            page = page
                    .append(ComponentUtil.clickable(display,
                            ComponentUtil.selectCommand(session.sessionId(), "category:" + category.id())))
                    .appendNewline();
        }
        page = page.append(ComponentUtil.clickable(locale.get("book.page.target-confirm.cancel"),
                ComponentUtil.selectCommand(session.sessionId(), "cancel")));

        return Book.book(locale.get("report.book-title"), Component.text("BookReports"), page);
    }
}
