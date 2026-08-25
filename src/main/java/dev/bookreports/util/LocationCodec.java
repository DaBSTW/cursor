package dev.bookreports.util;

import java.util.Locale;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Encodes a {@link Location} as a single delimited string for storage in one {@code VARCHAR} column — same compact
 * pattern already used for {@code chat_context}/{@code coreprotect_context} — rather than adding four or five numeric
 * columns for what is, in practice, a write-once/read-rarely snapshot.
 */
public final class LocationCodec {

    private static final String DELIMITER = ";";

    private LocationCodec() {
    }

    public static String encode(Location location) {
        World world = location.getWorld();
        return (world != null ? world.getName() : "") + DELIMITER + location.getX() + DELIMITER + location.getY()
                + DELIMITER + location.getZ() + DELIMITER + location.getYaw() + DELIMITER + location.getPitch();
    }

    /**
     * Empty when {@code encoded} is null/blank, malformed, or names a world that no longer exists (e.g. removed since
     * the report was filed) — never throws, since a stale location is a lore/teleport feature going quiet, not a reason
     * to break the report detail view.
     */
    public static Optional<Location> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        String[] parts = encoded.split(DELIMITER, -1);
        if (parts.length != 6) {
            return Optional.empty();
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return Optional.empty();
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = Float.parseFloat(parts[4]);
            float pitch = Float.parseFloat(parts[5]);
            return Optional.of(new Location(world, x, y, z, yaw, pitch));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** {@code "world (x, y, z)"} — for lore/chat display, not round-tripping. */
    public static Optional<String> describe(String encoded) {
        return decode(encoded).map(loc -> String.format(Locale.ROOT, "%s (%d, %d, %d)", loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }
}
