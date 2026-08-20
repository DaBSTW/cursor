package dev.bookreports.storage.model;

/**
 * A reporter's track record, used to gauge how much weight to give their future reports. {@code RESOLVED_DUPLICATE}
 * doesn't count either way — someone else already reported the same thing first, which isn't this reporter's fault.
 */
public record ReporterStats(int total, int actioned, int rejectedOrFalse) {

    /** Share of decided reports (excludes still-pending/in-review ones) that led to a sanction, 0-100. */
    public int accuracyPercent() {
        int decided = actioned + rejectedOrFalse;
        return decided == 0 ? 0 : Math.round(100f * actioned / decided);
    }
}
