package dev.bookreports.command.internal;

import dev.bookreports.book.BookBuilder;
import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.LocaleManager;
import dev.bookreports.config.ReportCategory;
import dev.bookreports.gui.AnvilInputGUI;
import dev.bookreports.service.ReportRejectedException;
import dev.bookreports.service.ReportService;
import dev.bookreports.service.SubmitReportRequest;
import dev.bookreports.session.ReportSession;
import dev.bookreports.session.ReportState;
import dev.bookreports.session.SessionManager;
import dev.bookreports.session.SessionTransitions;
import dev.bookreports.storage.model.Report;
import dev.bookreports.util.SchedulerAdapter;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /breport:select <sessionId> <optionId>} — the hidden command every book link runs.
 *
 * <p>
 * SPECS.md §13 requires three checks before any click is honored, and all three are mandatory, not optional: (1) a
 * session exists for the executor, (2) its {@code sessionId} matches the one in the command (catches a stale page from
 * a superseded session), and (3) the requested move is in the {@link SessionTransitions} whitelist. Anything that fails
 * any check is logged and rejected — never silently applied "close enough".
 */
public final class SelectOptionCommand implements CommandExecutor {

    private final SessionManager sessions;
    private final Supplier<BookReportsConfig> config;
    private final LocaleManager locale;
    private final BookBuilder books;
    private final ReportService reportService;
    private final AnvilInputGUI anvilInputGUI;
    private final SchedulerAdapter scheduler;
    private final Logger logger;

