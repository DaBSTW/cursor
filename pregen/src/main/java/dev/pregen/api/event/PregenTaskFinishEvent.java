package dev.pregen.api.event;

import dev.pregen.api.TaskProgress;
import dev.pregen.api.TaskState;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired asynchronously once when a task ends for good: {@link TaskState#COMPLETED} or {@link TaskState#CANCELLED}.
 * Pausing does not fire it.
 */
public final class PregenTaskFinishEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final TaskProgress progress;

    public PregenTaskFinishEvent(TaskProgress progress) {
        super(true);
        this.progress = progress;
    }

    /** Final snapshot; {@code progress().state()} tells whether the task completed or was cancelled. */
    public TaskProgress progress() {
        return progress;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
