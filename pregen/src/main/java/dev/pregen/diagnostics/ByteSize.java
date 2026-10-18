package dev.pregen.diagnostics;

import java.util.Locale;

/** Human-readable binary sizes: {@code 512 B}, {@code 1.5 KiB}, {@code 3.2 GiB}. */
public final class ByteSize {

    private static final String[] UNITS = {"B", "KiB", "MiB", "GiB", "TiB", "PiB"};

    private ByteSize() {
    }

    public static String format(long bytes) {
        if (bytes < 0) {
            return "?";
        }
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        return unit == 0 ? bytes + " B" : String.format(Locale.ROOT, "%.1f %s", value, UNITS[unit]);
    }
}
