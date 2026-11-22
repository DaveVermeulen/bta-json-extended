package io.github.tryrodave.jsonextended.obj;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.option.GameSettings;
import net.minecraft.client.render.LightingCache;
import net.minecraft.client.render.block.color.BlockColor;
import net.minecraft.client.render.block.color.BlockColorDispatcher;
import net.minecraft.client.render.block.model.generic.BlockModelGeneric;
import net.minecraft.client.render.tessellator.TessellatorGeneral;
import net.minecraft.client.render.texture.stitcher.IconCoordinate;
import net.minecraft.client.render.texture.stitcher.TextureRegistry;
import net.minecraft.core.block.BlockLogic;
import net.minecraft.core.util.helper.Direction;
import net.minecraft.core.util.helper.LightIndexHelper;
import net.minecraft.core.util.helper.Side;
import net.minecraft.core.world.WorldSource;
import net.minecraft.core.world.pos.TilePosc;
import org.useless.dragonfly.DisplayPos;
import org.useless.dragonfly.models.block.StaticBlockModel;

/**
 * An OBJ model as a BTA {@link StaticBlockModel}, so anything that draws model JSON (blockstates,
 * {@code BlockModelGeneric}, 3D item models) draws OBJ too. Follows {@code knowledge/bta/static-model.md}.
 * <p>
 * Lighting: with smooth lighting on (and {@code ambient_occlusion} in the JSON), {@link ObjSmoothLight} per vertex.
 * Otherwise flat: a face lying on a block side takes the light of the neighbour there, any other face the block's
 * own light. Directional shading comes from the face normal.
 */
@Environment(EnvType.CLIENT)
public final class ObjStaticModel implements StaticBlockModel {
    private static final LightingCache LIGHT = new LightingCache();

    /** A quad ready to draw: texture resolved to an atlas id, colour and glow from its material. */
    private record Face(float[][] positions, float[][] uvs, float[] normal, String texture, int tintIndex,
                        float r, float g, float b, int emission, ObjQuad.Side cull) {}

    /** A face turned by one set of quarter turns, with what the turn decides worked out once. */
    private record Placed(float[][] positions, float[] normal, ObjQuad.Side cull, ObjQuad.Side onSide,
                          ObjQuad.Side lightSide) {}

    private final ObjModelJson json;
    private final List<Face> faces;
    /** Faces placed for each quarter-turn combination (x, y, z each 0-3), made the first time a block uses it. */
    private final Placed[][] placed = new Placed[64][];
    private final int renderLayer;
    private final Map<String, IconCoordinate> icons = new HashMap<>();
    private int iconsGeneration = -1;

    public ObjStaticModel(ObjModelJson json, ObjModel model) {
        this.json = json;
        List<Face> faces = new ArrayList<>();
        for (ObjQuad quad : model.quads(json.flipV())) {
            ObjMaterial m = quad.material();
            String texture = m == null || m.texture() == null ? json.particle() : json.texture(m.texture());
            float[] color = m == null ? ObjMaterial.WHITE : m.color();
            int emission = m != null && json.emissiveAmbient() ? Math.round(m.ambient() * 15f) : 0;
            faces.add(new Face(quad.positions(), quad.uvs(), ObjQuad.faceNormal(quad.positions()),
                    texture != null ? texture : json.particle(), m == null ? -1 : m.tintIndex(),
                    color[0], color[1], color[2], emission, json.automaticCulling() ? quad.cullSide() : null));
        }
        this.faces = List.copyOf(faces);
        this.renderLayer = "translucent".equals(json.renderType()) ? 1 : 0;
    }

