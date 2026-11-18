package io.github.tryrodave.jsonextended.pose;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The parts of a model that code can move, as a tree. From a Blockbench model: its {@code groups} (each with a
 * {@code name}, a pivot {@code origin} in pixels, and {@code children} that are element indices or more groups).
 * Elements in no group belong to the root part, named {@code ""}, which code can move as well.
 */
public final class Rig {
    /**
     * @param origin   the pivot in blocks
     * @param rotation the rest rotation in degrees, as modelled ({@code rotation} on the group; zero when absent)
     */
    public record Part(String name, float[] origin, float[] rotation, List<Part> children, List<Integer> elements) {}

    private final Part root;
    private final Map<String, Part> byName;
    /** For each element or mesh index, the chain of parts from the root down to the one holding it. */
    private final Map<Integer, List<Part>> chains;

    private Rig(Part root) {
        this.root = root;
        Map<String, Part> byName = new LinkedHashMap<>();
        Map<Integer, List<Part>> chains = new LinkedHashMap<>();
        index(root, new ArrayList<>(), byName, chains);
        this.byName = Collections.unmodifiableMap(byName);
        this.chains = chains;
    }

    private static void index(Part part, List<Part> path, Map<String, Part> byName, Map<Integer, List<Part>> chains) {
        List<Part> here = new ArrayList<>(path);
        here.add(part);
        byName.putIfAbsent(part.name(), part);
        for (int element : part.elements())
            chains.putIfAbsent(element, List.copyOf(here));
        for (Part child : part.children())
            index(child, here, byName, chains);
    }

    /** From Blockbench's {@code groups}; with none, every element is in the root part. */
    public static Rig fromGroups(List<?> groups, int elementCount) {
        Set<Integer> grouped = new HashSet<>();
        List<Part> children = new ArrayList<>();
        List<Integer> loose = new ArrayList<>();
        for (Object entry : groups) {
            if (entry instanceof Map<?, ?> group)
                children.add(group(group, grouped));
            else if (entry instanceof Double d)
                loose.add(d.intValue());
        }
        grouped.addAll(loose);
        for (int i = 0; i < elementCount; i++)
            if (!grouped.contains(i))
                loose.add(i);
        return new Rig(new Part("", new float[3], new float[3], List.copyOf(children), List.copyOf(loose)));
    }

    /** One part per mesh (OBJ objects and groups), all pivoting about {@code pivots.get(name)} or the block corner. */
    public static Rig fromMeshes(List<String> meshNames, Map<String, float[]> pivots) {
        Map<String, List<Integer>> byName = new LinkedHashMap<>();
        for (int i = 0; i < meshNames.size(); i++)
            byName.computeIfAbsent(meshNames.get(i), k -> new ArrayList<>()).add(i);
        List<Part> children = new ArrayList<>();
        List<Integer> loose = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> e : byName.entrySet()) {
            if (e.getKey().isEmpty())
                loose.addAll(e.getValue());
            else
                children.add(new Part(e.getKey(), pivots.getOrDefault(e.getKey(), new float[3]).clone(), new float[3],
                        List.of(), List.copyOf(e.getValue())));
        }
        return new Rig(new Part("", new float[3], new float[3], List.copyOf(children), List.copyOf(loose)));
    }

    private static Part group(Map<?, ?> group, Set<Integer> grouped) {
        String name = group.get("name") instanceof String s ? s : "";
        float[] origin = vec(group.get("origin"), 1f / 16);
        float[] rotation = vec(group.get("rotation"), 1f);
        List<Part> children = new ArrayList<>();
        List<Integer> elements = new ArrayList<>();
        if (group.get("children") instanceof List<?> list) {
            for (Object child : list) {
                if (child instanceof Double d) {
                    elements.add(d.intValue());
                    grouped.add(d.intValue());
                } else if (child instanceof Map<?, ?> sub) {
                    children.add(group(sub, grouped));
                }
            }
        }
        return new Part(name, origin, rotation, List.copyOf(children), List.copyOf(elements));
    }

    private static float[] vec(Object o, float scale) {
        float[] v = new float[3];
        if (o instanceof List<?> l && l.size() == 3)
            for (int i = 0; i < 3; i++)
                if (l.get(i) instanceof Double d)
                    v[i] = d.floatValue() * scale;
        return v;
    }

    public Part root() {
        return root;
    }

    /** Part names, root ({@code ""}) first. */
    public Set<String> names() {
        return byName.keySet();
    }

    public Part part(String name) {
        return byName.get(name);
    }

    /**
     * The quads moved by the pose. Each part's transform applies about its pivot and carries its children along;
     * the rest rotation of a group applies first. Quads of elements no part holds are left as they are.
     */
    public List<ModelQuad> pose(List<ModelQuad> quads, Pose pose) {
        Map<Part, Mat4> matrices = new LinkedHashMap<>();
        matrix(root, Mat4.identity(), pose, matrices);
        List<ModelQuad> out = new ArrayList<>(quads.size());
        for (ModelQuad quad : quads) {
            List<Part> chain = chains.get(quad.element());
            Mat4 m = chain == null ? null : matrices.get(chain.get(chain.size() - 1));
            if (m == null || m.isIdentity()) {
                out.add(quad);
                continue;
            }
            float[][] p = new float[4][];
            for (int i = 0; i < 4; i++)
                p[i] = m.point(quad.positions()[i]);
            out.add(quad.moved(p, m.direction(quad.normal())));
        }
        return out;
    }

    private static void matrix(Part part, Mat4 parent, Pose pose, Map<Part, Mat4> out) {
        PartTransform t = pose.get(part.name());
        float[] o = part.origin();
        Mat4 local = Mat4.translation(o[0] + t.tx() / 16, o[1] + t.ty() / 16, o[2] + t.tz() / 16)
                .times(Mat4.rotation(t.rx(), t.ry(), t.rz()))
                .times(Mat4.rotation(part.rotation()[0], part.rotation()[1], part.rotation()[2]))
                .times(Mat4.scale(t.sx(), t.sy(), t.sz()))
                .times(Mat4.translation(-o[0], -o[1], -o[2]));
        Mat4 m = parent.times(local);
        out.put(part, m);
        for (Part child : part.children())
            matrix(child, m, pose, out);
    }
}
