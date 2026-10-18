package dev.pregen.pattern;

import dev.pregen.util.ChunkPos;

/** Row by row: x varies fastest, then z. */
final class LoopPattern extends AbstractLookaheadPattern {

    private final ChunkBounds bounds;
    private long x;
    private long z;

    LoopPattern(ChunkBounds bounds) {
        this.bounds = bounds;
        this.x = bounds.minX();
        this.z = bounds.minZ();
    }

    @Override
    protected boolean advance() {
        if (z > bounds.maxZ()) {
            return false;
        }
        offer(ChunkPos.pack((int) x, (int) z));
        if (++x > bounds.maxX()) {
            x = bounds.minX();
            z++;
        }
        return true;
    }
}
