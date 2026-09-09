package com.luminex_studios.lxac.util;

/**
 * Angle/vector math helpers used by the Baritone pattern detection.
 * <p>
 * Ported from AntiBaritoneX (kireiko.dev.antibaritonex.utils.RayUtils),
 * trimmed down to only the methods used by {@code BaritoneBChecker}.
 * https://github.com/Kireiko-dev/AntiBaritoneX
 */
public class RayUtils {

    public static double scaleVal(double value, double scale) {
        double scale2 = Math.pow(10, scale);
        return Math.ceil(value * scale2) / scale2;
    }

    public static float wrapYaw(float yaw) {
        float yR = (float) Math.toRadians(yaw);
        RayLine a = new RayLine(-Math.sin(yR), Math.cos(yR));
        return (float) calculateRayLine(a);
    }

    public static double calculateRayLines(RayLine a, RayLine b) {
        double angleA = Math.atan2(a.z(), a.x());
        double angleB = Math.atan2(b.z(), b.x());

        double angleDiff = Math.abs(angleA - angleB);

        if (angleDiff > Math.PI) {
            angleDiff = 2 * Math.PI - angleDiff;
        }

        return Math.toDegrees(angleDiff);
    }

    public static double calculateRayLine(RayLine a) {
        return Math.atan2(a.z(), a.x());
    }
}
