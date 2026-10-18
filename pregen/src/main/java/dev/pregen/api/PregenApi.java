package dev.pregen.api;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Entry point for other plugins. Obtain it from Bukkit's services manager:
 *
 * <pre>{@code
 * PregenApi api = Bukkit.getServicesManager().load(PregenApi.class);
 * }</pre>
 *
 * <p>All methods are safe to call from any thread. Lifecycle notifications are delivered as asynchronous Bukkit
 * events in
 * {@link dev.pregen.api.event}.
 */
public interface PregenApi {

    /** Whether a task for the world is currently requesting chunks. */
    boolean isRunning(String worldName);

    /** Progress of the in-memory task for the world (running, paused or preparing), if any. */
    Optional<TaskProgress> progress(String worldName);

    /** Progress of every in-memory task. The returned list is unmodifiable. */
    List<TaskProgress> tasks();

    /**
     * Starts generating the requested area. Preparation (counting chunks, checking disk space) happens off the main
     * thread.
     *
     * @return a future completing with the outcome; it completes on an internal thread, so dispatch back through a
     *         scheduler before touching the Bukkit API
     * @throws IllegalArgumentException if the shape or pattern id is unknown, the pattern is {@code csv}, or a radius
     *         is not positive
     */
    CompletableFuture<StartOutcome> start(GenerationRequest request);

    /** Pauses the world's running task and saves its progress. Returns false when no task is running. */
    boolean pause(String worldName);

    /**
     * Resumes the world's paused task, loading it from disk when needed.
     *
     * @return a future completing with false when there is nothing to resume; completes on an internal thread
     */
    CompletableFuture<Boolean> resume(String worldName);

    /**
     * Cancels the world's task and deletes its saved progress.
     *
     * @return a future completing with false when there was no task; completes on an internal thread
     */
    CompletableFuture<Boolean> cancel(String worldName);
}
