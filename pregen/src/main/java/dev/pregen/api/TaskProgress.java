package dev.pregen.api;

/**
 * Immutable snapshot of a generation task. Safe to keep and to read from any thread; it never changes after creation.
 *
 * @param worldName the world the task generates
 * @param state state when the snapshot was taken
 * @param processedChunks chunks handled so far, including chunks skipped because they already existed
 * @param totalChunks chunks in the selection; an estimate for very large selections (see {@link #exactTotal()})
 * @param exactTotal whether {@code totalChunks} was counted exactly
 * @param percent progress from 0 to 100
 * @param chunksPerSecond recent throughput averaged over a sliding window; 0 when unknown
 * @param etaSeconds estimated seconds left, or -1 when unknown
 * @param elapsedSeconds running time, excluding time spent paused, across restarts
 * @param lastChunkX x of the most recently finished chunk
 * @param lastChunkZ z of the most recently finished chunk
 */
public record TaskProgress(String worldName, TaskState state, long processedChunks, long totalChunks,
        boolean exactTotal, double percent, double chunksPerSecond, long etaSeconds, long elapsedSeconds,
        int lastChunkX, int lastChunkZ) {
}
