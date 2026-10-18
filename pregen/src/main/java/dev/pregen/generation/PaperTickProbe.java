package dev.pregen.generation;

import java.util.OptionalDouble;
import org.bukkit.Server;

/** Reads Paper's average tick time. Folia has no global tick and throws, after which the probe stays disabled. */
public final class PaperTickProbe implements TickProbe {

    private final Server server;
    private volatile boolean supported = true;

    public PaperTickProbe(Server server) {
        this.server = server;
    }

    @Override
    public OptionalDouble averageTickMillis() {
        if (!supported) {
            return OptionalDouble.empty();
        }
        try {
            double millis = server.getAverageTickTime();
            return Double.isFinite(millis) && millis > 0.0 ? OptionalDouble.of(millis) : OptionalDouble.empty();
        } catch (UnsupportedOperationException e) {
            supported = false;
            return OptionalDouble.empty();
        }
    }
}
