package dev.pregen.generation;

import dev.pregen.api.StartOutcome;
import dev.pregen.api.TaskState;
import dev.pregen.api.event.PregenTaskFinishEvent;
import dev.pregen.config.SettingsHolder;
import dev.pregen.generation.TaskLauncher.Ready;
import dev.pregen.generation.TaskLauncher.Rejected;
import dev.pregen.i18n.Arg;
import dev.pregen.i18n.MessageKey;
import dev.pregen.persistence.AsyncTaskStore;
import dev.pregen.persistence.StoredTask;
import dev.pregen.progress.Notifier;
import dev.pregen.selection.Selection;
import dev.pregen.util.SerialExecutor;
import dev.pregen.util.ThrottledLogger;
import dev.pregen.util.WorkerPools;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

/**
 * Owns the generation tasks (at most one per world) and every operation on them. Thread-safe: callable from command
 * threads, the heartbeat and the API concurrently.
 */
public final class GenerationService {

    private final Plugin plugin;
    private final SettingsHolder settings;
    private final ThrottleController throttle;
    private final TaskLauncher launcher;
    private final AsyncTaskStore store;
    private final WorkerPools pools;
    private final Notifier notifier;
    private final Logger logger;
    private final ThrottledLogger chunkErrors;
    private final Map<String, GenerationTask> tasks = new ConcurrentHashMap<>();
    private final Set<String> starting = ConcurrentHashMap.newKeySet();
    private final TaskObserver observer = new LifecycleObserver();
    // Only touched by the heartbeat thread.
    private int secondsSinceAutosave;

    public GenerationService(Plugin plugin, SettingsHolder settings, ThrottleController throttle, TaskLauncher launcher,
            AsyncTaskStore store, WorkerPools pools, Notifier notifier) {
        this.plugin = plugin;
        this.settings = settings;
        this.throttle = throttle;
        this.launcher = launcher;
        this.store = store;
        this.pools = pools;
        this.notifier = notifier;
        this.logger = plugin.getLogger();
        this.chunkErrors = new ThrottledLogger(logger, 60_000, System::currentTimeMillis);
    }

    public CompletableFuture<StartResult> start(Selection selection, boolean ignoreDiskSpace) {
        String world = selection.worldName();
        if (tasks.containsKey(world) || !starting.add(world)) {
            return CompletableFuture.completedFuture(StartResult.of(StartOutcome.ALREADY_RUNNING));
        }
        return launcher.prepareNew(selection, ignoreDiskSpace).handle((result, error) -> {
            starting.remove(world);
            if (error != null) {
                logger.log(Level.SEVERE, "Failed to start task: world=" + world, error);
                return StartResult.of(StartOutcome.FAILED);
            }
            if (result instanceof Rejected rejected) {
                return rejected.result();
            }
            Ready ready = (Ready) result;
            launch(ready);
            return new StartResult(StartOutcome.STARTED, ready.plan().totalChunks(), 0, 0);
        });
    }

    /** Resumes a paused task, or loads a saved one from disk. Completes with false when there is nothing to resume. */
    public CompletableFuture<Boolean> resume(String world) {
        GenerationTask existing = tasks.get(world);
        if (existing != null) {
            boolean paused = existing.state() == TaskState.PAUSED;
            if (paused) {
                existing.start();
            }
            return CompletableFuture.completedFuture(paused);
        }
        if (!starting.add(world)) {
            return CompletableFuture.completedFuture(false);
        }
        return launcher.prepareSaved(world).handle((ready, error) -> {
            starting.remove(world);
            if (error != null) {
                logger.log(Level.SEVERE, "Failed to resume saved task: world=" + world, error);
                return false;
            }
            ready.ifPresent(this::launch);
            return ready.isPresent();
        });
    }

