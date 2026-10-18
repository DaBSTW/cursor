package dev.pregen.diagnostics;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.Properties;

/** Read-only access to {@code server.properties}. The plugin never writes this file. */
public final class ServerProperties {

    private static final String PAUSE_WHEN_EMPTY = "pause-when-empty-seconds";

    private ServerProperties() {
    }

    /**
     * Seconds after which an empty server pauses ticking (1.21.2+), when enabled. While paused, chunk requests stall,
     * so pre-generating on an empty server needs this disabled.
     */
    public static OptionalInt pauseWhenEmptySeconds(Path file) {
        if (!Files.isRegularFile(file)) {
            return OptionalInt.empty();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException e) {
            return OptionalInt.empty();
        }
        String raw = properties.getProperty(PAUSE_WHEN_EMPTY);
        if (raw == null) {
            return OptionalInt.empty();
        }
        try {
            int seconds = Integer.parseInt(raw.trim());
            return seconds > 0 ? OptionalInt.of(seconds) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}
