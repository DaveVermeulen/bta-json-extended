package io.github.tryrodave.jsonextended.tileentity;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.renderer.BlendFactor;
import net.minecraft.client.render.renderer.GLRenderer;
import net.minecraft.client.render.renderer.Shaders;
import net.minecraft.client.render.renderer.State;
import net.minecraft.client.render.texture.stitcher.TextureRegistry;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL41;

/**
 * Every tile entity model mesh drawn this frame, drawn together after BTA's tile entity loop
 * ({@code RenderGlobal.renderEntities}, see {@code mixin/RenderGlobalMixin}).
 * <p>
 * {@code GLRenderer.render} binds the shader and sets about twenty uniforms for every call; with dozens of tile
 * entities that fixed cost was most of their frame time. Here the terrain shader is set up once, and each mesh
 * only changes the {@code model} matrix, binds its VAO and draws. The light uniforms are the same for every tile
 * entity in that pass, so the result is the same. Main thread only.
 */
@Environment(EnvType.CLIENT)
public final class TileEntityBatch {
    private static final int GL_TRIANGLES = 4;
    private static final List<Matrix4f> MATRICES = new ArrayList<>();
    private static int[] vaos = new int[64], counts = new int[64];
    private static int size;

    private TileEntityBatch() {
    }

    /** Queues one mesh: the current model matrix moved to {@code (x, y, z)}, its VAO and vertex count. */
    static void add(double x, double y, double z, int vao, int count) {
        if (size == vaos.length) {
            vaos = java.util.Arrays.copyOf(vaos, size * 2);
            counts = java.util.Arrays.copyOf(counts, size * 2);
        }
        if (size == MATRICES.size())
            MATRICES.add(new Matrix4f());
        MATRICES.get(size).set(GLRenderer.modelM4f()).translate((float) x, (float) y, (float) z);
        vaos[size] = vao;
        counts[size] = count;
        size++;
    }

    /** Draws and forgets everything queued this frame. Called once per frame, after the tile entity loop. */
    public static void flush() {
        if (size == 0)
            return;
        long started = System.nanoTime();
        GLRenderer.pushFrame();
        try {
            TextureRegistry.worldAtlas.bind();
            GLRenderer.setShader(Shaders.TERRAIN);
            GLRenderer.enableState(State.BLEND);
            GLRenderer.setBlendFunc(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA);
            GLRenderer.globalSetUniforms(Shaders.TERRAIN); // binds the shader and sets the shared uniforms once
            for (int i = 0; i < size; i++) {
                Shaders.TERRAIN.uniformMat4f("model", MATRICES.get(i));
                GL41.glBindVertexArray(vaos[i]);
                GL41.glDrawArrays(GL_TRIANGLES, 0, counts[i]);
            }
        } finally {
            GL41.glBindVertexArray(0);
            Shaders.TERRAIN.unbind();
            GLRenderer.disableState(State.BLEND);
            GLRenderer.popFrame();
            size = 0;
            TileEntityMesh.timedFlush(started);
        }
    }

    /** Forgets queued draws without drawing them, e.g. when their buffers are being freed. */
    static void clear() {
        size = 0;
    }
}
