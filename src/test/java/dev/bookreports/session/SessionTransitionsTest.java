package dev.bookreports.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SessionTransitionsTest {

    @Test
    void allowsEachDocumentedForwardStep() {
        assertTrue(SessionTransitions.isAllowed(ReportState.SELECT_TARGET, ReportState.TARGET_CONFIRM));
        assertTrue(SessionTransitions.isAllowed(ReportState.TARGET_CONFIRM, ReportState.CATEGORY));
        assertTrue(SessionTransitions.isAllowed(ReportState.CATEGORY, ReportState.SUBREASON));
        assertTrue(SessionTransitions.isAllowed(ReportState.CATEGORY, ReportState.EVIDENCE));
        assertTrue(SessionTransitions.isAllowed(ReportState.SUBREASON, ReportState.EVIDENCE));
        assertTrue(SessionTransitions.isAllowed(ReportState.EVIDENCE, ReportState.SUMMARY));
        assertTrue(SessionTransitions.isAllowed(ReportState.SUMMARY, ReportState.DONE));
    }

    @Test
    void rejectsSkippingAheadOrGoingBackwards() {
        assertFalse(SessionTransitions.isAllowed(ReportState.TARGET_CONFIRM, ReportState.SUMMARY));
        assertFalse(SessionTransitions.isAllowed(ReportState.TARGET_CONFIRM, ReportState.DONE));
        assertFalse(SessionTransitions.isAllowed(ReportState.SUMMARY, ReportState.CATEGORY));
        assertFalse(SessionTransitions.isAllowed(ReportState.CATEGORY, ReportState.TARGET_CONFIRM));
        assertFalse(SessionTransitions.isAllowed(ReportState.SELECT_TARGET, ReportState.CATEGORY));
    }

    @Test
    void doneIsTerminal() {
        for (ReportState state : ReportState.values()) {
            assertFalse(SessionTransitions.isAllowed(ReportState.DONE, state));
        }
    }
}
