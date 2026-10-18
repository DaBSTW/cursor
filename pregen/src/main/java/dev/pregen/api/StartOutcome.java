package dev.pregen.api;

/** Result of a start request. Only {@link #STARTED} means a task was created. */
public enum StartOutcome {
    STARTED,
    /** A task is already running or paused in memory for the world. */
    ALREADY_RUNNING,
    /** A saved task exists for the world; resume or cancel it first. */
    SAVED_TASK_EXISTS,
    WORLD_NOT_FOUND,
    /** The radius exceeds {@code generation.radius-limit}. */
    RADIUS_LIMIT_EXCEEDED,
    /** The estimated size of the new chunks exceeds the free disk space. */
    INSUFFICIENT_DISK_SPACE,
    /** The CSV file for the {@code csv} pattern does not exist or could not be read. */
    MISSING_INPUT_FILE,
    /** The selection contains no chunk. */
    EMPTY_SELECTION,
    /** A trim is scheduled for this world; generating now would be undone at the next restart. */
    TRIM_PENDING,
    /** A listener cancelled {@link dev.pregen.api.event.PregenTaskStartEvent}. */
    CANCELLED_BY_LISTENER,
    /** An unexpected error; details are in the server log. */
    FAILED
}
