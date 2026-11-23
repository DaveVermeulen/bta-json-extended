package io.github.tryrodave.jsonextended.obj;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The settings a mesh model JSON (OBJ or glTF) gives its renderer, with the key readers both formats share.
 * Keys and defaults follow NeoForge's OBJ loader.
 */
public interface MeshModelJson {
    /** The model JSON's id, for messages. */
    String id();

    /** Faces flat on a block side are hidden by a full neighbour there. */
    boolean automaticCulling();

    /** Faces are shaded by direction, like vanilla blocks; off draws every face full bright. */
    boolean shadeQuads();

    /** A material's glow ({@code Ka} in MTL, {@code emissiveFactor} in glTF) raises its faces' block light. */
    boolean emissiveAmbient();

    /** Smooth lighting when the game option is on. */
    boolean ambientOcclusion();

    /** {@code render_type} without namespace, or null. */
    String renderType();

    /** {@code #variable} to texture id or another {@code #variable}. */
    Map<String, String> textures();

    /**
     * A material texture as an atlas id: {@code #name} looks up {@link #textures()} (following further
     * {@code #variables}), anything else is already an id. Null when a variable isn't set or loops; then the
     * particle texture is drawn.
     */
    default String texture(String texture) {
        Set<String> seen = new HashSet<>();
        while (texture != null && texture.startsWith("#")) {
            if (!seen.add(texture))
                return null;
            texture = textures().get(texture.substring(1));
        }
        return texture;
    }

    /** {@code textures.particle}, resolved; null when absent. */
    default String particle() {
        return texture("#particle");
    }

    /** The loader id of a model JSON, or null: a string, or an object's {@code id} (some tools write that). */
    static String loaderOf(Map<?, ?> file) {
        Object loader = file.get("loader");
        if (loader instanceof Map<?, ?> object && object.get("id") instanceof String id)
            return id;
        return loader instanceof String s ? s : null;
    }

    /** {@code key} as true or false; {@code fallback} when absent. */
    static boolean bool(Map<?, ?> file, String key, boolean fallback, Function<String, RuntimeException> error) {
        Object value = file.get(key);
        if (value == null)
            return fallback;
        if (value instanceof Boolean b)
            return b;
        throw error.apply(key + ": expected true or false");
    }

    /** {@code textures} as variable to texture; empty when absent. */
    static Map<String, String> textures(Map<?, ?> file, Function<String, RuntimeException> error) {
        Map<String, String> textures = new LinkedHashMap<>();
        if (file.get("textures") instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getValue() instanceof String value)
                    textures.put((String) e.getKey(), value);
                else
                    throw error.apply("textures." + e.getKey() + ": a texture is a string");
            }
        } else if (file.containsKey("textures")) {
            throw error.apply("textures: expected an object");
        }
        return textures;
    }

    /** {@code render_type} without its namespace, or null. */
    static String renderType(Map<?, ?> file) {
        return file.get("render_type") instanceof String type ? type.substring(type.indexOf(':') + 1) : null;
    }
}
