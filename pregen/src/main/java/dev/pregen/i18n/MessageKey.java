package dev.pregen.i18n;

/** Every user-facing message. Keys map to paths in {@code lang/<language>.yml}. */
public enum MessageKey {
    PREFIX("prefix"),

    PLAYER_ONLY("general.player-only"),
    INVALID_RADIUS("general.invalid-radius"),
    INVALID_SHAPE("general.invalid-shape"),
    INVALID_PATTERN("general.invalid-pattern"),
    INVALID_FILE_NAME("general.invalid-file-name"),
    WORLD_NOT_FOUND("general.world-not-found"),
    NO_TASK_FOR_WORLD("general.no-task-for-world"),
    INTERNAL_ERROR("general.internal-error"),

    HELP_HEADER("help.header"),
    HELP_START("help.start"),
    HELP_PAUSE("help.pause"),
    HELP_RESUME("help.resume"),
    HELP_CANCEL("help.cancel"),
    HELP_CONFIRM("help.confirm"),
    HELP_STATUS("help.status"),
    HELP_WATCH("help.watch"),
    HELP_SELECTION("help.selection"),
    HELP_WORLD("help.world"),
    HELP_CENTER("help.center"),
    HELP_RADIUS("help.radius"),
    HELP_CORNERS("help.corners"),
    HELP_WORLDBORDER("help.worldborder"),
    HELP_SHAPE("help.shape"),
    HELP_PATTERN("help.pattern"),
    HELP_TRIM("help.trim"),
    HELP_SILENT("help.silent"),
    HELP_INTERVAL("help.interval"),
    HELP_DOCTOR("help.doctor"),
    HELP_RELOAD("help.reload"),

    SELECTION_HEADER("selection.header"),
    SELECTION_DETAILS("selection.details"),
    SELECTION_WORLD("selection.world"),
    SELECTION_CENTER("selection.center"),
    SELECTION_RADIUS("selection.radius"),
    SELECTION_RADII("selection.radii"),
    SELECTION_SHAPE("selection.shape"),
    SELECTION_PATTERN("selection.pattern"),
    SELECTION_NO_BORDER("selection.no-border"),

    START_PREPARING("start.preparing"),
    START_STARTED("start.started"),
    START_ALREADY_RUNNING("start.already-running"),
    START_SAVED_TASK("start.saved-task"),
    START_RADIUS_LIMIT("start.radius-limit"),
    START_DISK_SPACE("start.disk-space"),
    START_MISSING_FILE("start.missing-file"),
    START_EMPTY("start.empty"),
    START_TRIM_PENDING("start.trim-pending"),
    START_CANCELLED_BY_LISTENER("start.cancelled-by-listener"),
    START_PAUSE_WHEN_EMPTY("start.pause-when-empty"),

    PAUSE_DONE("pause.done"),
    PAUSE_NONE("pause.none"),
    RESUME_DONE("resume.done"),
    RESUME_NONE("resume.none"),
    CANCEL_CONFIRM("cancel.confirm"),
    CANCEL_DONE("cancel.done"),
    CANCEL_NONE("cancel.none"),

    CONFIRM_NOTHING("confirm.nothing"),
    CONFIRM_EXPIRED("confirm.expired"),

    STATUS_NONE("status.none"),
    STATUS_HEADER("status.header"),
    STATUS_LINE("status.line"),
    PROGRESS_CONSOLE("progress.console"),
    TASK_COMPLETED("task.completed"),
    TASK_PAUSED_BY_ERRORS("task.paused-by-errors"),
    TASK_RESUMED_AFTER_RESTART("task.resumed-after-restart"),
    STATE_PREPARING("state.preparing"),
    STATE_RUNNING("state.running"),
    STATE_PAUSED("state.paused"),
    STATE_COMPLETED("state.completed"),
    STATE_CANCELLED("state.cancelled"),
    STATE_THROTTLED("state.throttled"),

    WATCH_ON("watch.on"),
    WATCH_OFF("watch.off"),
    BOSSBAR_TITLE("bossbar.title"),
    SILENT_ON("silent.on"),
    SILENT_OFF("silent.off"),
    INTERVAL_SET("interval.set"),
    RELOAD_DONE("reload.done"),
    RELOAD_BLOCKED("reload.blocked"),

    TRIM_PLANNING("trim.planning"),
    TRIM_PREVIEW("trim.preview"),
    TRIM_PREVIEW_INHABITED("trim.preview-inhabited"),
    TRIM_CONFIRM_HINT("trim.confirm-hint"),
    TRIM_SCHEDULED("trim.scheduled"),
    TRIM_NOTHING("trim.nothing"),
    TRIM_TASK_RUNNING("trim.task-running"),
    TRIM_NO_STORAGE("trim.no-storage"),
    TRIM_CANCELLED_BY_LISTENER("trim.cancelled-by-listener"),
    TRIM_PENDING_HEADER("trim.pending-header"),
    TRIM_PENDING_ENTRY("trim.pending-entry"),
    TRIM_PENDING_NONE("trim.pending-none"),
    TRIM_UNSCHEDULED("trim.unscheduled"),
    TRIM_UNSCHEDULE_UNKNOWN("trim.unschedule-unknown"),
    TRIM_BACKUP_ON("trim.backup-on"),
    TRIM_BACKUP_OFF("trim.backup-off"),
    TRIM_MODE_INSIDE("trim.mode-inside"),
    TRIM_MODE_OUTSIDE("trim.mode-outside"),

    DOCTOR_HEADER("doctor.header"),
    DOCTOR_OK("doctor.ok"),
    DOCTOR_PAUSE_WHEN_EMPTY("doctor.pause-when-empty"),
    DOCTOR_THROTTLE_UNAVAILABLE("doctor.throttle-unavailable"),
    DOCTOR_DISK("doctor.disk"),
    DOCTOR_PENDING_TRIMS("doctor.pending-trims"),
    DOCTOR_BORDER("doctor.border"),
    DOCTOR_SAVED_TASKS("doctor.saved-tasks");

    private final String path;

    MessageKey(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
