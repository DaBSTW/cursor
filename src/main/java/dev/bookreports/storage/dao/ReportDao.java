package dev.bookreports.storage.dao;

import dev.bookreports.storage.model.Report;
import dev.bookreports.storage.model.ReportStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportDao {

    /** Persists a draft report (its {@code id} is ignored) and returns the row as stored, with a real id. */
    Report insert(Report report);

    Optional<Report> findByUuid(UUID uuid);

    Optional<Report> findById(long id);

    /** Most recent first. */
    List<Report> findByTarget(UUID targetUuid);

    /** Zero-indexed page, ordered by priority then age so the staff queue surfaces the most urgent first. */
    List<Report> findByStatus(ReportStatus status, int page, int pageSize);

    int countByReporterSince(UUID reporterUuid, Instant since);

    /** Returns {@code false} if no row matched {@code id} — the caller decides whether that is an error. */
    boolean updateStatus(long id, ReportStatus status, UUID reviewerUuid, String resolutionNote);

    /** Atomically assigns a reviewer. Returns {@code false} if the report was already claimed by someone else. */
    boolean claim(long id, UUID reviewerUuid);
}
