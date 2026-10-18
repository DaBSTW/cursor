package dev.pregen.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Logs a given problem at most once per interval so a failing chunk cannot flood the console. */
public final class ThrottledLogger {

    private static final int MAX_TRACKED_KEYS = 256;

    private final Logger logger;
    private final long intervalMillis;
    private final LongSupplier clock;
    private final Map<String, Long> lastLogged = new ConcurrentHashMap<>();

    public ThrottledLogger(Logger logger, long intervalMillis, LongSupplier clock) {
        this.logger = logger;
        this.intervalMillis = intervalMillis;
        this.clock = clock;
    }

    public void warn(String key, String message, Throwable error) {
        long now = clock.getAsLong();
        Long previous = lastLogged.get(key);
        if (previous != null && now - previous < intervalMillis) {
            return;
        }
        if (lastLogged.size() >= MAX_TRACKED_KEYS) {
            lastLogged.clear();
        }
        lastLogged.put(key, now);
        logger.log(Level.WARNING, message + " (similar messages are suppressed for " + intervalMillis / 1000 + "s)",
                error);
    }
}
