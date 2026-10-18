package dev.pregen.generation;

import dev.pregen.geometry.Shape;
import dev.pregen.pattern.ChunkPattern;
import dev.pregen.selection.Selection;

/**
 * Everything a task needs to run, computed off the main thread by {@link TaskPreparer}.
 *
 * @param pattern positioned at the resume point; ownership passes to the task
 * @param initialProcessed chunks already handled before the resume point
 * @param exactTotal false when {@code totalChunks} was estimated from the shape's area
 */
public record TaskPlan(Selection selection, Shape shape, ChunkPattern pattern, long totalChunks, boolean exactTotal,
        long initialProcessed, long elapsedMillis, boolean skipExistingChunks) {
}
