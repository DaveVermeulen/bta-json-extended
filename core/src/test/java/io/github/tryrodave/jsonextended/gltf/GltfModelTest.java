package io.github.tryrodave.jsonextended.gltf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tryrodave.jsonextended.obj.ObjQuad;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GltfModelTest {
    private static final float EPS = 1e-5f;

    private static String cubeScene(Gltf g, String node) {
        int pos = g.floats("VEC3", Gltf.cubePositions());
        int uv = g.floats("VEC2", Gltf.cubeUvs());
        int idx = g.indices(Gltf.cubeIndices());
        return "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":" + pos + ",\"TEXCOORD_0\":" + uv
                + "},\"indices\":" + idx + ",\"material\":0}]}],"
                + "\"materials\":[{\"name\":\"stone\"}],"
                + "\"nodes\":[{\"name\":\"box\",\"mesh\":0" + node + "}],\"scenes\":[{\"nodes\":[0]}],\"scene\":0";
    }

    private static GltfModel glb(byte[] bytes) {
        return GltfModel.load("test:models/block/m.glb", id -> id.equals("test:models/block/m.glb") ? bytes : null);
    }

    @Test
    void glbCubeBecomesSixQuadsOnTheBlockSides() {
        Gltf g = new Gltf();
        GltfModel model = glb(g.glb(cubeScene(g, "")));
        assertEquals(List.of(), model.warnings());
        assertEquals(6, model.quads().size());
        Set<ObjQuad.Side> sides = EnumSet.noneOf(ObjQuad.Side.class);
        for (ObjQuad q : model.quads()) {
            assertEquals("box", q.mesh());
            assertEquals("#stone", q.material().texture());
            ObjQuad.Side side = q.cullSide();
            assertTrue(side != null, "every cube face lies on a side, facing out");
            sides.add(side);
            // four distinct corners: merged, not a repeated triangle
            assertTrue(!java.util.Arrays.equals(q.positions()[2], q.positions()[3]));
        }
        assertEquals(EnumSet.allOf(ObjQuad.Side.class), sides);
    }

    @Test
    void mergedQuadKeepsBothOriginalTriangles() {
        Gltf g = new Gltf();
        ObjQuad up = glb(g.glb(cubeScene(g, ""))).quads().get(0);
        // Triangles (0,1,2) and (0,2,3) of the quad must be the file's triangles (v0,v1,v2) and (v0,v2,v3) in some rotation.
        float[][] p = up.positions();
        float[][] file = {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
        assertTrue(sameTriangle(new float[][] {p[0], p[1], p[2]}, file[0], file[1], file[2])
                || sameTriangle(new float[][] {p[0], p[1], p[2]}, file[0], file[2], file[3]));
        assertTrue(sameTriangle(new float[][] {p[0], p[2], p[3]}, file[0], file[1], file[2])
                || sameTriangle(new float[][] {p[0], p[2], p[3]}, file[0], file[2], file[3]));
        assertArrayEquals(new float[] {0, 1, 0}, ObjQuad.faceNormal(p), EPS);
    }

    private static boolean sameTriangle(float[][] t, float[] a, float[] b, float[] c) {
        float[][] r = {a, b, c};
        for (int s = 0; s < 3; s++) {
            boolean all = true;
            for (int i = 0; i < 3; i++)
                all &= java.util.Arrays.equals(t[i], r[(i + s) % 3]);
            if (all)
                return true;
        }
        return false;
    }

    @Test
    void uvsComeThroughUnflipped() {
        Gltf g = new Gltf();
        ObjQuad up = glb(g.glb(cubeScene(g, ""))).quads().get(0);
        // glTF's V points down like BTA's atlas, so corner (0,1,0) keeps uv (0,0) and (1,1,1) keeps (1,1).
        for (int i = 0; i < 4; i++) {
            float[] p = up.positions()[i], uv = up.uvs()[i];
            if (p[0] == 0 && p[2] == 0)
                assertArrayEquals(new float[] {0, 0}, uv, EPS);
            if (p[0] == 1 && p[2] == 1)
                assertArrayEquals(new float[] {1, 1}, uv, EPS);
        }
    }

    @Test
    void nodeTransformsArePlacedParentFirst() {
        Gltf g = new Gltf();
        int pos = g.floats("VEC3", 0, 0, 0, 1, 0, 0, 0, 1, 0);
        String rest = "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":" + pos + "}}]}],"
                + "\"nodes\":[{\"translation\":[1,0,0],\"children\":[1]},"
                + "{\"name\":\"tri\",\"mesh\":0,\"rotation\":[0,0.70710678,0,0.70710678],\"scale\":[2,2,2]}]";
        GltfModel model = glb(g.glb(rest));
        ObjQuad q = model.quads().get(0);
        // Child: scale 2, then +90 degrees about Y ((1,0,0) -> (0,0,-1)), then the parent's +1 on X.
        assertArrayEquals(new float[] {1, 0, 0}, q.positions()[0], EPS);
        assertArrayEquals(new float[] {1, 0, -2}, q.positions()[1], EPS);
        assertArrayEquals(new float[] {1, 2, 0}, q.positions()[2], EPS);
        assertArrayEquals(q.positions()[2], q.positions()[3], EPS); // a lone triangle repeats its last corner
    }

    @Test
    void matrixIsColumnMajor() {
        Gltf g = new Gltf();
        int pos = g.floats("VEC3", 0, 0, 0, 1, 0, 0, 0, 1, 0);
        String rest = "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":" + pos + "}}]}],"
                + "\"nodes\":[{\"mesh\":0,\"matrix\":[1,0,0,0, 0,1,0,0, 0,0,1,0, 0.5,0.25,0.125,1]}]";
        ObjQuad q = glb(g.glb(rest)).quads().get(0);
        assertArrayEquals(new float[] {0.5f, 0.25f, 0.125f}, q.positions()[0], EPS);
        assertEquals("node_0", q.mesh());
    }

    @Test
    void mirroredNodeKeepsFacesFacingOut() {
        Gltf g = new Gltf();
        GltfModel model = glb(g.glb(cubeScene(g, ",\"scale\":[-1,1,1],\"translation\":[1,0,0]")));
        assertEquals(6, model.quads().size());
        for (ObjQuad q : model.quads())
            assertTrue(q.cullSide() != null, "faces still point out of the cube after a mirror");
    }

    @Test
    void gltfWithDataUriAndWithBinFile() {
        Gltf g = new Gltf();
        String rest = cubeScene(g, "");
        String embedded = g.json(rest, g.dataUri());
        GltfModel a = GltfModel.load("test:models/block/m.gltf",
                id -> id.equals("test:models/block/m.gltf") ? embedded.getBytes(StandardCharsets.UTF_8) : null);
        assertEquals(6, a.quads().size());

        String external = g.json(rest, "../shared/m%20data.bin");
        Map<String, byte[]> files = Map.of("test:models/block/m.gltf", external.getBytes(StandardCharsets.UTF_8),
                "test:models/shared/m data.bin", g.bin());
        GltfModel b = GltfModel.load("test:models/block/m.gltf", files::get);
        assertEquals(6, b.quads().size());

        GltfException missing = assertThrows(GltfException.class,
                () -> GltfModel.load("test:models/block/m.gltf", id -> id.endsWith(".gltf") ? external.getBytes(StandardCharsets.UTF_8) : null));
        assertEquals("test:models/block/m.gltf: buffers[0].uri: no such file test:models/shared/m data.bin", missing.getMessage());
    }

    @Test
    void stripsAndFans() {
        Gltf g = new Gltf();
        // A square as a strip (0,1,2),(2,1,3) and as a fan (0,1,2),(0,2,3): both merge into one quad.
        int strip = g.floats("VEC3", 0, 0, 0, 1, 0, 0, 0, 1, 0, 1, 1, 0);
        int fan = g.floats("VEC3", 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0);
        String rest = "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":" + strip + "},\"mode\":5},"
                + "{\"attributes\":{\"POSITION\":" + fan + "},\"mode\":6},"
                + "{\"attributes\":{\"POSITION\":" + fan + "},\"mode\":1}]}],\"nodes\":[{\"mesh\":0}]";
        GltfModel model = glb(g.glb(rest));
        assertEquals(2, model.quads().size());
        for (ObjQuad q : model.quads())
            assertArrayEquals(new float[] {0, 0, 1}, ObjQuad.faceNormal(q.positions()), EPS);
        assertEquals(List.of("test:models/block/m.glb: meshes[0].primitives[2].mode: points and lines aren't drawn"),
                model.warnings());
    }

    @Test
    void bentPairIsNotMerged() {
        Gltf g = new Gltf();
        int pos = g.floats("VEC3", 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 1, 1);
        int idx = g.indices(0, 1, 2, 0, 2, 3);
        String rest = "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":" + pos + "},\"indices\":" + idx
                + "}]}],\"nodes\":[{\"mesh\":0}]";
        assertEquals(2, glb(g.glb(rest)).quads().size());
    }

    @Test
    void materialsGiveColourGlowTintAndTextureVariable() {
        Gltf g = new Gltf();
        int pos = g.floats("VEC3", 0, 0, 0, 1, 0, 0, 0, 1, 0);
        String rest = "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":" + pos + "},\"material\":0},"
                + "{\"attributes\":{\"POSITION\":" + pos + "},\"material\":1},"
                + "{\"attributes\":{\"POSITION\":" + pos + "}}]}],"
                + "\"materials\":[{\"name\":\"glass\",\"pbrMetallicRoughness\":{\"baseColorFactor\":[1,0.5,0.25,0.5]},"
                + "\"emissiveFactor\":[0,0.8,0.2],\"extras\":{\"tintindex\":1}},{}],"
                + "\"nodes\":[{\"mesh\":0}]";
        List<ObjQuad> quads = glb(g.glb(rest)).quads();
        var glass = quads.get(0).material();
        assertEquals("#glass", glass.texture());
        assertArrayEquals(new float[] {1, 0.5f, 0.25f}, glass.color(), EPS);
        assertEquals(0.5f, glass.alpha(), EPS);
        assertEquals(0.8f, glass.ambient(), EPS);
        assertEquals(1, glass.tintIndex());
        var plain = quads.get(1).material();
        assertEquals("#material_1", plain.texture());
        assertArrayEquals(new float[] {1, 1, 1}, plain.color(), EPS);
        assertEquals(-1, plain.tintIndex());
        assertNull(quads.get(2).material());
    }

    @Test
    void normalizedByteUvs() {
        Gltf g = new Gltf();
        int pos = g.floats("VEC3", 0, 0, 0, 1, 0, 0, 0, 1, 0);
        int uv = g.normalizedBytes("VEC2", 0, 0, 255, 0, 0, 255);
        String rest = "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":" + pos + ",\"TEXCOORD_0\":" + uv
                + "}}]}],\"nodes\":[{\"mesh\":0}]";
        ObjQuad q = glb(g.glb(rest)).quads().get(0);
        assertArrayEquals(new float[] {1, 0}, q.uvs()[1], EPS);
        assertArrayEquals(new float[] {0, 1}, q.uvs()[2], EPS);
    }

    @Test
    void sceneWithoutTrianglesWarnsAndSkinningWarns() {
        Gltf g = new Gltf();
        GltfModel empty = glb(g.glb("\"nodes\":[{\"name\":\"empty\"}]"));
        assertEquals(List.of("test:models/block/m.glb: the scene has no triangles to draw"), empty.warnings());
        Gltf h = new Gltf();
        GltfModel skinned = glb(h.glb(cubeScene(h, ",\"skin\":0")));
        assertEquals(List.of("test:models/block/m.glb: nodes[0]: skinning isn't supported; the mesh is drawn in its rest pose"),
                skinned.warnings());
    }

    @Test
    void errorsNameTheFileAndKey() {
        Gltf g = new Gltf();
        int pos = g.floats("VEC3", 0, 0, 0, 1, 0, 0, 0, 1, 0);
        String mesh = "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":" + pos + "},\"indices\":%s}]}],\"nodes\":[{\"mesh\":0}]";
        int bad = g.indices(0, 1, 7);
        assertError("test:models/block/m.glb: meshes[0].primitives[0].indices: 7 is past the last vertex (2)",
                g.glb(String.format(mesh, bad)));
        assertError("test:models/block/m.glb: extensionsRequired: KHR_draco_mesh_compression isn't supported"
                + " (export without it, e.g. no Draco compression or mesh quantization)",
                g.glb("\"extensionsRequired\":[\"KHR_draco_mesh_compression\"]"));
        assertError("test:models/block/m.glb: meshes[0].primitives[0].attributes: needs POSITION",
                g.glb("\"meshes\":[{\"primitives\":[{\"attributes\":{}}]}],\"nodes\":[{\"mesh\":0}]"));
        assertError("test:models/block/m.glb: nodes[0]: is its own ancestor",
                g.glb("\"nodes\":[{\"children\":[1]},{\"children\":[0]}],\"scenes\":[{\"nodes\":[0]}]"));

        byte[] glb = g.glb("");
        java.nio.ByteBuffer.wrap(glb).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(4, 1);
        assertError("test:models/block/m.glb: GLB version 1 (only glTF 2.0 is read)", glb);
        assertError("test:models/block/m.glb: expected a JSON object", "[]".getBytes(StandardCharsets.UTF_8));
        GltfException none = assertThrows(GltfException.class, () -> GltfModel.load("test:x.glb", id -> null));
        assertEquals("test:x.glb: no such glTF file", none.getMessage());
    }

    private static void assertError(String message, byte[] file) {
        GltfException e = assertThrows(GltfException.class, () -> glb(file));
        assertEquals(message, e.getMessage());
    }

    @Test
    void accessorPastItsBufferIsAnError() {
        Gltf g = new Gltf();
        g.floats("VEC3", 0, 0, 0, 1, 0, 0, 0, 1, 0);
        String json = g.json("\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0}}]}],\"nodes\":[{\"mesh\":0}]",
                g.dataUri()).replace("\"count\":3", "\"count\":4");
        GltfException e = assertThrows(GltfException.class, () -> GltfModel.load("t:m.gltf",
                id -> json.getBytes(StandardCharsets.UTF_8)));
        assertEquals("t:m.gltf: accessors[0]: reads past the end of its buffer", e.getMessage());
    }

    @Test
    void siblingResolvesDots() {
        assertEquals("ns:models/block/a.bin", GltfFile.sibling("ns:models/block/m.gltf", "a.bin"));
        assertEquals("ns:models/a.bin", GltfFile.sibling("ns:models/block/m.gltf", "../a.bin"));
        assertEquals("ns:models/block/sub/a.bin", GltfFile.sibling("ns:models/block/m.gltf", "./sub/a.bin"));
    }
}
