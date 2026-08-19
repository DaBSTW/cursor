package dev.bookreports.config;

/**
 * Durations used by the staff panel's quick-sanction buttons (SPECS.md §11) when a {@code PunishmentBridge} is active.
 */
public record PunishmentSettings(String defaultBanDuration, String defaultMuteDuration) {
}
