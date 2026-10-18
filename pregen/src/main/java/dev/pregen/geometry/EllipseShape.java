package dev.pregen.geometry;

public record EllipseShape(double centerX, double centerZ, double radiusX, double radiusZ) implements Shape {

    @Override
    public boolean contains(double x, double z) {
        double dx = (x - centerX) / radiusX;
        double dz = (z - centerZ) / radiusZ;
        return dx * dx + dz * dz <= 1.0;
    }

    @Override
    public double area() {
        return Math.PI * radiusX * radiusZ;
    }
}
