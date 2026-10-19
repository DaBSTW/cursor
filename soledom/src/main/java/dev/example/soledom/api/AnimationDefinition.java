package dev.example.soledom.api;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import dev.example.soledom.animation.TransformTrack;

/**
 * Immutable keyframe animation.
 *
 * @param id         namespaced id, e.g. {@code soledom:example_idle}
 * @param duration   total length in seconds
 * @param loop       restart from the beginning when the end is reached
 * @param priority   higher priorities win when several animations drive the same part channel
 * @param resetOnEnd return parts to their rest pose when a non-looping animation finishes
 * @param tracks     per-part, per-channel keyframe tracks
 */
public record AnimationDefinition(@NotNull String id, double duration, boolean loop, int priority,
        boolean resetOnEnd, @NotNull List<TransformTrack> tracks) {

    public AnimationDefinition {
        Objects.requireNonNull(id, "id");
        tracks = List.copyOf(tracks);
    }
}
