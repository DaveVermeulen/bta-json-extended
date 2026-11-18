package io.github.tryrodave.jsonextended.tileentity;

import io.github.tryrodave.jsonextended.JsonExtended;
import io.github.tryrodave.jsonextended.model.ElementQuads;
import io.github.tryrodave.jsonextended.model.Models;
import io.github.tryrodave.jsonextended.model.ResolvedModel;
import io.github.tryrodave.jsonextended.obj.QuarterTurns;
import io.github.tryrodave.jsonextended.pose.ModelQuad;
import io.github.tryrodave.jsonextended.pose.Pose;
import io.github.tryrodave.jsonextended.pose.Rig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.renderer.BlendFactor;
import net.minecraft.client.render.renderer.GLRenderer;
import net.minecraft.client.render.renderer.Shaders;
import net.minecraft.client.render.renderer.State;
import net.minecraft.client.render.tessellator.TessellatorGeneral;
import net.minecraft.client.render.texture.stitcher.IconCoordinate;
import net.minecraft.client.render.texture.stitcher.TextureRegistry;
import net.minecraft.client.render.tileentity.TileEntityRenderer;
import net.minecraft.core.block.entity.TileEntity;

/**
 * Draws a model JSON (a Blockbench "Java Block" export) for a tile entity every frame, with its parts posed from code.
 * Parts are Blockbench's groups (see {@code knowledge/formats/pose.md}); the {@link Poser} says how each part sits
 * this frame. The model is read once and again after a texture pack change (the game makes new renderers then).
 * <p>
 * Drawn under the model matrix like BTA's flower jar renderer: the block's own light, face shading from the normal.
 */
@Environment(EnvType.CLIENT)
public class ModelTileEntityRenderer<T extends TileEntity> extends TileEntityRenderer<T> {
    /** How the parts sit for one tile entity at one moment. */
    @FunctionalInterface
    public interface Poser<T> {
        Pose pose(T tileEntity, float partialTick);
    }

    /** Quarter turns about Y (as in {@link QuarterTurns}) for one tile entity, e.g. from its facing. */
    @FunctionalInterface
    public interface Facing<T> {
        int yTurns(T tileEntity);
    }

    private final String modelId;
    private final Poser<T> poser;
    private final Facing<T> facing;
    private List<ModelQuad> quads;
    private Rig rig;
    private final Map<String, IconCoordinate> icons = new HashMap<>();
    private int iconsGeneration = -1;

    /** @param modelId a model id as blockstates take it, e.g. {@code mymod:block/chest} */
    public ModelTileEntityRenderer(String modelId, Poser<T> poser, Facing<T> facing) {
        this.modelId = modelId;
        this.poser = poser;
        this.facing = facing;
    }

    public ModelTileEntityRenderer(String modelId, Poser<T> poser) {
        this(modelId, poser, te -> 0);
    }

    @Override
    public void doRender(TessellatorGeneral tess, T te, double x, double y, double z, float partialTick) {
        if (!loaded())
            return;
        List<ModelQuad> posed = rig.pose(quads, poser.pose(te, partialTick));
        int turns = facing.yTurns(te);
        // The block's own light, as BTA's dispatcher sets it before doRender (the model is drawn inside the block).
        byte light = te.worldObj.getLightmapCoord(te.tilePos.x, te.tilePos.y, te.tilePos.z, 0);

        GLRenderer.pushFrame();
        try {
            GLRenderer.setShader(Shaders.ITEM);
            TextureRegistry.worldAtlas.bind();
            GLRenderer.enableState(State.BLEND);
            GLRenderer.setBlendFunc(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA);
            GLRenderer.modelM4f().translate((float) x, (float) y, (float) z);
            tess.startDrawingQuads();
            for (ModelQuad quad : posed) {
                IconCoordinate icon = icon(quad.texture());
                if (icon == null)
                    continue;
                float[] n = QuarterTurns.vector(quad.normal(), 0, turns, 0);
                tess.setNormal(n[0], n[1], n[2]);
                tess.setColorOpaque3f(1f, 1f, 1f);
                for (int i = 0; i < 4; i++) {
                    float[] p = QuarterTurns.point(quad.positions()[i], 0, turns, 0);
                    tess.setLightmapCoord1i(light);
                    tess.setShade1i(255);
                    tess.addVertexWithUV(p[0], p[1], p[2],
                            icon.getSubIconU(quad.uvs()[i][0]), icon.getSubIconV(quad.uvs()[i][1]));
                }
            }
            tess.setNormal(0f, 1f, 0f);
            tess.draw();
        } finally {
            GLRenderer.disableState(State.BLEND);
            GLRenderer.popFrame();
        }
    }

    /** Reads and bakes the model the first time; false (and one error line) when it can't be read. */
    private boolean loaded() {
        if (quads != null)
            return rig != null;
        try {
            ResolvedModel model = ResolvedModel.load(modelId, Models::read);
            quads = ElementQuads.bake(model);
            rig = Rig.fromGroups(model.groups(), model.elements().size());
        } catch (RuntimeException e) {
            quads = List.of();
            JsonExtended.LOGGER.error("Tile entity model {} can't be drawn: {}", modelId, e.getMessage());
        }
        return rig != null;
    }

    /** Atlas entry for a texture id, cached until the atlas is rebuilt; null when it isn't in an atlas. */
    private IconCoordinate icon(String texture) {
        if (iconsGeneration != TextureRegistry.reloadGeneration) {
            icons.clear();
            iconsGeneration = TextureRegistry.reloadGeneration;
        }
        if (texture == null)
            return null;
        return icons.computeIfAbsent(texture, t -> {
            try {
                return TextureRegistry.getTexture(t);
            } catch (RuntimeException e) {
                return null;
            }
        });
    }
}
