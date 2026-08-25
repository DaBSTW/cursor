package dev.bookreports.service;

import java.util.Objects;
import java.util.UUID;

/**
 * Everything {@link ReportService#submitReport} needs, bundled since it is more than a handful of scalars.
 *
 * <p>
 * {@code chatContext} is the target's recently captured chat lines (nullable — empty for callers, such as
 * {@code BookReportsAPI}, that never went through the book flow's chat tracker). {@code reporterLocation}/
 * {@code targetLocation} are {@link dev.bookreports.util.LocationCodec}-encoded snapshots (nullable — the caller
 * decides whether it has a live {@code Location} to capture; {@code targetLocation} is naturally absent for an offline
 * target).
 */
public record SubmitReportRequest(UUID reporterUuid, String reporterName, UUID targetUuid, String targetName,
        String categoryId, String subReasonId, String evidenceText, String server, String chatContext,
        String reporterLocation, String targetLocation) {

    public SubmitReportRequest {
        Objects.requireNonNull(reporterUuid, "reporterUuid");
        Objects.requireNonNull(reporterName, "reporterName");
        Objects.requireNonNull(targetUuid, "targetUuid");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(server, "server");
    }
}
