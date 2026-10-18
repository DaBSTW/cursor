package dev.pregen.generation;

import dev.pregen.api.TaskProgress;
import dev.pregen.api.TaskState;
import dev.pregen.generation.TaskObserver.PauseReason;
import dev.pregen.pattern.ChunkPattern;
import dev.pregen.selection.Selection;
import dev.pregen.util.ChunkPos;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * Generates the chunks of one world. Non-blocking: at most {@code concurrencyLimit} chunk requests are in flight, and
 * each completion triggers the next request. No thread waits on a semaphore.
 *
 * <p>Threading: all mutable state is confined to the {@code serial} executor; public methods only enqueue work there.
 * Fields read by other threads for progress snapshots are volatile or atomic.
 */
public final class GenerationTask {

    static final int MAX_CONSECUTIVE_FAILURES = 50;
    // Bounds one serial step when long stretches of the pattern fall outside the shape (e.g. a thin star).
    private static final int MAX_CANDIDATES_PER_STEP = 65_536;
    private static final long RATE_WINDOW_MILLIS = 30_000;

    private final Selection selection;
    private final TaskPlan plan;
    private final ChunkAccess chunks;
    private final Executor serial;
    private final IntSupplier concurrencyLimit;
    private final TaskObserver observer;
    private final LongSupplier clock;

    // Confined to `serial`: pattern indices of the chunks currently requested, to compute a safe checkpoint.
    private final NavigableSet<Long> inFlight = new TreeSet<>();
    private int consecutiveFailures;

    private final AtomicLong processed;
    private final AtomicLong failed = new AtomicLong();
    private final RateEstimator rate = new RateEstimator(RATE_WINDOW_MILLIS);
    private volatile TaskState state = TaskState.PREPARING;
    private volatile boolean throttled;
    private volatile long elapsedBeforeMillis;
    private volatile long runningSinceMillis;
    private volatile int lastChunkX;
    private volatile int lastChunkZ;

    public GenerationTask(TaskPlan plan, ChunkAccess chunks, Executor serial, IntSupplier concurrencyLimit,
            TaskObserver observer, LongSupplier clock) {
        this.selection = plan.selection();
        this.plan = plan;
        this.chunks = chunks;
        this.serial = serial;
        this.concurrencyLimit = concurrencyLimit;
        this.observer = observer;
        this.clock = clock;
        this.processed = new AtomicLong(plan.initialProcessed());
        this.elapsedBeforeMillis = plan.elapsedMillis();
        this.lastChunkX = selection.centerChunkX();
        this.lastChunkZ = selection.centerChunkZ();
    }

    public String worldName() {
        return selection.worldName();
    }

    public Selection selection() {
        return selection;
    }

    public TaskState state() {
        return state;
    }

    public long failedChunks() {
        return failed.get();
    }

    /** Starts a prepared task, or resumes a paused one. */
    public void start() {
        serial.execute(() -> {
            if (state != TaskState.PREPARING && state != TaskState.PAUSED) {
                return;
            }
            state = TaskState.RUNNING;
            consecutiveFailures = 0;
            runningSinceMillis = clock.getAsLong();
            rate.reset();
            pump();
        });
    }

    public void pause(PauseReason reason) {
        serial.execute(() -> pauseNow(reason));
    }

    /** Pauses for shutdown and completes with the checkpoint to persist; completes empty if already finished. */
    public CompletableFuture<TaskCheckpoint> pauseForShutdown() {
        CompletableFuture<TaskCheckpoint> result = new CompletableFuture<>();
        serial.execute(() -> {
            if (state == TaskState.RUNNING) {
                stopClock();
                state = TaskState.PAUSED;
            }
            result.complete(state.isTerminal() ? null : checkpoint());
        });
        return result;
    }

    public void cancel() {
        serial.execute(() -> {
            if (state.isTerminal()) {
                return;
            }
            stopClock();
            state = TaskState.CANCELLED;
            observer.onCancelled(this);
        });
    }

    /** Re-evaluates the concurrency limit; called periodically so a raised limit takes effect without completions. */
    public void tick() {
        serial.execute(this::pumpIfRunning);
    }

