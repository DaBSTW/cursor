package dev.pregen.util;

/** Chunk coordinate helpers. Coordinates are packed into a single {@code long} to avoid boxing in hot paths. */
public final class ChunkPos {

    public static final int REGION_SIZE = 32;

    private ChunkPos() {
    }

    public static long pack(int x, int z) {
        return (x & 0xFFFFFFFFL) | ((long) z << 32);
    }

    public static int x(long packed) {
        return (int) packed;
    }

    public static int z(long packed) {
        return (int) (packed >> 32);
    }

    public static int blockToChunk(double block) {
        return (int) Math.floor(block / 16.0);
    }

    public static double chunkCenter(int chunk) {
        return chunk * 16.0 + 8.0;
    }

    public static int chunkToRegion(int chunk) {
        return Math.floorDiv(chunk, REGION_SIZE);
    }

    /** Index of a chunk inside its region file header (0..1023). */
    public static int indexInRegion(int chunkX, int chunkZ) {
        return Math.floorMod(chunkX, REGION_SIZE) + Math.floorMod(chunkZ, REGION_SIZE) * REGION_SIZE;
    }
}
