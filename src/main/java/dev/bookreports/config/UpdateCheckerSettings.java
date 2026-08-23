package dev.bookreports.config;

/**
 * Where and how often to check for a newer BookReports release. {@code resource} is a source-specific identifier: an
 * {@code "owner/repo"} for {@link UpdateSource#GITHUB}, a project slug or id for {@link UpdateSource#MODRINTH}.
 *
 * <p>
 * {@code lockWhenOutdated} is an opt-in, off-by-default kill switch: while a newer version is known to exist, the
 * player-facing report flow ({@code /report}, the report-tool item) refuses everyone without {@code bookreports.admin},
 * showing a plain "not available" message instead — {@code bookreports.admin} holders are unaffected and get the update
 * notice with a one-click updater. Defaults to {@code false} because most servers should not have their reporting
 * pipeline go dark just because a checker noticed a new tag.
 */
public record UpdateCheckerSettings(boolean enabled, UpdateSource source, String resource, int checkIntervalHours,
        boolean notifyOpsOnJoin, boolean lockWhenOutdated) {
}
