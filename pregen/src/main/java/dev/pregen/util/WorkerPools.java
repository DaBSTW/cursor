package dev.pregen.util;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The plugin's own threads, split by the kind of work so that one kind can never starve another:
 * <ul>
 * <li>coordinators: the tasks' serial executors, short non-blocking steps only;</li>
 * <li>checks: chunk existence checks, which may block on disk or wait for a server thread;</li>
 * <li>io: task preparation and file writes.</li>
 * </ul>
 */
public final class WorkerPools {

    private final ExecutorService coordinators;
    private final ExecutorService checks;
    private final ExecutorService io;
    private final Logger logger;

    public WorkerPools(int checkThreads, Logger logger) {
        this.logger = logger;
        this.coordinators = newPool("Pregen-Coordinator", 2, logger);
        this.checks = newPool("Pregen-Check", checkThreads, logger);
        this.io = newPool("Pregen-IO", 2, logger);
    }

    public ExecutorService coordinators() {
        return coordinators;
    }

    public ExecutorService checks() {
        return checks;
    }

    public ExecutorService io() {
        return io;
    }

    /**
     * Stops all threads. Pending chunk checks belong to tasks that are already paused and saved, and may be blocked
     * on a server thread that is itself shutting down, so they are interrupted right away; only file writes are
     * given time to finish.
     */
    public void shutdown(long timeoutMillis) {
        checks.shutdownNow();
        coordinators.shutdown();
        io.shutdown();
        try {
            if (!io.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
                logger.warning("File writes did not finish within " + timeoutMillis + "ms; interrupting them");
                io.shutdownNow();
            }
            if (!coordinators.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
                coordinators.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            List.of(checks, io, coordinators).forEach(ExecutorService::shutdownNow);
        }
    }

    private static ExecutorService newPool(String name, int threads, Logger logger) {
        AtomicInteger counter = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, name + "-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    thread.setUncaughtExceptionHandler((t, error) -> logger.log(Level.SEVERE,
                            "Uncaught exception in thread=" + t.getName(), error));
                    return thread;
                });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }
}
