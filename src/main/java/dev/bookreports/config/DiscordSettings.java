package dev.bookreports.config;

import dev.bookreports.storage.model.Priority;

public record DiscordSettings(boolean enabled, String webhookUrl, Priority minPriorityToNotify) {
}
