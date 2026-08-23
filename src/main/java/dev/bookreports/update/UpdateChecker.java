package dev.bookreports.update;

import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.UpdateCheckerSettings;
import dev.bookreports.config.UpdateSource;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 *
 * <p>
 * Also knows how to download and stage a found update via {@link #downloadAndStageUpdate()} — it writes the new jar
 * into the server's own {@code plugins/update/} folder under the exact same filename as the currently-running jar,
 * which is Bukkit/Paper's own, safe update mechanism: the server swaps it in on its next natural startup. This class
 * never attempts a live, in-process class swap or a forced restart — both are unsafe (the JVM cannot hot-swap a
 * plugin's own already-loaded classes, and a forced restart is host-dependent and disruptive) — "applied automatically"
 * here always means "staged for the next restart."
 */
public final class UpdateChecker {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(30);
    private static final String USER_AGENT = "BookReports-UpdateChecker (+https://github.com/DaBSTW/cursor)";
    private static final Pattern GITHUB_TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MODRINTH_VERSION = Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");
    // Both deliberately match only a .jar URL — a GitHub release commonly also attaches a checksum/source-zip
    // asset, and a Modrinth version can list non-primary files too; the first .jar match is a reasonable stand-in
    // for "the actual plugin download" without needing to parse asset metadata (content-type, "primary" flag, …).
    private static final Pattern GITHUB_ASSET_URL = Pattern
            .compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.jar)\"");
    private static final Pattern MODRINTH_FILE_URL = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+\\.jar)\"");

    private final Supplier<BookReportsConfig> config;
    private final String currentVersion;
    private final File stagedUpdateTarget;
    private final HttpClient httpClient;
    private final Logger logger;
    private volatile String latestKnownVersion;
    private volatile String latestDownloadUrl;

    /**
     * @param stagedUpdateTarget
     *            where a downloaded update is written — must be
     *            {@code <update-folder>/<this-plugin's-own-jar-file-name>} for Bukkit/Paper to pick it up on the next
     *            restart; see {@code BookReportsPlugin} for how that path is built.
     */
    public UpdateChecker(Supplier<BookReportsConfig> config, String currentVersion, File stagedUpdateTarget,
            Executor executor, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.currentVersion = Objects.requireNonNull(currentVersion, "currentVersion");
        this.stagedUpdateTarget = Objects.requireNonNull(stagedUpdateTarget, "stagedUpdateTarget");
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
                    .header("User-Agent", USER_AGENT).GET().build();
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Invalid update-checker.resource in config.yml", e);
            return CompletableFuture.completedFuture(Optional.empty());
        }
        UpdateSource source = settings.source();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() != 200) {
                return Optional.<String>empty();
            }
            String body = response.body();
            Optional<String> version = extractVersion(source, body);
            version.ifPresent(v -> applyCheckResult(v, extractDownloadUrl(source, body).orElse(null)));
            return version;
        }).exceptionally(error -> {
            logger.log(Level.FINE, "Update check failed, will retry on the next scheduled check", error);
            return Optional.empty();
        });
    }

    /**
     * Package-private seam: applies a fetched result exactly like {@link #checkNow()} would, without needing a real
     * network round trip — used both by {@code checkNow()} itself and directly by tests.
     */
    void applyCheckResult(String version, String downloadUrl) {
        latestKnownVersion = version;
        latestDownloadUrl = downloadUrl;
        if (VersionComparator.isNewer(version, currentVersion)) {
            UpdateCheckerSettings settings = config.get().updateChecker();
            logger.info("A new BookReports version is available: " + version + " (running " + currentVersion
                    + "). Get it at " + releasesPageUrl(settings)
                    + " — run '/reportadmin update' as an op to download and stage it automatically.");
        }
    }

    /** Cheap, in-memory — safe to call every player join without hitting the network. */
    public boolean updateAvailable() {
        String latest = latestKnownVersion;
        return latest != null && VersionComparator.isNewer(latest, currentVersion);
    }

    /**
     * {@code true} whenever a newer version is known to exist — the signal {@code ReportCommand}/
     * {@code ReportToolListener} gate the player-facing flow on. Unconditional, not configurable; see
     * {@link UpdateCheckerSettings}. The only way around it is keeping BookReports current, or disabling the checker
     * entirely ({@code update-checker.enabled: false}, which also means {@link #updateAvailable()} can never become
     * {@code true} in the first place).
     */
    public boolean serviceLocked() {
        return updateAvailable();
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

    /**
     * Downloads the latest known version's jar and writes it to {@link #stagedUpdateTarget} — Bukkit/Paper's own
     * {@code plugins/update/} mechanism then swaps it in automatically the next time the server starts. Never touches
     * the currently-running jar (which may be locked by the OS while the server holds it open), never restarts
     * anything, and never throws — a failure is logged and the future completes with {@code false}.
     *
     * @return {@code true} if a newer jar was downloaded and staged; {@code false} if there was nothing to do (no known
     *         update, or the release has no discoverable {@code .jar} download) or the download/write failed.
     */
    public CompletableFuture<Boolean> downloadAndStageUpdate() {
        if (!updateAvailable()) {
            return CompletableFuture.completedFuture(false);
        }
        String downloadUrl = latestDownloadUrl;
        if (downloadUrl == null) {
            logger.warning("Update " + latestKnownVersion + " was found but has no downloadable .jar — nothing to "
                    + "stage. Download it manually from " + releasesPageUrl());
            return CompletableFuture.completedFuture(false);
        }
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(downloadUrl)).timeout(DOWNLOAD_TIMEOUT)
                    .header("User-Agent", USER_AGENT).GET().build();
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Invalid update download URL: " + downloadUrl, e);
            return CompletableFuture.completedFuture(false);
        }
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(this::stage)
                .exceptionally(error -> {
                    logger.log(Level.WARNING, "Update download failed", error);
                    return false;
                });
    }

    private boolean stage(HttpResponse<byte[]> response) {
        if (response.statusCode() != 200 || response.body().length == 0) {
            logger.warning("Update download failed: HTTP " + response.statusCode());
            return false;
        }
        try {
            Path targetDir = stagedUpdateTarget.getParentFile().toPath();
            Files.createDirectories(targetDir);
            Path tempFile = Files.createTempFile(targetDir, "bookreports-update-", ".jar.tmp");
            Files.write(tempFile, response.body());
            moveIntoPlace(tempFile, stagedUpdateTarget.toPath());
            logger.info("Downloaded and staged BookReports " + latestKnownVersion
                    + " — it will be installed automatically the next time the server restarts.");
            return true;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to stage the downloaded update", e);
            return false;
        }
    }

    private void moveIntoPlace(Path tempFile, Path target) throws IOException {
        try {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Not every filesystem supports an atomic rename (uncommon, but seen on some network mounts) — a
            // plain replace is still safe here since nothing else reads plugins/update/ mid-write.
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
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

    /**
     * Same targeted-regex approach as {@link #extractVersion} — see
     * {@link #GITHUB_ASSET_URL}/{@link #MODRINTH_FILE_URL}.
     */
    static Optional<String> extractDownloadUrl(UpdateSource source, String body) {
        Pattern pattern = source == UpdateSource.GITHUB ? GITHUB_ASSET_URL : MODRINTH_FILE_URL;
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
