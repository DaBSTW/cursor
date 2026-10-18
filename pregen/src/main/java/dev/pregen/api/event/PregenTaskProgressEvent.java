package dev.pregen.api.event;

import dev.pregen.api.TaskProgress;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired asynchronously for every running task once per {@code progress.console-interval-seconds}, whether or not
 * console output is silenced.
 */
public final class PregenTaskProgressEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final TaskProgress progress;

    public PregenTaskProgressEvent(TaskProgress progress) {
        super(true);
        this.progress = progress;
    }

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
