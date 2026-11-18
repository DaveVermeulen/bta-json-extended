package io.github.tryrodave.jsonextended.obj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The geometry statements of a Wavefront OBJ file, written from the OBJ specification: vertices ({@code v},
 * {@code vt}, {@code vn}), polygon faces ({@code f}), objects ({@code o}), groups ({@code g}), materials
 * ({@code mtllib}, {@code usemtl}). Indices may be negative (relative). Lines, points, curves and surfaces are
 * skipped; any other unknown statement gives a warning.
 */
public final class ObjFile {
    /** Statements that are valid OBJ but draw nothing in a block model. */
    private static final Set<String> SKIPPED = Set.of("s", "l", "p", "vp", "mg", "lod", "bevel", "c_interp",
            "d_interp", "shadow_obj", "trace_obj", "ctech", "stech", "cstype", "deg", "bmat", "step", "curv", "curv2",
            "surf", "parm", "trim", "hole", "scrv", "sp", "end", "con", "maplib", "usemap");

    private final String id;
    private final List<float[]> positions = new ArrayList<>();
    private final List<float[]> uvs = new ArrayList<>();
    private final List<float[]> normals = new ArrayList<>();
    private final List<String> materialLibraries = new ArrayList<>();
    private final List<ObjMesh> meshes = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    private ObjFile(String id) {
        this.id = id;
    }

    /** Parses OBJ text. {@code id} ({@code ns:path/file.obj}) names the file in messages. */
    public static ObjFile parse(String id, String text) {
        ObjFile file = new ObjFile(id);
        file.read(text);
        return file;
    }

    private void read(String text) {
        String object = "";
        String group = "";
        String material = null;
        ObjMesh mesh = null;
        Set<String> unknown = new LinkedHashSet<>();
        for (ObjLines.Line line : ObjLines.split(text)) {
            switch (line.keyword()) {
                case "v" -> {
                    float[] v = numbers(line, 3, 4, 3);
                    if (line.args() >= 4) {
                        float w = v[3];
                        if (w == 0)
                            throw ObjException.at(id, line.number(), "v has w = 0");
                        v = new float[] {v[0] / w, v[1] / w, v[2] / w};
                    }
                    positions.add(new float[] {v[0], v[1], v[2]});
                }
                case "vt" -> {
                    float[] t = numbers(line, 1, 3, 2);
                    uvs.add(new float[] {t[0], t[1]});
                }
                case "vn" -> normals.add(numbers(line, 3, 3, 3));
                case "o" -> {
                    object = rest(line);
                    group = "";
                }
                case "g" -> group = rest(line);
                case "usemtl" -> {
                    if (line.args() < 1)
                        throw ObjException.at(id, line.number(), "usemtl needs a material name");
                    material = rest(line);
                }
                case "mtllib" -> {
                    if (line.args() < 1)
                        throw ObjException.at(id, line.number(), "mtllib needs a file name");
                    for (int i = 1; i < line.tokens().length; i++)
                        materialLibraries.add(line.tokens()[i]);
                }
                case "f", "fo" -> {
                    int[][] face = face(line);
                    if (mesh == null || !mesh.object().equals(object) || !mesh.group().equals(group)
                            || !Objects.equals(mesh.material(), material)) {
                        mesh = new ObjMesh(object, group, material, new ArrayList<>());
                        meshes.add(mesh);
                    }
                    mesh.faces().add(face);
                }
                default -> {
                    if (!SKIPPED.contains(line.keyword()) && unknown.add(line.keyword()))
                        warnings.add(id + " line " + line.number() + ": unknown statement '" + line.keyword() + "' is ignored");
                }
            }
        }
        if (meshes.isEmpty())
            warnings.add(id + ": no faces, so the model draws nothing");
    }

    /** {@code min}..{@code max} numbers; missing ones up to {@code fill} are 0. */
    private float[] numbers(ObjLines.Line line, int min, int max, int fill) {
        if (line.args() < min)
            throw ObjException.at(id, line.number(), line.keyword() + " needs " + min + (min == max ? "" : " to " + max) + " numbers");
        int count = Math.min(line.args(), max);
        float[] out = new float[Math.max(count, fill)];
        for (int i = 0; i < count; i++)
            out[i] = ObjLines.number(id, line, i + 1);
        return out;
    }

    /** {@code f v/vt/vn ...}: each corner as zero-based [position, uv or -1, normal or -1]. */
    private int[][] face(ObjLines.Line line) {
        if (line.args() < 3)
            throw ObjException.at(id, line.number(), "a face needs at least 3 corners");
        int[][] corners = new int[line.args()][];
        for (int i = 0; i < corners.length; i++) {
            String[] parts = line.tokens()[i + 1].split("/", -1);
            if (parts.length > 3 || parts[0].isEmpty())
                throw ObjException.at(id, line.number(), "'" + line.tokens()[i + 1] + "' is not v, v/vt, v//vn or v/vt/vn");
            int position = index(line, parts[0], positions.size(), "v");
            int uv = parts.length > 1 && !parts[1].isEmpty() ? index(line, parts[1], uvs.size(), "vt") : -1;
            int normal = parts.length > 2 && !parts[2].isEmpty() ? index(line, parts[2], normals.size(), "vn") : -1;
            corners[i] = new int[] {position, uv, normal};
        }
        return corners;
    }

    private int index(ObjLines.Line line, String text, int count, String kind) {
        int n;
        try {
            n = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw ObjException.at(id, line.number(), "'" + text + "' is not a " + kind + " index");
        }
        int index = n > 0 ? n - 1 : count + n;
        if (n == 0 || index < 0 || index >= count)
            throw ObjException.at(id, line.number(), kind + " index " + n + " is out of range: there are " + count + " so far");
        return index;
    }

    private static String rest(ObjLines.Line line) {
        return line.args() == 0 ? "" : String.join(" ", List.of(line.tokens()).subList(1, line.tokens().length));
    }

    public String id() {
        return id;
    }

    public List<float[]> positions() {
        return Collections.unmodifiableList(positions);
    }

    public List<float[]> uvs() {
        return Collections.unmodifiableList(uvs);
    }

    public List<float[]> normals() {
        return Collections.unmodifiableList(normals);
    }

    /** File names from {@code mtllib}, as written (relative to the OBJ file). */
    public List<String> materialLibraries() {
        return Collections.unmodifiableList(materialLibraries);
    }

    /** Faces split by object, group and material, in file order. */
    public List<ObjMesh> meshes() {
        return Collections.unmodifiableList(meshes);
    }

    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * The id of a file named relative to {@code objId}: {@code ns:models/block/x.obj} and {@code x.mtl} give
     * {@code ns:models/block/x.mtl}. A name with a namespace ({@code ns:path}) is used as it is.
     */
    public static String sibling(String objId, String name) {
        if (name.contains(":"))
            return name;
        int slash = objId.lastIndexOf('/');
        int colon = objId.indexOf(':');
        return objId.substring(0, Math.max(slash, colon) + 1) + name;
    }
}
