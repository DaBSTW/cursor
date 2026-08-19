package dev.bookreports.session;

/** A step in the report book flow. See {@link SessionTransitions} for the legal moves between states. */
public enum ReportState {
    SELECT_TARGET, TARGET_CONFIRM, CATEGORY, SUBREASON, EVIDENCE, SUMMARY, DONE
}
