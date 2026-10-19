package dev.example.soledom.animation;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import dev.example.soledom.api.Vec3;

/**
 * Keyframes for one channel of one part. Keyframes are sorted by time (enforced by the loader and here).
 *
 * @param part          part id the track drives
 * @param channel       transform component
 * @param interpolation blend mode between keyframes
 * @param keyframes     keyframes sorted by ascending time, at least one
 */
public record TransformTrack(@NotNull String part, @NotNull Channel channel, @NotNull Interpolation interpolation,
        @NotNull List<Keyframe> keyframes) {

    public TransformTrack {
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(interpolation, "interpolation");
        keyframes = List.copyOf(keyframes);
        if (keyframes.isEmpty()) {
            throw new IllegalArgumentException("track needs at least one keyframe");
        }
        for (int i = 1; i < keyframes.size(); i++) {
            if (keyframes.get(i).time() < keyframes.get(i - 1).time()) {
                throw new IllegalArgumentException("keyframes must be sorted by time");
            }
        }
    }

    /** Samples the track at {@code time} seconds. Values are clamped to the first/last keyframe outside range. */
    public @NotNull Vec3 sample(double time) {
        Keyframe first = keyframes.get(0);
        if (time <= first.time()) {
            return first.value();
        }
        Keyframe last = keyframes.get(keyframes.size() - 1);
        if (time >= last.time()) {
            return last.value();
        }
        int hi = upperIndex(time);
        Keyframe a = keyframes.get(hi - 1);
        Keyframe b = keyframes.get(hi);
        if (interpolation == Interpolation.STEP) {
            return a.value();
        }
        double span = b.time() - a.time();
        double t = span <= 0 ? 1.0 : (time - a.time()) / span;
        return a.value().lerp(b.value(), t);
    }

    /** Binary search for the first keyframe strictly after {@code time}; caller guarantees it exists. */
    private int upperIndex(double time) {
        int lo = 0;
        int hi = keyframes.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (keyframes.get(mid).time() <= time) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
