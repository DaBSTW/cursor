package dev.bookreports.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.bookreports.config.TestConfigs;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionManagerTest {

    private final SessionManager sessions = new SessionManager(() -> TestConfigs.withSessionTimeout(300),
            Clock.systemUTC());

    @Test
    void startWithTargetCreatesATargetConfirmSession() {
        UUID reporter = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        ReportSession session = sessions.startWithTarget(reporter, target);

        assertEquals(ReportState.TARGET_CONFIRM, session.state());
        assertEquals(target, session.targetId());
        assertEquals(reporter, session.reporterId());
        assertEquals(session, sessions.find(reporter).orElseThrow());
    }

    @Test
    void startSelectingTargetHasNoTargetYet() {
        UUID reporter = UUID.randomUUID();

        ReportSession session = sessions.startSelectingTarget(reporter);

        assertEquals(ReportState.SELECT_TARGET, session.state());
        assertNull(session.targetId());
    }

    @Test
    void aSessionRequiresATargetOnceItIsPastSelectTarget() {
        assertThrows(IllegalArgumentException.class, () -> new ReportSession(UUID.randomUUID(), UUID.randomUUID(), null,
                ReportState.CATEGORY, null, null, null, java.time.Instant.now()));
    }

    @Test
    void newSessionReplacesThePreviousOneForTheSameReporter() {
        UUID reporter = UUID.randomUUID();
        ReportSession first = sessions.startWithTarget(reporter, UUID.randomUUID());
        ReportSession second = sessions.startWithTarget(reporter, UUID.randomUUID());

        ReportSession found = sessions.find(reporter).orElseThrow();

        assertEquals(second.sessionId(), found.sessionId());
        assertNotEquals(first.sessionId(), second.sessionId());
    }

    @Test
    void invalidateRemovesTheSession() {
        UUID reporter = UUID.randomUUID();
        sessions.startWithTarget(reporter, UUID.randomUUID());

        sessions.invalidate(reporter);

        assertTrue(sessions.find(reporter).isEmpty());
    }

    @Test
    void replaceUpdatesTheStoredSession() {
        UUID reporter = UUID.randomUUID();
        ReportSession session = sessions.startWithTarget(reporter, UUID.randomUUID());

        sessions.replace(session.withState(ReportState.CATEGORY));

        assertEquals(ReportState.CATEGORY, sessions.find(reporter).orElseThrow().state());
    }

    @Test
    void unknownReporterHasNoSession() {
        assertTrue(sessions.find(UUID.randomUUID()).isEmpty());
    }
}
