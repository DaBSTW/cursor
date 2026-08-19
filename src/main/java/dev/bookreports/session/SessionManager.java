package dev.bookreports.session;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import dev.bookreports.config.BookReportsConfig;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Tracks the single active {@link ReportSession} per reporter.
 *
 * <p>
 * Keyed by reporter UUID rather than session id: Bukkit already guarantees a command's executor identity, so looking a
 * session up by the executing player's UUID makes ownership structurally impossible to spoof. A new {@code /report}
 * silently replaces any session already in flight for that player (SPECS.md §4.4). The session's own {@code sessionId}
 * still guards against replaying a stale page from a superseded session — see
 * {@link dev.bookreports.command.internal.SelectOptionCommand}.
 */
public final class SessionManager {

    private final Cache<UUID, ReportSession> sessions;
    private final Clock clock;

    public SessionManager(Supplier<BookReportsConfig> config, Clock clock) {
        Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sessions = Caffeine.newBuilder().maximumSize(10_000).expireAfter(new Expiry<UUID, ReportSession>() {
            @Override
            public long expireAfterCreate(UUID key, ReportSession value, long currentTime) {
                return Duration.ofSeconds(config.get().sessionTimeoutSeconds()).toNanos();
            }

            @Override
            public long expireAfterUpdate(UUID key, ReportSession value, long currentTime, long currentDuration) {
                return Duration.ofSeconds(config.get().sessionTimeoutSeconds()).toNanos();
            }

            @Override
            public long expireAfterRead(UUID key, ReportSession value, long currentTime, long currentDuration) {
                return currentDuration;
            }
        }).build();
    }

    /** Starts a session without a target yet — used when {@code /report} is run with no argument. */
    public ReportSession startSelectingTarget(UUID reporterId) {
        ReportSession session = new ReportSession(UUID.randomUUID(), reporterId, null, ReportState.SELECT_TARGET, null,
                null, null, clock.instant());
        sessions.put(reporterId, session);
        return session;
    }

    /** Starts a session with the target already known — used by {@code /report <player>} and the target picker. */
    public ReportSession startWithTarget(UUID reporterId, UUID targetId) {
        ReportSession session = new ReportSession(UUID.randomUUID(), reporterId, targetId, ReportState.TARGET_CONFIRM,
                null, null, null, clock.instant());
        sessions.put(reporterId, session);
        return session;
    }

    public Optional<ReportSession> find(UUID reporterId) {
        return Optional.ofNullable(sessions.getIfPresent(reporterId));
    }

    /** Persists a transitioned session. Callers must have validated the move against {@link SessionTransitions}. */
    public void replace(ReportSession session) {
        sessions.put(session.reporterId(), session);
    }

    public void invalidate(UUID reporterId) {
        sessions.invalidate(reporterId);
    }
}
