package dev.bookreports.session;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * In-flight state for one player's report book. {@code targetId} is {@code null} only while
 * {@code state == SELECT_TARGET} (the player hasn't picked whom to report yet); {@code categoryId}, {@code subReasonId}
 * and {@code evidenceText} are filled in as the player advances.
 */
public record ReportSession(UUID sessionId, UUID reporterId, UUID targetId, ReportState state, String categoryId,
        String subReasonId, String evidenceText, Instant createdAt) {

    public ReportSession {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(reporterId, "reporterId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        if (targetId == null && state != ReportState.SELECT_TARGET) {
            throw new IllegalArgumentException("targetId is required once past SELECT_TARGET, state=" + state);
        }
    }

    public ReportSession withTarget(UUID newTargetId, ReportState newState) {
        return new ReportSession(sessionId, reporterId, newTargetId, newState, categoryId, subReasonId, evidenceText,
                createdAt);
    }

    public ReportSession withState(ReportState newState) {
        return new ReportSession(sessionId, reporterId, targetId, newState, categoryId, subReasonId, evidenceText,
                createdAt);
    }

    public ReportSession withCategory(String newCategoryId, ReportState newState) {
        return new ReportSession(sessionId, reporterId, targetId, newState, newCategoryId, subReasonId, evidenceText,
                createdAt);
    }

    public ReportSession withSubReason(String newSubReasonId, ReportState newState) {
        return new ReportSession(sessionId, reporterId, targetId, newState, categoryId, newSubReasonId, evidenceText,
                createdAt);
    }

    public ReportSession withEvidence(String newEvidenceText, ReportState newState) {
        return new ReportSession(sessionId, reporterId, targetId, newState, categoryId, subReasonId, newEvidenceText,
                createdAt);
    }
}
