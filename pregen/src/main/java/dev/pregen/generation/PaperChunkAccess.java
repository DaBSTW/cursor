package dev.pregen.generation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * {@link ChunkAccess} backed by Paper's asynchronous chunk system. The same code runs on Paper and Folia: chunk futures
 * complete on the thread owning the chunk, which is also where tickets must be added.
 */
public final class PaperChunkAccess implements ChunkAccess {

    private final Plugin plugin;
    private final World world;
    private final Executor checkExecutor;
    private final int keepTickingSeconds;

    /**
     * @param checkExecutor runs {@link World#isChunkGenerated}, which may block on disk or wait for a server
     *        thread, so it must
     *        not share threads with the tasks' serial executors
     */
    public PaperChunkAccess(Plugin plugin, World world, Executor checkExecutor, int keepTickingSeconds) {
        this.plugin = plugin;
        this.world = world;
        this.checkExecutor = checkExecutor;
        this.keepTickingSeconds = keepTickingSeconds;
    }

    @Override
    public CompletableFuture<Boolean> isGenerated(int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> world.isChunkGenerated(chunkX, chunkZ), checkExecutor);
    }

    @Override
    public CompletableFuture<Void> load(int chunkX, int chunkZ) {
        return world.getChunkAtAsync(chunkX, chunkZ, true, false).thenAccept(chunk -> {
            if (chunk == null) {
                throw new IllegalStateException("Server returned no chunk at " + chunkX + ", " + chunkZ);
            }
            if (keepTickingSeconds > 0 && chunk.addPluginChunkTicket(plugin)) {
                plugin.getServer().getRegionScheduler().runDelayed(plugin, world, chunkX, chunkZ,
                        task -> world.removePluginChunkTicket(chunkX, chunkZ, plugin), keepTickingSeconds * 20L);
            }
        });
    }
}
