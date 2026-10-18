package dev.pregen.generation;

import dev.pregen.config.PluginSettings;
import java.util.OptionalDouble;
import java.util.function.IntSupplier;

/**
 * Decides how many chunks may be in flight across all tasks. Additive increase / multiplicative decrease on the
 * server's tick time: back off quickly when ticks get slow, recover gradually when they are fast again.
 *
 * <p>{@link #sample()} is called from a single scheduler thread; {@link #limit()} may be read from any thread.
 */
public final class ThrottleController {

    private static final double DECREASE_FACTOR = 0.75;
    // Ticks below this fraction of the target are considered healthy enough to add concurrency.
    private static final double RECOVERY_THRESHOLD = 0.8;

    private final TickProbe probe;
    private final IntSupplier onlinePlayers;
    private volatile PluginSettings settings;
    private volatile int limit;
    private volatile boolean probeAvailable = true;

    public ThrottleController(PluginSettings settings, TickProbe probe, IntSupplier onlinePlayers) {
        this.settings = settings;
        this.probe = probe;
        this.onlinePlayers = onlinePlayers;
        this.limit = settings.generation().maxConcurrentChunks();
    }

    public void updateSettings(PluginSettings newSettings) {
        this.settings = newSettings;
        this.limit = Math.min(limit, newSettings.generation().maxConcurrentChunks());
    }

    /** Current global limit; 0 means all tasks hold. */
    public int limit() {
        if (settings.generation().pauseWhenPlayersOnline() && onlinePlayers.getAsInt() > 0) {
            return 0;
        }
        return limit;
    }

    /** Share of the global limit for one of {@code runningTasks} tasks; at least 1 unless everything holds. */
    public int limitPerTask(int runningTasks) {
        int global = limit();
        if (global <= 0) {
            return 0;
        }
        return Math.max(1, global / Math.max(1, runningTasks));
    }

    public boolean isProbeAvailable() {
        return probeAvailable;
    }

    public void sample() {
        PluginSettings current = settings;
        int max = current.generation().maxConcurrentChunks();
        if (!current.throttle().enabled()) {
            limit = max;
            return;
        }
        OptionalDouble mspt = probe.averageTickMillis();
        probeAvailable = mspt.isPresent();
        if (mspt.isEmpty()) {
            limit = max;
            return;
        }
        int min = Math.min(current.throttle().minConcurrentChunks(), max);
        double target = current.throttle().targetMspt();
        int previous = limit;
        if (mspt.getAsDouble() > target) {
            limit = Math.max(min, (int) Math.floor(previous * DECREASE_FACTOR));
        } else if (mspt.getAsDouble() < target * RECOVERY_THRESHOLD) {
            limit = Math.min(max, previous + Math.max(1, max / 16));
        } else {
            limit = Math.min(max, Math.max(min, previous));
        }
    }
}
