package io.github.tryrodave.jsonextended.tileentity;

import io.github.tryrodave.jsonextended.JsonExtended;
import io.github.tryrodave.jsonextended.model.AtlasIcons;
import io.github.tryrodave.jsonextended.model.ElementQuads;
import io.github.tryrodave.jsonextended.model.Models;
import io.github.tryrodave.jsonextended.model.ResolvedModel;
import io.github.tryrodave.jsonextended.obj.QuarterTurns;
import io.github.tryrodave.jsonextended.pose.ModelQuad;
import io.github.tryrodave.jsonextended.pose.Pose;
import io.github.tryrodave.jsonextended.pose.Rig;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.tessellator.TessellatorGeneral;
import net.minecraft.client.render.texture.stitcher.IconCoordinate;
import net.minecraft.client.render.tileentity.TileEntityRenderer;
import net.minecraft.core.block.entity.TileEntity;
import net.minecraft.core.world.World;

/**
 * Draws a model JSON (a Blockbench "Java Block" export) for a tile entity every frame, with its parts posed from code.
 * Parts are Blockbench's groups (see {@code knowledge/formats/pose.md}); the {@link Poser} says how each part sits
 * this frame. The model is read once and again after a texture pack change (the game makes new renderers then).
 * <p>
 * Drawn by {@link TileEntityMesh}: faces are reused while the pose and facing stay the same, each face takes
 * light from the side it looks towards (blended with the block's own by depth), face shading comes from the
 * normal, and there is no ambient occlusion.
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
    private final AtlasIcons icons;
    /** Last drawn faces per tile entity; an unloaded one drops out with its tile entity. */
    private final Map<T, TileEntityMesh> meshes = new WeakHashMap<>();
    private static final float[] WHITE = {1f, 1f, 1f};

    /** @param modelId a model id as blockstates take it, e.g. {@code mymod:block/chest} */
    public ModelTileEntityRenderer(String modelId, Poser<T> poser, Facing<T> facing) {
        this.modelId = modelId;
        this.poser = poser;
        this.facing = facing;
        this.icons = new AtlasIcons(modelId);
    }

    public ModelTileEntityRenderer(String modelId, Poser<T> poser) {
        this(modelId, poser, te -> 0);
    }

    @Override
    public void tick() {
        super.tick();
        TileEntityMesh.sweep();
    }

    @Override
    public void onWorldChanged(World world) {
        super.onWorldChanged(world);
        meshes.clear();
        TileEntityMesh.freeAll();
    }

    @Override
    public void doRender(TessellatorGeneral tess, T te, double x, double y, double z, float partialTick) {
        if (!loaded())
            return;
        long started = System.nanoTime();
        TileEntityMesh mesh = meshes.computeIfAbsent(te, k -> new TileEntityMesh());
        // Far away, a moving model is re-posed less often (see TileEntityMesh.poseDue).
        if (mesh.poseDue(x, y, z)) {
            Pose pose = poser.pose(te, partialTick);
            int turns = facing.yTurns(te);
            Key key = new Key(pose, turns);
            if (!mesh.matches(key)) {
                mesh.begin(key);
                for (ModelQuad quad : rig.pose(quads, pose)) {
                    IconCoordinate icon = icon(quad.texture());
                    if (icon == null)
                        continue;
                    float[][] corners = new float[4][];
                    double[][] uvs = new double[4][];
                    for (int i = 0; i < 4; i++) {
                        corners[i] = QuarterTurns.point(quad.positions()[i], 0, turns, 0);
                        uvs[i] = new double[] {icon.getSubIconU(quad.uvs()[i][0]), icon.getSubIconV(quad.uvs()[i][1])};
                    }
                    float[] n = QuarterTurns.vector(quad.normal(), 0, turns, 0);
                    mesh.add(corners, uvs, n, n, WHITE, 0);
                }
            }
        }
        mesh.draw(tess, te, x, y, z);
        TileEntityMesh.timed(started);
    }

    /** What the faces were built for: the parts' pose and the facing. */
    private record Key(Pose pose, int turns) {
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

    /** Atlas entry for a texture id; null when it isn't in an atlas (logged once). */
    private IconCoordinate icon(String texture) {
        return icons.get(texture);
    }
}
