package io.github.tryrodave.jsonextended.gltf;

import io.github.tryrodave.jsonextended.JsonExtended;
import io.github.tryrodave.jsonextended.model.AtlasIcons;
import io.github.tryrodave.jsonextended.model.Models;
import io.github.tryrodave.jsonextended.obj.ObjMaterial;
import io.github.tryrodave.jsonextended.obj.ObjQuad;
import io.github.tryrodave.jsonextended.obj.QuarterTurns;
import io.github.tryrodave.jsonextended.tileentity.ModelTileEntityRenderer.Facing;
import io.github.tryrodave.jsonextended.tileentity.TileEntityMesh;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.tessellator.TessellatorGeneral;
import net.minecraft.client.render.texture.stitcher.IconCoordinate;
import net.minecraft.client.render.tileentity.TileEntityRenderer;
import net.minecraft.core.block.entity.TileEntity;

/**
 * Draws a glTF model JSON ({@code "loader": "jsonextended:gltf"}) for a tile entity every frame, with its nodes
 * moved from code, usually by a {@link GltfAnimationPlayer} playing the file's own animations. The model is read
 * once and again after a texture pack change (the game makes new renderers then).
 * <p>
 * Drawn like {@code ModelTileEntityRenderer}, by {@link TileEntityMesh}: faces are reused while the pose and facing
 * stay the same, faces on the block's edge take their neighbour's light; a material's {@code emissiveFactor} raises
 * block light when {@code emissive_ambient} is on.
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
    /** Last drawn faces per tile entity; an unloaded one drops out with its tile entity. */
    private final Map<T, TileEntityMesh> meshes = new WeakHashMap<>();
    private static final float[] WHITE = {1f, 1f, 1f};
    private static final float[] UP = {0f, 1f, 0f};

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
        Map<Integer, float[]> pose = poser.pose(te, model, partialTick);
        int turns = facing.yTurns(te);
        TileEntityMesh mesh = meshes.computeIfAbsent(te, k -> new TileEntityMesh());
        Key key = Key.of(pose, turns);
        if (!mesh.matches(key)) {
            mesh.begin(key);
            for (ObjQuad quad : settings.place(model.pose(pose))) {
                ObjMaterial m = quad.material();
                String texture = m == null || m.texture() == null ? settings.particle() : settings.texture(m.texture());
                IconCoordinate icon = icons.get(texture != null ? texture : settings.particle());
                if (icon == null)
                    continue;
                int emission = m != null && settings.emissiveAmbient() ? Math.round(m.ambient() * 15f) : 0;
                float[] geometry = QuarterTurns.vector(ObjQuad.faceNormal(quad.positions()), 0, turns, 0);
                float[] n = settings.shadeQuads() ? geometry : UP;
                float[][] corners = new float[4][];
                double[][] uvs = new double[4][];
                for (int i = 0; i < 4; i++) {
                    corners[i] = QuarterTurns.point(quad.positions()[i], 0, turns, 0);
                    uvs[i] = new double[] {icon.getSubIconU(quad.uvs()[i][0]), icon.getSubIconV(quad.uvs()[i][1])};
                }
                mesh.add(corners, uvs, n, geometry, m == null ? WHITE : m.color(), emission);
            }
        }
        mesh.draw(tess, te, x, y, z);
    }

    /** What the faces were built for: a copy of the node TRS (compared by value) and the facing. */
    private record Key(Map<Integer, List<Float>> pose, int turns) {
        static Key of(Map<Integer, float[]> pose, int turns) {
            Map<Integer, List<Float>> copy = new HashMap<>();
            pose.forEach((node, trs) -> {
                List<Float> values = new ArrayList<>(trs.length);
                for (float v : trs)
                    values.add(v);
                copy.put(node, values);
            });
            return new Key(copy, turns);
        }
    }
}
