package io.github.tryrodave.jsonextended.obj;

/**
 * One four-corner face ready to draw. Triangles repeat their last corner. Positions are in blocks (0 to 1 fills
 * the block), UVs 0 to 1 across the texture with V pointing up as in OBJ (flip it with {@code flip_v}).
 *
 * @param mesh     the {@link ObjMesh#name()} it came from
 * @param material its material, or null when it has none (the model JSON's particle texture is drawn)
 */
public record ObjQuad(String mesh, ObjMaterial material, float[][] positions, float[][] uvs, float[][] normals) {
    /** A block side, named as in model JSON. North is -Z, west is -X. */
    public enum Side {
        DOWN(1, 0), UP(1, 1), NORTH(2, 0), SOUTH(2, 1), WEST(0, 0), EAST(0, 1);

        /** 0 for X, 1 for Y, 2 for Z. */
        public final int axis;
        /** 0 or 1: the plane the side lies on. */
        public final int bound;

        Side(int axis, int bound) {
            this.axis = axis;
            this.bound = bound;
        }

        /** The side a normal points to most. */
        public static Side nearest(float[] normal) {
            float x = Math.abs(normal[0]), y = Math.abs(normal[1]), z = Math.abs(normal[2]);
            if (y >= x && y >= z)
                return normal[1] < 0 ? DOWN : UP;
            if (z >= x)
                return normal[2] < 0 ? NORTH : SOUTH;
            return normal[0] < 0 ? WEST : EAST;
        }
    }

    private static final float EPSILON = 1e-4f;

    /** The block side this face lies flat on and faces out of, or null for a face inside the block. */
    public Side cullSide() {
        return cullSide(positions, faceNormal(positions));
    }

    /**
     * The block side the corners lie flat on, when {@code normal} points out of the block through it; null
     * otherwise. A face there is hidden by a full neighbour on that side, and lit by the light on that side.
     */
    public static Side cullSide(float[][] positions, float[] normal) {
        float[] n = normalize(normal);
        for (Side side : Side.values()) {
            float outward = side.bound == 0 ? -n[side.axis] : n[side.axis];
            if (outward < 0.5f)
                continue;
            boolean flat = true;
            for (float[] p : positions)
                flat &= Math.abs(p[side.axis] - side.bound) < EPSILON;
            if (flat)
                return side;
        }
        return null;
    }

    /** The normal of four corners in counter-clockwise order: the cross product of the diagonals, unit length. */
    public static float[] faceNormal(float[][] p) {
        float ax = p[2][0] - p[0][0], ay = p[2][1] - p[0][1], az = p[2][2] - p[0][2];
        float bx = p[3][0] - p[1][0], by = p[3][1] - p[1][1], bz = p[3][2] - p[1][2];
        float[] n = {ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
        return normalize(n);
    }

    static float[] normalize(float[] n) {
        float length = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        if (length < 1e-12f)
            return new float[] {0, 1, 0};
        return new float[] {n[0] / length, n[1] / length, n[2] / length};
    }
}
