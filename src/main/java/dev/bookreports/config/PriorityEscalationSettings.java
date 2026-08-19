package dev.bookreports.config;

import dev.bookreports.storage.model.Priority;

public record PriorityEscalationSettings(int distinctReportersThreshold, int windowSeconds, Priority escalateTo) {
}
