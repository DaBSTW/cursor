package dev.bookreports.service;

import dev.bookreports.api.event.ReportClaimedEvent;
import dev.bookreports.api.event.ReportCreateEvent;
import dev.bookreports.api.event.ReportCreatedEvent;
import dev.bookreports.api.event.ReportFalseMarkedEvent;
import dev.bookreports.api.event.ReportResolvedEvent;
import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.FalseReportPenaltySettings;
import dev.bookreports.config.ReportCategory;
import dev.bookreports.storage.StorageException;
import dev.bookreports.storage.dao.PenaltyDao;
import dev.bookreports.storage.dao.ReportDao;
import dev.bookreports.storage.model.Priority;
import dev.bookreports.storage.model.Report;
import dev.bookreports.storage.model.ReportPenalty;
import dev.bookreports.storage.model.ReportStatus;
import dev.bookreports.util.SchedulerAdapter;
import dev.bookreports.util.TextSanitizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.plugin.PluginManager;

/**
 * Orchestrates report submission and resolution: validation, anti-abuse rules, persistence and the public
 * {@link dev.bookreports.api.event} lifecycle.
 *
 * <p>
 * Deliberately Bukkit-light: it depends on {@link PluginManager} only to fire events (already an interface, faked
 * easily in tests) and never touches {@code Bukkit.*} statics, so it is unit-testable without a full server. Callers
 * resolve player names/target existence themselves — see {@link #submitReport}.
 */
public final class ReportService {

    private final ReportDao reportDao;
    private final PenaltyDao penaltyDao;
    private final CooldownService cooldownService;
    private final DailyLimitService dailyLimitService;
    private final PriorityCalculator priorityCalculator;
    private final Supplier<BookReportsConfig> config;
    private final PluginManager pluginManager;
    private final SchedulerAdapter scheduler;
    private final Executor executor;
    private final Clock clock;
    private final Logger logger;

