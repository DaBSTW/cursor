package dev.bookreports.config;

import dev.bookreports.storage.model.Priority;
import java.util.List;
import java.util.Map;

/** Shared fixture builder — most tests only care about one or two fields, the rest is sane defaults. */
public final class TestConfigs {

    private TestConfigs() {
    }

    public static BookReportsConfig minimal() {
        ReportCategory hacks = new ReportCategory("hacks", "<red>Hacks</red>", Priority.HIGH,
                List.of("killaura", "speed"));
        ReportCategory other = new ReportCategory("other", "<gray>Other</gray>", Priority.LOW, List.of());
        return withCategories(Map.of("hacks", hacks, "other", other));
    }

    public static BookReportsConfig withCategories(Map<String, ReportCategory> categories) {
        return new BookReportsConfig(StorageType.SQLITE, new MySqlSettings("localhost", 3306, "db", "root", "", 10), 5,
                10, 300, true, true, categories, new PriorityEscalationSettings(3, 600, Priority.HIGH),
                new FalseReportPenaltySettings(false, 3, 4, 0), false,
                new StaffSettings("ENTITY_EXPERIENCE_ORB_PICKUP", 15), new DiscordSettings(false, "", Priority.HIGH),
                new PunishmentSettings("7d", "1h"), true, true, "es_ES", "default",
                new CoreProtectSettings(true, 300, 20),
                new UpdateCheckerSettings(false, UpdateSource.GITHUB, "DaBSTW/cursor", 12, true, false));
    }

    public static BookReportsConfig withUpdateChecker(boolean enabled, String resource) {
        BookReportsConfig base = minimal();
        return new BookReportsConfig(base.storageType(), base.mysql(), base.cooldownSeconds(), base.dailyLimit(),
                base.sessionTimeoutSeconds(), base.preventSelfReport(), base.preventDuplicatePending(),
                base.categories(), base.priorityEscalation(), base.falseReportPenalty(), base.enableReportTool(),
                base.staff(), base.discord(), base.punishments(), base.placeholderApiEnabled(), base.metricsEnabled(),
                base.locale(), base.serverId(), base.coreProtect(),
                new UpdateCheckerSettings(enabled, UpdateSource.GITHUB, resource, 12, true, false));
    }

    public static BookReportsConfig withLockWhenOutdated(boolean lockWhenOutdated) {
        BookReportsConfig base = minimal();
        return new BookReportsConfig(base.storageType(), base.mysql(), base.cooldownSeconds(), base.dailyLimit(),
                base.sessionTimeoutSeconds(), base.preventSelfReport(), base.preventDuplicatePending(),
                base.categories(), base.priorityEscalation(), base.falseReportPenalty(), base.enableReportTool(),
                base.staff(), base.discord(), base.punishments(), base.placeholderApiEnabled(), base.metricsEnabled(),
                base.locale(), base.serverId(), base.coreProtect(),
                new UpdateCheckerSettings(true, UpdateSource.GITHUB, "DaBSTW/cursor", 12, true, lockWhenOutdated));
    }

    public static BookReportsConfig withSessionTimeout(int seconds) {
        BookReportsConfig base = minimal();
        return new BookReportsConfig(base.storageType(), base.mysql(), base.cooldownSeconds(), base.dailyLimit(),
                seconds, base.preventSelfReport(), base.preventDuplicatePending(), base.categories(),
                base.priorityEscalation(), base.falseReportPenalty(), base.enableReportTool(), base.staff(),
                base.discord(), base.punishments(), base.placeholderApiEnabled(), base.metricsEnabled(), base.locale(),
                base.serverId(), base.coreProtect(), base.updateChecker());
    }
}
