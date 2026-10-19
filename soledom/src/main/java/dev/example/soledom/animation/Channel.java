package dev.example.soledom.animation;

import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Transform component an animation track drives. */
public enum Channel {
    /** Offset in blocks added to the part position. JSON names: {@code translation}, {@code position}. */
    TRANSLATION,
    /** Rotation in degrees (X, Y, Z) applied after the part's rest rotation. */
    ROTATION,
    /** Scale multiplied with the part's rest scale. */
    SCALE;

    /** Parses a JSON channel name, returning {@code null} when unknown. */
    public static @Nullable Channel parse(@NotNull String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "translation", "position", "offset" -> TRANSLATION;
            case "rotation" -> ROTATION;
            case "scale" -> SCALE;
            default -> null;
        };
    }
}
