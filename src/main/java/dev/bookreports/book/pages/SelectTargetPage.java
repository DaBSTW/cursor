package dev.bookreports.book.pages;

import dev.bookreports.config.LocaleManager;
import dev.bookreports.util.ComponentUtil;
import java.util.Collection;
import java.util.Map;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public final class SelectTargetPage {

    // A WRITTEN_BOOK page holds ~256 usable characters; capping the list keeps every page well under that,
    // even for long player names, without needing full multi-page pagination for the online-player list.
    private static final int MAX_ENTRIES = 40;

    private SelectTargetPage() {
    }

    public static Book build(Collection<? extends Player> candidates, LocaleManager locale) {
        Component page = locale.get("book.select-target.title").appendNewline().appendNewline();
        if (candidates.isEmpty()) {
            page = page.append(locale.get("book.select-target.empty"));
        } else {
            int shown = 0;
            for (Player candidate : candidates) {
                if (shown >= MAX_ENTRIES) {
                    break;
                }
                Component entry = locale.get("book.select-target.entry", Map.of("player", candidate.getName()));
                page = page.append(ComponentUtil.clickable(entry, ComponentUtil.targetCommand(candidate.getUniqueId())))
                        .appendNewline();
                shown++;
            }
        }
        return Book.book(locale.get("report.book-title"), Component.text("BookReports"), page);
    }
}
