package dev.bookreports.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.TestConfigs;
import dev.bookreports.config.UpdateSource;
import java.io.File;
import java.util.Optional;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class UpdateCheckerTest {

    private static final File STAGED_TARGET = new File("build/tmp/test-update/BookReports-1.0.0.jar");

    @Test
    void extractsTheGithubReleaseTagFromAReleaseObject() {
        String body = """
                {"url":"...","tag_name":"v1.2.0","name":"BookReports 1.2.0","draft":false}
                """;

        assertEquals(Optional.of("v1.2.0"), UpdateChecker.extractVersion(UpdateSource.GITHUB, body));
    }

    @Test
    void extractsTheFirstModrinthVersionNumberFromANewestFirstList() {
        String body = """
                [{"version_number":"1.3.0","id":"abc"},{"version_number":"1.2.0","id":"def"}]
                """;

        assertEquals(Optional.of("1.3.0"), UpdateChecker.extractVersion(UpdateSource.MODRINTH, body));
    }

    @Test
    void returnsEmptyWhenTheExpectedFieldIsMissing() {
        assertEquals(Optional.empty(),
                UpdateChecker.extractVersion(UpdateSource.GITHUB, "{\"message\":\"Not Found\"}"));
        assertEquals(Optional.empty(), UpdateChecker.extractVersion(UpdateSource.MODRINTH, "[]"));
    }

    @Test
    void extractsTheGithubJarAssetUrlIgnoringNonJarAssets() {
        String body = """
                {"tag_name":"v1.2.0","assets":[
                    {"name":"BookReports-1.2.0.jar.sha256",
                     "browser_download_url":"https://example.com/BookReports-1.2.0.jar.sha256"},
                    {"name":"BookReports-1.2.0.jar",
                     "browser_download_url":"https://example.com/BookReports-1.2.0.jar"}
                ]}
                """;

        assertEquals(Optional.of("https://example.com/BookReports-1.2.0.jar"),
                UpdateChecker.extractDownloadUrl(UpdateSource.GITHUB, body));
    }

    @Test
    void extractsTheModrinthPrimaryFileUrl() {
        String body = """
                [{"version_number":"1.3.0","files":[
                    {"url":"https://cdn.modrinth.com/data/abc/BookReports-1.3.0.jar","primary":true}
                ]}]
                """;

        assertEquals(Optional.of("https://cdn.modrinth.com/data/abc/BookReports-1.3.0.jar"),
                UpdateChecker.extractDownloadUrl(UpdateSource.MODRINTH, body));
    }

    @Test
    void returnsEmptyDownloadUrlWhenNoJarAssetIsAttached() {
        String body = """
                {"tag_name":"v1.2.0","assets":[]}
                """;

        assertEquals(Optional.empty(), UpdateChecker.extractDownloadUrl(UpdateSource.GITHUB, body));
    }

    @Test
    void checkNowSkipsTheNetworkCallWhenDisabled() {
        UpdateChecker checker = newChecker(TestConfigs.withUpdateChecker(false, "DaBSTW/cursor"), "1.0.0");

        assertEquals(Optional.empty(), checker.checkNow().join());
        assertFalse(checker.updateAvailable());
    }

    @Test
    void checkNowSkipsTheNetworkCallWhenResourceIsBlank() {
        UpdateChecker checker = newChecker(TestConfigs.withUpdateChecker(true, ""), "1.0.0");

        assertEquals(Optional.empty(), checker.checkNow().join());
    }

    @Test
    void updateAvailableIsFalseBeforeAnyCheckHasRun() {
        UpdateChecker checker = newChecker(TestConfigs.withUpdateChecker(true, "DaBSTW/cursor"), "1.0.0");

        assertFalse(checker.updateAvailable());
        assertTrue(checker.latestKnownVersion().isEmpty());
    }

    @Test
    void releasesPageUrlReflectsTheConfiguredSourceAndResource() {
        UpdateChecker checker = newChecker(TestConfigs.withUpdateChecker(true, "DaBSTW/cursor"), "1.0.0");

        assertEquals("https://github.com/DaBSTW/cursor/releases/latest", checker.releasesPageUrl());
    }

    @Test
    void updateAvailableBecomesTrueOnceANewerVersionIsApplied() {
        UpdateChecker checker = newChecker(TestConfigs.withUpdateChecker(true, "DaBSTW/cursor"), "1.0.0");

        checker.applyCheckResult("1.1.0", null);

        assertTrue(checker.updateAvailable());
        assertEquals(Optional.of("1.1.0"), checker.latestKnownVersion());
    }

    @Test
    void updateAvailableStaysFalseWhenTheFetchedVersionIsNotNewer() {
        UpdateChecker checker = newChecker(TestConfigs.withUpdateChecker(true, "DaBSTW/cursor"), "1.0.0");

        checker.applyCheckResult("1.0.0", null);

        assertFalse(checker.updateAvailable());
    }

    @Test
    void serviceLockedIsFalseWhenTheLockSettingIsOffEvenWithAnUpdateAvailable() {
        UpdateChecker checker = newChecker(TestConfigs.withLockWhenOutdated(false), "1.0.0");

        checker.applyCheckResult("1.1.0", null);

        assertTrue(checker.updateAvailable());
        assertFalse(checker.serviceLocked());
    }

    @Test
    void serviceLockedIsTrueOnlyWhenBothTheLockSettingIsOnAndAnUpdateIsAvailable() {
        UpdateChecker checker = newChecker(TestConfigs.withLockWhenOutdated(true), "1.0.0");

        assertFalse(checker.serviceLocked(), "no update known yet");

        checker.applyCheckResult("1.1.0", null);

        assertTrue(checker.serviceLocked());
    }

    @Test
    void downloadAndStageUpdateDoesNothingWhenNoUpdateIsAvailable() {
        UpdateChecker checker = newChecker(TestConfigs.withUpdateChecker(true, "DaBSTW/cursor"), "1.0.0");

        assertFalse(checker.downloadAndStageUpdate().join());
    }

    @Test
    void downloadAndStageUpdateDoesNothingWhenTheReleaseHasNoJarAsset() {
        UpdateChecker checker = newChecker(TestConfigs.withUpdateChecker(true, "DaBSTW/cursor"), "1.0.0");

        checker.applyCheckResult("1.1.0", null);

        assertFalse(checker.downloadAndStageUpdate().join());
    }

    private UpdateChecker newChecker(BookReportsConfig config, String currentVersion) {
        return new UpdateChecker(() -> config, currentVersion, STAGED_TARGET, Runnable::run,
                Logger.getLogger("BookReportsTest"));
    }
}
