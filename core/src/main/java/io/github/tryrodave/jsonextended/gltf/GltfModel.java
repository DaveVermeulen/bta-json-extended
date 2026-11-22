package io.github.tryrodave.jsonextended.gltf;

import io.github.tryrodave.jsonextended.obj.ObjMaterial;
import io.github.tryrodave.jsonextended.obj.ObjQuad;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The meshes of a glTF scene, placed by their nodes and turned into the same quads OBJ models give, so one
 * renderer draws both. One glTF unit is one block, +Y is up, and faces are counter-clockwise from the front, as in
 * Minecraft. Two triangles in a row that share an edge and lie in one plane become one quad (exporters split
 * every quad in two); any other triangle repeats its last corner.
 * <p>
 * Materials: {@code baseColorFactor} gives the colour and alpha, {@code emissiveFactor} the glow (its largest
 * channel, like an MTL {@code Ka}), {@code extras.tintindex} a tint. The texture is the model JSON's
 * {@code textures} entry named after the material ({@code #name}; {@code #material_N} for an unnamed one);
 * images in the file are not read.
 */
public final class GltfModel {
    private final List<ObjQuad> quads;
    private final List<String> warnings;

    private GltfModel(List<ObjQuad> quads, List<String> warnings) {
        this.quads = quads;
        this.warnings = warnings;
    }

    /**
     * @param fileId {@code ns:path/model.glb} (or {@code .gltf})
     * @param source a file id to its bytes, or null when absent
     * @throws GltfException when the file is missing or can't be read
     */
    public static GltfModel load(String fileId, Function<String, byte[]> source) {
        byte[] bytes = source.apply(fileId);
        if (bytes == null)
            throw new GltfException(fileId + ": no such glTF file");
        return of(GltfFile.parse(fileId, bytes, source));
    }

    public static GltfModel of(GltfFile file) {
        return new Builder(file).build();
    }

    /** Every face, node by node. {@link ObjQuad#mesh()} is the node's name. */
    public List<ObjQuad> quads() {
        return quads;
    }

    /** One line each, starting with the file id. */
    public List<String> warnings() {
        return warnings;
    }

    /** The texture variable a material draws with: its name, or {@code material_N} when it has none. */
    public static String textureVariable(String materialName, int index) {
        return "#" + (materialName == null || materialName.isEmpty() ? "material_" + index : materialName);
    }

    private static final class Builder {
        private final GltfFile file;
        private final String id;
        private final List<ObjQuad> quads = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final Set<String> warned = new HashSet<>();
        private final List<ObjMaterial> materials = new ArrayList<>();

        Builder(GltfFile file) {
            this.file = file;
            this.id = file.id();
        }

        GltfModel build() {
            List<Object> materialList = file.array("materials");
            for (int i = 0; i < materialList.size(); i++)
                materials.add(material(GltfFile.object(id, materialList.get(i), "materials[" + i + "]"), i));

            List<Object> nodes = file.array("nodes");
            Set<Integer> visiting = new HashSet<>();
            for (int root : roots(nodes))
                node(nodes, root, Matrix.IDENTITY, visiting);
            if (quads.isEmpty())
                warn(id + ": the scene has no triangles to draw");
            return new GltfModel(Collections.unmodifiableList(quads), List.copyOf(warnings));
        }

        /** The scene's root nodes: {@code scene} (or the first scene), else every node no other node holds. */
        private List<Integer> roots(List<Object> nodes) {
            List<Object> scenes = file.array("scenes");
            if (!scenes.isEmpty()) {
                int scene = GltfFile.integer(id, file.json(), "scene", "", 0);
                if (scene >= scenes.size())
                    throw new GltfException(id + ": scene: " + scene + " doesn't exist");
                List<Integer> roots = new ArrayList<>();
                Map<?, ?> s = GltfFile.object(id, scenes.get(scene), "scenes[" + scene + "]");
                for (Object n : GltfFile.list(s, "nodes"))
                    roots.add(index(n, nodes.size(), "scenes[" + scene + "].nodes"));
                return roots;
            }
            Set<Integer> children = new HashSet<>();
            for (int i = 0; i < nodes.size(); i++)
                for (Object c : GltfFile.list(GltfFile.object(id, nodes.get(i), "nodes[" + i + "]"), "children"))
                    children.add(index(c, nodes.size(), "nodes[" + i + "].children"));
            List<Integer> roots = new ArrayList<>();
            for (int i = 0; i < nodes.size(); i++)
                if (!children.contains(i))
                    roots.add(i);
            return roots;
        }

        private void node(List<Object> nodes, int index, Matrix parent, Set<Integer> visiting) {
            String path = "nodes[" + index + "]";
            if (!visiting.add(index))
                throw new GltfException(id + ": " + path + ": is its own ancestor");
            Map<?, ?> node = GltfFile.object(id, nodes.get(index), path);
            Matrix world = parent.times(local(node, path));
            String name = node.get("name") instanceof String s && !s.isEmpty() ? s : "node_" + index;
            if (node.containsKey("mesh"))
                mesh(GltfFile.integer(id, node, "mesh", path, -1), name, world, path);
            if (node.containsKey("skin"))
                warn(id + ": " + path + ": skinning isn't supported; the mesh is drawn in its rest pose");
            for (Object c : GltfFile.list(node, "children"))
                node(nodes, index(c, nodes.size(), path + ".children"), world, visiting);
            visiting.remove(index);
        }

        private Matrix local(Map<?, ?> node, String path) {
            if (node.get("matrix") instanceof List<?> m)
                return Matrix.columnMajor(numbers(m, 16, path + ".matrix"));
            float[] t = node.containsKey("translation") ? numbers(node.get("translation"), 3, path + ".translation") : new float[3];
            float[] r = node.containsKey("rotation") ? numbers(node.get("rotation"), 4, path + ".rotation") : new float[] {0, 0, 0, 1};
            float[] s = node.containsKey("scale") ? numbers(node.get("scale"), 3, path + ".scale") : new float[] {1, 1, 1};
            return Matrix.trs(t, r, s);
        }

        private void mesh(int meshIndex, String name, Matrix world, String nodePath) {
            List<Object> meshes = file.array("meshes");
            if (meshIndex >= meshes.size())
                throw new GltfException(id + ": " + nodePath + ".mesh: " + meshIndex + " doesn't exist");
            String path = "meshes[" + meshIndex + "]";
            Map<?, ?> mesh = GltfFile.object(id, meshes.get(meshIndex), path);
            List<Object> primitives = GltfFile.list(mesh, "primitives");
            for (int p = 0; p < primitives.size(); p++)
                primitive(GltfFile.object(id, primitives.get(p), path + ".primitives[" + p + "]"), name, world,
                        path + ".primitives[" + p + "]");
        }

        private void primitive(Map<?, ?> primitive, String name, Matrix world, String path) {
            int mode = GltfFile.integer(id, primitive, "mode", path, 4);
            if (mode < 4 || mode > 6) {
                warn(id + ": " + path + ".mode: points and lines aren't drawn");
                return;
            }
            Map<?, ?> attributes = GltfFile.object(id, primitive.get("attributes"), path + ".attributes");
            if (!attributes.containsKey("POSITION"))
                throw new GltfException(id + ": " + path + ".attributes: needs POSITION");
            String ap = path + ".attributes";
            float[] positions = vectors(GltfFile.integer(id, attributes, "POSITION", ap, -1), 3, ap + ".POSITION");
            int vertices = positions.length / 3;
            float[] uvs = attributes.containsKey("TEXCOORD_0")
                    ? vectors(GltfFile.integer(id, attributes, "TEXCOORD_0", ap, -1), 2, ap + ".TEXCOORD_0") : null;
            if (uvs != null && uvs.length / 2 != vertices)
                throw new GltfException(id + ": " + ap + ".TEXCOORD_0: count differs from POSITION");
            if (attributes.containsKey("TEXCOORD_1") || attributes.containsKey("COLOR_0"))
                warn(id + ": " + ap + ": only POSITION, NORMAL and TEXCOORD_0 are read");
            int[] indices;
            if (primitive.containsKey("indices")) {
                indices = file.ints(GltfFile.integer(id, primitive, "indices", path, -1), path + ".indices");
                for (int i : indices)
                    if (i < 0 || i >= vertices)
                        throw new GltfException(id + ": " + path + ".indices: " + (i & 0xFFFFFFFFL)
                                + " is past the last vertex (" + (vertices - 1) + ")");
            } else {
                indices = new int[vertices];
                for (int i = 0; i < vertices; i++)
                    indices[i] = i;
            }
            int[][] triangles = triangles(indices, mode);
            ObjMaterial material = null;
            if (primitive.containsKey("material")) {
                int m = GltfFile.integer(id, primitive, "material", path, -1);
                if (m >= materials.size())
                    throw new GltfException(id + ": " + path + ".material: " + m + " doesn't exist");
                material = materials.get(m);
            }

            boolean mirrored = world.determinant() < 0;
            float[][] placed = new float[vertices][];
            for (int v = 0; v < vertices; v++)
                placed[v] = world.point(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2]);

            for (int t = 0; t < triangles.length; t++) {
                int[] a = triangles[t];
                if (mirrored)
                    a = new int[] {a[0], a[2], a[1]};
                if (degenerate(placed, a))
                    continue;
                int[] corners = null;
                if (t + 1 < triangles.length) {
                    int[] b = triangles[t + 1];
                    if (mirrored)
                        b = new int[] {b[0], b[2], b[1]};
                    corners = merge(placed, a, b);
                    if (corners != null)
                        t++;
                }
                if (corners == null)
                    corners = new int[] {a[0], a[1], a[2], a[2]};
                float[][] p = new float[4][], uv = new float[4][];
                for (int i = 0; i < 4; i++) {
                    p[i] = placed[corners[i]].clone();
                    uv[i] = uvs == null ? DEFAULT_UVS[i].clone() : new float[] {uvs[corners[i] * 2], uvs[corners[i] * 2 + 1]};
                }
                float[] normal = ObjQuad.faceNormal(p);
                quads.add(new ObjQuad(name, material, p, uv, new float[][] {normal, normal.clone(), normal.clone(), normal.clone()}));
            }
        }

        private static final float[][] DEFAULT_UVS = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

        /** Index triples for triangles, strips and fans, all counter-clockwise. */
        private static int[][] triangles(int[] idx, int mode) {
            List<int[]> out = new ArrayList<>();
            if (mode == 4) {
                for (int i = 0; i + 2 < idx.length; i += 3)
                    out.add(new int[] {idx[i], idx[i + 1], idx[i + 2]});
            } else if (mode == 5) {
                for (int i = 0; i + 2 < idx.length; i++)
                    out.add(i % 2 == 0 ? new int[] {idx[i], idx[i + 1], idx[i + 2]} : new int[] {idx[i + 1], idx[i], idx[i + 2]});
            } else {
                for (int i = 1; i + 1 < idx.length; i++)
                    out.add(new int[] {idx[0], idx[i], idx[i + 1]});
            }
            return out.toArray(new int[0][]);
        }

        /**
         * Two triangles sharing an edge (the same two vertices, opposite ways round) and one plane, as one quad that
         * keeps their diagonal: {@code (x, w, y, u)} for triangles {@code (u, x, y)} and {@code (y, x, w)}. A quad
         * is drawn as the triangles (0, 1, 2) and (0, 2, 3), which are then exactly the two originals.
         */
        private static int[] merge(float[][] placed, int[] a, int[] b) {
            if (degenerate(placed, b))
                return null;
            for (int r = 0; r < 3; r++) {
                int u = a[r], x = a[(r + 1) % 3], y = a[(r + 2) % 3];
                for (int s = 0; s < 3; s++) {
                    if (b[s] == y && b[(s + 1) % 3] == x) {
                        int w = b[(s + 2) % 3];
                        if (w == u || w == x || w == y)
                            return null;
                        float[] na = normal(placed[u], placed[x], placed[y]);
                        float[] nb = normal(placed[y], placed[x], placed[w]);
                        if (na[0] * nb[0] + na[1] * nb[1] + na[2] * nb[2] < 0.9999f)
                            return null;
                        return new int[] {x, w, y, u};
                    }
                }
            }
            return null;
        }

        private static boolean degenerate(float[][] placed, int[] t) {
            float[] a = placed[t[0]], b = placed[t[1]], c = placed[t[2]];
            float[] n = cross(a, b, c);
            return n[0] * n[0] + n[1] * n[1] + n[2] * n[2] < 1e-14f;
        }

        private static float[] normal(float[] a, float[] b, float[] c) {
            float[] n = cross(a, b, c);
            float length = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            return new float[] {n[0] / length, n[1] / length, n[2] / length};
        }

        private static float[] cross(float[] a, float[] b, float[] c) {
            float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
            float vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
            return new float[] {uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
        }

        private ObjMaterial material(Map<?, ?> m, int index) {
            String path = "materials[" + index + "]";
            String name = m.get("name") instanceof String s ? s : null;
            float[] color = {1, 1, 1};
            float alpha = 1;
            if (m.get("pbrMetallicRoughness") instanceof Map<?, ?> pbr && pbr.containsKey("baseColorFactor")) {
                float[] f = numbers(pbr.get("baseColorFactor"), 4, path + ".pbrMetallicRoughness.baseColorFactor");
                color = new float[] {f[0], f[1], f[2]};
                alpha = f[3];
            }
            float glow = 0;
            if (m.containsKey("emissiveFactor")) {
                float[] e = numbers(m.get("emissiveFactor"), 3, path + ".emissiveFactor");
                glow = Math.min(1f, Math.max(e[0], Math.max(e[1], e[2])));
            }
            int tint = -1;
            if (m.get("extras") instanceof Map<?, ?> extras && extras.get("tintindex") instanceof Double d)
                tint = d.intValue();
            String variable = textureVariable(name, index);
            return new ObjMaterial(variable.substring(1), color, glow, variable, alpha, tint);
        }

        /** An accessor of {@code size}-component vectors as floats. */
        private float[] vectors(int accessor, int size, String path) {
            int components = file.components(accessor, path);
            if (components != size)
                throw new GltfException(id + ": " + path + ": expected VEC" + size);
            return file.floats(accessor, path);
        }

        private int index(Object value, int size, String path) {
            if (value instanceof Double d && d == Math.floor(d) && d >= 0 && d < size)
                return d.intValue();
            throw new GltfException(id + ": " + path + ": " + value + " isn't a node");
        }

        private float[] numbers(Object value, int size, String path) {
            if (value instanceof List<?> list && list.size() == size) {
                float[] out = new float[size];
                for (int i = 0; i < size; i++) {
                    if (!(list.get(i) instanceof Double d))
                        throw new GltfException(id + ": " + path + ": expected " + size + " numbers");
                    out[i] = d.floatValue();
                }
                return out;
            }
            throw new GltfException(id + ": " + path + ": expected " + size + " numbers");
        }

        private void warn(String line) {
            if (warned.add(line))
                warnings.add(line);
        }
    }
}
