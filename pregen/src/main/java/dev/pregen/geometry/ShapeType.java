package dev.pregen.geometry;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Selectable shapes. The id is what users type and what is persisted, so ids must never change. */
public enum ShapeType {
    SQUARE("square", false),
    RECTANGLE("rectangle", true),
    CIRCLE("circle", false),
    ELLIPSE("ellipse", true, "oval"),
    DIAMOND("diamond", true),
    TRIANGLE("triangle", true),
    PENTAGON("pentagon", true),
    HEXAGON("hexagon", true),
    STAR("star", true);

    // Inner/outer radius ratio of a regular five-pointed star (pentagram).
    private static final double STAR_INNER_RATIO = 0.381966;
    private static final List<String> IDS = Arrays.stream(values()).map(ShapeType::id).toList();

    private final String id;
    private final boolean usesSecondRadius;
    private final List<String> aliases;

    ShapeType(String id, boolean usesSecondRadius, String... aliases) {
        this.id = id;
        this.usesSecondRadius = usesSecondRadius;
        this.aliases = List.of(aliases);
    }

    public String id() {
        return id;
    }

    /** Whether a separate z radius is honoured; single-radius shapes reuse the x radius on both axes. */
    public boolean usesSecondRadius() {
        return usesSecondRadius;
    }

    public static List<String> ids() {
        return IDS;
    }

    public static Optional<ShapeType> fromId(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(type -> type.id.equals(normalized) || type.aliases.contains(normalized))
                .findFirst();
    }

    public Shape create(double centerX, double centerZ, double radiusX, double radiusZ) {
        double rz = usesSecondRadius ? radiusZ : radiusX;
        return switch (this) {
            case SQUARE, RECTANGLE -> new RectangleShape(centerX, centerZ, radiusX, rz);
            case CIRCLE, ELLIPSE -> new EllipseShape(centerX, centerZ, radiusX, rz);
            case DIAMOND -> new DiamondShape(centerX, centerZ, radiusX, rz);
            case TRIANGLE -> PolygonShape.regular(centerX, centerZ, radiusX, rz, 3, 1.0);
            case PENTAGON -> PolygonShape.regular(centerX, centerZ, radiusX, rz, 5, 1.0);
            case HEXAGON -> PolygonShape.regular(centerX, centerZ, radiusX, rz, 6, 1.0);
            case STAR -> PolygonShape.regular(centerX, centerZ, radiusX, rz, 5, STAR_INNER_RATIO);
        };
    }
}
