package dev.pregen.pattern;

/**
 * Deterministic sequence of chunk coordinates (packed with {@link dev.pregen.util.ChunkPos#pack}).
 *
 * <p>Determinism is what makes resuming possible: replaying the same pattern and skipping {@code position()}
 * coordinates
 * lands exactly where a task stopped. Not thread-safe; owned by a single task.
 */
public interface ChunkPattern {

    boolean hasNext();

    long next();

    /** Number of coordinates returned by {@link #next()} so far. */
    long position();

    /** Whether coordinates still need to be filtered by the selection shape. */
    default boolean appliesShapeFilter() {
        return true;
    }
}
