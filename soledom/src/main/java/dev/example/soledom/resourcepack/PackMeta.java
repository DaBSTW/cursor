package dev.example.soledom.resourcepack;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Builds {@code pack.mcmeta}.
 *
 * <p>Format rules (Java Edition):</p>
 * <ul>
 *   <li>Up to 1.21.8 clients read {@code pack_format} and, since 1.20.2, an optional {@code supported_formats}
 *       range.</li>
 *   <li>From 1.21.9 (resource pack format 65) clients read {@code min_format}/{@code max_format}. The legacy
 *       fields must still be present whenever the range reaches below 65, and must be absent otherwise.</li>
 * </ul>
 * Writing both sets therefore lets one ZIP work from 1.21 (format 34) up to 26.3 (format 97.1) and beyond as long
 * as {@code max_format} covers the client.
 *
 * @param description plain-text description
 * @param packFormat  {@code pack_format} for old clients
 * @param minFormat   lowest supported format (major)
 * @param maxFormat   highest supported format (major; any minor revision of it is accepted)
 */
public record PackMeta(@NotNull String description, int packFormat, int minFormat, int maxFormat) {

    /** First resource pack format whose clients understand {@code min_format}/{@code max_format}. */
    public static final int FIRST_RANGE_FORMAT = 65;

    public PackMeta {
        if (minFormat < 1 || maxFormat < minFormat) {
            throw new IllegalArgumentException("invalid format range " + minFormat + ".." + maxFormat);
        }
        if (packFormat < minFormat || packFormat > maxFormat) {
            packFormat = minFormat;
        }
    }

    public @NotNull JsonObject toJson() {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", description);
        if (minFormat < FIRST_RANGE_FORMAT) {
            pack.addProperty("pack_format", packFormat);
            JsonObject supported = new JsonObject();
            supported.add("min_inclusive", new JsonPrimitive(minFormat));
            supported.add("max_inclusive", new JsonPrimitive(maxFormat));
            pack.add("supported_formats", supported);
        }
        if (maxFormat >= FIRST_RANGE_FORMAT) {
            pack.addProperty("min_format", minFormat);
            pack.addProperty("max_format", maxFormat);
        }
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }
}
