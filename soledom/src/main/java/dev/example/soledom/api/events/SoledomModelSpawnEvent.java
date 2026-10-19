package dev.example.soledom.api.events;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dev.example.soledom.api.ModelDefinition;

/** Fired before a new model instance is spawned (not when a persisted model reloads). Cancellable. */
public final class SoledomModelSpawnEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ModelDefinition model;
    private final Location location;
    private final Entity attachTarget;
    private boolean cancelled;

    public SoledomModelSpawnEvent(@NotNull ModelDefinition model, @NotNull Location location,
            @Nullable Entity attachTarget) {
        this.model = model;
        this.location = location;
        this.attachTarget = attachTarget;
    }

    public @NotNull ModelDefinition getModel() {
        return model;
    }

    public @NotNull Location getLocation() {
        return location.clone();
    }

    public @Nullable Entity getAttachTarget() {
        return attachTarget;
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
