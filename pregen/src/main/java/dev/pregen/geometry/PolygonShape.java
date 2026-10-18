package dev.pregen.geometry;

import java.util.Arrays;

/** Arbitrary simple polygon (convex or concave), tested with the even-odd ray casting rule. */
public final class PolygonShape implements Shape {

    private final double[] xs;
    private final double[] zs;
    private final double area;

    PolygonShape(double[] xs, double[] zs) {
        if (xs.length != zs.length || xs.length < 3) {
            throw new IllegalArgumentException("A polygon needs at least 3 vertices, got " + xs.length);
        }
        this.xs = xs.clone();
        this.zs = zs.clone();
        this.area = computeArea(this.xs, this.zs);
    }

    /**
     * Builds a regular polygon (or a star when {@code innerRatio < 1}) inscribed in the given ellipse, with the first
     * vertex pointing north (negative z).
     */
    static PolygonShape regular(double centerX, double centerZ, double radiusX, double radiusZ, int points,
            double innerRatio) {
        boolean star = innerRatio < 1.0;
        int vertices = star ? points * 2 : points;
        double[] xs = new double[vertices];
        double[] zs = new double[vertices];
        for (int i = 0; i < vertices; i++) {
            double scale = star && i % 2 == 1 ? innerRatio : 1.0;
            double angle = -Math.PI / 2.0 + 2.0 * Math.PI * i / vertices;
            xs[i] = centerX + Math.cos(angle) * radiusX * scale;
            zs[i] = centerZ + Math.sin(angle) * radiusZ * scale;
        }
        return new PolygonShape(xs, zs);
    }

    @Override
    public boolean contains(double x, double z) {
        boolean inside = false;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            boolean crosses = (zs[i] > z) != (zs[j] > z);
            if (crosses && x < (xs[j] - xs[i]) * (z - zs[i]) / (zs[j] - zs[i]) + xs[i]) {
                inside = !inside;
            }
        }
        return inside;
    }

    @Override
    public double area() {
        return area;
    }

    private static double computeArea(double[] xs, double[] zs) {
        double sum = 0.0;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            sum += xs[j] * zs[i] - xs[i] * zs[j];
        }
        return Math.abs(sum) / 2.0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PolygonShape polygon && Arrays.equals(xs, polygon.xs) && Arrays.equals(zs, polygon.zs);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(xs) + Arrays.hashCode(zs);
    }
}
