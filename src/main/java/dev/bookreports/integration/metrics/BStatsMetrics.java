package dev.bookreports.integration.metrics;

import dev.bookreports.config.BookReportsConfig;
import java.util.function.Supplier;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * bStats usage metrics (bstats.org): anonymous, aggregate stats — server count, Paper/Java versions, a couple of config
 * summaries below — shown on the project's bStats page. Never player data. Gated by {@code metrics.enabled} in
 * config.yml, on top of bStats' own server-wide opt-out ({@code plugins/bStats/config.yml}, shared by every plugin
 * using it).
 */
public final class BStatsMetrics {

    /** BookReports' registered id at https://bstats.org/plugin/bukkit/BookReports/33549. */
    private static final int PLUGIN_ID = 33549;

    private BStatsMetrics() {
    }

    public static void start(JavaPlugin plugin, Supplier<BookReportsConfig> config) {
        if (!config.get().metricsEnabled()) {
            return;
        }
        Metrics metrics = new Metrics(plugin, PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("storage_type", () -> config.get().storageType().name()));
        metrics.addCustomChart(new SimplePie("locale", () -> config.get().locale()));
    }
}
