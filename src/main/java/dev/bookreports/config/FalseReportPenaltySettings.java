package dev.bookreports.config;

public record FalseReportPenaltySettings(boolean enabled, int thresholdIn30Days, int cooldownMultiplier,
        int muteMinutes) {

    public boolean muteEnabled() {
        return muteMinutes > 0;
    }
}
