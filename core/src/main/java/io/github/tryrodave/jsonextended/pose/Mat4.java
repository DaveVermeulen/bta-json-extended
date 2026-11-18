package io.github.tryrodave.jsonextended.pose;

/** A small affine 4x4 matrix (row-major, last row 0 0 0 1), so core needs no maths library. */
final class Mat4 {
    private final float[] m;

    private Mat4(float[] m) {
        this.m = m;
    }

    static Mat4 identity() {
        return new Mat4(new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0});
    }

    static Mat4 translation(float x, float y, float z) {
        return new Mat4(new float[] {1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z});
    }

    static Mat4 scale(float x, float y, float z) {
        return new Mat4(new float[] {x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0});
    }

    /** Rotation by degrees, X applied first, then Y, then Z (Rz * Ry * Rx), right-hand rule. */
    static Mat4 rotation(float x, float y, float z) {
        return axis(2, z).times(axis(1, y)).times(axis(0, x));
    }

    private static Mat4 axis(int a, float degrees) {
        if (degrees == 0)
            return identity();
        double rad = Math.toRadians(degrees);
        float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
        // Exact values for quarter turns, so 90 degrees moves a point exactly onto the grid.
        if (degrees % 90 == 0) {
            c = Math.round(c);
            s = Math.round(s);
        }
        return switch (a) {
            case 0 -> new Mat4(new float[] {1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0});
            case 1 -> new Mat4(new float[] {c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0});
            default -> new Mat4(new float[] {c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0});
        };
    }

    Mat4 times(Mat4 o) {
        float[] a = m, b = o.m, r = new float[12];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                float v = a[row * 4] * b[col] + a[row * 4 + 1] * b[4 + col] + a[row * 4 + 2] * b[8 + col];
                if (col == 3)
                    v += a[row * 4 + 3];
                r[row * 4 + col] = v;
            }
        }
        return new Mat4(r);
    }

    float[] point(float[] p) {
        return new float[] {
                m[0] * p[0] + m[1] * p[1] + m[2] * p[2] + m[3],
                m[4] * p[0] + m[5] * p[1] + m[6] * p[2] + m[7],
                m[8] * p[0] + m[9] * p[1] + m[10] * p[2] + m[11]};
    }

    /** A direction moved by the matrix's rotation and scale, then made unit length again. */
    float[] direction(float[] d) {
        float x = m[0] * d[0] + m[1] * d[1] + m[2] * d[2];
        float y = m[4] * d[0] + m[5] * d[1] + m[6] * d[2];
        float z = m[8] * d[0] + m[9] * d[1] + m[10] * d[2];
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        return length < 1e-12f ? new float[] {0, 1, 0} : new float[] {x / length, y / length, z / length};
    }

    boolean isIdentity() {
        float[] id = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};
        for (int i = 0; i < 12; i++)
            if (Math.abs(m[i] - id[i]) > 1e-7f)
                return false;
        return true;
    }
}
