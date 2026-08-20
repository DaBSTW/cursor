package dev.bookreports.storage.model;

/** A staff member's review activity: how many reports they've resolved, and how quickly on average. */
public record StaffStats(int resolvedCount, double avgResolutionMinutes) {
}
