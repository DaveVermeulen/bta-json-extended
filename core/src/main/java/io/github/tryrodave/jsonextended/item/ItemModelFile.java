package io.github.tryrodave.jsonextended.item;

import io.github.tryrodave.jsonextended.json.Json;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One item model JSON file as written, before following its parent: {@code parent}, {@code textures},
 * {@code display}, {@code gui_light} and whether it has {@code elements}. The element geometry itself is left to
 * BTA's own model reader.
 * <p>
 * Keys Blockbench writes that don't change how an item looks here ({@code texture_size}, {@code groups},
 * {@code credit}, ...) are accepted; anything else gives a warning, not an error.
 */
public final class ItemModelFile {
    /** Display contexts vanilla 1.21 knows. */
    public static final List<String> CONTEXTS = List.of("thirdperson_righthand", "thirdperson_lefthand",
            "firstperson_righthand", "firstperson_lefthand", "gui", "head", "ground", "fixed");
    private static final Set<String> KNOWN_KEYS = Set.of("parent", "textures", "display", "gui_light", "elements",
            "ambientocclusion", "texture_size", "groups", "credit", "render_type", "loader", "renderlayer", "particles");
    private static final Set<String> TRANSFORM_KEYS = Set.of("rotation", "translation", "scale");

    private final String id;
    private final String parent;
    private final Map<String, String> textures;
    private final Map<String, DisplayTransform> display;
    private final String guiLight;
    private final boolean hasElements;
    private final List<String> warnings;

    private ItemModelFile(String id, String parent, Map<String, String> textures, Map<String, DisplayTransform> display,
                          String guiLight, boolean hasElements, List<String> warnings) {
        this.id = id;
        this.parent = parent;
        this.textures = Collections.unmodifiableMap(textures);
        this.display = Collections.unmodifiableMap(display);
        this.guiLight = guiLight;
        this.hasElements = hasElements;
        this.warnings = List.copyOf(warnings);
    }

    /**
     * @param id names the model in messages, e.g. {@code jsonextended:item/test_ruby}
     * @throws ItemModelException naming the model and the key
     */
    public static ItemModelFile parse(String id, String json) {
        Object root;
        try {
            root = Json.parse(json);
        } catch (IllegalArgumentException e) {
            throw new ItemModelException(id + ": " + e.getMessage());
        }
        if (!(root instanceof Map<?, ?> rootMap))
            throw error(id, "", "expected an object");
        @SuppressWarnings("unchecked")
        Map<String, Object> file = (Map<String, Object>) rootMap;
        List<String> warnings = new ArrayList<>();
        for (String key : file.keySet())
            if (!KNOWN_KEYS.contains(key))
                warnings.add(id + ": " + key + ": " + (key.equals("overrides")
                        ? "overrides are not supported yet and are ignored"
                        : "unknown key '" + key + "' is ignored"));

        String parent = null;
        if (file.containsKey("parent")) {
            if (!(file.get("parent") instanceof String p) || p.isEmpty())
                throw error(id, "parent", "parent is a model id like \"minecraft:item/generated\"");
            parent = ItemModels.normalize(p);
        }

        Map<String, String> textures = new LinkedHashMap<>();
        if (file.containsKey("textures")) {
            for (Map.Entry<String, Object> e : object(id, "textures", file.get("textures")).entrySet()) {
                if (!(e.getValue() instanceof String t) || t.isEmpty())
                    throw error(id, "textures." + e.getKey(), "a texture is a texture id or a #reference");
                textures.put(e.getKey(), t);
            }
        }

        Map<String, DisplayTransform> display = new LinkedHashMap<>();
        if (file.containsKey("display")) {
            for (Map.Entry<String, Object> e : object(id, "display", file.get("display")).entrySet()) {
                String path = "display." + e.getKey();
                if (!CONTEXTS.contains(e.getKey())) {
                    warnings.add(id + ": " + path + ": unknown display context '" + e.getKey() + "' is ignored");
                    continue;
                }
                display.put(e.getKey(), transform(id, path, object(id, path, e.getValue())));
            }
        }

        String guiLight = null;
        if (file.containsKey("gui_light")) {
            Object g = file.get("gui_light");
            if (!"front".equals(g) && !"side".equals(g))
                throw error(id, "gui_light", "gui_light is \"front\" or \"side\"");
            guiLight = (String) g;
        }

        boolean hasElements = false;
        if (file.containsKey("elements")) {
            if (!(file.get("elements") instanceof List<?> elements))
                throw error(id, "elements", "expected a list");
            hasElements = true;
            if (elements.isEmpty())
                warnings.add(id + ": elements: the list is empty, so the model draws nothing");
        }
        return new ItemModelFile(id, parent, textures, display, guiLight, hasElements, warnings);
    }

    private static DisplayTransform transform(String id, String path, Map<String, Object> t) {
        for (String key : t.keySet())
            if (!TRANSFORM_KEYS.contains(key))
                throw error(id, path + "." + key, "unknown key '" + key + "'; expected rotation, translation or scale");
        double[] r = triple(id, path, t, "rotation", 0), tr = triple(id, path, t, "translation", 0), s = triple(id, path, t, "scale", 1);
        return new DisplayTransform(r[0], r[1], r[2], tr[0], tr[1], tr[2], s[0], s[1], s[2]);
    }

    private static double[] triple(String id, String path, Map<String, Object> t, String key, double fallback) {
        if (!t.containsKey(key))
            return new double[] {fallback, fallback, fallback};
        if (t.get(key) instanceof List<?> list && list.size() == 3
                && list.get(0) instanceof Double a && list.get(1) instanceof Double b && list.get(2) instanceof Double c)
            return new double[] {a, b, c};
        throw error(id, path + "." + key, key + " is a list of three numbers");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(String id, String path, Object o) {
        if (o instanceof Map<?, ?> map)
            return (Map<String, Object>) map;
        throw error(id, path, "expected an object");
    }

    static ItemModelException error(String id, String path, String reason) {
        return new ItemModelException(id + (path.isEmpty() ? "" : ": " + path) + ": " + reason);
    }

    public String id() {
        return id;
    }

    /** Normalised parent id ({@code minecraft:} added), or null. */
    public String parent() {
        return parent;
    }

    public Map<String, String> textures() {
        return textures;
    }

    public Map<String, DisplayTransform> display() {
        return display;
    }

    /** {@code front}, {@code side}, or null when the file doesn't say. */
    public String guiLight() {
        return guiLight;
    }

    public boolean hasElements() {
        return hasElements;
    }

    /** One line per ignored key, for the log. */
    public List<String> warnings() {
        return warnings;
    }
}