    public SelectOptionCommand(SessionManager sessions, Supplier<BookReportsConfig> config, LocaleManager locale,
            BookBuilder books, ReportService reportService, AnvilInputGUI anvilInputGUI, SchedulerAdapter scheduler,
            Logger logger) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.config = Objects.requireNonNull(config, "config");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.books = Objects.requireNonNull(books, "books");
        this.reportService = Objects.requireNonNull(reportService, "reportService");
        this.anvilInputGUI = Objects.requireNonNull(anvilInputGUI, "anvilInputGUI");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || args.length != 2) {
            return true;
        }
        UUID sessionId;
        try {
            sessionId = UUID.fromString(args[0]);
        } catch (IllegalArgumentException e) {
            return true;
        }
        String actionId = args[1];

        ReportSession session = sessions.find(player.getUniqueId()).orElse(null);
        if (session == null) {
            logRejected(player, sessionId, null, actionId, "SESSION_NOT_FOUND");
            player.sendMessage(locale.get("session.expired"));
            return true;
        }
        if (!session.sessionId().equals(sessionId)) {
            logRejected(player, sessionId, session.state(), actionId, "SESSION_ID_MISMATCH");
            player.sendMessage(locale.get("session.invalid"));
            return true;
        }
        if (!session.reporterId().equals(player.getUniqueId())) {
            // Structurally unreachable — sessions are keyed by reporter id — but kept as an explicit, logged
            // assertion: SPECS.md §13 requires all three checks to be visibly present, not implied.
            logRejected(player, sessionId, session.state(), actionId, "SESSION_OWNER_MISMATCH");
            player.sendMessage(locale.get("session.owner-mismatch"));
            return true;
        }

        if ("cancel".equals(actionId)) {
            handleCancel(player);
            return true;
        }
        dispatch(player, session, actionId);
        return true;
    }

    private void dispatch(Player player, ReportSession session, String actionId) {
        switch (session.state()) {
            case TARGET_CONFIRM -> handleTargetConfirm(player, session, actionId);
            case CATEGORY -> handleCategory(player, session, actionId);
            case SUBREASON -> handleSubReason(player, session, actionId);
            case EVIDENCE -> handleEvidence(player, session, actionId);
            case SUMMARY -> handleSummary(player, session, actionId);
            default -> rejectTransition(player, session, actionId);
        }
    }

    private void handleTargetConfirm(Player player, ReportSession session, String actionId) {
        if (!"confirm-target".equals(actionId)
                || !SessionTransitions.isAllowed(session.state(), ReportState.CATEGORY)) {
            rejectTransition(player, session, actionId);
            return;
        }
        ReportSession next = session.withState(ReportState.CATEGORY);
        sessions.replace(next);
        books.openCategory(player, next, config.get().categories().values());
    }

    private void handleCategory(Player player, ReportSession session, String actionId) {
        if (!actionId.startsWith("category:")) {
            rejectTransition(player, session, actionId);
            return;
        }
        String categoryId = actionId.substring("category:".length());
        Optional<ReportCategory> category = config.get().category(categoryId);
        ReportState nextState = category.map(c -> c.hasSubReasons() ? ReportState.SUBREASON : ReportState.EVIDENCE)
                .orElse(null);
        if (nextState == null || !SessionTransitions.isAllowed(session.state(), nextState)) {
            rejectTransition(player, session, actionId);
            return;
        }
        ReportSession next = session.withCategory(categoryId, nextState);
        sessions.replace(next);
        if (nextState == ReportState.SUBREASON) {
            books.openSubReason(player, next, category.orElseThrow());
        } else {
            books.openEvidence(player, next);
        }
    }

    private void handleSubReason(Player player, ReportSession session, String actionId) {
        if (!actionId.startsWith("subreason:")) {
            rejectTransition(player, session, actionId);
            return;
        }
        String subReasonId = actionId.substring("subreason:".length());
        ReportCategory category = config.get().category(session.categoryId()).orElse(null);
        boolean valid = category != null && category.subReasons().contains(subReasonId)
                && SessionTransitions.isAllowed(session.state(), ReportState.EVIDENCE);
        if (!valid) {
            rejectTransition(player, session, actionId);
            return;
        }
        ReportSession next = session.withSubReason(subReasonId, ReportState.EVIDENCE);
        sessions.replace(next);
        books.openEvidence(player, next);
    }

    private void handleEvidence(Player player, ReportSession session, String actionId) {
        if (!SessionTransitions.isAllowed(session.state(), ReportState.SUMMARY)) {
            rejectTransition(player, session, actionId);
            return;
        }
        if ("evidence-skip".equals(actionId)) {
            ReportSession next = session.withEvidence(null, ReportState.SUMMARY);
            sessions.replace(next);
            openSummary(player, next);
        } else if ("evidence-add".equals(actionId)) {
            anvilInputGUI.open(player, evidence -> onEvidenceCaptured(player, session, evidence));
        } else {
            rejectTransition(player, session, actionId);
        }
    }

    private void onEvidenceCaptured(Player player, ReportSession expectedSession, Optional<String> evidence) {
        ReportSession current = sessions.find(player.getUniqueId()).orElse(null);
        if (current == null || !current.sessionId().equals(expectedSession.sessionId())
                || current.state() != ReportState.EVIDENCE) {
            return;
        }
        ReportSession next = current.withEvidence(evidence.orElse(null), ReportState.SUMMARY);
        sessions.replace(next);
        openSummary(player, next);
    }

    private void handleSummary(Player player, ReportSession session, String actionId) {
        if (!"confirm-submit".equals(actionId) || !SessionTransitions.isAllowed(session.state(), ReportState.DONE)) {
            rejectTransition(player, session, actionId);
            return;
        }
        submitReport(player, session);
    }

    private void openSummary(Player player, ReportSession session) {
        ReportCategory category = config.get().category(session.categoryId()).orElseThrow();
        books.openSummary(player, session, category, targetName(session),
                reportService.remainingCooldown(player.getUniqueId()));
    }

    private void submitReport(Player player, ReportSession session) {
        String targetName = targetName(session);
        SubmitReportRequest request = new SubmitReportRequest(player.getUniqueId(), player.getName(),
                session.targetId(), targetName, session.categoryId(), session.subReasonId(), session.evidenceText(),
                config.get().serverId());

        reportService.submitReport(request).whenComplete((report, error) -> scheduler.runGlobal(() -> {
            sessions.invalidate(player.getUniqueId());
            if (error == null) {
                books.openResult(player, report);
            } else {
                handleSubmitError(player, error, targetName);
            }
        }));
    }

    private void handleSubmitError(Player player, Throwable error, String targetName) {
        if (!(error instanceof ReportRejectedException rejected)) {
            logger.warning("Unexpected report submission failure: actor=" + player.getUniqueId() + " error=" + error);
            player.sendMessage(locale.get("error.generic"));
            return;
        }
        switch (rejected.reason()) {
            case SELF_REPORT -> player.sendMessage(locale.get("report.self-report-blocked"));
            case DUPLICATE_PENDING -> {
                Report duplicate = (Report) rejected.detail();
                player.sendMessage(locale.get("report.duplicate-pending",
                        Map.of("player", targetName, "ticket_id", String.valueOf(duplicate.id()))));
            }
            case COOLDOWN -> {
                Duration remaining = (Duration) rejected.detail();
                player.sendMessage(
                        locale.get("report.cooldown-active", Map.of("cooldown", remaining.toSeconds() + "s")));
            }
            case DAILY_LIMIT -> player.sendMessage(
                    locale.get("report.daily-limit-reached", Map.of("limit", String.valueOf(rejected.detail()))));
            case CANCELLED -> player.sendMessage(locale.get("error.generic"));
        }
    }

    private void handleCancel(Player player) {
        sessions.invalidate(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(locale.get("report.cancelled"));
    }

    private void rejectTransition(Player player, ReportSession session, String actionId) {
        logRejected(player, session.sessionId(), session.state(), actionId, "INVALID_TRANSITION");
        player.sendMessage(locale.get("session.invalid"));
    }

    private void logRejected(Player player, UUID sessionId, ReportState state, String actionId, String reason) {
        logger.warning("Rejected selection: sessionId=" + sessionId + " actor=" + player.getUniqueId() + " state="
                + state + " action=" + actionId + " reason=" + reason);
    }

    private String targetName(ReportSession session) {
        String name = Bukkit.getOfflinePlayer(session.targetId()).getName();
        return name != null ? name : "Unknown";
    }
}
