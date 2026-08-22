package dev.bookreports.config;

/**
 * Where and how often to check for a newer BookReports release. {@code resource} is a source-specific identifier: an
 * {@code "owner/repo"} for {@link UpdateSource#GITHUB}, a project slug or id for {@link UpdateSource#MODRINTH}.
 */
public record UpdateCheckerSettings(boolean enabled, UpdateSource source, String resource, int checkIntervalHours,
        boolean notifyOpsOnJoin) {
}
