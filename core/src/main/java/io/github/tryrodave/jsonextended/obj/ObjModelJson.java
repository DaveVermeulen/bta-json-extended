package io.github.tryrodave.jsonextended.obj;

import io.github.tryrodave.jsonextended.json.Json;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A model JSON that draws an OBJ file: {@code "loader": "jsonextended:obj"}. The keys match NeoForge's OBJ loader,
 * so NeoForge, Forge and Betafront model files work as they are.
 *
 * <pre>
 * {
 *   "loader": "jsonextended:obj",                       // or betafront:obj, neoforge:obj, forge:obj
 *   "model": "mymod:models/block/lamp.obj",             // assets/mymod/models/block/lamp.obj
 *   "flip_v": true,                                     // Blender exports need this (default false)
 *   "textures": {"particle": "mymod:block/lamp", "texture0": "mymod:block/lamp"},
 *   "render_type": "minecraft:cutout",                  // as in other model JSON
 *   "automatic_culling": true, "shade_quads": true, "emissive_ambient": true, "ambient_occlusion": true,
 *   "mtl_override": "mymod:models/block/lamp_red.mtl"
 * }
 * </pre>
 *
 * @param automaticCulling faces flat on a block side are hidden by a full neighbour there
 * @param shadeQuads       faces are shaded by direction, like vanilla blocks; off draws every face full bright
 * @param emissiveAmbient  a material's {@code Ka} brightness makes its faces glow (block light up to 15)
 * @param ambientOcclusion smooth lighting when the game option is on
 * @param textures         {@code #variable} to texture id or another {@code #variable}
 * @param renderType       {@code render_type} without namespace, or null
 */
public record ObjModelJson(String id, String loader, String model, boolean flipV, boolean automaticCulling,
                           boolean shadeQuads, boolean emissiveAmbient, boolean ambientOcclusion, String mtlOverride,
                           Map<String, String> textures, String renderType, List<String> warnings) {
    /** Loader ids that pick this model type. */
    public static final Set<String> LOADERS = Set.of("jsonextended:obj", "betafront:obj", "neoforge:obj", "forge:obj");

    private static final Set<String> KEYS = Set.of("loader", "model", "flip_v", "automatic_culling", "shade_quads",
            "emissive_ambient", "ambient_occlusion", "mtl_override", "textures", "render_type", "display", "gui_light",
            "credit", "parent");

    /** The loader id of a model JSON, or null: a string, or an object's {@code id} (some tools write that). */
    public static String loaderOf(Map<?, ?> file) {
        Object loader = file.get("loader");
        if (loader instanceof Map<?, ?> object && object.get("id") instanceof String id)
            return id;
        return loader instanceof String s ? s : null;
    }

    /** True for one of {@link #LOADERS}; false for null. */
    public static boolean isLoader(String loader) {
        return loader != null && LOADERS.contains(loader);
    }

    /** True when this model JSON text names an OBJ loader. Broken JSON gives false. */
    public static boolean isObj(String json) {
        try {
            return Json.parse(json) instanceof Map<?, ?> file && isLoader(loaderOf(file));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** @throws ObjException when a required key is missing or has the wrong type, naming the model and key */
    public static ObjModelJson parse(String id, String json) {
        Object root;
        try {
            root = Json.parse(json);
        } catch (IllegalArgumentException e) {
            throw new ObjException(id + ": " + e.getMessage());
        }
        if (!(root instanceof Map<?, ?> file))
            throw new ObjException(id + ": expected an object");
        List<String> warnings = new ArrayList<>();
        String loader = loaderOf(file);
        if (!isLoader(loader))
            throw new ObjException(id + ": loader: '" + loader + "' is not one of " + LOADERS);
        for (Object key : file.keySet()) {
            if (!KEYS.contains(key))
                warnings.add(id + ": " + key + ": unknown key '" + key + "' is ignored");
        }
        if (file.containsKey("parent"))
            warnings.add(id + ": parent: OBJ models don't inherit from a parent; it is ignored");
        if (!(file.get("model") instanceof String model) || model.isEmpty())
            throw new ObjException(id + ": model: needs the OBJ file, e.g. \"ns:models/block/name.obj\"");
        if (!model.endsWith(".obj"))
            warnings.add(id + ": model: '" + model + "' doesn't end in .obj");

        Map<String, String> textures = new LinkedHashMap<>();
        if (file.get("textures") instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getValue() instanceof String value)
                    textures.put((String) e.getKey(), value);
                else
                    throw new ObjException(id + ": textures." + e.getKey() + ": a texture is a string");
            }
        } else if (file.containsKey("textures")) {
            throw new ObjException(id + ": textures: expected an object");
        }

        String renderType = null;
        if (file.get("render_type") instanceof String type)
            renderType = type.substring(type.indexOf(':') + 1);
        String mtlOverride = null;
        if (file.containsKey("mtl_override")) {
            if (!(file.get("mtl_override") instanceof String mtl))
                throw new ObjException(id + ": mtl_override: needs an MTL file id");
            mtlOverride = mtl;
        }
        return new ObjModelJson(id, loader, model,
                bool(id, file, "flip_v", false), bool(id, file, "automatic_culling", true),
                bool(id, file, "shade_quads", true), bool(id, file, "emissive_ambient", true),
                bool(id, file, "ambient_occlusion", true), mtlOverride,
                Collections.unmodifiableMap(textures), renderType, List.copyOf(warnings));
    }

    private static boolean bool(String id, Map<?, ?> file, String key, boolean fallback) {
        Object value = file.get(key);
        if (value == null)
            return fallback;
        if (value instanceof Boolean b)
            return b;
        throw new ObjException(id + ": " + key + ": expected true or false");
    }

    /**
     * A material texture as an atlas id: {@code #name} looks up {@code textures} (following further
     * {@code #variables}), anything else is already an id. Null when a variable isn't set or loops; then the
     * particle texture is drawn.
     */
    public String texture(String texture) {
        Set<String> seen = new HashSet<>();
        while (texture != null && texture.startsWith("#")) {
            if (!seen.add(texture))
                return null;
            texture = textures.get(texture.substring(1));
        }
        return texture;
    }

    /** {@code textures.particle}, resolved; null when absent. */
    public String particle() {
        return texture("#particle");
    }
}
