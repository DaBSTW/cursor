package dev.pregen.persistence;

import dev.pregen.generation.TaskCheckpoint;

/**
 * A paused or interrupted generation task as saved on disk.
 *
 * @param formatVersion bumped when the layout changes incompatibly
 */
public record StoredTask(int formatVersion, SelectionData selection, long patternIndex, long elapsedMillis) {

    public static final int CURRENT_FORMAT = 1;

    public static StoredTask from(TaskCheckpoint checkpoint) {
        return new StoredTask(CURRENT_FORMAT, SelectionData.from(checkpoint.selection()), checkpoint.patternIndex(),
                checkpoint.elapsedMillis());
    }

    public boolean isUsable() {
        return formatVersion == CURRENT_FORMAT && selection != null && patternIndex >= 0 && elapsedMillis >= 0
                && selection.toSelection().isPresent();
    }
}
