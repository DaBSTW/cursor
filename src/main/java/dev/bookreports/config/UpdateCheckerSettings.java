package dev.bookreports.config;

/**
 * Where and how often to check for a newer BookReports release. {@code resource} is a source-specific identifier: an
 * {@code "owner/repo"} for {@link UpdateSource#GITHUB}, a project slug or id for {@link UpdateSource#MODRINTH}.
 *
 * <p>
 * Whenever a newer version is known to exist, the player-facing report flow ({@code /report}, the report-tool item)
 * refuses everyone without {@code bookreports.admin}, showing a plain "not available" message instead —
 * {@code bookreports.admin} holders are unaffected and get the update notice with a one-click updater. This is
 * unconditional (not configurable): the only way to avoid it is to keep BookReports up to date, or to disable the
 * checker itself via {@code enabled: false}.
 */
public record UpdateCheckerSettings(boolean enabled, UpdateSource source, String resource, int checkIntervalHours,
        boolean notifyOpsOnJoin) {
}
