package dev.bookreports.config;

import dev.bookreports.storage.model.Priority;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

/**
 * Parses a raw {@link ConfigurationSection} into a validated {@link BookReportsConfig}.
 *
 * <p>
 * Deliberately independent of {@link org.bukkit.plugin.Plugin} so it can be unit tested against a plain in-memory YAML
 * document, without a running server.
 */
public final class ConfigParser {

    public BookReportsConfig parse(ConfigurationSection root) {
        Objects.requireNonNull(root, "root");

        StorageType storageType = parseEnum(StorageType.class, root.getString("storage.type", "sqlite"),
                "storage.type");
        MySqlSettings mysql = parseMySql(sectionOrEmpty(root, "storage.mysql"));

        int cooldownSeconds = requirePositiveOrZero(root.getInt("report.cooldown-seconds", 120),
                "report.cooldown-seconds");
        int dailyLimit = requirePositiveOrZero(root.getInt("report.daily-limit", 10), "report.daily-limit");
        int sessionTimeoutSeconds = requirePositiveOrZero(root.getInt("report.session-timeout-seconds", 300),
                "report.session-timeout-seconds");
        boolean preventSelfReport = root.getBoolean("report.prevent-self-report", true);
        boolean preventDuplicatePending = root.getBoolean("report.prevent-duplicate-pending", true);

        Map<String, ReportCategory> categories = parseCategories(root.getConfigurationSection("report.categories"));
        if (categories.isEmpty()) {
            throw new ConfigurationException("'report.categories' must declare at least one category");
        }

        PriorityEscalationSettings escalation = parseEscalation(sectionOrEmpty(root, "priority-escalation"));
        FalseReportPenaltySettings penalty = parsePenalty(sectionOrEmpty(root, "false-report-penalty"));
        boolean enableReportTool = root.getBoolean("enable-report-tool", false);
        StaffSettings staff = parseStaff(sectionOrEmpty(root, "staff"));
        DiscordSettings discord = parseDiscord(sectionOrEmpty(root, "discord"));
        PunishmentSettings punishments = parsePunishments(sectionOrEmpty(root, "punishments"));
        boolean placeholderApiEnabled = root.getBoolean("placeholderapi", true);
        boolean metricsEnabled = root.getBoolean("metrics.enabled", true);
        String locale = requireNonBlank(root.getString("locale", "en_US"), "locale");
        String serverId = requireNonBlank(root.getString("server-id", "default"), "server-id");
        CoreProtectSettings coreProtect = parseCoreProtect(sectionOrEmpty(root, "coreprotect"));
        UpdateCheckerSettings updateChecker = parseUpdateChecker(sectionOrEmpty(root, "update-checker"));

        return new BookReportsConfig(storageType, mysql, cooldownSeconds, dailyLimit, sessionTimeoutSeconds,
                preventSelfReport, preventDuplicatePending, categories, escalation, penalty, enableReportTool, staff,
                discord, punishments, placeholderApiEnabled, metricsEnabled, locale, serverId, coreProtect,
                updateChecker);
    }

    private Map<String, ReportCategory> parseCategories(ConfigurationSection section) {
        Map<String, ReportCategory> result = new LinkedHashMap<>();
        if (section == null) {
            return result;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection categorySection = section.getConfigurationSection(id);
            if (categorySection == null) {
                throw new ConfigurationException(
                        "Category '" + id + "' must be a mapping with 'display' and 'priority'");
            }
            String display = requireNonBlank(categorySection.getString("display"),
                    "report.categories." + id + ".display");
            String rawPriority = categorySection.getString("priority");
            if (rawPriority == null) {
                throw new ConfigurationException("Category '" + id + "' is missing 'priority'");
            }
            Priority priority = parseCategoryPriority(id, rawPriority);
            List<String> subReasons = new ArrayList<>(categorySection.getStringList("sub-reasons"));
            result.put(id, new ReportCategory(id, display, priority, subReasons));
        }
        return result;
    }

