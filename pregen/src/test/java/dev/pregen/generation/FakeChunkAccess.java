package dev.pregen.generation;

import dev.pregen.util.ChunkPos;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Chunk access whose loads complete only when the test says so, to observe in-flight behaviour. */
final class FakeChunkAccess implements ChunkAccess {

    record PendingLoad(int x, int z, CompletableFuture<Void> future) {
    }

    final Set<Long> existing = new HashSet<>();
    final Set<Long> failing = new HashSet<>();
    final List<Long> loaded = new ArrayList<>();
    final Deque<PendingLoad> pending = new ArrayDeque<>();
    boolean completeImmediately = true;
    int maxInFlight;

    @Override
    public CompletableFuture<Boolean> isGenerated(int chunkX, int chunkZ) {
        return CompletableFuture.completedFuture(existing.contains(ChunkPos.pack(chunkX, chunkZ)));
    }

    @Override
    public CompletableFuture<Void> load(int chunkX, int chunkZ) {
        long packed = ChunkPos.pack(chunkX, chunkZ);
        loaded.add(packed);
        if (failing.contains(packed)) {
            return CompletableFuture.failedFuture(new IllegalStateException("generation failed"));
        }
        if (completeImmediately) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        pending.add(new PendingLoad(chunkX, chunkZ, future));
        maxInFlight = Math.max(maxInFlight, pending.size());
        return future;
    }

    /** Completes the oldest pending load; returns false when none is pending. */
    boolean completeNext() {
        PendingLoad next = pending.poll();
        if (next == null) {
            return false;
        }
        next.future().complete(null);
        return true;
    }

    /** Completes the newest pending load, simulating out-of-order completion. */
    boolean completeLatest() {
        PendingLoad last = pending.pollLast();
        if (last == null) {
            return false;
        }
        last.future().complete(null);
        return true;
    }
}
