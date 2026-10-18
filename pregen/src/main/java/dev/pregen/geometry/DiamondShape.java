package dev.pregen.geometry;

public record DiamondShape(double centerX, double centerZ, double radiusX, double radiusZ) implements Shape {

    @Override
    public boolean contains(double x, double z) {
        return Math.abs(x - centerX) / radiusX + Math.abs(z - centerZ) / radiusZ <= 1.0;
    }

    @Override
    public double area() {
        return 2.0 * radiusX * radiusZ;
    }
}
