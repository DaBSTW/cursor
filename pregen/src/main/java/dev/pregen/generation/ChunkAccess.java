package dev.pregen.generation;

import java.util.concurrent.CompletableFuture;

/** The server operations a generation task needs. Kept minimal so tasks can be tested without a server. */
public interface ChunkAccess {

    /** Completes with whether the chunk already exists on disk. Must not load or generate it. */
    CompletableFuture<Boolean> isGenerated(int chunkX, int chunkZ);

    /** Loads the chunk, generating it when missing; completes once the chunk is fully generated. */
    CompletableFuture<Void> load(int chunkX, int chunkZ);
}
