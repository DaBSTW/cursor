package dev.bookreports.config;

import java.util.Map;
import java.util.Optional;

/** Fully parsed, immutable snapshot of {@code config.yml}. Replaced wholesale on {@code /reportsreload}. */
public record BookReportsConfig(StorageType storageType, MySqlSettings mysql, int cooldownSeconds, int dailyLimit,
        int sessionTimeoutSeconds, boolean preventSelfReport, boolean preventDuplicatePending,
        Map<String, ReportCategory> categories, PriorityEscalationSettings priorityEscalation,
        FalseReportPenaltySettings falseReportPenalty, boolean enableReportTool, StaffSettings staff,
        DiscordSettings discord, PunishmentSettings punishments, boolean placeholderApiEnabled, boolean metricsEnabled,
        String locale, String serverId, CoreProtectSettings coreProtect) {

    public BookReportsConfig {
        categories = Map.copyOf(categories);
    }

    public Optional<ReportCategory> category(String id) {
        return Optional.ofNullable(categories.get(id));
    }
}
