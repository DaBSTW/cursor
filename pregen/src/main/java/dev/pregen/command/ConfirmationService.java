package dev.pregen.command;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds one pending sensitive action per sender until {@code /pregen confirm} or expiry. A new request replaces the
 * previous one, so a confirm always runs the action the sender saw last.
 */
public final class ConfirmationService {

    public enum Result {
        CONFIRMED,
        NOTHING_PENDING,
        EXPIRED
    }

    private record Pending(Runnable action, long expiresAtMillis) {
    }

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public void request(UUID sender, Runnable action, long nowMillis, long timeoutMillis) {
        pending.put(sender, new Pending(action, nowMillis + timeoutMillis));
    }

    /** Runs and clears the sender's pending action, if it has not expired. */
    public Result confirm(UUID sender, long nowMillis) {
        Pending action = pending.remove(sender);
        if (action == null) {
            return Result.NOTHING_PENDING;
        }
        if (nowMillis > action.expiresAtMillis()) {
            return Result.EXPIRED;
        }
        action.action().run();
        return Result.CONFIRMED;
    }

    public void forget(UUID sender) {
        pending.remove(sender);
    }
}
