package io.github.tryrodave.jsonextended.model;

import io.github.tryrodave.jsonextended.json.Json;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A model JSON with its parents merged the vanilla way: {@code textures} from every file (child wins),
 * {@code elements} (and Blockbench's {@code groups}) from the nearest file that has them. Built-in parents and
 * missing parent files end the chain. Problems are left to {@link ModelCheck}; this only reads what is there.
 *
 * @param elements each element as read ({@code from}, {@code to}, {@code faces}, ...)
 * @param groups   Blockbench's {@code groups} from the same file as the elements, or an empty list
 */
public record ResolvedModel(String id, List<Map<?, ?>> elements, List<Object> groups, Map<String, String> textures) {
    private static final int MAX_DEPTH = 32;

    /** @throws IllegalArgumentException when the model itself is missing or isn't a JSON object */
    public static ResolvedModel load(String id, Function<String, String> source) {
        Map<String, String> textures = new LinkedHashMap<>();
        List<Map<?, ?>> elements = null;
        List<Object> groups = List.of();
        Set<String> seen = new HashSet<>();
        String current = ModelCheck.normalize(id);
        boolean first = true;
        while (current != null && seen.add(current) && seen.size() <= MAX_DEPTH) {
            String text = source.apply(current);
            if (text == null) {
                if (first)
                    throw new IllegalArgumentException(id + ": no model file");
                break;
            }
            if (!(Json.parse(text) instanceof Map<?, ?> file)) {
                if (first)
                    throw new IllegalArgumentException(id + ": expected an object");
                break;
            }
            first = false;
            if (file.get("textures") instanceof Map<?, ?> map)
                for (Map.Entry<?, ?> e : map.entrySet())
                    if (e.getValue() instanceof String value)
                        textures.putIfAbsent((String) e.getKey(), value);
            if (elements == null && file.get("elements") instanceof List<?> list) {
                elements = new ArrayList<>();
                for (Object element : list)
                    if (element instanceof Map<?, ?> m)
                        elements.add(m);
                if (file.get("groups") instanceof List<?> g)
                    groups = new ArrayList<>(g);
            }
            current = file.get("parent") instanceof String parent ? ModelCheck.normalize(parent) : null;
        }
        return new ResolvedModel(id, elements == null ? List.of() : List.copyOf(elements), groups, Map.copyOf(textures));
    }

    /** {@code #variable} to a texture id, following further variables; null when unset or looping. */
    public String texture(String texture) {
        Set<String> seen = new HashSet<>();
        while (texture != null && texture.startsWith("#")) {
            if (!seen.add(texture))
                return null;
            texture = textures.get(texture.substring(1));
        }
        return texture;
    }
}
