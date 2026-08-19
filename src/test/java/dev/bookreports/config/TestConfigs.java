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
                true, "es_ES", "default");
    }

    public static BookReportsConfig withSessionTimeout(int seconds) {
        BookReportsConfig base = minimal();
        return new BookReportsConfig(base.storageType(), base.mysql(), base.cooldownSeconds(), base.dailyLimit(),
                seconds, base.preventSelfReport(), base.preventDuplicatePending(), base.categories(),
                base.priorityEscalation(), base.falseReportPenalty(), base.enableReportTool(), base.staff(),
                base.discord(), base.placeholderApiEnabled(), base.locale(), base.serverId());
    }
}
