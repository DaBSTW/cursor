package dev.pregen.config;

import net.kyori.adventure.bossbar.BossBar;

/** Validated, immutable view of {@code config.yml}. A reload builds a new instance instead of mutating this one. */
public record PluginSettings(String language, boolean useClientLocale, int confirmTimeoutSeconds, boolean debug,
        Generation generation, Throttle throttle, Progress progress, Trim trim) {

    public record Generation(int maxConcurrentChunks, boolean skipExistingChunks, int keepTickingSeconds,
            boolean resumeOnRestart, int autosaveSeconds, boolean pauseWhenPlayersOnline, double radiusLimit,
            boolean checkDiskSpace, int workerThreads) {
    }

    public record Throttle(boolean enabled, double targetMspt, int minConcurrentChunks) {
    }

    public record Progress(int consoleIntervalSeconds, boolean silent, BossBar.Color bossBarColor,
            BossBar.Overlay bossBarOverlay) {
    }

    public record Trim(boolean backup, String backupDirectory) {
    }

    public PluginSettings withProgress(Progress newProgress) {
        return new PluginSettings(language, useClientLocale, confirmTimeoutSeconds, debug, generation, throttle,
                newProgress, trim);
    }
}
