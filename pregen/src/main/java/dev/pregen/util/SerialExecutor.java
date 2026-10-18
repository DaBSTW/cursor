package dev.pregen.util;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Runs submitted tasks one at a time, in submission order, on a shared delegate pool. State confined to a serial
 * executor needs no locking, and no thread is parked while the executor is idle.
 */
public final class SerialExecutor implements Executor {

    private final Executor delegate;
    private final Consumer<Throwable> errorHandler;
    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();

    public SerialExecutor(Executor delegate, Consumer<Throwable> errorHandler) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
    }

    @Override
    public void execute(Runnable command) {
        queue.add(Objects.requireNonNull(command, "command"));
        scheduleDrain();
    }

    private void scheduleDrain() {
        if (!scheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            delegate.execute(this::drain);
        } catch (RejectedExecutionException e) {
            // The pool is shutting down; queued work is intentionally dropped with the plugin.
            scheduled.set(false);
            queue.clear();
        }
    }

    private void drain() {
        try {
            Runnable next;
            while ((next = queue.poll()) != null) {
                try {
                    next.run();
                } catch (RuntimeException | Error e) {
                    errorHandler.accept(e);
                }
            }
        } finally {
            scheduled.set(false);
        }
        if (!queue.isEmpty()) {
            scheduleDrain();
        }
    }
}
