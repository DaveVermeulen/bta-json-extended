package io.github.tryrodave.jsonextended.model;

import io.github.tryrodave.jsonextended.json.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Checks a block or item model JSON (and its parents) the way modern Minecraft and Blockbench write them, and
 * explains every problem in one line naming the model and the key. BTA's own reader skips most of these silently
 * (a missing parent, an unknown key, a texture variable nobody sets), so this is what makes a bad model easy to fix.
 * <p>
 * Errors mean the model can't draw as written; warnings mean something is ignored or probably a mistake.
 */
public final class ModelCheck {
    /** Keys BTA reads, plus the ones this mod handles. */
    private static final Set<String> MODEL_KEYS = Set.of("parent", "ambientocclusion", "renderlayer", "display", "textures",
            "elements", "particles", "gui_light", "render_type", "loader", "texture_size", "credit", "groups");
    /** Written by Blockbench or vanilla, harmless here. */
    private static final Set<String> INFO_KEYS = Set.of("credit", "groups", "texture_size");
    private static final Set<String> ELEMENT_KEYS = Set.of("from", "to", "rotation", "shade", "light_emission", "faces",
            "name", "color", "uuid", "box_uv", "render_order", "locked");
    private static final Set<String> FACE_KEYS = Set.of("uv", "texture", "rotation", "cullface", "tintindex");
    private static final Set<String> SIDES = Set.of("north", "south", "east", "west", "up", "down");
    private static final Set<String> CULLFACES = Set.of("north", "south", "east", "west", "up", "down", "bottom", "top");
    private static final Set<String> RENDER_TYPES = Set.of("solid", "cutout", "cutout_mipped", "cutout_mipped_all", "translucent",
            "tripwire");
    private static final int MAX_DEPTH = 32;

    /** One problem. */
    public record Problem(boolean error, String message) {
        @Override
        public String toString() {
            return (error ? "error: " : "warning: ") + message;
        }
    }

    private final Function<String, String> source;

    /** @param source model id ({@code ns:block/x}) to JSON text, or null when there is no such file */
    public ModelCheck(Function<String, String> source) {
        this.source = source;
    }

    /** Every problem in the model and its parents, child first. Empty means it is fine. */
    public List<Problem> check(String id) {
        List<Problem> problems = new ArrayList<>();
        Map<String, String> textures = new LinkedHashMap<>();
        List<String> referenced = new ArrayList<>();
        List<String> referencedFrom = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        boolean hasElements = false;
        boolean builtIn = false;
        String current = normalize(id);
        String childOf = null;
        while (current != null) {
            if (seen.contains(current)) {
                problems.add(new Problem(true, seen.get(seen.size() - 1) + ": parent: parent loop through " + current));
                break;
            }
            if (seen.size() >= MAX_DEPTH) {
                problems.add(new Problem(true, id + ": more than " + MAX_DEPTH + " parents"));
                break;
            }
            if (BUILT_IN.contains(current)) {
                builtIn = true;
                break;
            }
            String json = source.apply(current);
            if (json == null) {
                problems.add(new Problem(true, childOf == null
                        ? current + ": no model file"
                        : childOf + ": parent: parent " + current + " doesn't exist (BTA ignores it silently)"));
                break;
            }
            seen.add(current);
            Map<String, Object> file;
            try {
                Object root = Json.parse(json);
                if (!(root instanceof Map<?, ?>)) {
                    problems.add(new Problem(true, current + ": expected an object"));
                    break;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) root;
                file = cast;
            } catch (IllegalArgumentException e) {
                problems.add(new Problem(true, current + ": " + e.getMessage()));
                break;
            }
            hasElements |= checkFile(current, file, problems, textures, referenced, referencedFrom);
            childOf = current;
            current = file.get("parent") instanceof String p ? normalize(p) : null;
        }

        for (int i = 0; i < referenced.size(); i++) {
            String variable = referenced.get(i);
            if (resolve(textures, variable) == null)
                problems.add(new Problem(true, referencedFrom.get(i) + ": #" + variable
                        + " is never set in textures (here or in a parent), so BTA draws its default texture"));
        }
        if (!hasElements && !builtIn && problems.stream().noneMatch(Problem::error))
            problems.add(new Problem(false, id + ": no elements here or in any parent, so the model draws nothing"));
        return problems;
    }

