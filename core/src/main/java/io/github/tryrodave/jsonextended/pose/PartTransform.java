package io.github.tryrodave.jsonextended.pose;

/**
 * How code moves one part: rotation in degrees about the part's pivot (applied X, then Y, then Z, right-hand
 * rule, as Blockbench shows it), then scale about the pivot, then translation in pixels (1/16 block).
 */
public record PartTransform(float rx, float ry, float rz, float tx, float ty, float tz, float sx, float sy, float sz) {
    public static final PartTransform NONE = new PartTransform(0, 0, 0, 0, 0, 0, 1, 1, 1);

    public static PartTransform rotation(float rx, float ry, float rz) {
        return new PartTransform(rx, ry, rz, 0, 0, 0, 1, 1, 1);
    }

    public static PartTransform translation(float tx, float ty, float tz) {
        return new PartTransform(0, 0, 0, tx, ty, tz, 1, 1, 1);
    }

    public PartTransform withRotation(float rx, float ry, float rz) {
        return new PartTransform(rx, ry, rz, tx, ty, tz, sx, sy, sz);
    }

    public PartTransform withTranslation(float tx, float ty, float tz) {
        return new PartTransform(rx, ry, rz, tx, ty, tz, sx, sy, sz);
    }

    public PartTransform withScale(float sx, float sy, float sz) {
        return new PartTransform(rx, ry, rz, tx, ty, tz, sx, sy, sz);
    }

    /** This transform followed by {@code other}'s offsets: rotations and translations add, scales multiply. */
    public PartTransform plus(PartTransform other) {
        return new PartTransform(rx + other.rx, ry + other.ry, rz + other.rz, tx + other.tx, ty + other.ty, tz + other.tz,
                sx * other.sx, sy * other.sy, sz * other.sz);
    }

    /** Each value from {@code a} at t = 0 to {@code b} at t = 1. */
    public static PartTransform lerp(PartTransform a, PartTransform b, float t) {
        return new PartTransform(mix(a.rx, b.rx, t), mix(a.ry, b.ry, t), mix(a.rz, b.rz, t),
                mix(a.tx, b.tx, t), mix(a.ty, b.ty, t), mix(a.tz, b.tz, t),
                mix(a.sx, b.sx, t), mix(a.sy, b.sy, t), mix(a.sz, b.sz, t));
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
