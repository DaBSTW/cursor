package dev.bookreports.config;

/** Thrown when {@code config.yml} or a locale file is malformed. Fails plugin startup loudly, never silently. */
public final class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
