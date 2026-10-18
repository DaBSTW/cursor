package dev.pregen.geometry;

/** A 2D area on the block grid (x/z plane). Implementations are immutable and thread-safe. */
public sealed interface Shape permits RectangleShape, EllipseShape, DiamondShape, PolygonShape {

    boolean contains(double x, double z);

    /** Area in square blocks, used to estimate chunk counts for selections too large to count exactly. */
    double area();
}
