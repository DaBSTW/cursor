package dev.example.soledom.runtime;

import org.jetbrains.annotations.NotNull;

/** Why a model could not be spawned. The {@link Reason} maps to a user-facing message. */
public final class SpawnException extends Exception {

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    public SpawnException(@NotNull Reason reason, @NotNull String message) {
        super(message);
        this.reason = reason;
    }

    public @NotNull Reason getReason() {
        return reason;
    }

    public enum Reason {
        UNKNOWN_MODEL,
        CHUNK_NOT_LOADED,
        CHUNK_LIMIT,
        CANCELLED,
        FAILED
    }
}
