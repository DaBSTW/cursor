package dev.pregen.trim;

import dev.pregen.geometry.Shape;
import dev.pregen.selection.Selection;
import dev.pregen.util.ChunkPos;

/**
 * What to delete: chunks inside or outside the selection, optionally only those players barely visited.
 *
 * @param maxInhabitedTicks only delete chunks whose {@code InhabitedTime} is at most this; negative disables the filter
 */
public record TrimRequest(Selection selection, boolean deleteInside, long maxInhabitedTicks) {

    public boolean hasInhabitedFilter() {
        return maxInhabitedTicks >= 0;
    }

    /** Whether the chunk's position alone marks it for deletion. */
    public boolean selectsByPosition(Shape shape, int chunkX, int chunkZ) {
        boolean inside = shape.contains(ChunkPos.chunkCenter(chunkX), ChunkPos.chunkCenter(chunkZ));
        return inside == deleteInside;
    }
}
