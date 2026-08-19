package dev.bookreports.util;

import org.bukkit.entity.Player;

/** Runs everything synchronously on the calling thread — deterministic scheduling for tests. */
public final class ImmediateSchedulerAdapter implements SchedulerAdapter {

    @Override
    public void runAsync(Runnable task) {
        task.run();
    }

    @Override
    public void runGlobal(Runnable task) {
        task.run();
    }

    @Override
    public void runForPlayer(Player player, Runnable task) {
        task.run();
    }

    @Override
    public void runGlobalLater(Runnable task, long delayTicks) {
        task.run();
    }

    @Override
    public void runForPlayerLater(Player player, Runnable task, long delayTicks) {
        task.run();
    }

    @Override
    public void cancelAll() {
        // No-op: nothing is ever actually scheduled.
    }
}
