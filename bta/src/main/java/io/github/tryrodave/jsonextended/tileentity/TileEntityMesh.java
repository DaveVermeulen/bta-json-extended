package io.github.tryrodave.jsonextended.tileentity;

import java.util.Arrays;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.renderer.BlendFactor;
import net.minecraft.client.render.renderer.GLRenderer;
import net.minecraft.client.render.renderer.Shaders;
import net.minecraft.client.render.renderer.State;
import net.minecraft.client.render.tessellator.TessellatorGeneral;
import net.minecraft.client.render.texture.stitcher.TextureRegistry;
import net.minecraft.core.block.entity.TileEntity;
import net.minecraft.core.util.helper.LightIndexHelper;
import net.minecraft.core.world.World;

/**
 * The faces of one tile entity's model as last drawn, ready to send again, plus the light around its block.
 * <p>
 * Faces are rebuilt only when the key (pose and facing) changes or the atlas is rebuilt, so a still model costs
 * one pass over its vertices per frame and no posing. Light is read at most once per tick (50 ms): the block's
 * own, and for each face lying flat on the block's edge, the light of the block it faces, as chunk blocks do.
 * A face against an opaque block keeps the block's own light (in a chunk it would be culled).
 * <p>
 * Drawn with the item shader like BTA's own tile entities, so there is no ambient occlusion
 * (see {@code knowledge/bta/tile-entities.md}). Main thread only.
 */
@Environment(EnvType.CLIENT)
public final class TileEntityMesh {
    /** Side index for a face inside the block: it takes the block's own light. */
    public static final int INSIDE = 6;
    private static final long TICK_NANOS = 50_000_000L;
    private static final float EDGE = 1e-3f;
    /** Neighbour offsets for sides 0 to 5: down, up, north, south, west, east. */
    private static final int[][] SIDES = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    private Object key;
    private int generation = -1;
    private int count;
    private float[] positions = new float[0]; // 12 per face
    private float[] uvs = new float[0];       // 8 per face, atlas coordinates
    private float[] normals = new float[0];   // 3 per face
    private float[] colors = new float[0];    // 3 per face
    private byte[] sides = new byte[0];
    private byte[] emissions = new byte[0];

    private final byte[] light = new byte[7];
    private long lightRead;
    private boolean lightSet;

    /** True when the faces were built for this key and the current atlas, so they can be drawn as they are. */
    public boolean matches(Object key) {
        return generation == TextureRegistry.reloadGeneration && Objects.equals(this.key, key);
    }

    /** Forgets the faces and starts a new set for {@code key}. */
    public void begin(Object key) {
        this.key = key;
        this.generation = TextureRegistry.reloadGeneration;
        this.count = 0;
    }

    /**
     * Adds one face.
     *
     * @param corners  four corners in blocks, already turned to the block's facing
     * @param uvs      four atlas UVs
     * @param normal   the shading normal, already turned
     * @param geometry the face's geometric normal, already turned, for finding the side it lies on
     * @param emission block light the face gives itself, 0 to 15
     */
    public void add(float[][] corners, float[][] uvs, float[] normal, float[] geometry, float[] color, int emission) {
        grow();
        for (int i = 0; i < 4; i++) {
            System.arraycopy(corners[i], 0, positions, count * 12 + i * 3, 3);
            System.arraycopy(uvs[i], 0, this.uvs, count * 8 + i * 2, 2);
        }
        System.arraycopy(normal, 0, normals, count * 3, 3);
        System.arraycopy(color, 0, colors, count * 3, 3);
        sides[count] = (byte) side(corners, geometry);
        emissions[count] = (byte) Math.max(0, Math.min(15, emission));
        count++;
    }

    /** Draws the faces at the camera-relative position, with the light around {@code te}. */
    public void draw(TessellatorGeneral tess, TileEntity te, double x, double y, double z) {
        readLight(te);
        GLRenderer.pushFrame();
        try {
            GLRenderer.setShader(Shaders.ITEM);
            TextureRegistry.worldAtlas.bind();
            GLRenderer.enableState(State.BLEND);
            GLRenderer.setBlendFunc(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA);
            GLRenderer.modelM4f().translate((float) x, (float) y, (float) z);
            tess.startDrawingQuads();
            for (int f = 0; f < count; f++) {
                byte faceLight = light[sides[f]];
                if (emissions[f] > LightIndexHelper.blockLightFromIndex(faceLight))
                    faceLight = LightIndexHelper.setBlockLight(faceLight, emissions[f]);
                tess.setNormal(normals[f * 3], normals[f * 3 + 1], normals[f * 3 + 2]);
                for (int i = 0; i < 4; i++) {
                    int p = f * 12 + i * 3, t = f * 8 + i * 2;
                    tess.setColorOpaque3f(colors[f * 3], colors[f * 3 + 1], colors[f * 3 + 2]);
                    tess.setLightmapCoord1i(faceLight);
                    tess.setShade1i(255);
                    tess.addVertexWithUV(positions[p], positions[p + 1], positions[p + 2], uvs[t], uvs[t + 1]);
                }
            }
            tess.setNormal(0f, 1f, 0f);
            tess.draw();
        } finally {
            GLRenderer.disableState(State.BLEND);
            GLRenderer.popFrame();
        }
    }

    /** Reads the block's light and its six neighbours', at most once per tick. */
    private void readLight(TileEntity te) {
        long now = System.nanoTime();
        if (lightSet && now - lightRead < TICK_NANOS)
            return;
        lightSet = true;
        lightRead = now;
        World world = te.worldObj;
        int x = te.tilePos.x, y = te.tilePos.y, z = te.tilePos.z;
        light[INSIDE] = world.getLightmapCoord(x, y, z, 0);
        for (int s = 0; s < 6; s++) {
            int nx = x + SIDES[s][0], ny = y + SIDES[s][1], nz = z + SIDES[s][2];
            light[s] = world.isBlockOpaqueCube(nx, ny, nz) ? light[INSIDE] : world.getLightmapCoord(nx, ny, nz, 0);
        }
    }

    /** The side (0 to 5) a face lies flat on, facing out of the block; {@link #INSIDE} otherwise. */
    static int side(float[][] corners, float[] normal) {
        for (int axis = 0; axis < 3; axis++) {
            float n = normal[axis];
            if (Math.abs(n) < 0.99f)
                continue;
            float plane = n > 0 ? 1f : 0f;
            for (float[] c : corners)
                if (Math.abs(c[axis] - plane) > EDGE)
                    return INSIDE;
            // axis 0 is x (west 4, east 5), 1 is y (down 0, up 1), 2 is z (north 2, south 3)
            int base = axis == 0 ? 4 : axis == 1 ? 0 : 2;
            return base + (n > 0 ? 1 : 0);
        }
        return INSIDE;
    }

    private void grow() {
        if (count < sides.length)
            return;
        int size = Math.max(16, sides.length * 2);
        positions = Arrays.copyOf(positions, size * 12);
        uvs = Arrays.copyOf(uvs, size * 8);
        normals = Arrays.copyOf(normals, size * 3);
        colors = Arrays.copyOf(colors, size * 3);
        sides = Arrays.copyOf(sides, size);
        emissions = Arrays.copyOf(emissions, size);
    }
}
