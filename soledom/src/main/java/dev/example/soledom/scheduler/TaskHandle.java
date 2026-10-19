package dev.example.soledom.scheduler;

/** Cancellable handle to a scheduled task, independent of the scheduler implementation. */
@FunctionalInterface
public interface TaskHandle {

    TaskHandle NOOP = () -> { };

    void cancel();
}
