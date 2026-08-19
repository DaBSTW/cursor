package dev.bookreports.api;

import dev.bookreports.storage.model.Report;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public, stable entry point for other plugins. Obtain it via
 * {@code Bukkit.getServicesManager().load(BookReportsAPI.class)}.
 */
public interface BookReportsAPI {

    /**
     * Submits a report on behalf of the given reporter.
     *
     * <p>
     * Safe to call from any thread. The returned future completes on an internal pool — dispatch back through the
     * scheduler before touching the Bukkit API.
     *
     * @return a future completing exceptionally with {@link dev.bookreports.service.ReportRejectedException} if a
     *         cooldown, daily limit, duplicate check or a cancelled {@link dev.bookreports.api.event.ReportCreateEvent}
     *         rejects the report
     */
    CompletableFuture<Report> submitReport(UUID reporter, UUID target, String categoryId, String subReasonId,
            String evidence);

    /** Safe to call from any thread; completes on an internal pool. Most recent first. */
    CompletableFuture<List<Report>> getReportHistory(UUID target);

    /** Safe to call from any thread; completes on an internal pool. */
    CompletableFuture<Optional<Report>> getReport(UUID reportUuid);

    /** Non-blocking, in-memory check. Safe to call from any thread, including the main thread. */
    boolean isOnCooldown(UUID reporter);
}
