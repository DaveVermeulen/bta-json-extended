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
import java.util.Arrays;
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
 * Draws a glTF model JSON ({@code "loader": "jsonextended:gltf"}) for a tile entity every frame, with its nodes
 * moved from code, usually by a {@link GltfAnimationPlayer} playing the file's own animations. The model is read
 * once and again after a texture pack change (the game makes new renderers then).
 * <p>
 * Drawn like {@code ModelTileEntityRenderer}, by {@link TileEntityMesh}: faces are reused while the pose and facing
 * stay the same, each face takes light from the side it looks towards; a material's {@code emissiveFactor} raises
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
        GltfModel model = model();
        if (model == null)
            return;
        long started = System.nanoTime();
        TileEntityMesh mesh = meshes.computeIfAbsent(te, k -> new TileEntityMesh());
        // Far away, a moving model is re-posed less often (see TileEntityMesh.poseDue).
        if (mesh.poseDue(x, y, z)) {
            Map<Integer, float[]> pose = poser.pose(te, model, partialTick);
            int turns = facing.yTurns(te);
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
        }
        mesh.draw(tess, te, x, y, z);
        TileEntityMesh.timed(started);
    }

    /** What the faces were built for: the node TRS (copied, compared by value) and the facing. */
    private static final class Key {
        private final int[] nodes;
        private final float[] values;
        private final int turns;
        private final int hash;

        private Key(int[] nodes, float[] values, int turns) {
            this.nodes = nodes;
            this.values = values;
            this.turns = turns;
            this.hash = 31 * (31 * Arrays.hashCode(nodes) + Arrays.hashCode(values)) + turns;
        }

        static Key of(Map<Integer, float[]> pose, int turns) {
            int[] nodes = new int[pose.size()];
            int n = 0, length = 0;
            for (Map.Entry<Integer, float[]> e : pose.entrySet()) {
                nodes[n++] = e.getKey();
                length += e.getValue().length;
            }
            Arrays.sort(nodes);
            float[] values = new float[length];
            int at = 0;
            for (int node : nodes) {
                float[] trs = pose.get(node);
                System.arraycopy(trs, 0, values, at, trs.length);
                at += trs.length;
            }
            return new Key(nodes, values, turns);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && turns == k.turns && hash == k.hash && Arrays.equals(nodes, k.nodes)
                    && Arrays.equals(values, k.values);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
