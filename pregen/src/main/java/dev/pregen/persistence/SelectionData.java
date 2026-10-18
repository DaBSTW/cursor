package dev.pregen.persistence;

import dev.pregen.geometry.ShapeType;
import dev.pregen.pattern.PatternType;
import dev.pregen.selection.Selection;
import java.util.Optional;

/**
 * Serialized form of a {@link Selection}. Shapes and patterns are stored by id, so renaming a Java constant can never
 * break saved files, and every field is validated again when read back.
 */
public record SelectionData(String world, double centerX, double centerZ, double radiusX, double radiusZ, String shape,
        String pattern, String patternArgument) {

    public static SelectionData from(Selection selection) {
        return new SelectionData(selection.worldName(), selection.centerX(), selection.centerZ(), selection.radiusX(),
                selection.radiusZ(), selection.shape().id(), selection.pattern().id(), selection.patternArgument());
    }

    /** Empty when the stored data is incomplete or invalid (hand edits, files from a future version). */
    public Optional<Selection> toSelection() {
        Optional<ShapeType> shapeType = ShapeType.fromId(shape);
        Optional<PatternType> patternType = PatternType.fromId(pattern);
        if (world == null || world.isBlank() || shapeType.isEmpty() || patternType.isEmpty()) {
            return Optional.empty();
        }
        String argument = patternArgument == null ? "" : patternArgument;
        if (patternType.get().requiresArgument() && !Selection.isValidFileName(argument)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Selection(world, centerX, centerZ, radiusX, radiusZ, shapeType.get(),
                    patternType.get(), argument));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