    /** @return whether the file has elements */
    private static boolean checkFile(String id, Map<String, Object> file, List<Problem> problems, Map<String, String> textures,
                                     List<String> referenced, List<String> referencedFrom) {
        for (String key : file.keySet()) {
            if (key.equals("overrides"))
                problems.add(new Problem(false, id + ": overrides: item overrides are not supported yet and are ignored"));
            else if (!MODEL_KEYS.contains(key))
                problems.add(new Problem(false, id + ": " + key + ": unknown key '" + key + "' is ignored"));
        }
        if (file.containsKey("parent") && !(file.get("parent") instanceof String))
            problems.add(new Problem(true, id + ": parent: parent is a model id"));
        if (file.get("texture_size") instanceof List<?> size && size.size() == 2
                && !(Double.valueOf(16).equals(size.get(0)) && Double.valueOf(16).equals(size.get(1))))
            problems.add(new Problem(false, id + ": texture_size: " + size
                    + " is only for Blockbench; UVs are still 0-16 across the whole texture"));
        if (file.containsKey("render_type")) {
            Object type = file.get("render_type");
            String name = type instanceof String s ? s.substring(s.indexOf(':') + 1) : null;
            if (name == null || !RENDER_TYPES.contains(name))
                problems.add(new Problem(true, id + ": render_type: '" + type + "' is not one of " + RENDER_TYPES));
        }
        if (file.containsKey("gui_light") && !"front".equals(file.get("gui_light")) && !"side".equals(file.get("gui_light")))
            problems.add(new Problem(true, id + ": gui_light: gui_light is \"front\" or \"side\""));
        if (file.containsKey("ambientocclusion") && !(file.get("ambientocclusion") instanceof Boolean))
            problems.add(new Problem(true, id + ": ambientocclusion: ambientocclusion is true or false"));

        // Textures: the child's entries win, so only add variables a closer file hasn't set.
        if (file.get("textures") instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!(e.getValue() instanceof String value) || value.isEmpty()) {
                    problems.add(new Problem(true, id + ": textures." + e.getKey() + ": a texture is an id or a #reference"));
                    continue;
                }
                textures.putIfAbsent((String) e.getKey(), value);
                if (value.startsWith("#")) {
                    referenced.add(value.substring(1));
                    referencedFrom.add(id + ": textures." + e.getKey());
                }
            }
        } else if (file.containsKey("textures")) {
            problems.add(new Problem(true, id + ": textures: expected an object"));
        }

        if (!file.containsKey("elements"))
            return false;
        if (!(file.get("elements") instanceof List<?> elements)) {
            problems.add(new Problem(true, id + ": elements: expected a list"));
            return false;
        }
        for (int i = 0; i < elements.size(); i++)
            checkElement(id, "elements[" + i + "]", elements.get(i), problems, referenced, referencedFrom);
        return true;
    }

    private static void checkElement(String id, String path, Object json, List<Problem> problems,
                                     List<String> referenced, List<String> referencedFrom) {
        if (!(json instanceof Map<?, ?> element)) {
            problems.add(new Problem(true, id + ": " + path + ": expected an object"));
            return;
        }
        for (Object key : element.keySet())
            if (!ELEMENT_KEYS.contains(key))
                problems.add(new Problem(false, id + ": " + path + "." + key + ": unknown key '" + key + "' is ignored"));
        double[] from = numbers(id, path + ".from", element.get("from"), 3, problems);
        double[] to = numbers(id, path + ".to", element.get("to"), 3, problems);
        if (from != null && to != null) {
            for (int a = 0; a < 3; a++) {
                if (from[a] < -16 || from[a] > 32 || to[a] < -16 || to[a] > 32)
                    problems.add(new Problem(false, id + ": " + path + ": from/to outside -16..32 (vanilla's limit) still draws in BTA"));
                if (from[a] > to[a])
                    problems.add(new Problem(true, id + ": " + path + ": from is larger than to on the " + "xyz".charAt(a) + " axis"));
            }
        }
        if (element.containsKey("rotation")) {
            if (element.get("rotation") instanceof Map<?, ?> rotation) {
                Object axis = rotation.get("axis");
                if (!"x".equals(axis) && !"y".equals(axis) && !"z".equals(axis))
                    problems.add(new Problem(true, id + ": " + path + ".rotation.axis: axis is \"x\", \"y\" or \"z\""));
                if (!(rotation.get("angle") instanceof Double))
                    problems.add(new Problem(true, id + ": " + path + ".rotation.angle: angle is a number of degrees"));
                numbers(id, path + ".rotation.origin", rotation.get("origin"), 3, problems);
            } else {
                problems.add(new Problem(true, id + ": " + path + ".rotation: expected an object"));
            }
        }
        if (!(element.get("faces") instanceof Map<?, ?> faces)) {
            problems.add(new Problem(true, id + ": " + path + ".faces: every element needs faces"));
            return;
        }
        if (faces.isEmpty())
            problems.add(new Problem(false, id + ": " + path + ".faces: no faces, so the element draws nothing"));
        for (Map.Entry<?, ?> f : faces.entrySet()) {
            String facePath = path + ".faces." + f.getKey();
            if (!SIDES.contains(f.getKey())) {
                problems.add(new Problem(true, id + ": " + facePath + ": a face is north, south, east, west, up or down"));
                continue;
            }
            if (!(f.getValue() instanceof Map<?, ?> face)) {
                problems.add(new Problem(true, id + ": " + facePath + ": expected an object"));
                continue;
            }
            for (Object key : face.keySet())
                if (!FACE_KEYS.contains(key))
                    problems.add(new Problem(false, id + ": " + facePath + "." + key + ": unknown key '" + key + "' is ignored"));
            if (!(face.get("texture") instanceof String texture) || texture.isEmpty()) {
                problems.add(new Problem(true, id + ": " + facePath + ".texture: every face needs a texture"));
            } else if (texture.startsWith("#")) {
                referenced.add(texture.substring(1));
                referencedFrom.add(id + ": " + facePath + ".texture");
            }
            if (face.containsKey("uv"))
                numbers(id, facePath + ".uv", face.get("uv"), 4, problems);
            if (face.containsKey("rotation") && !(face.get("rotation") instanceof Double r && (r == 0 || r == 90 || r == 180 || r == 270)))
                problems.add(new Problem(true, id + ": " + facePath + ".rotation: face rotation is 0, 90, 180 or 270"));
            if (face.containsKey("cullface") && !CULLFACES.contains(face.get("cullface")))
                problems.add(new Problem(true, id + ": " + facePath + ".cullface: '" + face.get("cullface") + "' is not a side"));
            if (face.containsKey("tintindex") && !(face.get("tintindex") instanceof Double))
                problems.add(new Problem(true, id + ": " + facePath + ".tintindex: tintindex is a number"));
        }
    }

    private static double[] numbers(String id, String path, Object json, int count, List<Problem> problems) {
        if (json instanceof List<?> list && list.size() == count && list.stream().allMatch(Double.class::isInstance)) {
            double[] out = new double[count];
            for (int i = 0; i < count; i++)
                out[i] = (Double) list.get(i);
            return out;
        }
        problems.add(new Problem(true, id + ": " + path + ": expected a list of " + count + " numbers"));
        return null;
    }

    /** Follows {@code #} references; null when one isn't set or they loop. */
    private static String resolve(Map<String, String> textures, String variable) {
        String value = "#" + variable;
        for (int depth = 0; value != null && value.startsWith("#"); depth++) {
            if (depth > textures.size())
                return null;
            value = textures.get(value.substring(1));
        }
        return value;
    }

    /** Parents that have no file: their content is built in. */
    private static final Set<String> BUILT_IN = Set.of("minecraft:builtin/generated", "minecraft:builtin/entity",
            "minecraft:item/generated", "minecraft:item/handheld", "minecraft:item/handheld_rod");

    static String normalize(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }
}
