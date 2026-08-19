package dev.bookreports.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.storage.dao.ReportDao;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Enforces {@code report.daily-limit}, counted from UTC midnight and cached for 60s per reporter. */
public final class DailyLimitService {

    private final ReportDao reportDao;
    private final Supplier<BookReportsConfig> config;
    private final Clock clock;
    private final Cache<UUID, Integer> countCache;

    public DailyLimitService(ReportDao reportDao, Supplier<BookReportsConfig> config, Clock clock) {
        this.reportDao = Objects.requireNonNull(reportDao, "reportDao");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.countCache = Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofSeconds(60)).build();
    }

    /** Blocking — hits the database on a cache miss. Call from an async context. */
    public boolean hasReachedDailyLimit(UUID reporterUuid) {
        int limit = config.get().dailyLimit();
        if (limit <= 0) {
            return false;
        }
        int count = countCache.get(reporterUuid, id -> reportDao.countByReporterSince(id, startOfUtcDay()));
        return count >= limit;
    }

    /** Call after a report is accepted so the next check reflects it immediately instead of waiting on the TTL. */
    public void invalidate(UUID reporterUuid) {
        countCache.invalidate(reporterUuid);
    }

    private Instant startOfUtcDay() {
        return clock.instant().truncatedTo(ChronoUnit.DAYS);
    }
}
