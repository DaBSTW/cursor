package dev.pregen.api;

/** Lifecycle of a generation task. */
public enum TaskState {
    /** Counting chunks and positioning the pattern; no chunk has been requested yet. */
    PREPARING,
    /** Requesting chunks from the server. */
    RUNNING,
    /** Stopped with its progress saved; can be resumed, also after a restart. */
    PAUSED,
    /** Every chunk of the selection was processed. Terminal. */
    COMPLETED,
    /** Stopped and discarded by a user or plugin. Terminal. */
    CANCELLED;

    /** Whether the task can no longer change state. */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
