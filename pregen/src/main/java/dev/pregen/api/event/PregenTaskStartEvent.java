package dev.pregen.api.event;

import dev.pregen.api.GenerationRequest;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired asynchronously right before a new task starts requesting chunks. Cancelling it aborts the start. Not fired when
 * a saved task is resumed.
 */
public final class PregenTaskStartEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final GenerationRequest request;
    private final long totalChunks;
    private boolean cancelled;

    public PregenTaskStartEvent(GenerationRequest request, long totalChunks) {
        super(true);
        this.request = request;
        this.totalChunks = totalChunks;
    }

    /** The area about to be generated. */
    public GenerationRequest request() {
        return request;
    }

    /** Chunks in the selection (an estimate for very large selections). */
    public long totalChunks() {
        return totalChunks;
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

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
