package dev.pregen.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pregen.api.TaskState;
import dev.pregen.geometry.ShapeType;
import dev.pregen.pattern.PatternType;
import dev.pregen.selection.Selection;
import dev.pregen.util.ChunkPos;
import dev.pregen.util.SerialExecutor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GenerationTaskTest {

    // 10x10 chunks: blocks -80..79 contain chunk centers -72..72.
    private static final Selection SELECTION = new Selection("world", 0, 0, 80, 80, ShapeType.SQUARE, PatternType.LOOP,
            "");

    private final FakeChunkAccess chunks = new FakeChunkAccess();
    private final RecordingObserver observer = new RecordingObserver();
    private final SerialExecutor serial = new SerialExecutor(Runnable::run, error -> {
        throw new AssertionError(error);
    });
    private final AtomicInteger limit = new AtomicInteger(4);
    private long now = 1_000;

    private GenerationTask newTask(TaskPlan plan) {
        return new GenerationTask(plan, chunks, serial, limit::get, observer, () -> now);
    }

    private static TaskPlan plan(long resumeIndex, boolean skipExisting) {
        return TaskPreparer.prepare(SELECTION, new long[0], resumeIndex, 0, skipExisting);
    }

    @Test
    void generatesEveryChunkOnceAndCompletes() {
        GenerationTask task = newTask(plan(0, true));
        task.start();
        assertEquals(TaskState.COMPLETED, task.state());
        assertEquals(100, chunks.loaded.size());
        assertEquals(100, new HashSet<>(chunks.loaded).size());
        assertEquals(1, observer.completed);
        assertEquals(100.0, task.progress().percent());
    }

    @Test
    void skipsExistingChunksWithoutLoadingThem() {
        chunks.existing.add(ChunkPos.pack(0, 0));
        chunks.existing.add(ChunkPos.pack(-5, -5));
        GenerationTask task = newTask(plan(0, true));
        task.start();
        assertEquals(98, chunks.loaded.size());
        assertEquals(100, task.progress().processedChunks());
    }

    @Test
    void loadsExistingChunksWhenSkippingIsDisabled() {
        chunks.existing.add(ChunkPos.pack(0, 0));
        newTask(plan(0, false)).start();
        assertEquals(100, chunks.loaded.size());
    }

    @Test
    void neverExceedsTheConcurrencyLimit() {
        chunks.completeImmediately = false;
        GenerationTask task = newTask(plan(0, true));
        task.start();
        assertEquals(4, chunks.pending.size());
        while (chunks.completeNext()) {
            assertTrue(chunks.pending.size() <= 4);
        }
        assertEquals(4, chunks.maxInFlight);
        assertEquals(TaskState.COMPLETED, task.state());
    }

    @Test
    void holdsWhileTheLimitIsZeroAndContinuesOnTick() {
        limit.set(0);
        GenerationTask task = newTask(plan(0, true));
        task.start();
        assertTrue(chunks.loaded.isEmpty());
        assertTrue(task.isThrottled());
        limit.set(8);
        task.tick();
        assertEquals(TaskState.COMPLETED, task.state());
    }

    @Test
    void checkpointNeverSkipsAnUnfinishedChunk() {
        chunks.completeImmediately = false;
        GenerationTask task = newTask(plan(0, true));
        task.start();
        // Finish the newest requests first: the oldest one (index 0) is still in flight.
        chunks.completeLatest();
        chunks.completeLatest();
        task.pause(TaskObserver.PauseReason.USER);
        assertEquals(TaskState.PAUSED, task.state());
        assertEquals(0, observer.lastCheckpoint.patternIndex());
    }

    @Test
    void resumingFromACheckpointFinishesTheRemainingChunks() {
        chunks.completeImmediately = false;
        GenerationTask first = newTask(plan(0, true));
        first.start();
        for (int i = 0; i < 30; i++) {
            chunks.completeNext();
        }
        first.pause(TaskObserver.PauseReason.USER);
        long index = observer.lastCheckpoint.patternIndex();
        assertTrue(index >= 30);

        FakeChunkAccess fresh = new FakeChunkAccess();
        GenerationTask second = new GenerationTask(plan(index, true), fresh, serial, limit::get, observer, () -> now);
        // FIFO completion: exactly the 30 finished chunks precede the checkpoint (unselected positions are skipped).
        long resumedProcessed = second.progress().processedChunks();
        assertEquals(30, resumedProcessed);
        second.start();
        assertEquals(TaskState.COMPLETED, second.state());
        assertEquals(100 - resumedProcessed, fresh.loaded.size());
        assertEquals(100, second.progress().processedChunks());
    }

    @Test
    void pausesAfterTooManyConsecutiveFailures() {
        for (int x = -5; x < 5; x++) {
            for (int z = -5; z < 5; z++) {
                chunks.failing.add(ChunkPos.pack(x, z));
            }
        }
        GenerationTask task = newTask(plan(0, true));
        task.start();
        assertEquals(TaskState.PAUSED, task.state());
        assertEquals(TaskObserver.PauseReason.ERRORS, observer.lastReason);
        assertTrue(observer.failures >= GenerationTask.MAX_CONSECUTIVE_FAILURES);
    }

    @Test
    void isolatedFailuresDoNotStopTheTask() {
        chunks.failing.add(ChunkPos.pack(1, 1));
        GenerationTask task = newTask(plan(0, true));
        task.start();
        assertEquals(TaskState.COMPLETED, task.state());
        assertEquals(1, task.failedChunks());
    }

    @Test
    void cancelStopsRequestingAndNotifiesOnce() {
        chunks.completeImmediately = false;
        GenerationTask task = newTask(plan(0, true));
        task.start();
        task.cancel();
        task.cancel();
        while (chunks.completeNext()) {
            // Draining in-flight loads must not request anything new.
        }
        assertEquals(4, chunks.loaded.size());
        assertEquals(TaskState.CANCELLED, task.state());
        assertEquals(1, observer.cancelled);
    }

    @Test
    void elapsedTimeExcludesPausedTime() {
        chunks.completeImmediately = false;
        GenerationTask task = newTask(plan(0, true));
        task.start();
        now += 10_000;
        task.pause(TaskObserver.PauseReason.USER);
        now += 50_000;
        assertEquals(10, task.progress().elapsedSeconds());
    }

    private static final class RecordingObserver implements TaskObserver {
        int completed;
        int cancelled;
        int failures;
        TaskCheckpoint lastCheckpoint;
        PauseReason lastReason;
        final List<String> events = new ArrayList<>();

        @Override
        public void onPaused(GenerationTask task, TaskCheckpoint checkpoint, PauseReason reason) {
            lastCheckpoint = checkpoint;
            lastReason = reason;
            events.add("paused");
        }

        @Override
        public void onCompleted(GenerationTask task) {
            completed++;
        }

        @Override
        public void onCancelled(GenerationTask task) {
            cancelled++;
        }

        @Override
        public void onChunkFailed(GenerationTask task, int chunkX, int chunkZ, Throwable error) {
            failures++;
        }
    }
}
