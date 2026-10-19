package dev.example.soledom.animation;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dev.example.soledom.api.AnimationDefinition;
import dev.example.soledom.api.Vec3;

/**
 * Playback state of one animation on one model. Not thread-safe: only touched on the model's owning thread.
 */
public final class AnimationPlayer {

    private final String name;
    private final AnimationDefinition definition;
    private final double speed;
    private double time;
    private int loops;
    private boolean paused;
    private boolean finished;

    public AnimationPlayer(@NotNull String name, @NotNull AnimationDefinition definition, double speed) {
        this.name = Objects.requireNonNull(name, "name");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.speed = speed > 0 ? speed : 1.0;
    }

    /**
     * Advances playback.
     *
     * @param seconds real seconds elapsed since the last advance
     * @return what happened during this step
     */
    public @NotNull Step advance(double seconds) {
        if (paused || finished) {
            return Step.RUNNING;
        }
        double duration = definition.duration();
        time += seconds * speed;
        if (duration <= 0) {
            finished = !definition.loop();
            return finished ? Step.FINISHED : Step.RUNNING;
        }
        if (time < duration) {
            return Step.RUNNING;
        }
        if (definition.loop()) {
            time %= duration;
            loops++;
            return Step.LOOPED;
        }
        time = duration;
        finished = true;
        return Step.FINISHED;
    }

    /** Value of the given part channel at the current time, or {@code null} if this animation doesn't drive it. */
    public @Nullable Vec3 sample(@NotNull String partId, @NotNull Channel channel) {
        for (TransformTrack track : definition.tracks()) {
            if (track.channel() == channel && track.part().equals(partId)) {
                return track.sample(time);
            }
        }
        return null;
    }

    public @NotNull String name() {
        return name;
    }

    public @NotNull AnimationDefinition definition() {
        return definition;
    }

    public int priority() {
        return definition.priority();
    }

    public double time() {
        return time;
    }

    public int loops() {
        return loops;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isFinished() {
        return finished;
    }

    public enum Step {
        RUNNING,
        LOOPED,
        FINISHED
    }
}
