package dev.pregen.persistence;

import dev.pregen.util.SerialExecutor;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs {@link TaskStore} writes off the calling thread, strictly in submission order. Ordering matters: an autosave
 * queued before a cancel must never land after the delete and resurrect the task.
 */
public final class AsyncTaskStore {

    private final TaskStore store;
    private final Executor writer;
    private final Logger logger;

    public AsyncTaskStore(TaskStore store, Executor pool, Logger logger) {
        this.store = store;
        this.logger = logger;
        this.writer = new SerialExecutor(pool,
                error -> logger.log(Level.SEVERE, "Unexpected error in the task store writer", error));
    }

    public TaskStore delegate() {
        return store;
    }

    public void save(StoredTask task) {
        writer.execute(() -> {
            try {
                store.save(task);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to save task progress: world=" + task.selection().world(), e);
            }
        });
    }

    public void delete(String worldName) {
        writer.execute(() -> {
            try {
                store.delete(worldName);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to delete saved task: world=" + worldName, e);
            }
        });
    }

    /** Completes once every write submitted before this call has finished. */
    public CompletableFuture<Void> flush() {
        CompletableFuture<Void> done = new CompletableFuture<>();
        writer.execute(() -> done.complete(null));
        return done;
    }
}
