package dev.bookreports.api.event;

import dev.bookreports.storage.model.Report;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired when a staff member claims a report. */
public final class ReportClaimedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Report report;
    private final UUID reviewerUuid;

    public ReportClaimedEvent(Report report, UUID reviewerUuid) {
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
