package dev.bookreports.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks, per reporter, the instant at which their next report is allowed.
 *
 * <p>
 * Purely in-memory (SPECS.md §8.1: cooldowns are short-lived, so a Caffeine cache is enough — no DB round trip). The
 * effective duration (base or false-report-penalty-multiplied) is resolved once by the caller and handed to
 * {@link #recordReport}; this class never touches storage.
 */
public final class CooldownService {

    private final Cache<UUID, Instant> nextAllowedAt;
    private final Clock clock;

    public CooldownService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.nextAllowedAt = Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofDays(1)).build();
    }

    public Optional<Duration> remainingCooldown(UUID reporterUuid) {
        Instant allowedAt = nextAllowedAt.getIfPresent(reporterUuid);
        if (allowedAt == null) {
            return Optional.empty();
        }
        Duration remaining = Duration.between(clock.instant(), allowedAt);
        return remaining.isNegative() || remaining.isZero() ? Optional.empty() : Optional.of(remaining);
    }

    public boolean isOnCooldown(UUID reporterUuid) {
        return remainingCooldown(reporterUuid).isPresent();
    }

    public void recordReport(UUID reporterUuid, Duration cooldown) {
        nextAllowedAt.put(reporterUuid, clock.instant().plus(cooldown));
    }
}
