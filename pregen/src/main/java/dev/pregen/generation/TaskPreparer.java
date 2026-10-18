package dev.pregen.generation;

import dev.pregen.geometry.Shape;
import dev.pregen.pattern.ChunkBounds;
import dev.pregen.pattern.ChunkPattern;
import dev.pregen.pattern.PatternType;
import dev.pregen.selection.Selection;
import dev.pregen.util.ChunkPos;

/**
 * Builds a {@link TaskPlan}: counts the chunks of the selection and replays the pattern up to the resume point. Pure
 * computation that can take seconds for huge selections, so it always runs off the main thread.
 */
public final class TaskPreparer {

    /** Above this many candidate chunks the total is estimated from the shape's area instead of counted. */
    static final long EXACT_COUNT_LIMIT = 64_000_000L;

    private TaskPreparer() {
    }

    /**
     * @param listChunks coordinates for list-based patterns ({@code csv}, {@code world}); ignored otherwise
     * @param resumeIndex number of pattern coordinates already handled (0 for a new task)
     */
    public static TaskPlan prepare(Selection selection, long[] listChunks, long resumeIndex, long elapsedMillis,
            boolean skipExistingChunks) {
        Shape shape = selection.toShape();
        ChunkBounds bounds = selection.chunkBounds();
        PatternType type = selection.pattern();
        ChunkPattern pattern = type.isListBased()
                ? PatternType.fromList(listChunks)
                : type.create(bounds, selection.centerChunkX(), selection.centerChunkZ());

        long total;
        boolean exact = true;
        if (type.isListBased()) {
            total = listChunks.length;
        } else if (bounds.area() <= EXACT_COUNT_LIMIT) {
            total = countExact(bounds, shape);
        } else {
            total = Math.min(bounds.area(), Math.round(shape.area() / 256.0));
            exact = false;
        }

        long processed = 0;
        for (long i = 0; i < resumeIndex && pattern.hasNext(); i++) {
            long packed = pattern.next();
            if (isSelected(pattern, shape, packed)) {
                processed++;
            }
        }
        // The "world" pattern exists to load chunks that are already on disk, so skipping them would do nothing.
        boolean skipExisting = skipExistingChunks && type != PatternType.WORLD;
        return new TaskPlan(selection, shape, pattern, Math.max(total, processed), exact, processed, elapsedMillis,
                skipExisting);
    }

    static boolean isSelected(ChunkPattern pattern, Shape shape, long packed) {
        return !pattern.appliesShapeFilter()
                || shape.contains(ChunkPos.chunkCenter(ChunkPos.x(packed)), ChunkPos.chunkCenter(ChunkPos.z(packed)));
    }

    static long countExact(ChunkBounds bounds, Shape shape) {
        long count = 0;
        for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
            double centerZ = ChunkPos.chunkCenter(z);
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                if (shape.contains(ChunkPos.chunkCenter(x), centerZ)) {
                    count++;
                }
            }
        }
        return count;
    }
}
