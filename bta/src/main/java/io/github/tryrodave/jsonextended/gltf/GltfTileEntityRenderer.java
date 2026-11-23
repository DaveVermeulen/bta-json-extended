package io.github.tryrodave.jsonextended.gltf;

import io.github.tryrodave.jsonextended.JsonExtended;
import io.github.tryrodave.jsonextended.model.AtlasIcons;
import io.github.tryrodave.jsonextended.model.Models;
import io.github.tryrodave.jsonextended.obj.ObjMaterial;
import io.github.tryrodave.jsonextended.obj.ObjQuad;
import io.github.tryrodave.jsonextended.obj.QuarterTurns;
import io.github.tryrodave.jsonextended.tileentity.ModelTileEntityRenderer.Facing;
import java.io.UncheckedIOException;
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
import net.minecraft.core.util.helper.LightIndexHelper;

/**
 * Draws a glTF model JSON ({@code "loader": "jsonextended:gltf"}) for a tile entity every frame, with its nodes
 * moved from code, usually by a {@link GltfAnimationPlayer} playing the file's own animations. The model is read
 * once and again after a texture pack change (the game makes new renderers then).
 * <p>
 * Drawn like {@code ModelTileEntityRenderer}: the block's own light, face shading from the normal; a material's
 * {@code emissiveFactor} raises block light when {@code emissive_ambient} is on.
 */
@Environment(EnvType.CLIENT)
public class GltfTileEntityRenderer<T extends TileEntity> extends TileEntityRenderer<T> {
    /** How the nodes sit for one tile entity at one moment: node TRS for {@link GltfModel#pose}. */
    @FunctionalInterface
    public interface Poser<T> {
        Map<Integer, float[]> pose(T tileEntity, GltfModel model, float partialTick);
    }

    private final String modelId;
    private final Poser<T> poser;
    private final Facing<T> facing;
    private final AtlasIcons icons;
    private GltfModelJson settings;
    private GltfModel model;
    private boolean failed;

    /** @param modelId the model JSON's id, e.g. {@code mymod:block/fan} */
    public GltfTileEntityRenderer(String modelId, Poser<T> poser, Facing<T> facing) {
        this.modelId = modelId;
        this.poser = poser;
        this.facing = facing;
        this.icons = new AtlasIcons(modelId);
    }

    public GltfTileEntityRenderer(String modelId, Poser<T> poser) {
        this(modelId, poser, te -> 0);
    }

    /** The model, read on first use; null when it can't be read (logged once). */
    public GltfModel model() {
        if (model == null && !failed) {
            try {
                String json = Models.read(modelId);
                if (json == null)
                    throw new GltfException(modelId + ": no model JSON");
                settings = GltfModelJson.parse(modelId, json);
                settings.warnings().forEach(line -> Models.logOnce(false, line));
                model = GltfModel.load(settings.model(), Models::readBytes);
                model.warnings().forEach(line -> Models.logOnce(false, line));
            } catch (GltfException | UncheckedIOException e) {
                failed = true;
                JsonExtended.LOGGER.error("Tile entity model {} can't be drawn: {}", modelId, e.getMessage());
            }
        }
        return model;
    }

    @Override
    public void doRender(TessellatorGeneral tess, T te, double x, double y, double z, float partialTick) {
        GltfModel model = model();
        if (model == null)
            return;
        List<ObjQuad> quads = settings.place(model.pose(poser.pose(te, model, partialTick)));
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
            for (ObjQuad quad : quads) {
                ObjMaterial m = quad.material();
                String texture = m == null || m.texture() == null ? settings.particle() : settings.texture(m.texture());
                IconCoordinate icon = icons.get(texture != null ? texture : settings.particle());
                if (icon == null)
                    continue;
                byte faceLight = light;
                int emission = m != null && settings.emissiveAmbient() ? Math.round(m.ambient() * 15f) : 0;
                if (emission > LightIndexHelper.blockLightFromIndex(faceLight))
                    faceLight = LightIndexHelper.setBlockLight(faceLight, emission);
                float[] n = settings.shadeQuads() ? QuarterTurns.vector(ObjQuad.faceNormal(quad.positions()), 0, turns, 0)
                        : new float[] {0, 1, 0};
                tess.setNormal(n[0], n[1], n[2]);
                float[] color = m == null ? new float[] {1, 1, 1} : m.color();
                for (int i = 0; i < 4; i++) {
                    float[] p = QuarterTurns.point(quad.positions()[i], 0, turns, 0);
                    tess.setColorOpaque3f(color[0], color[1], color[2]);
                    tess.setLightmapCoord1i(faceLight);
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
}
