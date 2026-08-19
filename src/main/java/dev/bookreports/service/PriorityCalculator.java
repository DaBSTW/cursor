package dev.bookreports.service;

import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.PriorityEscalationSettings;
import dev.bookreports.config.ReportCategory;
import dev.bookreports.storage.model.Priority;
import dev.bookreports.storage.model.Report;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Resolves a report's priority: the category's base value, escalated to {@code priority-escalation.escalate-to} when
 * enough distinct reporters converge on the same target and category within the configured window (SPECS.md §8.4).
 */
public final class PriorityCalculator {

    private final Supplier<BookReportsConfig> config;
    private final Clock clock;

    public PriorityCalculator(Supplier<BookReportsConfig> config, Clock clock) {
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * @param existingReportsForTarget
     *            every known report against the same target, e.g. from
     *            {@link dev.bookreports.storage.dao.ReportDao#findByTarget}; only pending/in-review reports within the
     *            escalation window and matching category are counted.
     */
    public Priority calculate(ReportCategory category, UUID newReporterUuid, List<Report> existingReportsForTarget) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(newReporterUuid, "newReporterUuid");
        Objects.requireNonNull(existingReportsForTarget, "existingReportsForTarget");

        PriorityEscalationSettings escalation = config.get().priorityEscalation();
        Instant windowStart = clock.instant().minusSeconds(escalation.windowSeconds());

        Set<UUID> distinctReporters = new HashSet<>();
        distinctReporters.add(newReporterUuid);
        for (Report report : existingReportsForTarget) {
            if (report.categoryId().equals(category.id()) && !report.isResolved()
                    && !report.createdAt().isBefore(windowStart)) {
                distinctReporters.add(report.reporterUuid());
            }
        }

        return distinctReporters.size() >= escalation.distinctReportersThreshold()
                ? escalation.escalateTo()
                : category.priority();
    }
}
