package dev.pregen.command;

import java.util.concurrent.CompletableFuture;

/** Operations the plugin itself owns (config file writes, reload), exposed to the admin commands. */
public interface PluginControl {

    /** Persists a config value to {@code config.yml} off the calling thread. */
    void persistSetting(String path, Object value);

    /** Re-reads config and messages; completes once the new settings are active. */
    CompletableFuture<Void> reload();
}
