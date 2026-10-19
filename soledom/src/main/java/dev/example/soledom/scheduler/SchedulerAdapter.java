package dev.example.soledom.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Thread-model abstraction over the Bukkit scheduler (single main thread) and Folia (region threads).
 *
 * <p>Rule of thumb: entity work goes through {@link #runAtEntity}, block/location work through
 * {@link #runAtLocation}, server-wide work through {@link #runGlobal}, and IO through {@link #runAsync}.</p>
 */
public interface SchedulerAdapter {

    /** Runs on the main thread (Paper) or the global region thread (Folia), next tick. */
    @NotNull TaskHandle runGlobal(@NotNull Runnable task);

    /** Runs off the server threads. Never touch the world from here. */
    @NotNull TaskHandle runAsync(@NotNull Runnable task);

    /** Runs on the thread owning {@code entity}, next tick. Silently skipped if the entity is removed first. */
    @NotNull TaskHandle runAtEntity(@NotNull Entity entity, @NotNull Runnable task);

    @NotNull TaskHandle runAtEntityLater(@NotNull Entity entity, @NotNull Runnable task, long delayTicks);

    /** Repeats on the thread owning {@code entity} until cancelled or the entity is removed. */
    @NotNull TaskHandle runAtEntityTimer(@NotNull Entity entity, @NotNull Runnable task, long delayTicks,
            long periodTicks);

    /** Runs on the thread owning the region at {@code location}, next tick. */
    @NotNull TaskHandle runAtLocation(@NotNull Location location, @NotNull Runnable task);

    /** True when the current thread may touch {@code entity} right now. */
    boolean isOwnedByCurrentThread(@NotNull Entity entity);

    /** True when the current thread may touch the world at {@code location} right now. */
    boolean isOwnedByCurrentThread(@NotNull Location location);

    /** Cancels every task this adapter scheduled. Called on plugin disable. */
    void cancelAll();
}
