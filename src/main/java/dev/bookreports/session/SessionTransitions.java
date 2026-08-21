package dev.bookreports.session;

import java.util.Map;
import java.util.Set;

/**
 * Whitelist of legal forward moves in the book flow. Anything not listed here is rejected by
 * {@link dev.bookreports.command.internal.SelectOptionCommand} — default-deny, not default-allow. A pasted or replayed
 * {@code /bookreports-select} command that tries to skip a page never matches an entry here.
 */
public final class SessionTransitions {

    private static final Map<ReportState, Set<ReportState>> ALLOWED = Map.of(ReportState.SELECT_TARGET,
            Set.of(ReportState.TARGET_CONFIRM), ReportState.TARGET_CONFIRM, Set.of(ReportState.CATEGORY),
            ReportState.CATEGORY, Set.of(ReportState.SUBREASON, ReportState.EVIDENCE), ReportState.SUBREASON,
            Set.of(ReportState.EVIDENCE), ReportState.EVIDENCE, Set.of(ReportState.SUMMARY), ReportState.SUMMARY,
            Set.of(ReportState.DONE), ReportState.DONE, Set.of());

    private SessionTransitions() {
    }

    public static boolean isAllowed(ReportState from, ReportState to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
