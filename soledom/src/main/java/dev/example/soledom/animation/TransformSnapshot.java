package dev.example.soledom.animation;

import java.util.Objects;

import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import dev.example.soledom.api.ModelDefinition;
import dev.example.soledom.api.PartDefinition;
import dev.example.soledom.api.Vec3;

/**
 * Animated delta for one part at one instant: a translation offset, an extra rotation and a scale factor.
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
     * Builds the display transformation for {@code part} of {@code model} with this delta applied.
     *
     * <p>Local pose: {@code T(position + Δt) · R(rotation) · R(Δr) · S(scale ⊙ Δs)}. The model-level offset,
     * rotation and scale are then applied around the model origin.</p>
     */
    public @NotNull Transformation toTransformation(@NotNull ModelDefinition model, @NotNull PartDefinition part) {
        Vector3f localTranslation = part.position().add(translation).toVector3f();
        Quaternionf localRotation = eulerDegrees(part.rotation()).mul(eulerDegrees(rotation));
        Vector3f localScale = part.scale().multiply(scale).toVector3f();

        Quaternionf modelRotation = eulerDegrees(model.rotation());
        Vector3f modelScale = model.scale().toVector3f();

        Vector3f finalTranslation = localTranslation.mul(modelScale).rotate(modelRotation)
                .add(model.offset().toVector3f());
        Quaternionf finalRotation = new Quaternionf(modelRotation).mul(localRotation);
        Vector3f finalScale = localScale.mul(modelScale);
        return new Transformation(finalTranslation, finalRotation, finalScale, new Quaternionf());
    }

    /** Euler angles in degrees, applied X then Y then Z around fixed axes (matches Blockbench). */
    static @NotNull Quaternionf eulerDegrees(@NotNull Vec3 degrees) {
        return new Quaternionf().rotationZYX((float) Math.toRadians(degrees.z()), (float) Math.toRadians(degrees.y()),
                (float) Math.toRadians(degrees.x()));
    }
}