    @Override
    public boolean renderAttached(BlockModelGeneric<? extends BlockLogic> source, TessellatorGeneral tess,
                                  WorldSource world, TilePosc pos, int rotX, int rotY, int rotZ,
                                  double xOff, double yOff, double zOff, boolean uvlock, boolean cullFaces,
                                  IconCoordinate overrideTexture) {
        LIGHT.setupCache(source.block, world, pos);
        // Same rule as BTA's own models: smooth lighting with the option on, and not for blocks that give off light.
        ObjSmoothLight smooth = json.ambientOcclusion() && Boolean.TRUE.equals(GameSettings.AMBIENT_OCCLUSION.value)
                && source.block.emission == 0 ? new ObjSmoothLight(LIGHT) : null;
        BlockColor colors = null;
        int culled = 0, checked = 0;
        boolean drew = false;
        Placed[] turned = placed(rotX, rotY, rotZ);
        for (int f = 0; f < turned.length; f++) {
            Face face = faces.get(f);
            Placed at = turned[f];
            if (cullFaces && at.cull() != null) {
                ObjQuad.Side side = at.cull();
                int bit = 1 << side.ordinal();
                if ((checked & bit) == 0) {
                    checked |= bit;
                    if (source.cullSide(world, pos, direction(side)))
                        culled |= bit;
                }
                if ((culled & bit) != 0)
                    continue;
            }
            IconCoordinate icon = overrideTexture != null ? overrideTexture : icon(face.texture());
            if (icon == null)
                continue;

            float r = face.r(), g = face.g(), b = face.b();
            if (face.tintIndex() >= 0) {
                if (colors == null)
                    colors = BlockColorDispatcher.getInstance().getDispatch(source.block);
                if (colors != null) {
                    int c = colors.getWorldColor(world, pos, face.tintIndex());
                    r *= ((c >> 16) & 0xFF) / 255f;
                    g *= ((c >> 8) & 0xFF) / 255f;
                    b *= (c & 0xFF) / 255f;
                }
            }

            float[][] p = at.positions();
            float[] normal = at.normal();
            // A face on a block side takes the neighbour's light there, like a full cube face; others the block's own.
            ObjQuad.Side onSide = at.onSide();
            byte light = onSide == null ? LIGHT.getLightIndex(0, 0, 0)
                    : LIGHT.getLightIndex(onSide.axis == 0 ? side(onSide) : 0, onSide.axis == 1 ? side(onSide) : 0,
                    onSide.axis == 2 ? side(onSide) : 0);
            if (face.emission() > LightIndexHelper.blockLightFromIndex(light))
                light = LightIndexHelper.setBlockLight(light, face.emission());

            if (json.shadeQuads())
                tess.setNormal(normal[0], normal[1], normal[2]);
            else
                tess.setNormal(0f, 1f, 0f);
            ObjQuad.Side lightSide = at.lightSide();
            for (int i = 0; i < 4; i++) {
                tess.setColorOpaque3f(r, g, b);
                if (smooth != null) {
                    smooth.sample(lightSide, p[i]);
                    byte corner = smooth.lightIndex;
                    if (face.emission() > LightIndexHelper.blockLightFromIndex(corner))
                        corner = LightIndexHelper.setBlockLight(corner, face.emission());
                    tess.setLightmapCoord1i(corner);
                    tess.setShade1i(face.emission() > 0 ? 255 : Math.round(smooth.shade * 255f));
                } else {
                    tess.setLightmapCoord1i(light);
                    // In BTA the shade is only the ambient-occlusion factor; world light goes through the light index.
                    tess.setShade1i(255);
                }
                tess.addVertexWithUV(p[i][0] + pos.x() + xOff, p[i][1] + pos.y() + yOff, p[i][2] + pos.z() + zOff,
                        icon.getSubIconU(face.uvs()[i][0]), icon.getSubIconV(face.uvs()[i][1]));
            }
            tess.setNormal(0f, 1f, 0f);
            drew = true;
        }
        return drew;
    }

    @Override
    public boolean renderStandalone(BlockModelGeneric<? extends BlockLogic> source, TessellatorGeneral tess,
                                    double x, double y, double z, int meta, byte lightIndex, BlockColor color) {
        boolean drew = false;
        tess.startDrawingQuads();
        for (Face face : faces) {
            IconCoordinate icon = icon(face.texture());
            if (icon == null)
                continue;
            float r = face.r(), g = face.g(), b = face.b();
            if (face.tintIndex() >= 0 && color != null) {
                int c = color.getFallbackColor(meta, face.tintIndex());
                r *= ((c >> 16) & 0xFF) / 255f;
                g *= ((c >> 8) & 0xFF) / 255f;
                b *= (c & 0xFF) / 255f;
            }
            byte light = lightIndex;
            if (face.emission() > LightIndexHelper.blockLightFromIndex(light))
                light = LightIndexHelper.setBlockLight(light, face.emission());
            float[] n = json.shadeQuads() ? face.normal() : new float[] {0, 1, 0};
            tess.setNormal(n[0], n[1], n[2]);
            for (int i = 0; i < 4; i++) {
                float[] p = face.positions()[i];
                tess.setColorOpaque3f(r, g, b);
                tess.setLightmapCoord1i(light);
                tess.addVertexWithUV(p[0] - 0.5, p[1] - 0.5, p[2] - 0.5,
                        icon.getSubIconU(face.uvs()[i][0]), icon.getSubIconV(face.uvs()[i][1]));
            }
            drew = true;
        }
        tess.draw();
        return drew;
    }

