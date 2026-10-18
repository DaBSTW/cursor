package dev.pregen.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired asynchronously when a user confirms a trim, before it is written to the pending list that runs at the next
 * startup. Cancelling it discards the trim.
 */
public final class PregenTrimScheduledEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String worldName;
    private final boolean deleteInside;
    private final long estimatedChunks;
    private boolean cancelled;

    public PregenTrimScheduledEvent(String worldName, boolean deleteInside, long estimatedChunks) {
        super(true);
        this.worldName = worldName;
        this.deleteInside = deleteInside;
        this.estimatedChunks = estimatedChunks;
    }

    public String worldName() {
        return worldName;
    }

    /** True when chunks inside the selection are deleted, false when chunks outside it are. */
    public boolean deleteInside() {
        return deleteInside;
    }

    /** Upper bound of chunks that will be deleted, from the dry run. */
    public long estimatedChunks() {
        return estimatedChunks;
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
