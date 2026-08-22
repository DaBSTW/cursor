package dev.bookreports.update;

import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.UpdateCheckerSettings;
import dev.bookreports.config.UpdateSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks GitHub Releases or a Modrinth project for a newer BookReports version (SPECS.md §11-style optional
 * integration). One HTTP GET per check, bounded by {@link #TIMEOUT}, never retried within a single check — same
 * one-shot, fire-and-log philosophy as {@code DiscordNotifier}. A failed or disabled check never throws; it simply
 * completes with an empty result.
 */
public final class UpdateChecker {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern GITHUB_TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MODRINTH_VERSION = Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");

    private final Supplier<BookReportsConfig> config;
    private final String currentVersion;
    private final HttpClient httpClient;
    private final Logger logger;
    private volatile String latestKnownVersion;

    public UpdateChecker(Supplier<BookReportsConfig> config, String currentVersion, Executor executor, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.currentVersion = Objects.requireNonNull(currentVersion, "currentVersion");
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).executor(executor).build();
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Safe to call from any thread; never blocks and never throws. Logs a console line when a newer version is found —
     * the result is also cached for {@link #updateAvailable()} to answer without hitting the network again.
     */
    public CompletableFuture<Optional<String>> checkNow() {
        UpdateCheckerSettings settings = config.get().updateChecker();
        if (!settings.enabled() || settings.resource() == null || settings.resource().isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(apiUrl(settings))).timeout(TIMEOUT)
                    .header("User-Agent", "BookReports-UpdateChecker (+https://github.com/DaBSTW/cursor)").GET()
                    .build();
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Invalid update-checker.resource in config.yml", e);
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200
                        ? extractVersion(settings.source(), response.body())
                        : Optional.<String>empty())
                .exceptionally(error -> {
                    logger.log(Level.FINE, "Update check failed, will retry on the next scheduled check", error);
                    return Optional.empty();
                }).thenApply(latest -> onChecked(latest, settings));
    }

    private Optional<String> onChecked(Optional<String> latest, UpdateCheckerSettings settings) {
        latest.ifPresent(version -> {
            latestKnownVersion = version;
            if (VersionComparator.isNewer(version, currentVersion)) {
                logger.info("A new BookReports version is available: " + version + " (running " + currentVersion
                        + "). Get it at " + releasesPageUrl(settings));
            }
        });
        return latest;
    }

    /** Cheap, in-memory — safe to call every player join without hitting the network. */
    public boolean updateAvailable() {
        String latest = latestKnownVersion;
        return latest != null && VersionComparator.isNewer(latest, currentVersion);
    }

    public Optional<String> latestKnownVersion() {
        return Optional.ofNullable(latestKnownVersion);
    }

    public String currentVersion() {
        return currentVersion;
    }

    public String releasesPageUrl() {
        return releasesPageUrl(config.get().updateChecker());
    }

    private static String apiUrl(UpdateCheckerSettings settings) {
        return switch (settings.source()) {
            case GITHUB -> "https://api.github.com/repos/" + settings.resource() + "/releases/latest";
            case MODRINTH -> "https://api.modrinth.com/v2/project/" + settings.resource() + "/version";
        };
    }

    private static String releasesPageUrl(UpdateCheckerSettings settings) {
        return switch (settings.source()) {
            case GITHUB -> "https://github.com/" + settings.resource() + "/releases/latest";
            case MODRINTH -> "https://modrinth.com/plugin/" + settings.resource() + "/versions";
        };
    }

    /**
     * Deliberately not a full JSON parser: both providers' "latest version" endpoints have the version as a single,
     * stable top-level string field — GitHub's release object has {@code tag_name}; Modrinth's version-list endpoint
     * returns entries newest-first, so the first {@code version_number} match is the latest. A targeted regex avoids
     * pulling in a JSON dependency for one field, at the cost of not being a general-purpose parser.
     */
    static Optional<String> extractVersion(UpdateSource source, String body) {
        Pattern pattern = source == UpdateSource.GITHUB ? GITHUB_TAG : MODRINTH_VERSION;
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
