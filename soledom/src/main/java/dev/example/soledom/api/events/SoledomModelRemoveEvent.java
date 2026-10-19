package dev.example.soledom.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.api.ModelController;

/** Fired right before a model instance is permanently removed. */
public final class SoledomModelRemoveEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ModelController model;
    private final Cause cause;

    public SoledomModelRemoveEvent(@NotNull ModelController model, @NotNull Cause cause) {
        this.model = model;
        this.cause = cause;
    }

    public @NotNull ModelController getModel() {
        return model;
    }

    public @NotNull Cause getCause() {
        return cause;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }

    public enum Cause {
        /** Removed through a command or the API. */
        PLUGIN,
        /** The root entity was killed or discarded by something else. */
        ROOT_REMOVED,
        /** The entity the model was attached to died or disappeared. */
        ATTACHED_ENTITY_GONE,
        /** The model definition no longer exists and auto-cleanup is enabled. */
        CLEANUP
    }
}
