package dev.example.soledom.animation;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import dev.example.soledom.api.PartDefinition;
import dev.example.soledom.api.Vec3;

/**
 * Animated delta for one part at one instant: a translation offset, a rotation offset and a scale factor.
 * {@link #IDENTITY} leaves the part in its rest pose.
 */
public record TransformSnapshot(@NotNull Vec3 translation, @NotNull Vec3 rotation, @NotNull Vec3 scale) {

    public static final TransformSnapshot IDENTITY = new TransformSnapshot(Vec3.ZERO, Vec3.ZERO, Vec3.ONE);

    public TransformSnapshot {
        Objects.requireNonNull(translation, "translation");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(scale, "scale");
    }

    public @NotNull TransformSnapshot with(@NotNull Channel channel, @NotNull Vec3 value) {
        return switch (channel) {
            case TRANSLATION -> new TransformSnapshot(value, rotation, scale);
            case ROTATION -> new TransformSnapshot(translation, value, scale);
            case SCALE -> new TransformSnapshot(translation, rotation, value);
        };
    }

    /**
     * Local transform of {@code part} relative to its parent with this delta applied:
     * {@code T(position + Δt) · R(rotation + Δr) · S(scale ⊙ Δs)}.
     *
     * <p>Rotation deltas are added to the rest angles in Euler space (as Blockbench does), not multiplied as
     * quaternions, so keyframe values read in the same space as the part's {@code rotation} field.</p>
     */
    public @NotNull Matrix4f localMatrix(@NotNull PartDefinition part) {
        return new Matrix4f()
                .translation(part.position().add(translation).toVector3f())
                .rotate(eulerDegrees(part.rotation().add(rotation)))
                .scale(part.scale().multiply(scale).toVector3f());
    }

    /** Euler angles in degrees, applied X then Y then Z around fixed axes (matches Blockbench). */
    public static @NotNull Quaternionf eulerDegrees(@NotNull Vec3 degrees) {
        return new Quaternionf().rotationZYX((float) Math.toRadians(degrees.z()), (float) Math.toRadians(degrees.y()),
                (float) Math.toRadians(degrees.x()));
    }
}