    /** Resumes every paused task and every saved task; completes with the worlds that resumed. */
    public CompletableFuture<List<String>> resumeAll() {
        return CompletableFuture.supplyAsync(this::savedWorlds, pools.io()).thenCompose(saved -> {
            Set<String> worlds = new TreeSet<>(saved);
            tasks.forEach((world, task) -> {
                if (task.state() == TaskState.PAUSED) {
                    worlds.add(world);
                }
            });
            List<CompletableFuture<Optional<String>>> results = worlds.stream()
                    .map(world -> resume(world).thenApply(ok -> ok ? Optional.of(world) : Optional.<String>empty()))
                    .toList();
            return CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).thenApply(
                    ignored -> results.stream().map(CompletableFuture::join).flatMap(Optional::stream).toList());
        });
    }

    public boolean pause(String world) {
        GenerationTask task = tasks.get(world);
        if (task == null || task.state() != TaskState.RUNNING) {
            return false;
        }
        task.pause(TaskObserver.PauseReason.USER);
        return true;
    }

    public List<String> pauseAll() {
        List<String> paused = new ArrayList<>();
        for (String world : List.copyOf(tasks.keySet())) {
            if (pause(world)) {
                paused.add(world);
            }
        }
        return paused;
    }

    /** Cancels the task in memory and deletes its saved progress. Completes with false when neither existed. */
    public CompletableFuture<Boolean> cancel(String world) {
        GenerationTask task = tasks.get(world);
        if (task != null) {
            task.cancel();
            return CompletableFuture.completedFuture(true);
        }
        return CompletableFuture.supplyAsync(() -> savedWorlds().contains(world), pools.io()).thenApply(saved -> {
            if (saved) {
                store.delete(world);
            }
            return saved;
        });
    }

    public CompletableFuture<List<String>> cancelAll() {
        return CompletableFuture.supplyAsync(this::savedWorlds, pools.io()).thenApply(saved -> {
            Set<String> worlds = new TreeSet<>(saved);
            worlds.addAll(tasks.keySet());
            worlds.forEach(world -> {
                GenerationTask task = tasks.get(world);
                if (task != null) {
                    task.cancel();
                } else {
                    store.delete(world);
                }
            });
            return List.copyOf(worlds);
        });
    }

    public Optional<GenerationTask> task(String world) {
        return Optional.ofNullable(tasks.get(world));
    }

    public Collection<GenerationTask> tasks() {
        return List.copyOf(tasks.values());
    }

    public boolean hasRunningTasks() {
        return tasks.values().stream().anyMatch(task -> task.state() == TaskState.RUNNING) || !starting.isEmpty();
    }

    public ThrottleController throttle() {
        return throttle;
    }

    /** Called once per second from an async scheduler thread. */
    public void heartbeat() {
        throttle.sample();
        for (GenerationTask task : tasks.values()) {
            task.sampleRate();
            task.tick();
        }
        if (++secondsSinceAutosave >= settings.get().generation().autosaveSeconds()) {
            secondsSinceAutosave = 0;
            tasks.values().stream().filter(task -> task.state() == TaskState.RUNNING)
                    .forEach(task -> task.checkpoint(checkpoint -> store.save(StoredTask.from(checkpoint))));
        }
    }

    /** Resumes saved tasks after a restart when {@code generation.resume-on-restart} is enabled. */
    public void resumeSavedTasksOnStartup() {
        if (!settings.get().generation().resumeOnRestart()) {
            return;
        }
        resumeAll().whenComplete((worlds, error) -> {
            if (error != null) {
                logger.log(Level.SEVERE, "Failed to resume saved tasks after restart", error);
                return;
            }
            for (String world : worlds) {
                task(world).ifPresent(task -> notifier.console(MessageKey.TASK_RESUMED_AFTER_RESTART,
                        Arg.text("world", world), Arg.decimal("percent", task.progress().percent())));
            }
        });
    }

    /** Stops tracking a world that is being unloaded; its progress is saved so it can be resumed later. */
    public void evict(String world) {
        GenerationTask task = tasks.remove(world);
        if (task == null) {
            return;
        }
        task.pauseForShutdown().thenAccept(checkpoint -> {
            if (checkpoint != null) {
                store.save(StoredTask.from(checkpoint));
            }
        });
    }

    /** Pauses everything and writes the final checkpoints synchronously. Called from {@code onDisable}. */
    public void shutdown(long timeoutMillis) {
        List<GenerationTask> active = List.copyOf(tasks.values());
        tasks.clear();
        List<CompletableFuture<TaskCheckpoint>> checkpoints = active.stream().map(GenerationTask::pauseForShutdown)
                .toList();
        await(store.flush(), timeoutMillis, "pending task writes");
        for (int i = 0; i < active.size(); i++) {
            TaskCheckpoint checkpoint = await(checkpoints.get(i), timeoutMillis, "checkpoint");
            if (checkpoint == null) {
                continue;
            }
            try {
                store.delegate().save(StoredTask.from(checkpoint));
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to save task on shutdown: world=" + active.get(i).worldName(), e);
            }
        }
    }

    private void launch(Ready ready) {
        TaskPlan plan = ready.plan();
        String world = plan.selection().worldName();
        ChunkAccess access = new PaperChunkAccess(plugin, ready.world(), pools.checks(),
                settings.get().generation().keepTickingSeconds());
        SerialExecutor serial = new SerialExecutor(pools.coordinators(),
                error -> logger.log(Level.SEVERE, "Unexpected error in task: world=" + world, error));
        GenerationTask task = new GenerationTask(plan, access, serial, () -> throttle.limitPerTask(runningTaskCount()),
                observer, System::currentTimeMillis);
        tasks.put(world, task);
        store.save(
                StoredTask.from(new TaskCheckpoint(plan.selection(), plan.pattern().position(), plan.elapsedMillis())));
        task.start();
        if (settings.get().debug()) {
            logger.info("Task launched: world=" + world + " total=" + plan.totalChunks() + " resumedAt="
                    + plan.initialProcessed());
        }
    }

    private int runningTaskCount() {
        int count = 0;
        for (GenerationTask task : tasks.values()) {
            if (task.state() == TaskState.RUNNING) {
                count++;
            }
        }
        return count;
    }

    private List<String> savedWorlds() {
        try {
            return store.delegate().loadAll().stream().map(stored -> stored.selection().world()).toList();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to list saved tasks", e);
            return List.of();
        }
    }

    private <T> T await(CompletableFuture<T> future, long timeoutMillis, String what) {
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            logger.log(Level.WARNING, "Gave up waiting for " + what + " during shutdown", e);
        }
        return null;
    }

    private final class LifecycleObserver implements TaskObserver {

        @Override
        public void onPaused(GenerationTask task, TaskCheckpoint checkpoint, PauseReason reason) {
            store.save(StoredTask.from(checkpoint));
            if (reason == PauseReason.ERRORS) {
                notifier.broadcast(MessageKey.TASK_PAUSED_BY_ERRORS, Arg.text("world", task.worldName()),
                        Arg.number("errors", GenerationTask.MAX_CONSECUTIVE_FAILURES));
            }
        }

        @Override
        public void onCompleted(GenerationTask task) {
            finish(task);
            notifier.broadcast(MessageKey.TASK_COMPLETED, Arg.text("world", task.worldName()),
                    Arg.number("processed", task.progress().processedChunks()),
                    Arg.duration("elapsed", task.progress().elapsedSeconds()));
            if (task.failedChunks() > 0) {
                logger.warning("Task finished with failed chunks: world=" + task.worldName() + " failed="
                        + task.failedChunks());
            }
        }

        @Override
        public void onCancelled(GenerationTask task) {
            finish(task);
        }

        @Override
        public void onChunkFailed(GenerationTask task, int chunkX, int chunkZ, Throwable error) {
            chunkErrors.warn("chunk:" + task.worldName(),
                    "Chunk generation failed: world=" + task.worldName() + " x=" + chunkX + " z=" + chunkZ, error);
        }

        private void finish(GenerationTask task) {
            tasks.remove(task.worldName(), task);
            store.delete(task.worldName());
            plugin.getServer().getPluginManager().callEvent(new PregenTaskFinishEvent(task.progress()));
        }
    }
}
