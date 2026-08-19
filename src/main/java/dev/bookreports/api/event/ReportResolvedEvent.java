package dev.bookreports.api.event;

import dev.bookreports.storage.model.Report;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a report is resolved to a terminal, non-false status. See {@link ReportFalseMarkedEvent} for false
 * reports.
 */
public final class ReportResolvedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Report report;
    private final UUID reviewerUuid;

    public ReportResolvedEvent(Report report, UUID reviewerUuid) {
        this.report = Objects.requireNonNull(report, "report");
        this.reviewerUuid = Objects.requireNonNull(reviewerUuid, "reviewerUuid");
    }

    public Report report() {
        return report;
    }

    public UUID reviewerUuid() {
        return reviewerUuid;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
