package dev.bookreports.book;

import dev.bookreports.book.pages.CategoryPage;
import dev.bookreports.book.pages.EvidencePage;
import dev.bookreports.book.pages.ResultPage;
import dev.bookreports.book.pages.SelectTargetPage;
import dev.bookreports.book.pages.SubReasonPage;
import dev.bookreports.book.pages.SummaryPage;
import dev.bookreports.book.pages.TargetConfirmPage;
import dev.bookreports.config.LocaleManager;
import dev.bookreports.config.ReportCategory;
import dev.bookreports.session.ReportSession;
import dev.bookreports.storage.model.Report;
import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.entity.Player;

/**
 * Sends the report book's pages. Every method builds a fresh {@link net.kyori.adventure.inventory.Book} and opens it.
 */
public final class BookBuilder {

    private final LocaleManager locale;

    public BookBuilder(LocaleManager locale) {
        this.locale = Objects.requireNonNull(locale, "locale");
    }

    public void openSelectTarget(Player viewer, Collection<? extends Player> candidates) {
        viewer.openBook(SelectTargetPage.build(candidates, locale));
    }

    public void openTargetConfirm(Player viewer, ReportSession session, String targetName) {
        viewer.openBook(TargetConfirmPage.build(session, targetName, locale));
    }

    public void openCategory(Player viewer, ReportSession session, Collection<ReportCategory> categories) {
        viewer.openBook(CategoryPage.build(session, categories, locale));
    }

    public void openSubReason(Player viewer, ReportSession session, ReportCategory category) {
        viewer.openBook(SubReasonPage.build(session, category, locale));
    }

    public void openEvidence(Player viewer, ReportSession session) {
        viewer.openBook(EvidencePage.build(session, locale));
    }

    public void openSummary(Player viewer, ReportSession session, ReportCategory category, String targetName,
            Optional<Duration> cooldownRemaining) {
        viewer.openBook(SummaryPage.build(session, category, targetName, cooldownRemaining, locale));
    }

    public void openResult(Player viewer, Report report) {
        viewer.openBook(ResultPage.build(report, locale));
    }
}
