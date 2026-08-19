package dev.bookreports.api.event;

import dev.bookreports.storage.model.Report;
import java.util.Objects;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired before a report is persisted. Cancel to veto it, e.g. from an anti-spam plugin. */
public final class ReportCreateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Report draft;
    private boolean cancelled;

    public ReportCreateEvent(Report draft) {
        this.draft = Objects.requireNonNull(draft, "draft");
    }

    /** The not-yet-persisted report; {@code id} and {@code claimVersion} are still {@code 0}. */
    public Report draft() {
        return draft;
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
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
