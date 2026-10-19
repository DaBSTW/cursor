package dev.example.soledom.api.events;

import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.api.AnimationDefinition;
import dev.example.soledom.api.ModelController;

/** Base class of animation lifecycle events. Always fired on the thread that owns the model. */
public abstract class SoledomAnimationEvent extends Event {

    private final ModelController model;
    private final String animationName;
    private final AnimationDefinition animation;

    protected SoledomAnimationEvent(@NotNull ModelController model, @NotNull String animationName,
            @NotNull AnimationDefinition animation) {
        this.model = model;
        this.animationName = animationName;
        this.animation = animation;
    }

    public @NotNull ModelController getModel() {
        return model;
    }

    /** Local name the animation was played under (e.g. {@code idle}). */
    public @NotNull String getAnimationName() {
        return animationName;
    }

    public @NotNull AnimationDefinition getAnimation() {
        return animation;
    }
}
