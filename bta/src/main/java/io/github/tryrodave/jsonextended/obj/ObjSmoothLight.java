package io.github.tryrodave.jsonextended.obj;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.LightingCache;
import net.minecraft.core.util.helper.LightIndexHelper;

/**
 * Smooth lighting for OBJ faces at any position and angle, carried over from Betafront's OBJ renderer (Dave's own
 * code, see knowledge/prior-art.md).
 * <p>
 * Each corner of a block side averages the four blocks touching it (skipping ones hidden behind opaque blocks)
 * for the light index, and their brightness for the shade, like vanilla's ambient occlusion. That is worked out
 * for two layers: the neighbours outside the side, and the block's own layer. A vertex blends the four corners by
 * where it sits on the side, and the two layers by how deep inside the block it is. So a face flat on the block
 * edge gets vanilla's lighting and faces inside the block get in-between values. When the neighbour on that side
 * is opaque, the outside layer is the block's own (its light would be the dark inside of a solid block).
 * <p>
 * One instance per {@code renderAttached} call, after {@link LightingCache#setupCache}; corners are worked out
 * lazily, at most once per side and layer.
 */
@Environment(EnvType.CLIENT)
final class ObjSmoothLight {
    /** Offset of the neighbour on each side, by {@link ObjQuad.Side} ordinal: down, up, north, south, west, east. */
    private static final int[][] DIRECTION = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    /** The two axes lying in each side's plane. */
    private static final int[][] PLANE_AXES = {{0, 2}, {0, 2}, {0, 1}, {0, 1}, {1, 2}, {1, 2}};

    private final LightingCache cache;
    /** [side * 2 + layer][corner * 3 + (sky, block, brightness)], corner = u * 2 + v. */
    private final float[][] corners = new float[12][];

    /** Results of the last {@link #sample}. */
    byte lightIndex;
    float shade;

    ObjSmoothLight(LightingCache cache) {
        this.cache = cache;
    }

    /** Light index and shade for a vertex at {@code p} (block units) on a face looking towards {@code side}. */
    void sample(ObjQuad.Side side, float[] p) {
        int s = side.ordinal();
        int au = PLANE_AXES[s][0], av = PLANE_AXES[s][1];
        float along = clamp(p[side.axis]);
        float depth = side.bound == 1 ? 1f - along : along;
        float u = clamp(p[au]), v = clamp(p[av]);
        float w00 = (1 - u) * (1 - v), w01 = (1 - u) * v, w10 = u * (1 - v), w11 = u * v;

        float sky = 0, block = 0, bright = 0;
        for (int layer = 0; layer < 2; layer++) {
            float weight = layer == 0 ? 1f - depth : depth;
            if (weight <= 0f)
                continue;
            float[] c = corners(s, layer);
            sky += weight * (c[0] * w00 + c[3] * w01 + c[6] * w10 + c[9] * w11);
            block += weight * (c[1] * w00 + c[4] * w01 + c[7] * w10 + c[10] * w11);
            bright += weight * (c[2] * w00 + c[5] * w01 + c[8] * w10 + c[11] * w11);
        }
        lightIndex = LightIndexHelper.lightIndex2i(Math.round(sky), Math.round(block));
        shade = bright;
    }

    /** Corner values of one side, for the outside layer (0) or the block's own layer (1). */
    private float[] corners(int side, int layer) {
        int slot = side * 2 + layer;
        if (corners[slot] != null)
            return corners[slot];
        int[] d = DIRECTION[side];
        // A slanted face leaning towards an opaque neighbour (an upside-down ramp on the ground) can't get light from
        // inside that block, which is dark: it takes the block's own layer instead, as if it were inside the block.
        if (layer == 0 && cache.getOpacity(d[0], d[1], d[2]))
            return corners[slot] = corners(side, 1);
        int ox = layer == 0 ? d[0] : 0, oy = layer == 0 ? d[1] : 0, oz = layer == 0 ? d[2] : 0;
        int au = PLANE_AXES[side][0], av = PLANE_AXES[side][1];
        float[] c = new float[12];
        for (int u = 0; u < 2; u++) {
            for (int v = 0; v < 2; v++) {
                int[] a = {ox, oy, oz}, b = {ox, oy, oz}, ab = {ox, oy, oz};
                a[au] += u == 0 ? -1 : 1;
                b[av] += v == 0 ? -1 : 1;
                ab[au] = a[au];
                ab[av] = b[av];
                boolean aOpaque = cache.getOpacity(a[0], a[1], a[2]);
                boolean bOpaque = cache.getOpacity(b[0], b[1], b[2]);
                boolean abOpaque = cache.getOpacity(ab[0], ab[1], ab[2]);

                byte lc = cache.getLightIndex(ox, oy, oz);
                byte la = aOpaque ? lc : cache.getLightIndex(a[0], a[1], a[2]);
                byte lb = bOpaque ? lc : cache.getLightIndex(b[0], b[1], b[2]);
                byte lab = aOpaque && bOpaque ? la : abOpaque ? lc : cache.getLightIndex(ab[0], ab[1], ab[2]);
                byte corner = LightIndexHelper.avg(lc, la, lb, lab);

                float bc = cache.getBrightness(ox, oy, oz);
                float ba = cache.getBrightness(a[0], a[1], a[2]);
                float bb = cache.getBrightness(b[0], b[1], b[2]);
                float bab = aOpaque && bOpaque ? ba : cache.getBrightness(ab[0], ab[1], ab[2]);

                int i = (u * 2 + v) * 3;
                c[i] = LightIndexHelper.skyLightFromIndex(corner);
                c[i + 1] = LightIndexHelper.blockLightFromIndex(corner);
                c[i + 2] = (bc + ba + bb + bab) / 4f;
            }
        }
        corners[slot] = c;
        return c;
    }

    private static float clamp(float f) {
        return f < 0f ? 0f : Math.min(f, 1f);
    }
}
