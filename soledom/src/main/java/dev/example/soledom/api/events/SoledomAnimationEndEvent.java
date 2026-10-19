package dev.example.soledom.api.events;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.api.AnimationDefinition;
import dev.example.soledom.api.ModelController;

/** Fired when an animation stops playing, for whatever reason. */
public final class SoledomAnimationEndEvent extends SoledomAnimationEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Reason reason;

    public SoledomAnimationEndEvent(@NotNull ModelController model, @NotNull String animationName,
            @NotNull AnimationDefinition animation, @NotNull Reason reason) {
        super(model, animationName, animation);
        this.reason = reason;
    }

    public @NotNull Reason getReason() {
        return reason;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }

    public enum Reason {
        /** A non-looping animation reached its end. */
        FINISHED,
        /** Stopped through a command or the API. */
        STOPPED,
        /** The same animation was played again and restarted. */
        REPLACED,
        /** The model was removed or rebuilt. */
        MODEL_REMOVED,
        /** The model's chunk unloaded. */
        UNLOADED,
        /** The plugin is disabling. */
        SHUTDOWN
    }
}