    private Priority parseCategoryPriority(String categoryId, String raw) {
        try {
            return Priority.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Category '" + categoryId + "' has unknown priority '" + raw
                    + "'; expected one of " + Arrays.toString(Priority.values()));
        }
    }

    private MySqlSettings parseMySql(ConfigurationSection section) {
        return new MySqlSettings(section.getString("host", "localhost"), section.getInt("port", 3306),
                section.getString("database", "bookreports"), section.getString("user", "root"),
                section.getString("password", ""),
                requirePositiveOrZero(section.getInt("pool-size", 10), "storage.mysql.pool-size"));
    }

    private PriorityEscalationSettings parseEscalation(ConfigurationSection section) {
        int threshold = section.getInt("distinct-reporters-threshold", 3);
        int windowSeconds = section.getInt("window-seconds", 600);
        if (threshold < 1) {
            throw new ConfigurationException("'priority-escalation.distinct-reporters-threshold' must be >= 1");
        }
        if (windowSeconds < 1) {
            throw new ConfigurationException("'priority-escalation.window-seconds' must be >= 1");
        }
        Priority escalateTo = parseEnum(Priority.class, section.getString("escalate-to", "HIGH"),
                "priority-escalation.escalate-to");
        return new PriorityEscalationSettings(threshold, windowSeconds, escalateTo);
    }

    private FalseReportPenaltySettings parsePenalty(ConfigurationSection section) {
        boolean enabled = section.getBoolean("enabled", true);
        int threshold = section.getInt("threshold-in-30-days", 3);
        int multiplier = section.getInt("cooldown-multiplier", 4);
        int muteMinutes = section.getInt("mute-minutes", 0);
        if (enabled && threshold < 1) {
            throw new ConfigurationException("'false-report-penalty.threshold-in-30-days' must be >= 1 when enabled");
        }
        if (multiplier < 1) {
            throw new ConfigurationException("'false-report-penalty.cooldown-multiplier' must be >= 1");
        }
        if (muteMinutes < 0) {
            throw new ConfigurationException("'false-report-penalty.mute-minutes' must be >= 0");
        }
        return new FalseReportPenaltySettings(enabled, threshold, multiplier, muteMinutes);
    }

    private StaffSettings parseStaff(ConfigurationSection section) {
        // Validated lazily by NotificationService: org.bukkit.Sound is registry-backed and touching it
        // this early would force server registry init during what should be a plain YAML parse.
        String alertSound = requireNonBlank(section.getString("alert-sound", "ENTITY_EXPERIENCE_ORB_PICKUP"),
                "staff.alert-sound");
        int claimTimeoutMinutes = section.getInt("claim-timeout-minutes", 15);
        if (claimTimeoutMinutes < 1) {
            throw new ConfigurationException("'staff.claim-timeout-minutes' must be >= 1");
        }
        return new StaffSettings(alertSound.toUpperCase(Locale.ROOT), claimTimeoutMinutes);
    }

    private DiscordSettings parseDiscord(ConfigurationSection section) {
        boolean enabled = section.getBoolean("enabled", false);
        String webhookUrl = section.getString("webhook-url", "");
        if (enabled && webhookUrl.isBlank()) {
            throw new ConfigurationException("'discord.webhook-url' is required when 'discord.enabled' is true");
        }
        Priority minPriority = parseEnum(Priority.class, section.getString("min-priority-to-notify", "HIGH"),
                "discord.min-priority-to-notify");
        return new DiscordSettings(enabled, webhookUrl, minPriority);
    }

    private CoreProtectSettings parseCoreProtect(ConfigurationSection section) {
        boolean enabled = section.getBoolean("enabled", true);
        int lookbackSeconds = requirePositiveOrZero(section.getInt("lookback-seconds", 300),
                "coreprotect.lookback-seconds");
        int maxEntries = requirePositiveOrZero(section.getInt("max-entries", 20), "coreprotect.max-entries");
        if (enabled && lookbackSeconds < 1) {
            throw new ConfigurationException("'coreprotect.lookback-seconds' must be >= 1 when enabled");
        }
        if (enabled && maxEntries < 1) {
            throw new ConfigurationException("'coreprotect.max-entries' must be >= 1 when enabled");
        }
        return new CoreProtectSettings(enabled, lookbackSeconds, maxEntries);
    }

    private UpdateCheckerSettings parseUpdateChecker(ConfigurationSection section) {
        boolean enabled = section.getBoolean("enabled", true);
        UpdateSource source = parseEnum(UpdateSource.class, section.getString("source", "github"),
                "update-checker.source");
        String resource = section.getString("resource", "DaBSTW/cursor");
        int checkIntervalHours = section.getInt("check-interval-hours", 12);
        boolean notifyOpsOnJoin = section.getBoolean("notify-ops-on-join", true);
        boolean lockWhenOutdated = section.getBoolean("lock-when-outdated", false);
        if (enabled && (resource == null || resource.isBlank())) {
            throw new ConfigurationException(
                    "'update-checker.resource' is required when 'update-checker.enabled' is true");
        }
        if (enabled && checkIntervalHours < 1) {
            throw new ConfigurationException("'update-checker.check-interval-hours' must be >= 1 when enabled");
        }
        return new UpdateCheckerSettings(enabled, source, resource, checkIntervalHours, notifyOpsOnJoin,
                lockWhenOutdated);
    }

    private PunishmentSettings parsePunishments(ConfigurationSection section) {
        String banDuration = requireNonBlank(section.getString("default-ban-duration", "7d"),
                "punishments.default-ban-duration");
        String muteDuration = requireNonBlank(section.getString("default-mute-duration", "1h"),
                "punishments.default-mute-duration");
        return new PunishmentSettings(banDuration, muteDuration);
    }

    private <T extends Enum<T>> T parseEnum(Class<T> type, String raw, String path) {
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("'" + path + "' has invalid value '" + raw + "'; expected one of "
                    + Arrays.toString(type.getEnumConstants()));
        }
    }

    private ConfigurationSection sectionOrEmpty(ConfigurationSection root, String path) {
        ConfigurationSection section = root.getConfigurationSection(path);
        return section != null ? section : new MemoryConfiguration();
    }

    private int requirePositiveOrZero(int value, String path) {
        if (value < 0) {
            throw new ConfigurationException("'" + path + "' must be >= 0");
        }
        return value;
    }

    private String requireNonBlank(String value, String path) {
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("'" + path + "' is required and must not be blank");
        }
        return value;
    }
}
