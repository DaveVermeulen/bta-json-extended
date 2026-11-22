package io.github.tryrodave.jsonextended.item;

import java.util.List;
import java.util.Random;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.block.color.BlockColor;
import net.minecraft.client.render.renderer.GLRenderer;
import net.minecraft.client.render.renderer.Shaders;
import net.minecraft.client.render.renderer.State;
import net.minecraft.client.render.tessellator.TessellatorGeneral;
import net.minecraft.client.render.texture.stitcher.IconCoordinate;
import net.minecraft.client.render.texture.stitcher.TextureRegistry;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.world.WorldSource;
import net.minecraft.core.world.pos.TilePosc;
import org.joml.Matrix4f;
import org.useless.dragonfly.DisplayPos;
import org.useless.dragonfly.models.block.StaticBlockModel;

/**
 * A 3D item from an item model JSON with {@code elements}: the elements are read and drawn by BTA's own JSON model
 * code ({@code StaticBlockModel.renderStandalone}, centred on the origin), posed like {@code ItemModelBlock} does
 * for 3D block items. The inventory icon keeps its depth and lighting, as for block items.
 */
@Environment(EnvType.CLIENT)
public class JsonItemModel3d extends JsonItemModel {
    /** Faces with a {@code tintindex} need a colour; item models have no tint source yet, so white. */
    private static final BlockColor WHITE = new BlockColor() {
        @Override
        public int getFallbackColor(int meta, int tintIndex) {
            return 0xFFFFFF;
        }

        @Override
        public int getWorldColor(WorldSource world, TilePosc pos, int tintIndex) {
            return 0xFFFFFF;
        }
    };

    /** Cluster offsets; reseeded every draw so they're the same each frame. Main thread only. */
    private static final Random CLUSTER = new Random();

    protected final StaticBlockModel geometry;

    public JsonItemModel3d(Item item, ResolvedItemModel model, IconCoordinate particle, StaticBlockModel geometry) {
        super(item, model, List.of(particle));
        this.geometry = geometry;
    }

    @Override
    public void render(TessellatorGeneral tessellator, Entity holder, ItemStack stack, String displayPosId,
                       boolean items3d, int clusterSize, byte lightIndex, float partialTick, boolean leftHanded) {
        GLRenderer.setShader(Shaders.ITEM);
        DisplayPos p = getDisplayPos(displayPosId);
        float mirror = leftHanded ? -1f : 1f;
        Matrix4f m = GLRenderer.modelM4f();
        // Same order and mirroring as ItemModelStandard/ItemModelBlock: translate, rotate X, Y, Z, scale.
        m.translate(p.tx * mirror, p.ty, p.tz)
                .rotateX((float) Math.toRadians(p.rx))
                .rotateY((float) Math.toRadians(p.ry * mirror))
                .rotateZ((float) Math.toRadians(p.rz * mirror))
                .scale(p.sx, p.sy, p.sz);
        TextureRegistry.worldAtlas.bind();
        int meta = stack == null ? 0 : stack.getMetadata();
        Random random = CLUSTER;
        random.setSeed(187);
        for (int i = 0; i < Math.max(1, clusterSize); i++) {
            float dx = 0, dy = 0, dz = 0;
            if (i > 0) {
                // Like block items on the ground: small random offsets, the same every frame.
                dx = (random.nextFloat() * 2 - 1) * 0.2f / p.sx;
                dy = (random.nextFloat() * 2 - 1) * 0.2f / p.sy;
                dz = (random.nextFloat() * 2 - 1) * 0.2f / p.sz;
                m.translate(dx, dy, dz);
            }
            // renderStandalone ignores its source model and position and draws centred on the origin.
            geometry.renderStandalone(null, tessellator, 0, 0, 0, meta, lightIndex, WHITE);
            if (i > 0)
                m.translate(-dx, -dy, -dz);
        }
    }

    /**
     * Like {@code ItemModelBlock.renderGui}: keeps depth (z scale 16) and lighting, so the model looks solid.
     * {@code gui_light: front} turns lighting off instead, as BTA does for flat items, so the model is drawn flat-lit.
     */
    @Override
    public void renderGui(TessellatorGeneral tessellator, Entity holder, ItemStack stack, int x, int y, byte lightIndex, float partialTick) {
        boolean front = "front".equals(model.guiLight());
        boolean lightWas = GLRenderer.globalGetLightEnabled();
        GLRenderer.pushFrame();
        try {
            if (front)
                GLRenderer.globalSetLightEnabled(false);
            GLRenderer.setShader(Shaders.ITEM);
            GLRenderer.enableState(State.BLEND);
            GLRenderer.enableState(State.CULL_FACE);
            GLRenderer.modelM4f().translate(x + 8f, y + 8f, 8f).scale(16f, -16f, 16f);
            render(tessellator, holder, stack, "gui", false, 1, lightIndex, partialTick, false);
        } finally {
            if (front)
                GLRenderer.globalSetLightEnabled(lightWas);
            GLRenderer.popFrame();
        }
    }
}
