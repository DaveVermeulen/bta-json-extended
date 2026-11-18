package io.github.tryrodave.jsonextended.item;

import io.github.tryrodave.jsonextended.JsonExtended;
import io.github.tryrodave.jsonextended.model.Models;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.client.render.item.model.ItemModelStandard;
import net.minecraft.client.render.texture.stitcher.IconCoordinate;
import net.minecraft.client.render.texture.stitcher.TextureRegistry;
import net.minecraft.core.item.Item;
import org.useless.dragonfly.models.block.StaticBlockModel;

/**
 * Builds item models from item model JSON. Call from a {@code ClientEvents.ITEM_MODEL_RELOAD} listener, so files are
 * read again (through the selected texture packs) on every reload.
 * <p>
 * A model that can't be loaded logs one error and draws BTA's missing item texture. Flat items become a
 * {@link JsonItemModel}, item models with {@code elements} a {@link JsonItemModel3d} whose geometry BTA's own
 * JSON model reader loads.
 */
@Environment(EnvType.CLIENT)
public final class JsonItems {
    private JsonItems() {}

    /** @param modelId e.g. {@code jsonextended:item/test_ruby}, for {@code assets/jsonextended/models/item/test_ruby.json} */
    public static ItemModel model(Item item, String modelId) {
        ResolvedItemModel model;
        try {
            model = new ItemModels(Models::read).resolve(modelId);
        } catch (ItemModelException | UncheckedIOException e) {
            JsonExtended.LOGGER.error("Item model {} can't be loaded, drawing the missing texture: {}", modelId, e.getMessage());
            ItemModelStandard missing = new ItemModelStandard(item, false);
            missing.icon = ItemModelStandard.ITEM_TEXTURE_MISSING;
            return missing;
        }
        for (String warning : model.warnings())
            JsonExtended.LOGGER.warn("Item model {}", warning);

        if (model.flat()) {
            List<IconCoordinate> layers = new ArrayList<>();
            for (String layer : model.layers())
                layers.add(icon(model.id(), layer));
            return new JsonItemModel(item, model, layers);
        }
        String particle = model.textures().get("particle");
        if (particle == null && !model.textures().isEmpty())
            particle = model.textures().values().iterator().next();
        StaticBlockModel geometry = Models.load(model.id());
        return new JsonItemModel3d(item, model, icon(model.id(), particle), geometry);
    }

    /** {@code TextureRegistry.getTexture} throws for ids outside a known atlas folder: log once and use the missing texture. */
    static IconCoordinate icon(String modelId, String texture) {
        if (texture == null)
            return ItemModelStandard.ITEM_TEXTURE_MISSING;
        try {
            IconCoordinate icon = TextureRegistry.getTexture(texture);
            return icon != null ? icon : ItemModelStandard.ITEM_TEXTURE_MISSING;
        } catch (RuntimeException e) {
            JsonExtended.LOGGER.error("Item model {}: texture {} isn't in the atlas ({}); use ns:item/... or ns:block/...",
                    modelId, texture, e.getMessage());
            return ItemModelStandard.ITEM_TEXTURE_MISSING;
        }
    }

}
