package com.luminex_studios.lxac.util;

import java.util.Objects;

/**
 * Simple 2D direction vector (X/Z plane) used to compare movement
 * or look-direction angles between two points in time.
 * <p>
 * Ported from AntiBaritoneX (kireiko.dev.antibaritonex.utils.RayLine)
 * https://github.com/Kireiko-dev/AntiBaritoneX
 */
public class RayLine {

    private final double x;
    private final double z;

    public RayLine(double x, double z) {
        this.x = x;
        this.z = z;
    }

    public double x() {
        return x;
    }

    public double z() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RayLine rayLine = (RayLine) o;
        return Double.compare(rayLine.x, x) == 0 && Double.compare(rayLine.z, z) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }

    @Override
    public String toString() {
        return "RayLine{" +
                "x=" + x +
                ", z=" + z +
                '}';
    }
}
