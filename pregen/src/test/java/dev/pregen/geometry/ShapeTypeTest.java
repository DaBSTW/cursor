package dev.pregen.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ShapeTypeTest {

    @ParameterizedTest
    @EnumSource(ShapeType.class)
    void everyShapeContainsItsCenterAndExcludesFarPoints(ShapeType type) {
        Shape shape = type.create(100, -50, 200, 120);
        assertTrue(shape.contains(100, -50), type.id());
        assertFalse(shape.contains(100 + 201, -50), type.id());
        assertFalse(shape.contains(100, -50 - 201), type.id());
    }

    @ParameterizedTest
    @EnumSource(ShapeType.class)
    void areaMatchesAMonteCarloEstimate(ShapeType type) {
        Shape shape = type.create(0, 0, 100, 60);
        double radiusZ = type.usesSecondRadius() ? 60 : 100;
        int inside = 0;
        int samples = 0;
        for (double x = -100; x <= 100; x += 0.5) {
            for (double z = -radiusZ; z <= radiusZ; z += 0.5) {
                samples++;
                if (shape.contains(x, z)) {
                    inside++;
                }
            }
        }
        double estimated = 200.0 * 2 * radiusZ * inside / samples;
        assertEquals(shape.area(), estimated, shape.area() * 0.03, type.id());
    }

    @Test
    void singleRadiusShapesIgnoreTheSecondRadius() {
        Shape circle = ShapeType.CIRCLE.create(0, 0, 100, 10);
        assertTrue(circle.contains(0, 90));
        Shape square = ShapeType.SQUARE.create(0, 0, 100, 10);
        assertTrue(square.contains(99, 99));
    }

    @Test
    void ellipseHonoursBothRadii() {
        Shape ellipse = ShapeType.ELLIPSE.create(0, 0, 100, 10);
        assertTrue(ellipse.contains(99, 0));
        assertFalse(ellipse.contains(0, 11));
    }

    @Test
    void starIsConcave() {
        Shape star = ShapeType.STAR.create(0, 0, 100, 100);
        assertTrue(star.contains(0, -95));
        // Between two tips, beyond the inner radius, lies outside the star.
        double angle = -Math.PI / 2 + Math.PI / 5;
        assertFalse(star.contains(Math.cos(angle) * 60, Math.sin(angle) * 60));
    }

    @Test
    void parsesIdsAndAliasesCaseInsensitively() {
        assertEquals(Optional.of(ShapeType.ELLIPSE), ShapeType.fromId("Oval"));
        assertEquals(Optional.of(ShapeType.HEXAGON), ShapeType.fromId(" HEXAGON "));
        assertTrue(ShapeType.fromId("blob").isEmpty());
        assertTrue(ShapeType.fromId(null).isEmpty());
    }
}
