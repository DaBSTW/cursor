package dev.example.soledom.api.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.api.AnimationDefinition;
import dev.example.soledom.api.ModelController;

/** Fired before an animation starts. Cancel to prevent it from playing. */
public final class SoledomAnimationStartEvent extends SoledomAnimationEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private boolean cancelled;

    public SoledomAnimationStartEvent(@NotNull ModelController model, @NotNull String animationName,
            @NotNull AnimationDefinition animation) {
        super(model, animationName, animation);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
