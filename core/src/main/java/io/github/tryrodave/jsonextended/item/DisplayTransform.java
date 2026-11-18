package io.github.tryrodave.jsonextended.item;

/**
 * One {@code display} entry, as written in the JSON: rotation in degrees (X, Y, Z), translation in pixels
 * (1/16 block), scale. Vanilla clamps translation to ±80 and scale to 4; {@link #clamped()} does the same.
 */
public record DisplayTransform(double rotX, double rotY, double rotZ,
                               double transX, double transY, double transZ,
                               double scaleX, double scaleY, double scaleZ) {
    public static final DisplayTransform IDENTITY = new DisplayTransform(0, 0, 0, 0, 0, 0, 1, 1, 1);

    public DisplayTransform clamped() {
        return new DisplayTransform(rotX, rotY, rotZ,
                clamp(transX, -80, 80), clamp(transY, -80, 80), clamp(transZ, -80, 80),
                clamp(scaleX, -4, 4), clamp(scaleY, -4, 4), clamp(scaleZ, -4, 4));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