    public ReportService(ReportDao reportDao, PenaltyDao penaltyDao, CooldownService cooldownService,
            DailyLimitService dailyLimitService, PriorityCalculator priorityCalculator,
            Supplier<BookReportsConfig> config, PluginManager pluginManager, SchedulerAdapter scheduler,
            Executor executor, Clock clock, Logger logger) {
        this.reportDao = Objects.requireNonNull(reportDao, "reportDao");
        this.penaltyDao = Objects.requireNonNull(penaltyDao, "penaltyDao");
        this.cooldownService = Objects.requireNonNull(cooldownService, "cooldownService");
        this.dailyLimitService = Objects.requireNonNull(dailyLimitService, "dailyLimitService");
        this.priorityCalculator = Objects.requireNonNull(priorityCalculator, "priorityCalculator");
        this.config = Objects.requireNonNull(config, "config");
        this.pluginManager = Objects.requireNonNull(pluginManager, "pluginManager");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Validates, persists and announces a new report. Whether {@code request.targetUuid()} actually exists is the
     * caller's responsibility — this service operates purely on the ids/names it is given.
     *
     * <p>
     * Safe to call from any thread; work happens on the executor passed to the constructor. The returned future
     * completes exceptionally with {@link ReportRejectedException} when a cooldown, the daily limit, a duplicate
     * pending report or a cancelled {@link ReportCreateEvent} rejects the submission.
     */
    public CompletableFuture<Report> submitReport(SubmitReportRequest request) {
        Objects.requireNonNull(request, "request");
        CompletableFuture<Report> result = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                fireCreateEventThenPersist(validateAndBuildDraft(request), result);
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    private Report validateAndBuildDraft(SubmitReportRequest request) {
        BookReportsConfig cfg = config.get();
        UUID reporterUuid = request.reporterUuid();
        UUID targetUuid = request.targetUuid();

        if (cfg.preventSelfReport() && reporterUuid.equals(targetUuid)) {
            throw new ReportRejectedException(ReportRejectedException.Reason.SELF_REPORT,
                    "Reporter cannot report themselves: reporter=" + reporterUuid);
        }

        List<Report> existingForTarget = reportDao.findByTarget(targetUuid);

        if (cfg.preventDuplicatePending()) {
            Optional<Report> duplicate = existingForTarget.stream().filter(r -> r.reporterUuid().equals(reporterUuid))
                    .filter(r -> !r.isResolved()).findFirst();
            if (duplicate.isPresent()) {
                throw new ReportRejectedException(ReportRejectedException.Reason.DUPLICATE_PENDING,
                        "Duplicate pending report: reporter=" + reporterUuid + " target=" + targetUuid,
                        duplicate.get());
            }
        }

        Optional<Duration> cooldown = cooldownService.remainingCooldown(reporterUuid);
        if (cooldown.isPresent()) {
            throw new ReportRejectedException(ReportRejectedException.Reason.COOLDOWN,
                    "Reporter is on cooldown: reporter=" + reporterUuid, cooldown.get());
        }

        if (dailyLimitService.hasReachedDailyLimit(reporterUuid)) {
            throw new ReportRejectedException(ReportRejectedException.Reason.DAILY_LIMIT,
                    "Reporter hit daily limit: reporter=" + reporterUuid, cfg.dailyLimit());
        }

        ReportCategory category = cfg.category(request.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown category id=" + request.categoryId()));
        Priority priority = priorityCalculator.calculate(category, reporterUuid, existingForTarget);
        // Re-sanitized here (not just in the anvil GUI): BookReportsAPI lets other plugins call submitReport
        // directly, bypassing that GUI entirely — SPECS.md §13 requires this defense in depth.
        String evidenceText = TextSanitizer.stripAndTruncate(request.evidenceText(), TextSanitizer.EVIDENCE_MAX_LENGTH);

        return new Report(0, UUID.randomUUID(), reporterUuid, request.reporterName(), targetUuid, request.targetName(),
                request.categoryId(), request.subReasonId(), evidenceText, request.server(), ReportStatus.PENDING,
                priority, null, null, clock.instant(), null, null, 0);
    }

    private void fireCreateEventThenPersist(Report draft, CompletableFuture<Report> result) {
        scheduler.runGlobal(() -> {
            ReportCreateEvent event = new ReportCreateEvent(draft);
            pluginManager.callEvent(event);
            boolean cancelled = event.isCancelled();
            executor.execute(() -> {
                if (cancelled) {
                    result.completeExceptionally(new ReportRejectedException(ReportRejectedException.Reason.CANCELLED,
                            "Report vetoed by a ReportCreateEvent listener: reporter=" + draft.reporterUuid()));
                    return;
                }
                persistAndAnnounce(draft, result);
            });
        });
    }

    private void persistAndAnnounce(Report draft, CompletableFuture<Report> result) {
        try {
            Report persisted = reportDao.insert(draft);
            cooldownService.recordReport(draft.reporterUuid(), resolveCooldownDuration(draft.reporterUuid()));
            dailyLimitService.invalidate(draft.reporterUuid());
            logger.info("Report created: id=" + persisted.id() + " reporter=" + persisted.reporterUuid() + " target="
                    + persisted.targetUuid() + " category=" + persisted.categoryId() + " priority="
                    + persisted.priority());
            scheduler.runGlobal(() -> pluginManager.callEvent(new ReportCreatedEvent(persisted)));
            result.complete(persisted);
        } catch (RuntimeException e) {
            result.completeExceptionally(e);
        }
    }

    private Duration resolveCooldownDuration(UUID reporterUuid) {
        BookReportsConfig cfg = config.get();
        Duration base = Duration.ofSeconds(cfg.cooldownSeconds());
        FalseReportPenaltySettings penalty = cfg.falseReportPenalty();
        if (!penalty.enabled()) {
            return base;
        }
        Instant since = clock.instant().minus(Duration.ofDays(30));
        int falseReports = penaltyDao.countByPlayerSince(reporterUuid, "FALSE_REPORT", since);
        return falseReports >= penalty.thresholdIn30Days() ? base.multipliedBy(penalty.cooldownMultiplier()) : base;
    }

    public CompletableFuture<List<Report>> getReportHistory(UUID targetUuid) {
        return CompletableFuture.supplyAsync(() -> reportDao.findByTarget(targetUuid), executor);
    }

    public CompletableFuture<Optional<Report>> getReport(UUID reportUuid) {
        return CompletableFuture.supplyAsync(() -> reportDao.findByUuid(reportUuid), executor);
    }

    public CompletableFuture<Optional<Report>> getReportById(long reportId) {
        return CompletableFuture.supplyAsync(() -> reportDao.findById(reportId), executor);
    }

    /** For console tooling (SPECS.md §5.1) — GUI code talks to {@link ReportDao} directly instead. */
    public CompletableFuture<List<Report>> getQueue(ReportStatus status, int page, int pageSize) {
        return CompletableFuture.supplyAsync(() -> reportDao.findByStatus(status, page, pageSize), executor);
    }

    /** Non-blocking, in-memory check — safe to call from the main thread. */
    public boolean isOnCooldown(UUID reporterUuid) {
        return cooldownService.isOnCooldown(reporterUuid);
    }

    /** Non-blocking, in-memory read — safe to call from the main thread. Used to render the book's summary page. */
    public Optional<Duration> remainingCooldown(UUID reporterUuid) {
        return cooldownService.remainingCooldown(reporterUuid);
    }

    /** {@code true} if this reviewer newly claimed the report; {@code false} if someone already had it. */
    public CompletableFuture<Boolean> claim(long reportId, UUID reviewerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            boolean claimed = reportDao.claim(reportId, reviewerUuid, clock.instant());
            if (claimed) {
                reportDao.findById(reportId).ifPresent(report -> scheduler
                        .runGlobal(() -> pluginManager.callEvent(new ReportClaimedEvent(report, reviewerUuid))));
            }
            return claimed;
        }, executor);
    }

    public CompletableFuture<Boolean> resolve(long reportId, ReportStatus status, UUID reviewerUuid,
            String resolutionNote) {
        if (status == ReportStatus.PENDING || status == ReportStatus.IN_REVIEW) {
            throw new IllegalArgumentException("resolve() requires a terminal status, got " + status);
        }
        return CompletableFuture.supplyAsync(() -> {
            boolean updated = reportDao.updateStatus(reportId, status, reviewerUuid, resolutionNote, clock.instant());
            if (updated) {
                reportDao.findById(reportId).ifPresent(report -> scheduler
                        .runGlobal(() -> pluginManager.callEvent(new ReportResolvedEvent(report, reviewerUuid))));
            }
            return updated;
        }, executor);
    }

    /** Resolves the report as {@link ReportStatus#FALSE_REPORT} and records a penalty strike against the reporter. */
    public CompletableFuture<Boolean> markFalse(long reportId, UUID reviewerUuid, String resolutionNote) {
        return CompletableFuture.supplyAsync(() -> {
            boolean updated = reportDao.updateStatus(reportId, ReportStatus.FALSE_REPORT, reviewerUuid, resolutionNote,
                    clock.instant());
            if (updated) {
                Report report = reportDao.findById(reportId).orElseThrow(
                        () -> new StorageException("Report id=" + reportId + " vanished right after updateStatus"));
                penaltyDao.insert(new ReportPenalty(0, report.reporterUuid(), "FALSE_REPORT", clock.instant(), null));
                scheduler.runGlobal(() -> pluginManager.callEvent(new ReportFalseMarkedEvent(report, reviewerUuid)));
            }
            return updated;
        }, executor);
    }

    /**
     * Releases every claim older than {@code staff.claim-timeout-minutes}, back to {@code PENDING}. Intended to be
     * called periodically (Fase 5 wires the schedule alongside the staff panel); safe to call from any thread, work
     * happens on the executor passed to the constructor.
     */
    public CompletableFuture<Integer> releaseStaleClaims() {
        return CompletableFuture.supplyAsync(() -> {
            Instant cutoff = clock.instant().minus(Duration.ofMinutes(config.get().staff().claimTimeoutMinutes()));
            int released = 0;
            for (Report report : reportDao.findStaleClaims(cutoff)) {
                if (reportDao.releaseClaim(report.id(), report.reviewerUuid())) {
                    released++;
                    logger.info("Auto-released stale claim: id=" + report.id() + " reviewer=" + report.reviewerUuid()
                            + " claimedAt=" + report.claimedAt());
                }
            }
            return released;
        }, executor);
    }
}
