package dev.pregen.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RateEstimatorTest {

    @Test
    void reportsZeroUntilTwoSamplesExist() {
        RateEstimator rate = new RateEstimator(30_000);
        assertEquals(0.0, rate.perSecond());
        rate.record(0, 0);
        assertEquals(0.0, rate.perSecond());
    }

    @Test
    void averagesOverTheWindowOnly() {
        RateEstimator rate = new RateEstimator(10_000);
        rate.record(0, 0);
        rate.record(10_000, 1_000);
        assertEquals(100.0, rate.perSecond(), 1e-9);
        // A faster phase pushes the old samples out of the window.
        for (int second = 11; second <= 30; second++) {
            rate.record(second * 1000L, 1_000 + (second - 10) * 500L);
        }
        assertEquals(500.0, rate.perSecond(), 1e-9);
    }
}
