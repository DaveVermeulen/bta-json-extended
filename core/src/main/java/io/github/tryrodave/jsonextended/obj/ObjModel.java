package io.github.tryrodave.jsonextended.obj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * An OBJ file with its materials, turned into quads. Load with {@link #load}; {@code source} maps a file id
 * ({@code ns:path/file.obj}, {@code ns:path/file.mtl}) to its text, or null when there's no such file.
 * <p>
 * Materials: {@code usemtl name} uses that material from the {@code mtllib} files. When there is none,
 * {@code usemtl #variable} still works: it draws the model JSON's {@code textures} entry for that variable. Any
 * other unknown name gives a warning and the face is drawn with the particle texture.
 */
public final class ObjModel {
    private final ObjFile file;
    private final Map<String, ObjMaterial> materials;
    private final List<String> warnings;

    private ObjModel(ObjFile file, Map<String, ObjMaterial> materials, List<String> warnings) {
        this.file = file;
        this.materials = materials;
        this.warnings = warnings;
    }

    /**
     * @param mtlOverride a material library to use instead of the file's {@code mtllib} ({@code mtl_override} in
     *                    the model JSON), or null
     * @throws ObjException when the OBJ file is missing or can't be read; a missing MTL file is a warning
     */
    public static ObjModel load(String objId, Function<String, String> source, String mtlOverride) {
        String text = source.apply(objId);
        if (text == null)
            throw new ObjException(objId + ": no such OBJ file");
        ObjFile file = ObjFile.parse(objId, text);
        List<String> warnings = new ArrayList<>(file.warnings());

        List<String> libraries = new ArrayList<>();
        if (mtlOverride != null)
            libraries.add(mtlOverride);
        else
            for (String name : file.materialLibraries())
                libraries.add(ObjFile.sibling(objId, name));
        Map<String, ObjMaterial> materials = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (String library : libraries) {
            String mtl = source.apply(library);
            if (mtl == null)
                missing.add(library);
            else
                Mtl.parse(library, mtl).forEach(materials::putIfAbsent);
        }

        boolean needsLibrary = false;
        for (ObjMesh mesh : file.meshes()) {
            String name = mesh.material();
            if (name == null || materials.containsKey(name) || name.startsWith("#"))
                continue;
            needsLibrary = true;
            warnings.add(objId + ": usemtl '" + name + "' is not in " + (libraries.isEmpty() ? "any mtllib" : libraries)
                    + ", so its faces draw the particle texture");
        }
        // Blender always writes mtllib; a missing file only matters when a material was needed from it.
        if (needsLibrary)
            for (String library : missing)
                warnings.add(objId + ": material library " + library + " doesn't exist");
        return new ObjModel(file, materials, warnings);
    }

    public ObjFile file() {
        return file;
    }

    /** Materials from the libraries, by name. */
    public Map<String, ObjMaterial> materials() {
        return Collections.unmodifiableMap(materials);
    }

    /** One line each, starting with the file id. */
    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    /** The material a mesh draws with, or null for none. */
    public ObjMaterial material(ObjMesh mesh) {
        String name = mesh.material();
        if (name == null)
            return null;
        ObjMaterial material = materials.get(name);
        if (material == null && name.startsWith("#"))
            return ObjMaterial.forVariable(name);
        return material;
    }

    /** Every face as quads, mesh by mesh. */
    public List<ObjQuad> quads(boolean flipV) {
        List<ObjQuad> quads = new ArrayList<>();
        for (ObjMesh mesh : file.meshes())
            quads.addAll(quads(mesh, flipV));
        return quads;
    }

    /**
     * One mesh's faces as quads. A triangle repeats its last corner; a polygon with more corners is split into
     * a fan of quads from its first corner (the last one a triangle when the count is odd). Faces without
     * {@code vn} get the face normal; corners without {@code vt} get the corners of the texture.
     */
    public List<ObjQuad> quads(ObjMesh mesh, boolean flipV) {
        ObjMaterial material = material(mesh);
        List<ObjQuad> quads = new ArrayList<>();
        for (int[][] face : mesh.faces()) {
            int n = face.length;
            if (n <= 4) {
                quads.add(quad(mesh, material, face, new int[] {0, 1, 2, n == 3 ? 2 : 3}, flipV));
                continue;
            }
            for (int i = 1; i < n - 1; i += 2)
                quads.add(quad(mesh, material, face, new int[] {0, i, i + 1, i + 2 < n ? i + 2 : i + 1}, flipV));
        }
        return quads;
    }

    private static final float[][] DEFAULT_UVS = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

    private ObjQuad quad(ObjMesh mesh, ObjMaterial material, int[][] face, int[] order, boolean flipV) {
        float[][] positions = new float[4][];
        float[][] uvs = new float[4][];
        float[][] normals = new float[4][];
        boolean allNormals = true;
        for (int i = 0; i < 4; i++) {
            int[] corner = face[order[i]];
            positions[i] = file.positions().get(corner[0]).clone();
            float[] uv = corner[1] >= 0 ? file.uvs().get(corner[1]).clone() : DEFAULT_UVS[i].clone();
            if (flipV)
                uv[1] = 1 - uv[1];
            uvs[i] = uv;
            if (corner[2] >= 0)
                normals[i] = ObjQuad.normalize(file.normals().get(corner[2]));
            else
                allNormals = false;
        }
        if (!allNormals) {
            float[] normal = ObjQuad.faceNormal(positions);
            for (int i = 0; i < 4; i++)
                normals[i] = normal.clone();
        }
        return new ObjQuad(mesh.name(), material, positions, uvs, normals);
    }
}
