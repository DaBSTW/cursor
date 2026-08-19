package dev.bookreports.service;

import java.util.Objects;

/** Thrown by {@link ReportService#submitReport} when an anti-abuse rule or a listener rejects a report. */
public final class ReportRejectedException extends RuntimeException {

    public enum Reason {
        SELF_REPORT, DUPLICATE_PENDING, COOLDOWN, DAILY_LIMIT, CANCELLED
    }

    private final Reason reason;

    /**
     * Extra context for the caller to render the right message: a {@link java.time.Duration} for
     * {@link Reason#COOLDOWN}, a {@link dev.bookreports.storage.model.Report} for {@link Reason#DUPLICATE_PENDING}, an
     * {@link Integer} limit for {@link Reason#DAILY_LIMIT}, {@code null} otherwise.
     */
    private final transient Object detail;

    public ReportRejectedException(Reason reason, String message) {
        this(reason, message, null);
    }

    public ReportRejectedException(Reason reason, String message, Object detail) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.detail = detail;
    }

    public Reason reason() {
        return reason;
    }

    public Object detail() {
        return detail;
    }
}
