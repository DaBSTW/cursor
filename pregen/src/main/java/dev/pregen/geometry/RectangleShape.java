package dev.pregen.geometry;

public record RectangleShape(double centerX, double centerZ, double radiusX, double radiusZ) implements Shape {

    @Override
    public boolean contains(double x, double z) {
        return Math.abs(x - centerX) <= radiusX && Math.abs(z - centerZ) <= radiusZ;
    }

    @Override
    public double area() {
        return 4.0 * radiusX * radiusZ;
    }
}
