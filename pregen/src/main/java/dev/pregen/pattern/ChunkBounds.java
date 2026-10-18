package dev.pregen.pattern;

import dev.pregen.util.ChunkPos;

/** Inclusive rectangle of chunk coordinates. */
public record ChunkBounds(int minX, int minZ, int maxX, int maxZ) {

    public ChunkBounds {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("Empty bounds: x=" + minX + ".." + maxX + " z=" + minZ + ".." + maxZ);
        }
    }

    public static ChunkBounds around(double centerX, double centerZ, double radiusX, double radiusZ) {
        return new ChunkBounds(ChunkPos.blockToChunk(centerX - radiusX), ChunkPos.blockToChunk(centerZ - radiusZ),
                ChunkPos.blockToChunk(centerX + radiusX), ChunkPos.blockToChunk(centerZ + radiusZ));
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public long width() {
        return (long) maxX - minX + 1;
    }

    public long depth() {
        return (long) maxZ - minZ + 1;
    }

    public long area() {
        return width() * depth();
    }

    public ChunkBounds toRegions() {
        return new ChunkBounds(ChunkPos.chunkToRegion(minX), ChunkPos.chunkToRegion(minZ), ChunkPos.chunkToRegion(maxX),
                ChunkPos.chunkToRegion(maxZ));
    }

    /** Distance in rings from the given point to the farthest edge; walking that many rings covers the bounds. */
    public int ringsToCover(int centerX, int centerZ) {
        long dx = Math.max(Math.abs((long) centerX - minX), Math.abs((long) maxX - centerX));
        long dz = Math.max(Math.abs((long) centerZ - minZ), Math.abs((long) maxZ - centerZ));
        return (int) Math.min(Integer.MAX_VALUE, Math.max(dx, dz));
    }
}
