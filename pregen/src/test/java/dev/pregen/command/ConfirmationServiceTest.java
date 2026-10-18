package dev.pregen.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConfirmationServiceTest {

    private final ConfirmationService confirmations = new ConfirmationService();
    private final UUID sender = UUID.randomUUID();
    private final AtomicInteger runs = new AtomicInteger();

    @Test
    void runsThePendingActionOnce() {
        confirmations.request(sender, runs::incrementAndGet, 0, 1000);
        assertEquals(ConfirmationService.Result.CONFIRMED, confirmations.confirm(sender, 500));
        assertEquals(ConfirmationService.Result.NOTHING_PENDING, confirmations.confirm(sender, 600));
        assertEquals(1, runs.get());
    }

    @Test
    void expiredActionsNeverRun() {
        confirmations.request(sender, runs::incrementAndGet, 0, 1000);
        assertEquals(ConfirmationService.Result.EXPIRED, confirmations.confirm(sender, 1001));
        assertEquals(0, runs.get());
    }

    @Test
    void aNewRequestReplacesTheOldOne() {
        AtomicInteger other = new AtomicInteger();
        confirmations.request(sender, runs::incrementAndGet, 0, 1000);
        confirmations.request(sender, other::incrementAndGet, 0, 1000);
        confirmations.confirm(sender, 1);
        assertEquals(0, runs.get());
        assertEquals(1, other.get());
    }

    @Test
    void sendersDoNotShareConfirmations() {
        confirmations.request(sender, runs::incrementAndGet, 0, 1000);
        assertEquals(ConfirmationService.Result.NOTHING_PENDING, confirmations.confirm(UUID.randomUUID(), 1));
        assertEquals(0, runs.get());
    }
}
