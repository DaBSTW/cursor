package dev.bookreports.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CooldownServiceTest {

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void reporterWithNoHistoryIsNotOnCooldown() {
        CooldownService service = new CooldownService(new MutableClock(Instant.parse("2026-01-01T00:00:00Z")));

        assertFalse(service.isOnCooldown(UUID.randomUUID()));
    }

    @Test
    void reporterIsOnCooldownImmediatelyAfterReporting() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        CooldownService service = new CooldownService(clock);
        UUID reporter = UUID.randomUUID();

        service.recordReport(reporter, Duration.ofSeconds(120));

        assertTrue(service.isOnCooldown(reporter));
        assertTrue(service.remainingCooldown(reporter).orElseThrow().toSeconds() <= 120);
    }

    @Test
    void cooldownClearsTheInstantItExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        CooldownService service = new CooldownService(clock);
        UUID reporter = UUID.randomUUID();
        service.recordReport(reporter, Duration.ofSeconds(120));

        clock.advance(Duration.ofSeconds(119));
        assertTrue(service.isOnCooldown(reporter));

        clock.advance(Duration.ofSeconds(1));
        assertFalse(service.isOnCooldown(reporter));
    }

    @Test
    void cooldownIsPerReporter() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        CooldownService service = new CooldownService(clock);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        service.recordReport(a, Duration.ofSeconds(120));

        assertTrue(service.isOnCooldown(a));
        assertFalse(service.isOnCooldown(b));
    }
}
