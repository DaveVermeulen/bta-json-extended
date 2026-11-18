package io.github.tryrodave.jsonextended.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.item.model.ItemModelDispatcher;
import net.minecraft.client.render.item.model.ItemModelStandard;
import net.minecraft.client.render.renderer.GLRenderer;
import net.minecraft.client.render.tessellator.TessellatorGeneral;
import net.minecraft.client.render.texture.stitcher.IconCoordinate;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import org.useless.dragonfly.DisplayPos;

/**
 * A flat item from an item model JSON ({@code item/generated}, {@code item/handheld}, {@code item/handheld_rod}):
 * BTA's own sprite drawing ({@link ItemModelStandard}, flat or extruded) with the JSON's layers and
 * {@code display} poses. Each layer is drawn as its own sprite, in order, from the same starting matrix
 * ({@code GLRenderer.pushFrame}/{@code popFrame} around each).
 * <p>
 * BTA's sprite is centred on the origin and its built-in poses are vanilla's divided by 16, so vanilla
 * {@code display} values carry over as they are: translation in pixels becomes blocks (/16), rotation and scale
 * stay. Contexts the JSON doesn't set use BTA's defaults: {@code ItemModelStandard}'s for generated items,
 * {@code ItemModelDispatcher.HANDHELD_*} for tools and rods.
 */
@Environment(EnvType.CLIENT)
public class JsonItemModel extends ItemModelStandard {
    protected final ResolvedItemModel model;
    private final Map<String, DisplayPos> poses = new HashMap<>();
    private final List<IconCoordinate> layers;
    /** The layer being drawn, for {@link #getIcon}; null outside {@link #render}. */
    private IconCoordinate drawing;

    /** @param layers icons for layer0, layer1, ... (one icon for 3D models: their particle texture) */
    public JsonItemModel(Item item, ResolvedItemModel model, List<IconCoordinate> layers) {
        super(item, false);
        this.model = model;
        this.layers = List.copyOf(layers);
        this.icon = this.layers.get(0);
        for (String context : ItemModelFile.CONTEXTS) {
            DisplayTransform t = model.display(context);
            if (t != null)
                poses.put(context, toDisplayPos(t.clamped()));
        }
    }

    public ResolvedItemModel resolved() {
        return model;
    }

    @Override
    public void render(TessellatorGeneral tessellator, Entity holder, ItemStack stack, String displayPosId,
                       boolean items3d, int clusterSize, byte lightIndex, float partialTick, boolean leftHanded) {
        if (layers.size() == 1) {
            super.render(tessellator, holder, stack, displayPosId, items3d, clusterSize, lightIndex, partialTick, leftHanded);
            return;
        }
        // ItemModelStandard.render applies the pose to the live model matrix without restoring it, so each layer
        // gets its own frame to start from the same place.
        for (IconCoordinate layer : layers) {
            GLRenderer.pushFrame();
            drawing = layer;
            try {
                super.render(tessellator, holder, stack, displayPosId, items3d, clusterSize, lightIndex, partialTick, leftHanded);
            } finally {
                drawing = null;
                GLRenderer.popFrame();
            }
        }
    }

    @Override
    public IconCoordinate getIcon(Entity holder, ItemStack stack) {
        return drawing != null ? drawing : icon;
    }

    @Override
    public DisplayPos getDisplayPos(String id) {
        DisplayPos pose = poses.get(id);
        if (pose != null)
            return pose;
        DisplayPos styled = styleDefault(id);
        return styled != null ? styled : super.getDisplayPos(id);
    }

    private DisplayPos styleDefault(String id) {
        return switch (model.style()) {
            case HANDHELD -> switch (id) {
                case "thirdperson_righthand" -> ItemModelDispatcher.HANDHELD_THIRD_PERSON_RIGHT_HAND;
                case "thirdperson_lefthand" -> ItemModelDispatcher.HANDHELD_THIRD_PERSON_LEFT_HAND;
                case "firstperson_righthand" -> ItemModelDispatcher.HANDHELD_FIRST_PERSON_RIGHT_HAND;
                case "firstperson_lefthand" -> ItemModelDispatcher.HANDHELD_FIRST_PERSON_LEFT_HAND;
                default -> null;
            };
            case HANDHELD_ROD -> switch (id) {
                case "thirdperson_righthand" -> ItemModelDispatcher.HANDHELD_ROD_THIRD_PERSON_RIGHT_HAND;
                case "thirdperson_lefthand" -> ItemModelDispatcher.HANDHELD_ROD_THIRD_PERSON_LEFT_HAND;
                case "firstperson_righthand" -> ItemModelDispatcher.HANDHELD_ROD_FIRST_PERSON_RIGHT_HAND;
                case "firstperson_lefthand" -> ItemModelDispatcher.HANDHELD_ROD_FIRST_PERSON_LEFT_HAND;
                default -> null;
            };
            default -> null;
        };
    }

    /** Same conversion as BTA's {@code DisplayPos.Serializer}: translation from pixels to blocks. */
    static DisplayPos toDisplayPos(DisplayTransform t) {
        return new DisplayPos((float) (t.transX() / 16), (float) (t.transY() / 16), (float) (t.transZ() / 16),
                (float) t.rotX(), (float) t.rotY(), (float) t.rotZ(),
                (float) t.scaleX(), (float) t.scaleY(), (float) t.scaleZ());
    }
}
