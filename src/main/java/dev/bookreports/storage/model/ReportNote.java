package dev.bookreports.storage.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A free-text note a staff member left on a report — independent of the report's resolution note, and never
 * overwritten: unlike {@code resolutionNote} (one per report, set once at resolution time), a report can carry any
 * number of notes added over its whole lifetime, e.g. "still watching this player" while it's still open.
 */
public record ReportNote(long id, long reportId, UUID authorUuid, String authorName, String noteText,
        Instant createdAt) {

    public ReportNote {
        Objects.requireNonNull(authorUuid, "authorUuid");
        Objects.requireNonNull(authorName, "authorName");
        Objects.requireNonNull(noteText, "noteText");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
