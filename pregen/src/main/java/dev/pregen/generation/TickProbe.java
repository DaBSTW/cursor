package dev.pregen.generation;

import java.util.OptionalDouble;

/** Source of the server's recent tick duration. */
@FunctionalInterface
public interface TickProbe {

    /** Average milliseconds per tick, or empty when the server cannot report it (Folia has no global tick). */
    OptionalDouble averageTickMillis();
}
