package dev.pregen.pattern;

import dev.pregen.util.ChunkPos;

/**
 * Finishes one 32x32 region file before moving to the next, with regions visited in rings around the center. Writing a
 * region file in one go is the friendliest order for disk I/O and the region file cache.
 */
final class RegionPattern extends AbstractLookaheadPattern {

    private final ChunkBounds bounds;
    private final RingWalker regions;
    private int regionMinX;
    private int regionMinZ;
    private int offset = ChunkPos.REGION_SIZE * ChunkPos.REGION_SIZE;

    RegionPattern(ChunkBounds bounds, int centerX, int centerZ) {
        this.bounds = bounds;
        this.regions = new RingWalker(bounds.toRegions(), ChunkPos.chunkToRegion(centerX),
                ChunkPos.chunkToRegion(centerZ));
    }

    @Override
    protected boolean advance() {
        int cellsPerRegion = ChunkPos.REGION_SIZE * ChunkPos.REGION_SIZE;
        while (true) {
            while (offset < cellsPerRegion) {
                int x = regionMinX + offset % ChunkPos.REGION_SIZE;
                int z = regionMinZ + offset / ChunkPos.REGION_SIZE;
                offset++;
                if (bounds.contains(x, z)) {
                    offer(ChunkPos.pack(x, z));
                    return true;
                }
            }
            if (!regions.hasNext()) {
                return false;
            }
            long region = regions.next();
            regionMinX = ChunkPos.x(region) * ChunkPos.REGION_SIZE;
            regionMinZ = ChunkPos.z(region) * ChunkPos.REGION_SIZE;
            offset = 0;
        }
    }
}
