package dev.example.soledom.core.utils;

import java.util.Locale;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Validation and normalisation of {@code namespace:path} identifiers (same charset as vanilla resource ids). */
public final class NamespacedIds {

    public static final String DEFAULT_NAMESPACE = "soledom";

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    private NamespacedIds() {
    }

    /**
     * Lower-cases {@code raw} and adds {@code defaultNamespace} when missing.
     *
     * @return the normalised id, or {@code null} if it contains invalid characters
     */
    public static @Nullable String normalize(@Nullable String raw, @NotNull String defaultNamespace) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        int colon = value.indexOf(':');
        String namespace = colon < 0 ? defaultNamespace : value.substring(0, colon);
        String path = colon < 0 ? value : value.substring(colon + 1);
        if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches()
                || path.startsWith("/") || path.endsWith("/") || path.contains("..")) {
            return null;
        }
        return namespace + ':' + path;
    }

    public static boolean isValid(@Nullable String raw) {
        return raw != null && raw.indexOf(':') > 0 && normalize(raw, DEFAULT_NAMESPACE) != null
                && normalize(raw, DEFAULT_NAMESPACE).equals(raw);
    }

    public static @NotNull String namespace(@NotNull String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft" : id.substring(0, colon);
    }

    public static @NotNull String path(@NotNull String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }
}
