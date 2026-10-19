package dev.example.soledom.animation;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import dev.example.soledom.api.Vec3;

/**
 * Value of a channel at a point in time.
 *
 * @param time  seconds since the animation started
 * @param value channel value at that time
 */
public record Keyframe(double time, @NotNull Vec3 value) {

    public Keyframe {
        Objects.requireNonNull(value, "value");
    }
}
