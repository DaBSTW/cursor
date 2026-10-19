package dev.example.soledom.animation;

import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** How values between two keyframes are computed. */
public enum Interpolation {
    /** Straight-line blend between neighbouring keyframes. */
    LINEAR,
    /** Hold the previous keyframe's value until the next keyframe is reached. */
    STEP;

    public static @Nullable Interpolation parse(@NotNull String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "linear", "lerp" -> LINEAR;
            case "step", "constant" -> STEP;
            default -> null;
        };
    }
}
