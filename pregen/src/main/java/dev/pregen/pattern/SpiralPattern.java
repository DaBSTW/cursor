package dev.pregen.pattern;

import dev.pregen.util.ChunkPos;

/**
 * Continuous outward spiral (east, south, west, north with legs 1, 1, 2, 2, 3, 3...). Unlike concentric rings, each
 * step is adjacent to the previous one, which keeps neighbouring chunks warm in the server's caches.
 */
final class SpiralPattern extends AbstractLookaheadPattern {

    private static final int[] DX = {1, 0, -1, 0};
    private static final int[] DZ = {0, 1, 0, -1};

    private final ChunkBounds bounds;
    private final long cellsToWalk;
    private long walked;
    private long x;
    private long z;
    private int direction;
    private long legLength = 1;
    private long legProgress;
    private int legsAtThisLength;

    SpiralPattern(ChunkBounds bounds, int centerX, int centerZ) {
        this.bounds = bounds;
        long side = 2L * bounds.ringsToCover(centerX, centerZ) + 1;
        this.cellsToWalk = side * side;
        this.x = centerX;
        this.z = centerZ;
    }

    @Override
    protected boolean advance() {
        while (walked < cellsToWalk) {
            long currentX = x;
            long currentZ = z;
            walked++;
            step();
            if (bounds.contains((int) currentX, (int) currentZ)) {
                offer(ChunkPos.pack((int) currentX, (int) currentZ));
                return true;
            }
        }
        return false;
    }

    private void step() {
        x += DX[direction];
        z += DZ[direction];
        if (++legProgress < legLength) {
            return;
        }
        legProgress = 0;
        direction = (direction + 1) % 4;
        if (++legsAtThisLength == 2) {
            legsAtThisLength = 0;
            legLength++;
        }
    }
}
