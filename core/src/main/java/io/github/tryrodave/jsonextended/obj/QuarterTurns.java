package io.github.tryrodave.jsonextended.obj;

/**
 * BTA's quarter-turn rotation for block models ({@code StaticBlockModel.renderAttached}): around the block centre,
 * X first, then Y, then Z, each by {@code turns * 90} degrees with the right-hand rule. So one Y turn moves east to
 * north, and vanilla's blockstate {@code "y": 90} (north to east) is 3 turns here. Exact for any whole turn count.
 */
public final class QuarterTurns {
    private QuarterTurns() {}

    /** A point in block space (0 to 1), rotated about (0.5, 0.5, 0.5). */
    public static float[] point(float[] p, int x, int y, int z) {
        float[] r = vector(new float[] {p[0] - 0.5f, p[1] - 0.5f, p[2] - 0.5f}, x, y, z);
        return new float[] {r[0] + 0.5f, r[1] + 0.5f, r[2] + 0.5f};
    }

    /** A direction (normal), rotated. */
    public static float[] vector(float[] v, int x, int y, int z) {
        float a = v[0], b = v[1], c = v[2], t;
        for (int i = Math.floorMod(x, 4); i > 0; i--) { // (x, y, z) -> (x, -z, y)
            t = b;
            b = -c;
            c = t;
        }
        for (int i = Math.floorMod(y, 4); i > 0; i--) { // (x, y, z) -> (z, y, -x)
            t = a;
            a = c;
            c = -t;
        }
        for (int i = Math.floorMod(z, 4); i > 0; i--) { // (x, y, z) -> (-y, x, z)
            t = a;
            a = -b;
            b = t;
        }
        return new float[] {a, b, c};
    }

    /** A block side, rotated: the side the rotated face lies on. */
    public static ObjQuad.Side side(ObjQuad.Side side, int x, int y, int z) {
        float[] n = new float[3];
        n[side.axis] = side.bound == 0 ? -1 : 1;
        return ObjQuad.Side.nearest(vector(n, x, y, z));
    }
}