    @Override
    public IconCoordinate getOverlay() {
        return icon(json.particle());
    }

    @Override
    public IconCoordinate getParticle(Side side) {
        IconCoordinate icon = icon(json.particle());
        if (icon == null && !faces.isEmpty())
            icon = icon(faces.get(0).texture());
        return icon;
    }

    @Override
    public int particleColorIndex(Side side) {
        return -1;
    }

    @Override
    public int renderLayer() {
        return renderLayer;
    }

    @Override
    public DisplayPos getItemDisplayPos(String id) {
        return BLOCK_DISPLAY.getOrDefault(id, DisplayPos.DEFAULT_DISPLAY_POS);
    }

    /** BTA's {@code BlockModelStandard} poses for block items (knowledge/bta/static-model.md). */
    private static final Map<String, DisplayPos> BLOCK_DISPLAY = Map.of(
            DisplayPos.GUI, new DisplayPos(0, 0, 0, 30, 225, 0, 0.625f, 0.625f, 0.625f),
            DisplayPos.GROUND, new DisplayPos(0, 0.1875f, 0, 0, 0, 0, 0.25f, 0.25f, 0.25f),
            DisplayPos.FIXED, new DisplayPos(0, 0, 0, 0, 0, 0, 0.5f, 0.5f, 0.5f),
            DisplayPos.THIRD_PERSON_RIGHT_HAND, new DisplayPos(0, 0.15625f, 0, 75, 45, 0, 0.375f, 0.375f, 0.375f),
            DisplayPos.THIRD_PERSON_LEFT_HAND, new DisplayPos(0, 0.15625f, 0, 75, 45, 0, 0.375f, 0.375f, 0.375f),
            DisplayPos.FIRST_PERSON_RIGHT_HAND, new DisplayPos(0, 0, 0, 0, 45, 0, 0.4f, 0.4f, 0.4f),
            DisplayPos.FIRST_PERSON_LEFT_HAND, new DisplayPos(0, 0, 0, 0, 225, 0, 0.4f, 0.4f, 0.4f));

    /** The faces turned by these quarter turns; chunk rebuilds draw the same few turns over and over. */
    private Placed[] placed(int rotX, int rotY, int rotZ) {
        int x = Math.floorMod(rotX, 4), y = Math.floorMod(rotY, 4), z = Math.floorMod(rotZ, 4);
        int key = x << 4 | y << 2 | z;
        Placed[] turned = placed[key];
        if (turned != null)
            return turned;
        turned = new Placed[faces.size()];
        for (int f = 0; f < turned.length; f++) {
            Face face = faces.get(f);
            float[][] p = new float[4][];
            for (int i = 0; i < 4; i++)
                p[i] = QuarterTurns.point(face.positions()[i], x, y, z);
            float[] normal = QuarterTurns.vector(face.normal(), x, y, z);
            ObjQuad.Side cull = face.cull() == null ? null : QuarterTurns.side(face.cull(), x, y, z);
            turned[f] = new Placed(p, normal, cull, ObjQuad.cullSide(p, normal), ObjQuad.Side.nearest(normal));
        }
        placed[key] = turned;
        return turned;
    }

    private static int side(ObjQuad.Side side) {
        return side.bound == 0 ? -1 : 1;
    }

    private static Direction direction(ObjQuad.Side side) {
        return switch (side) {
            case DOWN -> Direction.DOWN;
            case UP -> Direction.UP;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case EAST -> Direction.EAST;
        };
    }

    /** Atlas entry for a texture id, cached until the atlas is rebuilt; null when it isn't in an atlas. */
    private IconCoordinate icon(String texture) {
        if (iconsGeneration != TextureRegistry.reloadGeneration) {
            icons.clear();
            iconsGeneration = TextureRegistry.reloadGeneration;
        }
        if (texture == null)
            return null;
        if (icons.containsKey(texture))
            return icons.get(texture);
        IconCoordinate icon;
        try {
            icon = TextureRegistry.getTexture(texture);
        } catch (RuntimeException e) {
            // TextureRegistry throws for an id with no atlas folder (one without block/ or item/).
            icon = null;
        }
        icons.put(texture, icon);
        return icon;
    }
}
