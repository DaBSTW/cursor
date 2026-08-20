package dev.bookreports.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatContextTrackerTest {

    @Test
    void returnsNullWhenNothingBuffered() {
        ChatContextTracker tracker = new ChatContextTracker();

        assertNull(tracker.recentContext(UUID.randomUUID()));
    }

    @Test
    void joinsLinesOldestFirst() {
        ChatContextTracker tracker = new ChatContextTracker();
        UUID player = UUID.randomUUID();
        tracker.record(player, "first");
        tracker.record(player, "second");

        assertEquals("first | second", tracker.recentContext(player));
    }

    @Test
    void dropsTheOldestLineOnceTheBufferIsFull() {
        ChatContextTracker tracker = new ChatContextTracker();
        UUID player = UUID.randomUUID();
        for (int i = 0; i < ChatContextTracker.MAX_LINES + 3; i++) {
            tracker.record(player, "line" + i);
        }

        String context = tracker.recentContext(player);

        assertTrue(context.startsWith("line3 "), "Expected the first 3 lines to be evicted, got: " + context);
        assertTrue(context.endsWith("line" + (ChatContextTracker.MAX_LINES + 2)));
    }

    @Test
    void stripsColorCodesAndBlankLinesAreIgnored() {
        ChatContextTracker tracker = new ChatContextTracker();
        UUID player = UUID.randomUUID();
        tracker.record(player, "&chello");
        tracker.record(player, "   ");

        assertEquals("hello", tracker.recentContext(player));
    }

    @Test
    void keepsEachPlayerBufferIndependent() {
        ChatContextTracker tracker = new ChatContextTracker();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        tracker.record(a, "from a");

        assertEquals("from a", tracker.recentContext(a));
        assertNull(tracker.recentContext(b));
    }
}
