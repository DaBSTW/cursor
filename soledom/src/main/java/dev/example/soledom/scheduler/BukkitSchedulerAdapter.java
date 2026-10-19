package dev.example.soledom.scheduler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

/** Classic single-main-thread scheduling for Paper and its non-regionised forks. */
public final class BukkitSchedulerAdapter implements SchedulerAdapter {

    private final Plugin plugin;
    private final Set<BukkitTask> repeating = ConcurrentHashMap.newKeySet();

    public BukkitSchedulerAdapter(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull TaskHandle runGlobal(@NotNull Runnable task) {
        return Bukkit.getScheduler().runTask(plugin, task)::cancel;
    }

    @Override
    public @NotNull TaskHandle runAsync(@NotNull Runnable task) {
        return Bukkit.getScheduler().runTaskAsynchronously(plugin, task)::cancel;
    }

    @Override
    public @NotNull TaskHandle runAtEntity(@NotNull Entity entity, @NotNull Runnable task) {
        return runAtEntityLater(entity, task, 1);
    }

    @Override
    public @NotNull TaskHandle runAtEntityLater(@NotNull Entity entity, @NotNull Runnable task, long delayTicks) {
        return Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (entity.isValid()) {
                task.run();
            }
        }, Math.max(1, delayTicks))::cancel;
    }

    @Override
    public @NotNull TaskHandle runAtEntityTimer(@NotNull Entity entity, @NotNull Runnable task, long delayTicks,
            long periodTicks) {
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!entity.isValid()) {
                holder[0].cancel();
                repeating.remove(holder[0]);
                return;
            }
            task.run();
        }, Math.max(1, delayTicks), Math.max(1, periodTicks));
        repeating.add(holder[0]);
        return () -> {
            holder[0].cancel();
            repeating.remove(holder[0]);
        };
    }

    @Override
    public @NotNull TaskHandle runAtLocation(@NotNull Location location, @NotNull Runnable task) {
        return runGlobal(task);
    }

    @Override
    public boolean isOwnedByCurrentThread(@NotNull Entity entity) {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public boolean isOwnedByCurrentThread(@NotNull Location location) {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public void cancelAll() {
        repeating.clear();
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
