package io.github.tryrodave.jsonextended.model;

import io.github.tryrodave.jsonextended.JsonExtended;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.texture.stitcher.IconCoordinate;
import net.minecraft.client.render.texture.stitcher.TextureRegistry;

/**
 * Atlas entries by texture id for one model, cached until the atlas is rebuilt ({@code TextureRegistry.reloadGeneration}).
 * An id that isn't in an atlas is cached as null too and logged once, so a wrong name costs one lookup, not one
 * per face per frame. Main thread only, like BTA's renderers.
 */
@Environment(EnvType.CLIENT)
public final class AtlasIcons {
    private final String owner;
    private final Map<String, IconCoordinate> icons = new HashMap<>();
    private int generation = -1;

    /** @param owner the model id, for the log line */
    public AtlasIcons(String owner) {
        this.owner = owner;
    }

    /** The atlas entry, or null when {@code texture} is null or not in an atlas. */
    public IconCoordinate get(String texture) {
        if (generation != TextureRegistry.reloadGeneration) {
            icons.clear();
            generation = TextureRegistry.reloadGeneration;
        }
        if (texture == null)
            return null;
        if (icons.containsKey(texture))
            return icons.get(texture);
        IconCoordinate icon;
        String problem = null;
        try {
            icon = TextureRegistry.getTexture(texture);
            if (icon == null)
                problem = "isn't in the atlas";
        } catch (RuntimeException e) {
            // TextureRegistry throws for an id with no atlas folder (one without block/ or item/).
            icon = null;
            problem = "isn't in an atlas folder (" + e.getMessage() + "); use ns:block/... or ns:item/...";
        }
        icons.put(texture, icon);
        if (problem != null)
            Models.logOnce(true, owner + ": texture " + texture + " " + problem + ", so its faces aren't drawn");
        return icon;
    }
}
