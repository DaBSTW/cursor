package dev.bookreports.storage.dao;

import dev.bookreports.storage.model.Priority;
import dev.bookreports.storage.model.Report;
import dev.bookreports.storage.model.ReportStatus;
import dev.bookreports.storage.model.ReporterStats;
import dev.bookreports.storage.model.StaffStats;
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

    /**
     * Same as {@link #findByStatus(ReportStatus, int, int)}, additionally filtered by category when {@code categoryId}
     * is non-null.
     */
    List<Report> findByStatus(ReportStatus status, String categoryId, int page, int pageSize);

    /**
     * Same as {@link #findByStatus(ReportStatus, String, int, int)}, with three more optional filters for the staff
     * queue's search bar: {@code priority} (exact match), {@code targetNameQuery} (case-insensitive substring of the
     * target's name) and {@code claimedBy} (exact reviewer match) — each ignored when {@code null}.
     */
    List<Report> findByStatus(ReportStatus status, String categoryId, Priority priority, String targetNameQuery,
            UUID claimedBy, int page, int pageSize);

    /** This reporter's own tickets, most recent first, capped at {@code limit} — backs {@code /report status}. */
    List<Report> findByReporter(UUID reporterUuid, int limit);

    int countByReporterSince(UUID reporterUuid, Instant since);

    /** Total reports currently at {@code status}, regardless of category — used for the pending-count placeholder. */
    int countByStatus(ReportStatus status);

    /** Returns {@code false} if no row matched {@code id} — the caller decides whether that is an error. */
    boolean updateStatus(long id, ReportStatus status, UUID reviewerUuid, String resolutionNote, Instant resolvedAt);

    /**
     * Records what a punishment bridge actually applied — independent of, and written separately from, the free-text
     * resolution note. {@code sanctionDuration} is nullable (kicks have none). Returns {@code false} if no row matched.
     */
    boolean recordSanction(long id, String sanctionType, String sanctionDuration);

    /** Atomically assigns a reviewer. Returns {@code false} if the report was already claimed by someone else. */
    boolean claim(long id, UUID reviewerUuid, Instant claimedAt);

    /** {@code IN_REVIEW} reports whose claim is older than {@code claimedBefore} — candidates for auto-release. */
    List<Report> findStaleClaims(Instant claimedBefore);

    /**
     * Atomically releases a claim back to {@code PENDING}. Returns {@code false} if the report was resolved or
     * re-claimed by someone else in the meantime.
     */
    boolean releaseClaim(long id, UUID reviewerUuid);

    /** This reporter's overall track record — see {@link ReporterStats} for how accuracy is defined. */
    ReporterStats reporterStats(UUID reporterUuid);

    /** How many reports this reviewer has resolved, and their average claim-to-resolution time. */
    StaffStats staffStats(UUID reviewerUuid);

    /**
     * Sets or clears the archived flag — an archived report drops out of every {@code findByStatus} queue view but
     * stays reachable via {@link #findById}/{@link #findByUuid}, and its notes/sanction history are untouched. Returns
     * {@code false} if no row matched {@code id}.
     */
    boolean setArchived(long id, boolean archived);

    /**
     * Permanently deletes a report and its notes. Irreversible — {@link #setArchived} is almost always what staff
     * actually want; this is for the rare case of genuinely removing a mistaken or abusive entry. Returns {@code false}
     * if no row matched {@code id}.
     */
    boolean purge(long id);
}
