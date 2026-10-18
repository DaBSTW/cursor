package dev.pregen.selection;

import dev.pregen.geometry.Shape;
import dev.pregen.geometry.ShapeType;
import dev.pregen.pattern.ChunkBounds;
import dev.pregen.pattern.PatternType;
import dev.pregen.util.ChunkPos;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * What to generate or trim: a world, an area and an order. Immutable; every change produces a new instance, which is
 * what lets selections be shared across threads without locking.
 *
 * @param patternArgument extra pattern input (the CSV file name); empty when the pattern takes none
 */
public record Selection(String worldName, double centerX, double centerZ, double radiusX, double radiusZ,
        ShapeType shape, PatternType pattern, String patternArgument) {

    public static final double DEFAULT_RADIUS = 500.0;
    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    public Selection {
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(patternArgument, "patternArgument");
        if (!(radiusX > 0.0) || !(radiusZ > 0.0)) {
            throw new IllegalArgumentException("Radius must be positive: x=" + radiusX + " z=" + radiusZ);
        }
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
            throw new IllegalArgumentException("Center must be finite: x=" + centerX + " z=" + centerZ);
        }
    }

    public static Selection defaults(String worldName) {
        return new Selection(worldName, 0.0, 0.0, DEFAULT_RADIUS, DEFAULT_RADIUS, ShapeType.SQUARE, PatternType.REGION,
                "");
    }

    /** CSV names become file names, so anything that could escape the CSV folder is rejected. */
    public static boolean isValidFileName(String name) {
        return name != null && FILE_NAME.matcher(name).matches();
    }

    public double effectiveRadiusZ() {
        return shape.usesSecondRadius() ? radiusZ : radiusX;
    }

    public double largestRadius() {
        return Math.max(radiusX, effectiveRadiusZ());
    }

    public Shape toShape() {
        return shape.create(centerX, centerZ, radiusX, effectiveRadiusZ());
    }

    public ChunkBounds chunkBounds() {
        return ChunkBounds.around(centerX, centerZ, radiusX, effectiveRadiusZ());
    }

    public int centerChunkX() {
        return ChunkPos.blockToChunk(centerX);
    }

    public int centerChunkZ() {
        return ChunkPos.blockToChunk(centerZ);
    }

    public Selection withWorld(String newWorld) {
        return new Selection(newWorld, centerX, centerZ, radiusX, radiusZ, shape, pattern, patternArgument);
    }

    public Selection withCenter(double x, double z) {
        return new Selection(worldName, x, z, radiusX, radiusZ, shape, pattern, patternArgument);
    }

    public Selection withRadius(double radius) {
        return new Selection(worldName, centerX, centerZ, radius, radius, shape, pattern, patternArgument);
    }

    public Selection withRadii(double newRadiusX, double newRadiusZ) {
        return new Selection(worldName, centerX, centerZ, newRadiusX, newRadiusZ, shape, pattern, patternArgument);
    }

    public Selection withShape(ShapeType newShape) {
        return new Selection(worldName, centerX, centerZ, radiusX, radiusZ, newShape, pattern, patternArgument);
    }

    public Selection withPattern(PatternType newPattern, String argument) {
        return new Selection(worldName, centerX, centerZ, radiusX, radiusZ, shape, newPattern, argument);
    }
}
