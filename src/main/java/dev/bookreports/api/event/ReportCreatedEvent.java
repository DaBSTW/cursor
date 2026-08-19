package dev.bookreports.api.event;

import dev.bookreports.storage.model.Report;
import java.util.Objects;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after a report is persisted. Not cancelable — the report already exists by this point. */
public final class ReportCreatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Report report;

    public ReportCreatedEvent(Report report) {
        this.report = Objects.requireNonNull(report, "report");
    }

    public Report report() {
        return report;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
