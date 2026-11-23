package io.github.tryrodave.jsonextended.gltf;

import io.github.tryrodave.jsonextended.json.Json;
import io.github.tryrodave.jsonextended.obj.MeshModelJson;
import io.github.tryrodave.jsonextended.obj.ObjMaterial;
import io.github.tryrodave.jsonextended.obj.ObjQuad;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A model JSON that draws a glTF file: {@code "loader": "jsonextended:gltf"}. The other keys are the OBJ loader's.
 *
 * <pre>
 * {
 *   "loader": "jsonextended:gltf",
 *   "model": "mymod:models/block/lamp.glb",          // assets/mymod/models/block/lamp.glb (or a .gltf)
 *   "textures": {"particle": "mymod:block/lamp", "glass": "mymod:block/lamp_glass"},   // by material name
 *   "offset": [0.5, 0, 0.5],                         // added to every corner, in blocks (default none)
 *   "render_type": "minecraft:cutout",
 *   "automatic_culling": true, "shade_quads": true, "emissive_ambient": true, "ambient_occlusion": true,
 *   "flip_v": false                                  // glTF's V already points down (default false)
 * }
 * </pre>
 *
 * Each material draws the {@code textures} entry named after it; unnamed materials are {@code material_0},
 * {@code material_1}, ... by their place in the file.
 *
 * @param offset in blocks: a model built around the origin (as most exporters do) needs {@code [0.5, 0, 0.5]}
 */
public record GltfModelJson(String id, String model, float[] offset, boolean flipV, boolean automaticCulling,
                            boolean shadeQuads, boolean emissiveAmbient, boolean ambientOcclusion,
                            Map<String, String> textures, String renderType, List<String> warnings)
        implements MeshModelJson {
    public static final String LOADER = "jsonextended:gltf";

    private static final Set<String> KEYS = Set.of("loader", "model", "offset", "flip_v", "automatic_culling",
            "shade_quads", "emissive_ambient", "ambient_occlusion", "textures", "render_type", "display", "gui_light",
            "credit", "parent");

    /** True when this model JSON text names the glTF loader. Broken JSON gives false. */
    public static boolean isGltf(String json) {
        try {
            return Json.parse(json) instanceof Map<?, ?> file && LOADER.equals(MeshModelJson.loaderOf(file));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** @throws GltfException when a required key is missing or has the wrong type, naming the model and key */
    public static GltfModelJson parse(String id, String json) {
        Object root;
        try {
            root = Json.parse(json);
        } catch (IllegalArgumentException e) {
            throw new GltfException(id + ": " + e.getMessage());
        }
        if (!(root instanceof Map<?, ?> file))
            throw new GltfException(id + ": expected an object");
        String loader = MeshModelJson.loaderOf(file);
        if (!LOADER.equals(loader))
            throw new GltfException(id + ": loader: '" + loader + "' is not " + LOADER);
        List<String> warnings = new ArrayList<>();
        for (Object key : file.keySet())
            if (!KEYS.contains(key))
                warnings.add(id + ": " + key + ": unknown key '" + key + "' is ignored");
        if (file.containsKey("parent"))
            warnings.add(id + ": parent: glTF models don't inherit from a parent; it is ignored");
        if (!(file.get("model") instanceof String model) || model.isEmpty())
            throw new GltfException(id + ": model: needs the glTF file, e.g. \"ns:models/block/name.glb\"");
        if (!model.endsWith(".glb") && !model.endsWith(".gltf"))
            warnings.add(id + ": model: '" + model + "' doesn't end in .glb or .gltf");

        float[] offset = new float[3];
        if (file.containsKey("offset")) {
            if (!(file.get("offset") instanceof List<?> list) || list.size() != 3
                    || !list.stream().allMatch(n -> n instanceof Double))
                throw new GltfException(id + ": offset: expected [x, y, z] in blocks");
            for (int i = 0; i < 3; i++)
                offset[i] = ((Double) list.get(i)).floatValue();
        }
        Map<String, String> textures = MeshModelJson.textures(file, message -> new GltfException(id + ": " + message));
        return new GltfModelJson(id, model, offset,
                bool(id, file, "flip_v", false), bool(id, file, "automatic_culling", true),
                bool(id, file, "shade_quads", true), bool(id, file, "emissive_ambient", true),
                bool(id, file, "ambient_occlusion", true),
                Collections.unmodifiableMap(textures), MeshModelJson.renderType(file), List.copyOf(warnings));
    }

    /** The model's quads with {@code offset} and {@code flip_v} applied. */
    public List<ObjQuad> place(List<ObjQuad> quads) {
        List<ObjQuad> out = new ArrayList<>(quads.size());
        for (ObjQuad q : quads) {
            float[][] p = new float[4][], uv = new float[4][];
            for (int i = 0; i < 4; i++) {
                float[] c = q.positions()[i];
                p[i] = new float[] {c[0] + offset[0], c[1] + offset[1], c[2] + offset[2]};
                float[] t = q.uvs()[i];
                uv[i] = flipV ? new float[] {t[0], 1 - t[1]} : t.clone();
            }
            out.add(new ObjQuad(q.mesh(), q.material(), p, uv, q.normals()));
        }
        return out;
    }

    /** Materials whose texture variable the {@code textures} don't set: their faces draw the particle texture. */
    public List<String> unsetMaterials(List<ObjQuad> quads) {
        List<String> unset = new ArrayList<>();
        for (ObjQuad q : quads) {
            ObjMaterial m = q.material();
            if (m != null && texture(m.texture()) == null && !unset.contains(m.name()))
                unset.add(m.name());
        }
        return unset;
    }

    private static boolean bool(String id, Map<?, ?> file, String key, boolean fallback) {
        return MeshModelJson.bool(file, key, fallback, message -> new GltfException(id + ": " + message));
    }
}
