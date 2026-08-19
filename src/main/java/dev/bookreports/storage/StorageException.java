package dev.bookreports.storage;

/** Wraps any {@link java.sql.SQLException} with the context needed to diagnose it from logs alone. */
public final class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
