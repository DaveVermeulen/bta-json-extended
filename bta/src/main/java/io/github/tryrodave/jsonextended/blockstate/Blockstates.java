package io.github.tryrodave.jsonextended.blockstate;

import io.github.tryrodave.jsonextended.JsonExtended;
import io.github.tryrodave.jsonextended.model.Models;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.block.model.BlockModel;
import net.minecraft.client.render.block.model.generic.BlockModelGeneric;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.BlockLogic;
import org.useless.dragonfly.data.block.mojang.BlockModelMojangData;
import org.useless.dragonfly.models.block.StaticBlockModel;

/**
 * Builds block models from blockstate files. Call from a {@code ClientEvents.BLOCK_MODEL_RELOAD} listener, so the
 * file and its models are read again (through the selected texture packs) on every reload.
 * <p>
 * A file that is missing or wrong gives one error line and the missing model for the whole block; a state the
 * file can't describe gives one warning line and the missing model for that state.
 */
@Environment(EnvType.CLIENT)
public final class Blockstates {
    private Blockstates() {}

    /** A block fully described by its metadata. {@code id} is {@code ns:name} for {@code assets/ns/blockstates/name.json}. */
    public static <T extends BlockLogic> BlockModel<T> model(Block<T> block, String id) {
        return model(block, id, null, null);
    }

    /**
     * @param resolver      supplies the properties declared without {@code bits}; null if there are none
     * @param inventoryModel model id for the inventory and held item (e.g. a fence's {@code fence_inventory}),
     *                      or null for the first part of the state
     */
    public static <T extends BlockLogic> BlockModel<T> model(Block<T> block, String id, StateResolver resolver, String inventoryModel) {
        StaticBlockModel missing = BlockModelMojangData.Cache.getMissingModel().asModel();
        Blockstate blockstate;
        try {
            String json = Models.readAsset(id, "blockstates", ".json");
            if (json == null)
                throw new BlockstateException(id + ": no file assets/<ns>/blockstates/<name>.json");
            blockstate = Blockstate.parse(id, json);
        } catch (UncheckedIOException | BlockstateException e) {
            JsonExtended.LOGGER.error("Blockstate {} can't be loaded, drawing the missing model: {}", id, e.getMessage());
            return new BlockModelGeneric<>(block, missing);
        }
        if (blockstate.needsResolver() && resolver == null)
            JsonExtended.LOGGER.error("Blockstate {} declares {} without bits but the block has no StateResolver; those states draw the missing model",
                    id, blockstate.resolverProperties());
        blockstate.onWarning(line -> JsonExtended.LOGGER.warn("Blockstate {}, drawing the missing model", line));

        Map<String, StaticBlockModel> models = new LinkedHashMap<>();
        int renderLayer = -1;
        for (String model : blockstate.models()) {
            models.put(model, Models.load(model));
            renderLayer = Math.max(renderLayer, Models.renderLayer(model));
        }
        StaticBlockModel inventory = inventoryModel == null ? null : Models.load(inventoryModel);
        return new BlockstateModel<>(block, blockstate, models, missing, resolver, inventory, renderLayer);
    }

}
