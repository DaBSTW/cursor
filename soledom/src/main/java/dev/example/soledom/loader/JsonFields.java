package dev.example.soledom.loader;

import java.util.Locale;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import dev.example.soledom.api.Vec3;

/** Typed, error-collecting accessors over Gson objects. Each method records a problem instead of throwing. */
final class JsonFields {

    private final Problems problems;

    JsonFields(@NotNull Problems problems) {
        this.problems = problems;
    }

    static @NotNull String join(@NotNull String parent, @NotNull String key) {
        return parent.isEmpty() ? key : parent + "." + key;
    }

    void warnUnknownKeys(@NotNull JsonObject object, @NotNull String path, @NotNull Set<String> known) {
        for (String key : object.keySet()) {
            if (!known.contains(key)) {
                problems.warn(join(path, key), "unknown field (typo?) — ignored");
            }
        }
    }

    @Nullable String string(@NotNull JsonObject obj, @NotNull String key, @NotNull String path, boolean required) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            if (required) {
                problems.error(join(path, key), "required string is missing");
            }
            return null;
        }
        if (!isPrimitive(element, PrimitiveKind.STRING)) {
            problems.error(join(path, key), "expected a string");
            return null;
        }
        return element.getAsString();
    }

    double number(@NotNull JsonObject obj, @NotNull String key, @NotNull String path, double fallback) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!isPrimitive(element, PrimitiveKind.NUMBER)) {
            problems.error(join(path, key), "expected a number");
            return fallback;
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value)) {
            problems.error(join(path, key), "must be a finite number");
            return fallback;
        }
        return value;
    }

    @Nullable Double optionalNumber(@NotNull JsonObject obj, @NotNull String key, @NotNull String path) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        double value = number(obj, key, path, Double.NaN);
        return Double.isNaN(value) ? null : value;
    }

    @Nullable Integer optionalInt(@NotNull JsonObject obj, @NotNull String key, @NotNull String path) {
        Double value = optionalNumber(obj, key, path);
        if (value == null) {
            return null;
        }
        if (value != Math.rint(value) || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            problems.error(join(path, key), "expected a whole number");
            return null;
        }
        return value.intValue();
    }

    boolean bool(@NotNull JsonObject obj, @NotNull String key, @NotNull String path, boolean fallback) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!isPrimitive(element, PrimitiveKind.BOOLEAN)) {
            problems.error(join(path, key), "expected true or false");
            return fallback;
        }
        return element.getAsBoolean();
    }

    @Nullable JsonObject object(@NotNull JsonObject obj, @NotNull String key, @NotNull String path) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonObject()) {
            problems.error(join(path, key), "expected an object");
            return null;
        }
        return element.getAsJsonObject();
    }

    @Nullable JsonArray array(@NotNull JsonObject obj, @NotNull String key, @NotNull String path, boolean required) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            if (required) {
                problems.error(join(path, key), "required array is missing");
            }
            return null;
        }
        if (!element.isJsonArray()) {
            problems.error(join(path, key), "expected an array");
            return null;
        }
        return element.getAsJsonArray();
    }

    /**
     * Reads {@code [x, y, z]}. When {@code allowScalar} is true a single number {@code n} means {@code [n, n, n]}.
     */
    @NotNull Vec3 vec3(@NotNull JsonObject obj, @NotNull String key, @NotNull String path, @NotNull Vec3 fallback,
            boolean allowScalar) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        Vec3 parsed = vec3(element, join(path, key), allowScalar);
        return parsed == null ? fallback : parsed;
    }

    @Nullable Vec3 vec3(@NotNull JsonElement element, @NotNull String path, boolean allowScalar) {
        if (allowScalar && isPrimitive(element, PrimitiveKind.NUMBER)) {
            double v = element.getAsDouble();
            if (!Double.isFinite(v)) {
                problems.error(path, "must be a finite number");
                return null;
            }
            return Vec3.uniform(v);
        }
        if (!element.isJsonArray() || element.getAsJsonArray().size() != 3) {
            problems.error(path, allowScalar ? "expected a number or an array of 3 numbers"
                    : "expected an array of 3 numbers like [0.0, 1.0, 0.0]");
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        double[] values = new double[3];
        for (int i = 0; i < 3; i++) {
            JsonElement item = array.get(i);
            if (!isPrimitive(item, PrimitiveKind.NUMBER) || !Double.isFinite(item.getAsDouble())) {
                problems.error(path + "[" + i + "]", "expected a finite number");
                return null;
            }
            values[i] = item.getAsDouble();
        }
        return new Vec3(values[0], values[1], values[2]);
    }

    /** Reads an enum name (case-insensitive), recording an error listing the accepted values. */
    <E extends Enum<E>> @Nullable E enumValue(@NotNull JsonObject obj, @NotNull String key, @NotNull String path,
            @NotNull Class<E> type, @Nullable E fallback) {
        String raw = string(obj, key, path, fallback == null);
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            problems.error(join(path, key), "'" + raw + "' is not one of " + names(type));
            return fallback;
        }
    }

    /** Validates a string against a fixed set of upper-case names, returning the canonical name. */
    @NotNull String choice(@NotNull JsonObject obj, @NotNull String key, @NotNull String path, @NotNull Set<String> allowed,
            @NotNull String fallback) {
        String raw = string(obj, key, path, false);
        if (raw == null) {
            return fallback;
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(upper)) {
            problems.error(join(path, key), "'" + raw + "' is not one of " + allowed);
            return fallback;
        }
        return upper;
    }

    /** Parses {@code #RRGGBB} (alpha forced opaque) or {@code #AARRGGBB}. */
    @Nullable Integer color(@NotNull JsonObject obj, @NotNull String key, @NotNull String path) {
        String raw = string(obj, key, path, false);
        if (raw == null) {
            return null;
        }
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        if (!hex.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) {
            problems.error(join(path, key), "expected a colour like #RRGGBB or #AARRGGBB");
            return null;
        }
        long value = Long.parseLong(hex, 16);
        return (int) (hex.length() == 6 ? 0xFF000000L | value : value);
    }

    private static <E extends Enum<E>> String names(Class<E> type) {
        StringBuilder sb = new StringBuilder("[");
        for (E constant : type.getEnumConstants()) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(constant.name());
        }
        return sb.append(']').toString();
    }

    private static boolean isPrimitive(JsonElement element, PrimitiveKind kind) {
        if (!element.isJsonPrimitive()) {
            return false;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        return switch (kind) {
            case STRING -> primitive.isString();
            case NUMBER -> primitive.isNumber();
            case BOOLEAN -> primitive.isBoolean();
        };
    }

    private enum PrimitiveKind {
        STRING,
        NUMBER,
        BOOLEAN
    }
}
