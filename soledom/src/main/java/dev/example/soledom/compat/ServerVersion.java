package dev.example.soledom.compat;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

/**
 * Parsed Minecraft version of the running server.
 *
 * <p>Handles both the classic {@code 1.21.4} scheme and the year-based scheme introduced in 2026
 * ({@code 26.1}, {@code 26.3.1}). Because {@code 26 > 1}, plain numeric comparison keeps working across the
 * scheme change. Raw strings such as {@code 1.21.4-R0.1-SNAPSHOT} or {@code 26.3.build.205-stable} are accepted
 * too: only the leading {@code major.minor[.patch]} triple is read.</p>
 */
public final class ServerVersion implements Comparable<ServerVersion> {

    /** Returned when the version string cannot be parsed. Compares lower than every real version. */
    public static final ServerVersion UNKNOWN = new ServerVersion(0, 0, 0, "unknown");

    /** Newest Paper line this build was verified against. Newer versions run through capability detection. */
    public static final ServerVersion NEWEST_TESTED = new ServerVersion(26, 3, 0, "26.3");

    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private final int major;
    private final int minor;
    private final int patch;
    private final String raw;

    private ServerVersion(int major, int minor, int patch, @NotNull String raw) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.raw = raw;
    }

    public static @NotNull ServerVersion of(int major, int minor, int patch) {
        return new ServerVersion(major, minor, patch, major + "." + minor + (patch > 0 ? "." + patch : ""));
    }

    /** Parses a version string, returning {@link #UNKNOWN} rather than throwing when it is unrecognisable. */
    public static @NotNull ServerVersion parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        Matcher matcher = VERSION.matcher(raw.trim());
        if (!matcher.find()) {
            return UNKNOWN;
        }
        try {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            String patchGroup = matcher.group(3);
            // "26.3.build.205" style strings never put a number right after the minor, so patch stays 0.
            int patch = patchGroup == null ? 0 : Integer.parseInt(patchGroup);
            return new ServerVersion(major, minor, patch, raw.trim());
        } catch (NumberFormatException overflow) {
            return UNKNOWN;
        }
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    public @NotNull String raw() {
        return raw;
    }

    public boolean isKnown() {
        return this != UNKNOWN && (major | minor | patch) != 0;
    }

    public boolean isAtLeast(int major, int minor) {
        return isAtLeast(major, minor, 0);
    }

    public boolean isAtLeast(int major, int minor, int patch) {
        return compareTo(of(major, minor, patch)) >= 0;
    }

    /** True when the server is newer than anything this build was verified against. */
    public boolean isNewerThanTested() {
        return compareTo(NEWEST_TESTED) > 0 && !(major == 26 && minor == 3);
    }

    @Override
    public int compareTo(@NotNull ServerVersion other) {
        int cmp = Integer.compare(major, other.major);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(minor, other.minor);
        return cmp != 0 ? cmp : Integer.compare(patch, other.patch);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ServerVersion v && v.major == major && v.minor == minor && v.patch == patch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public String toString() {
        return isKnown() ? major + "." + minor + (patch > 0 ? "." + patch : "") : "unknown";
    }
}