    /** Delivers the current checkpoint to the sink on the task's executor (used by autosave). */
    public void checkpoint(Consumer<TaskCheckpoint> sink) {
        serial.execute(() -> {
            if (!state.isTerminal()) {
                sink.accept(checkpoint());
            }
        });
    }

    public void sampleRate() {
        rate.record(clock.getAsLong(), processed.get());
    }

    public TaskProgress progress() {
        TaskState current = state;
        long done = processed.get();
        long total = Math.max(plan.totalChunks(), done);
        double percent = current == TaskState.COMPLETED
                ? 100.0
                : total == 0 ? 0.0 : Math.min(99.99, done * 100.0 / total);
        double perSecond = current == TaskState.RUNNING ? rate.perSecond() : 0.0;
        long eta = perSecond > 0.0 ? Math.round((total - done) / perSecond) : -1;
        return new TaskProgress(worldName(), current, done, total, plan.exactTotal(), percent, perSecond, eta,
                elapsedMillis() / 1000, lastChunkX, lastChunkZ);
    }

    /** Whether the task is running but currently gets no concurrency (throttled or held for online players). */
    public boolean isThrottled() {
        return state == TaskState.RUNNING && throttled;
    }

    private long elapsedMillis() {
        long elapsed = elapsedBeforeMillis;
        return state == TaskState.RUNNING ? elapsed + clock.getAsLong() - runningSinceMillis : elapsed;
    }

    private void pumpIfRunning() {
        if (state == TaskState.RUNNING) {
            pump();
        }
    }

    private void pump() {
        ChunkPattern pattern = plan.pattern();
        int limit = concurrencyLimit.getAsInt();
        throttled = limit <= 0;
        int examined = 0;
        while (state == TaskState.RUNNING && inFlight.size() < limit && pattern.hasNext()) {
            if (++examined > MAX_CANDIDATES_PER_STEP) {
                serial.execute(this::pumpIfRunning);
                return;
            }
            long index = pattern.position();
            long packed = pattern.next();
            if (TaskPreparer.isSelected(pattern, plan.shape(), packed)) {
                request(index, ChunkPos.x(packed), ChunkPos.z(packed));
            }
        }
        if (state == TaskState.RUNNING && inFlight.isEmpty() && !pattern.hasNext()) {
            complete();
        }
    }

    private void request(long index, int x, int z) {
        inFlight.add(index);
        CompletableFuture<Void> future;
        try {
            future = plan.skipExistingChunks()
                    ? chunks.isGenerated(x, z)
                            .thenCompose(exists -> exists ? CompletableFuture.completedFuture(null) : chunks.load(x, z))
                    : chunks.load(x, z);
        } catch (RuntimeException e) {
            future = CompletableFuture.failedFuture(e);
        }
        future.whenCompleteAsync((ignored, error) -> onFinished(index, x, z, error), serial);
    }

    private void onFinished(long index, int x, int z, Throwable error) {
        inFlight.remove(index);
        if (state == TaskState.CANCELLED) {
            return;
        }
        processed.incrementAndGet();
        lastChunkX = x;
        lastChunkZ = z;
        if (error != null) {
            failed.incrementAndGet();
            observer.onChunkFailed(this, x, z, error);
            if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                pauseNow(PauseReason.ERRORS);
                return;
            }
        } else {
            consecutiveFailures = 0;
        }
        pumpIfRunning();
    }

    private void pauseNow(PauseReason reason) {
        if (state != TaskState.RUNNING && state != TaskState.PREPARING) {
            return;
        }
        stopClock();
        state = TaskState.PAUSED;
        observer.onPaused(this, checkpoint(), reason);
    }

    private void complete() {
        stopClock();
        state = TaskState.COMPLETED;
        observer.onCompleted(this);
    }

    private void stopClock() {
        if (state == TaskState.RUNNING) {
            elapsedBeforeMillis = elapsedBeforeMillis + clock.getAsLong() - runningSinceMillis;
        }
    }

    private TaskCheckpoint checkpoint() {
        long index = inFlight.isEmpty() ? plan.pattern().position() : inFlight.first();
        return new TaskCheckpoint(selection, index, elapsedMillis());
    }
}
