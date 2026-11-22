package io.github.tryrodave.jsonextended.gltf;

/** An affine transform for node placement: rows 0-2 of a 4x4 matrix, row-major. */
final class Matrix {
    static final Matrix IDENTITY = new Matrix(new double[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0});

    private final double[] m;

    private Matrix(double[] m) {
        this.m = m;
    }

    /** glTF's {@code matrix}: 16 numbers, column by column. The last row is assumed to be 0 0 0 1. */
    static Matrix columnMajor(float[] c) {
        return new Matrix(new double[] {c[0], c[4], c[8], c[12], c[1], c[5], c[9], c[13], c[2], c[6], c[10], c[14]});
    }

    /** Translation, then rotation (unit quaternion x, y, z, w), then scale: {@code T * R * S}. */
    static Matrix trs(float[] t, float[] q, float[] s) {
        double x = q[0], y = q[1], z = q[2], w = q[3];
        double n = Math.sqrt(x * x + y * y + z * z + w * w);
        if (n > 1e-12) {
            x /= n;
            y /= n;
            z /= n;
            w /= n;
        } else {
            x = y = z = 0;
            w = 1;
        }
        double[] r = {
                1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w),
                2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w),
                2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)};
        return new Matrix(new double[] {
                r[0] * s[0], r[1] * s[1], r[2] * s[2], t[0],
                r[3] * s[0], r[4] * s[1], r[5] * s[2], t[1],
                r[6] * s[0], r[7] * s[1], r[8] * s[2], t[2]});
    }

    Matrix times(Matrix o) {
        double[] a = m, b = o.m, r = new double[12];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                double v = a[row * 4] * b[col] + a[row * 4 + 1] * b[4 + col] + a[row * 4 + 2] * b[8 + col];
                if (col == 3)
                    v += a[row * 4 + 3];
                r[row * 4 + col] = v;
            }
        }
        return new Matrix(r);
    }

    float[] point(float x, float y, float z) {
        return new float[] {
                (float) (m[0] * x + m[1] * y + m[2] * z + m[3]),
                (float) (m[4] * x + m[5] * y + m[6] * z + m[7]),
                (float) (m[8] * x + m[9] * y + m[10] * z + m[11])};
    }

    /** Negative for a mirroring transform, which turns faces inside out. */
    double determinant() {
        return m[0] * (m[5] * m[10] - m[6] * m[9]) - m[1] * (m[4] * m[10] - m[6] * m[8]) + m[2] * (m[4] * m[9] - m[5] * m[8]);
    }
}
