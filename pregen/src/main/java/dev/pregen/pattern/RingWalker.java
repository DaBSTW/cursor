package dev.pregen.pattern;

import dev.pregen.util.ChunkPos;

/**
 * Walks square rings around a center, ring 0 first, and yields the cells that fall inside the bounds. Each ring
 * {@code r > 0} has {@code 8r} cells walked clockwise from its north-west corner.
 */
final class RingWalker extends AbstractLookaheadPattern {

    private final ChunkBounds bounds;
    private final int centerX;
    private final int centerZ;
    private final int lastRing;
    private int ring;
    private long step;

    RingWalker(ChunkBounds bounds, int centerX, int centerZ) {
        this.bounds = bounds;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.lastRing = bounds.ringsToCover(centerX, centerZ);
    }

    @Override
    protected boolean advance() {
        while (ring <= lastRing) {
            long cells = ring == 0 ? 1 : 8L * ring;
            while (step < cells) {
                long current = step++;
                int x = cellX(current);
                int z = cellZ(current);
                if (bounds.contains(x, z)) {
                    offer(ChunkPos.pack(x, z));
                    return true;
                }
            }
            ring++;
            step = 0;
        }
        return false;
    }

    private int cellX(long step) {
        long side = 2L * ring;
        if (ring == 0) {
            return centerX;
        }
        if (step < side) {
            return (int) (centerX - ring + step);
        }
        if (step < 2 * side) {
            return centerX + ring;
        }
        if (step < 3 * side) {
            return (int) (centerX + ring - (step - 2 * side));
        }
        return centerX - ring;
    }

    private int cellZ(long step) {
        long side = 2L * ring;
        if (ring == 0) {
            return centerZ;
        }
        if (step < side) {
            return centerZ - ring;
        }
        if (step < 2 * side) {
            return (int) (centerZ - ring + (step - side));
        }
        if (step < 3 * side) {
            return centerZ + ring;
        }
        return (int) (centerZ + ring - (step - 3 * side));
    }
}
