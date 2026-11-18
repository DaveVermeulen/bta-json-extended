package io.github.tryrodave.jsonextended.blockstate;

import io.github.tryrodave.jsonextended.model.Models;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.block.model.generic.BlockModelGeneric;
import net.minecraft.client.render.tessellator.TessellatorGeneral;
import net.minecraft.client.render.texture.stitcher.IconCoordinate;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.BlockLogic;
import net.minecraft.core.world.WorldSource;
import net.minecraft.core.world.pos.TilePosc;
import org.useless.dragonfly.models.block.StaticBlockModel;

/**
 * Draws a block from its blockstate file: metadata (plus a {@link StateResolver} for world-dependent properties)
 * picks the parts, and each part is BTA's own JSON model drawn with {@code renderAttached} quarter turns and
 * uvlock. Selections are cached per state in {@link Blockstate}.
 */
@Environment(EnvType.CLIENT)
public class BlockstateModel<T extends BlockLogic> extends BlockModelGeneric<T> {
    /** Spreads the seeds of several weighted multipart entries at one position. */
    private static final long PART_SEED_STEP = 0x9E3779B97F4A7C15L;

    protected final Blockstate blockstate;
    protected final Map<String, StaticBlockModel> models;
    protected final StateResolver resolver;
    /** Model for the inventory and wherever there's no world position; null means the state's first part. */
    protected final StaticBlockModel inventory;
    /** First value of every resolver property, for drawing without a world. */
    private final Map<String, String> defaults;
    /** BTA render pass from the models' {@code render_type}; -1 to keep BTA's choice. */
    private final int renderLayer;

    public BlockstateModel(Block<T> block, Blockstate blockstate, Map<String, StaticBlockModel> models,
                           StateResolver resolver, StaticBlockModel inventory, int renderLayer) {
        super(block, inventory != null ? inventory : firstModel(blockstate, models));
        this.blockstate = blockstate;
        this.models = Map.copyOf(models);
        this.resolver = resolver;
        this.inventory = inventory;
        Map<String, String> defaults = new LinkedHashMap<>();
        for (Property p : blockstate.resolverProperties())
            defaults.put(p.name(), p.values().get(0));
        this.defaults = Map.copyOf(defaults);
        this.renderLayer = renderLayer;
    }

    /** {@code render_type: translucent} on any part puts the block in BTA's blended pass. */
    @Override
    public int renderLayer() {
        return renderLayer >= 0 ? renderLayer : super.renderLayer();
    }

    public Blockstate blockstate() {
        return blockstate;
    }

    @Override
    public boolean renderAttached(TessellatorGeneral tessellator, WorldSource world, TilePosc pos,
                                  boolean cullFaces, IconCoordinate overrideTexture) {
        Selection selection = select(world, pos);
        if (selection.missing())
            return Models.missing().renderAttached(this, tessellator, world, pos, 0, 0, 0, 0, 0, 0, false, cullFaces, overrideTexture);
        long seed = getPositionalSeed(pos);
        boolean drew = false;
        int index = 0;
        for (Choice choice : selection.choices()) {
            Part part = choice.pick(seed + index++ * PART_SEED_STEP);
            drew |= model(part).renderAttached(this, tessellator, world, pos,
                    part.btaRotX(), part.btaRotY(), part.btaRotZ(), 0, 0, 0, part.uvlock(), cullFaces, overrideTexture);
        }
        return drew;
    }

    /** The first part at this position: BTA uses it for particles and the like. */
    @Override
    public StaticBlockModel getModel(WorldSource world, TilePosc pos) {
        Selection selection = select(world, pos);
        if (selection.missing() || selection.choices().isEmpty())
            return selection.missing() ? Models.missing() : staticModel;
        return model(selection.choices().get(0).pick(getPositionalSeed(pos)));
    }

    /** Without a world (inventory, held item): the inventory model, else the first part with default resolver values. */
    @Override
    public StaticBlockModel getModelFromData(int meta) {
        if (inventory != null)
            return inventory;
        Selection selection = blockstate.select(meta, defaults);
        if (selection.missing())
            return Models.missing();
        return selection.choices().isEmpty() ? staticModel : model(selection.choices().get(0).first());
    }

    protected Selection select(WorldSource world, TilePosc pos) {
        int meta = world.getBlockData(pos);
        Map<String, String> resolved = resolver != null && blockstate.needsResolver()
                ? resolver.resolve(world, pos, meta, blockstate)
                : Map.of();
        return blockstate.select(meta, resolved);
    }

    private StaticBlockModel model(Part part) {
        StaticBlockModel model = models.get(part.model());
        return model != null ? model : Models.missing();
    }

    private static StaticBlockModel firstModel(Blockstate blockstate, Map<String, StaticBlockModel> models) {
        for (String id : blockstate.models()) {
            StaticBlockModel model = models.get(id);
            if (model != null)
                return model;
        }
        return Models.missing();
    }
}
