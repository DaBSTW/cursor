package dev.bookreports.util;

import org.bukkit.entity.Player;

/**
 * Dispatches tasks to the correct thread on both Bukkit-threaded Paper and region-threaded Folia.
 *
 * <p>
 * Business logic never touches {@link org.bukkit.scheduler.BukkitScheduler} directly so that a single code path works
 * under both threading models.
 */
public interface SchedulerAdapter {

    /** Runs a task off the main/region thread. Used for blocking I/O such as database access. */
    void runAsync(Runnable task);

    /** Runs a task on the global region thread (Folia) or the main thread (Paper). */
    void runGlobal(Runnable task);

    /** Runs a task on the region thread that owns the given player (Folia) or the main thread (Paper). */
    void runForPlayer(Player player, Runnable task);

    /** Same as {@link #runGlobal(Runnable)}, delayed by the given number of ticks. */
    void runGlobalLater(Runnable task, long delayTicks);

    /** Same as {@link #runForPlayer(Player, Runnable)}, delayed by the given number of ticks. */
    void runForPlayerLater(Player player, Runnable task, long delayTicks);

    /** Cancels every task scheduled through this adapter. Called from {@code onDisable}. */
    void cancelAll();
}
