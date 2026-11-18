package io.github.tryrodave.jsonextended.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Follows an item model's parents and merges them, like vanilla: textures and display entries from the parent with
 * the child's on top, the nearest {@code gui_light}, and the nearest file with {@code elements} decides that the
 * item is a 3D model.
 * <p>
 * {@code minecraft:item/generated}, {@code item/handheld}, {@code item/handheld_rod} and
 * {@code builtin/generated} are built in (BTA ships no such files): they make a flat layered item, and the renderer
 * fills in BTA's own default poses for contexts the files don't set. A pack that supplies one of those files
 * replaces the built-in one.
 */
public final class ItemModels {
    private static final int MAX_DEPTH = 32;
    private static final int MAX_LAYERS = 16;

    private final Function<String, String> source;

    /** @param source model id to JSON text, or null when there is no such file */
    public ItemModels(Function<String, String> source) {
        this.source = source;
    }

    /** Adds {@code minecraft:} to an id without a namespace. */
    public static String normalize(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    /**
     * @throws ItemModelException for a missing model or parent, a parent loop, a flat item without
     *                            {@code layer0}, or a texture reference that doesn't resolve
     */
    public ResolvedItemModel resolve(String id) {
        id = normalize(id);
        List<ItemModelFile> chain = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        ResolvedItemModel.Style builtIn = null;
        String current = id;
        while (current != null) {
            for (ItemModelFile f : chain)
                if (f.id().equals(current))
                    throw new ItemModelException(id + ": parent loop through " + current);
            if (chain.size() >= MAX_DEPTH)
                throw new ItemModelException(id + ": more than " + MAX_DEPTH + " parents");
            String json = source.apply(current);
            if (json == null) {
                builtIn = builtIn(current);
                if (builtIn == null)
                    throw new ItemModelException(chain.isEmpty()
                            ? id + ": no model file"
                            : chain.get(chain.size() - 1).id() + ": parent: parent " + current + " doesn't exist");
                break;
            }
            ItemModelFile file = ItemModelFile.parse(current, json);
            warnings.addAll(file.warnings());
            chain.add(file);
            current = file.parent();
            if (current != null && current.equals("minecraft:builtin/generated")) {
                builtIn = ResolvedItemModel.Style.GENERATED;
                break;
            }
        }

        // Merge from the root down so children win.
        Map<String, String> textures = new LinkedHashMap<>();
        Map<String, DisplayTransform> display = new LinkedHashMap<>();
        String guiLight = null;
        String elementsFrom = null;
        ResolvedItemModel.Style style = builtIn;
        for (int i = chain.size() - 1; i >= 0; i--) {
            ItemModelFile f = chain.get(i);
            textures.putAll(f.textures());
            display.putAll(f.display());
            if (f.guiLight() != null)
                guiLight = f.guiLight();
            if (f.hasElements()) {
                // The nearest file with elements wins over anything flat above it.
                elementsFrom = f.id();
                style = ResolvedItemModel.Style.MODEL;
            } else if (style != ResolvedItemModel.Style.MODEL) {
                ResolvedItemModel.Style named = builtIn(f.id());
                if (named != null)
                    style = named;
            }
        }
        // A child without elements below a model with elements stays a model; a child that names a flat parent
        // after a 3D one is not something vanilla allows either, so the nearest decides in both cases.
        if (style == null)
            throw new ItemModelException(id + ": neither elements nor a generated parent (e.g. \"parent\": \"item/generated\")");
        if (guiLight == null)
            guiLight = style == ResolvedItemModel.Style.MODEL ? "side" : "front";

        Map<String, String> resolvedTextures = new LinkedHashMap<>();
        for (String key : textures.keySet())
            resolvedTextures.put(key, resolveTexture(id, textures, key));

        List<String> layers = new ArrayList<>();
        if (style != ResolvedItemModel.Style.MODEL) {
            for (int i = 0; i < MAX_LAYERS && textures.containsKey("layer" + i); i++)
                layers.add(resolvedTextures.get("layer" + i));
            if (layers.isEmpty())
                throw new ItemModelException(id + ": textures.layer0: a generated item needs a layer0 texture");
            if (textures.containsKey("layer" + (layers.size() + 1)))
                warnings.add(id + ": textures: layer" + layers.size() + " is missing, so the layers after it are ignored");
        }
        return new ResolvedItemModel(id, style, layers, resolvedTextures, display, guiLight, elementsFrom, warnings);
    }

    private static String resolveTexture(String id, Map<String, String> textures, String key) {
        String value = textures.get(key);
        for (int depth = 0; value.startsWith("#"); depth++) {
            String next = textures.get(value.substring(1));
            if (next == null || depth > textures.size())
                throw new ItemModelException(id + ": textures." + key + ": " + value + " doesn't point to a texture");
            value = next;
        }
        return normalize(value);
    }

    private static ResolvedItemModel.Style builtIn(String id) {
        return switch (id) {
            case "minecraft:builtin/generated", "minecraft:item/generated" -> ResolvedItemModel.Style.GENERATED;
            case "minecraft:item/handheld" -> ResolvedItemModel.Style.HANDHELD;
            case "minecraft:item/handheld_rod" -> ResolvedItemModel.Style.HANDHELD_ROD;
            default -> null;
        };
    }
}
