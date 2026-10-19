package dev.example.soledom.api;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

/** Immutable 3-component vector used by model and animation definitions. */
public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public static final Vec3 ONE = new Vec3(1, 1, 1);

    public static @NotNull Vec3 uniform(double value) {
        return new Vec3(value, value, value);
    }

    public @NotNull Vec3 add(@NotNull Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public @NotNull Vec3 multiply(@NotNull Vec3 other) {
        return new Vec3(x * other.x, y * other.y, z * other.z);
    }

    /** Linear interpolation; {@code t = 0} returns this, {@code t = 1} returns {@code to}. */
    public @NotNull Vec3 lerp(@NotNull Vec3 to, double t) {
        return new Vec3(x + (to.x - x) * t, y + (to.y - y) * t, z + (to.z - z) * t);
    }

    public @NotNull Vector3f toVector3f() {
        return new Vector3f((float) x, (float) y, (float) z);
    }

    public double[] toArray() {
        return new double[] {x, y, z};
    }
}
