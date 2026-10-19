package dev.example.soledom.scheduler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Region-aware scheduling through Paper's regionised scheduler API. Required on Folia; the same API also exists
 * on regular Paper, where it simply delegates to the main thread.
 */
public final class FoliaSchedulerAdapter implements SchedulerAdapter {

    private final Plugin plugin;
    private final Set<ScheduledTask> entityTasks = ConcurrentHashMap.newKeySet();

    public FoliaSchedulerAdapter(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull TaskHandle runGlobal(@NotNull Runnable task) {
        return Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run())::cancel;
    }

    @Override
    public @NotNull TaskHandle runAsync(@NotNull Runnable task) {
        return Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run())::cancel;
    }

    @Override
    public @NotNull TaskHandle runAtEntity(@NotNull Entity entity, @NotNull Runnable task) {
        return runAtEntityLater(entity, task, 1);
    }

    @Override
    public @NotNull TaskHandle runAtEntityLater(@NotNull Entity entity, @NotNull Runnable task, long delayTicks) {
        ScheduledTask[] holder = new ScheduledTask[1];
        holder[0] = entity.getScheduler().runDelayed(plugin, t -> {
            entityTasks.remove(t);
            task.run();
        }, () -> entityTasks.remove(holder[0]), Math.max(1, delayTicks));
        return track(holder[0]);
    }

    @Override
    public @NotNull TaskHandle runAtEntityTimer(@NotNull Entity entity, @NotNull Runnable task, long delayTicks,
            long periodTicks) {
        ScheduledTask[] holder = new ScheduledTask[1];
        holder[0] = entity.getScheduler().runAtFixedRate(plugin, t -> task.run(),
                () -> entityTasks.remove(holder[0]), Math.max(1, delayTicks), Math.max(1, periodTicks));
        return track(holder[0]);
    }

    @Override
    public @NotNull TaskHandle runAtLocation(@NotNull Location location, @NotNull Runnable task) {
        return Bukkit.getRegionScheduler().run(plugin, location, t -> task.run())::cancel;
    }

    @Override
    public boolean isOwnedByCurrentThread(@NotNull Entity entity) {
        return Bukkit.isOwnedByCurrentRegion(entity);
    }

    @Override
    public boolean isOwnedByCurrentThread(@NotNull Location location) {
        return Bukkit.isOwnedByCurrentRegion(location);
    }

    @Override
    public void cancelAll() {
        // Entity schedulers have no per-plugin bulk cancel, so every entity task is tracked individually.
        entityTasks.forEach(ScheduledTask::cancel);
        entityTasks.clear();
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }

    /** {@code null} means the entity was already removed: the task will never run. */
    private TaskHandle track(ScheduledTask task) {
        if (task == null) {
            return TaskHandle.NOOP;
        }
        entityTasks.add(task);
        if (task.getExecutionState() == ScheduledTask.ExecutionState.FINISHED
                || task.getExecutionState() == ScheduledTask.ExecutionState.CANCELLED) {
            // Ran (or was cancelled) on another region thread before we got to track it.
            entityTasks.remove(task);
        }
        return () -> {
            task.cancel();
            entityTasks.remove(task);
        };
    }
}
