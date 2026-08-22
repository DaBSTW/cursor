package dev.bookreports.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.bookreports.config.TestConfigs;
import dev.bookreports.config.UpdateSource;
import java.util.Optional;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class UpdateCheckerTest {

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
    void checkNowSkipsTheNetworkCallWhenDisabled() {
        UpdateChecker checker = new UpdateChecker(() -> TestConfigs.withUpdateChecker(false, "DaBSTW/cursor"), "1.0.0",
                Runnable::run, Logger.getLogger("BookReportsTest"));

        assertEquals(Optional.empty(), checker.checkNow().join());
        assertFalse(checker.updateAvailable());
    }

    @Test
    void checkNowSkipsTheNetworkCallWhenResourceIsBlank() {
        UpdateChecker checker = new UpdateChecker(() -> TestConfigs.withUpdateChecker(true, ""), "1.0.0", Runnable::run,
                Logger.getLogger("BookReportsTest"));

        assertEquals(Optional.empty(), checker.checkNow().join());
    }

    @Test
    void updateAvailableIsFalseBeforeAnyCheckHasRun() {
        UpdateChecker checker = new UpdateChecker(() -> TestConfigs.withUpdateChecker(true, "DaBSTW/cursor"), "1.0.0",
                Runnable::run, Logger.getLogger("BookReportsTest"));

        assertFalse(checker.updateAvailable());
        assertTrue(checker.latestKnownVersion().isEmpty());
    }

    @Test
    void releasesPageUrlReflectsTheConfiguredSourceAndResource() {
        UpdateChecker checker = new UpdateChecker(() -> TestConfigs.withUpdateChecker(true, "DaBSTW/cursor"), "1.0.0",
                Runnable::run, Logger.getLogger("BookReportsTest"));

        assertEquals("https://github.com/DaBSTW/cursor/releases/latest", checker.releasesPageUrl());
    }
}
