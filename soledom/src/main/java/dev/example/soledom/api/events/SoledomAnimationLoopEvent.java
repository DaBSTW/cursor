package dev.example.soledom.api.events;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.api.AnimationDefinition;
import dev.example.soledom.api.ModelController;

/** Fired each time a looping animation wraps back to its start. */
public final class SoledomAnimationLoopEvent extends SoledomAnimationEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int loopCount;

    public SoledomAnimationLoopEvent(@NotNull ModelController model, @NotNull String animationName,
            @NotNull AnimationDefinition animation, int loopCount) {
        super(model, animationName, animation);
        this.loopCount = loopCount;
    }

    /** Number of completed loops, starting at 1. */
    public int getLoopCount() {
        return loopCount;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
