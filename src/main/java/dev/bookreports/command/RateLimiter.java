package dev.bookreports.command;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.UUID;

/** Simple per-player rate limit — used to keep {@code /report} to about one book-open per second (SPECS.md §13). */
public final class RateLimiter {

    private final Cache<UUID, Boolean> recent;

    public RateLimiter(Duration window) {
        this.recent = Caffeine.newBuilder().expireAfterWrite(window).maximumSize(10_000).build();
    }

    /** Returns {@code true} and starts a new window if the player wasn't already inside one. */
    public boolean tryAcquire(UUID playerId) {
        if (recent.getIfPresent(playerId) != null) {
            return false;
        }
        recent.put(playerId, Boolean.TRUE);
        return true;
    }
}
