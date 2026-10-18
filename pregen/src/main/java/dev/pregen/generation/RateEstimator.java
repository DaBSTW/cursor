package dev.pregen.generation;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Chunks per second over a sliding time window. A window (rather than total/elapsed) reflects throughput changes such
 * as throttling or terrain getting heavier within seconds.
 */
public final class RateEstimator {

    private final long windowMillis;
    private final Deque<long[]> samples = new ArrayDeque<>();

    public RateEstimator(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    /** Records the cumulative processed count at the given time. */
    public synchronized void record(long nowMillis, long processed) {
        samples.addLast(new long[]{nowMillis, processed});
        while (samples.size() > 2 && nowMillis - samples.peekFirst()[0] > windowMillis) {
            samples.removeFirst();
        }
    }

    public synchronized double perSecond() {
        if (samples.size() < 2) {
            return 0.0;
        }
        long[] first = samples.peekFirst();
        long[] last = samples.peekLast();
        long millis = last[0] - first[0];
        if (millis <= 0) {
            return 0.0;
        }
        return (last[1] - first[1]) * 1000.0 / millis;
    }

    public synchronized void reset() {
        samples.clear();
    }
}
