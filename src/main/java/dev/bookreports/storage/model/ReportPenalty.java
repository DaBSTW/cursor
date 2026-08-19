package dev.bookreports.storage.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** An anti-abuse penalty applied to a player, e.g. a false-report strike. {@code expiresAt} is nullable. */
public record ReportPenalty(long id, UUID playerUuid, String reason, Instant appliedAt, Instant expiresAt) {

    public ReportPenalty {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(appliedAt, "appliedAt");
    }
}
