package dev.pregen.generation;

import dev.pregen.selection.Selection;

/**
 * Point from which a task can be resumed without losing chunks.
 *
 * @param patternIndex every pattern coordinate before this index is finished; coordinates after it may be redone
 */
public record TaskCheckpoint(Selection selection, long patternIndex, long elapsedMillis) {
}
