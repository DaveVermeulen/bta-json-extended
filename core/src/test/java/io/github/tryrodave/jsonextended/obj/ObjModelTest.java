package io.github.tryrodave.jsonextended.obj;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tryrodave.jsonextended.obj.ObjQuad.Side;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Fixtures in src/test/resources/obj/ are Betafront's test models (ours): a cube, the ramp and a Blender crystal. */
class ObjModelTest {
    private final List<String> read = new ArrayList<>();
    private final Map<String, String> inline = new HashMap<>();

    /** {@code t:obj/x.obj} → {@code /obj/x.obj} on the test classpath; inline files first. */
    private String source(String id) {
        read.add(id);
        if (inline.containsKey(id))
            return inline.get(id);
        try (InputStream in = ObjModelTest.class.getResourceAsStream("/" + id.substring(id.indexOf(':') + 1))) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ObjModel load(String id) {
        return ObjModel.load(id, this::source, null);
    }

    private ObjModel text(String obj) {
        inline.put("t:inline.obj", obj);
        return load("t:inline.obj");
    }

    @Test
    void cubeReadsItsMaterialLibraryNextToIt() {
        ObjModel cube = load("t:obj/cube.obj");
        assertEquals(List.of("t:obj/cube.obj", "t:obj/cube.mtl"), read);
        assertEquals(8, cube.file().positions().size());
        assertEquals(6, cube.file().normals().size());
        assertEquals(1, cube.file().meshes().size());
        ObjMaterial stone = cube.material(cube.file().meshes().get(0));
        assertEquals("stone", stone.name());
        assertEquals("#texture0", stone.texture());
        assertEquals(6, cube.quads(false).size());
        assertEquals(List.of(), cube.warnings());
    }

    @Test
    void everyCubeFaceCullsToItsOwnSide() {
        List<Side> sides = new ArrayList<>();
        for (ObjQuad quad : load("t:obj/cube.obj").quads(false))
            sides.add(quad.cullSide());
        assertEquals(List.of(Side.DOWN, Side.UP, Side.NORTH, Side.SOUTH, Side.WEST, Side.EAST), sides);
    }

    @Test
    void facesInsideOrFacingInDontCull() {
        float[][] middle = {{0.5f, 0, 0}, {0.5f, 1, 0}, {0.5f, 1, 1}, {0.5f, 0, 1}};
        assertNull(ObjQuad.cullSide(middle, new float[] {1, 0, 0}));
        float[][] west = {{0, 0, 0}, {0, 1, 0}, {0, 1, 1}, {0, 0, 1}};
        assertNull(ObjQuad.cullSide(west, new float[] {1, 0, 0}));
        assertEquals(Side.WEST, ObjQuad.cullSide(west, new float[] {-2, 0, 0}));
    }

    @Test
    void flipVTurnsTextureVOver() {
        assertEquals(1f, load("t:obj/cube.obj").quads(false).get(0).uvs()[2][1]);
        assertEquals(0f, load("t:obj/cube.obj").quads(true).get(0).uvs()[2][1]);
    }

    @Test
    void mtlOverrideReplacesMtllib() {
        inline.put("other:obj/cube.mtl", "newmtl stone\nmap_Kd other:block/stone\n");
        ObjModel cube = ObjModel.load("t:obj/cube.obj", this::source, "other:obj/cube.mtl");
        assertEquals(List.of("t:obj/cube.obj", "other:obj/cube.mtl"), read);
        assertEquals("other:block/stone", cube.material(cube.file().meshes().get(0)).texture());
    }

    @Test
    void triangleRepeatsItsLastCornerAndGetsAFaceNormal() {
        ObjModel shapes = load("t:obj/shapes.obj");
        ObjMesh tri = shapes.file().meshes().get(0);
        assertEquals("tri", tri.name());
        assertNull(shapes.material(tri));
        List<ObjQuad> quads = shapes.quads(tri, false);
        assertEquals(1, quads.size());
        assertArrayEquals(quads.get(0).positions()[2], quads.get(0).positions()[3]);
        assertEquals(-1f, quads.get(0).normals()[0][1], 1e-5f);
        assertEquals(Side.DOWN, Side.nearest(quads.get(0).normals()[0]));
    }

    @Test
    void hexagonIsAFanOfTwoQuads() {
        ObjModel shapes = load("t:obj/shapes.obj");
        List<ObjQuad> quads = shapes.quads(shapes.file().meshes().get(1), false);
        assertEquals(2, quads.size());
        assertArrayEquals(new float[] {0, 0, 0}, quads.get(0).positions()[0]);
        assertArrayEquals(new float[] {1, 0, 1}, quads.get(0).positions()[3]);
        assertArrayEquals(new float[] {1, 0, 1}, quads.get(1).positions()[1]);
        assertArrayEquals(new float[] {-0.5f, 0, 0.5f}, quads.get(1).positions()[3]);
    }

    @Test
    void pentagonEndsWithATriangle() {
        ObjModel model = text("v 0 0 0\nv 1 0 0\nv 1 0 1\nv 0.5 0 1.5\nv 0 0 1\nf 1 2 3 4 5\n");
        List<ObjQuad> quads = model.quads(false);
        assertEquals(2, quads.size());
        assertArrayEquals(quads.get(1).positions()[2], quads.get(1).positions()[3]);
    }

    @Test
    void meshesSplitByObjectGroupAndMaterial() {
        List<String> names = new ArrayList<>();
        for (ObjMesh mesh : load("t:obj/groups.obj").file().meshes())
            names.add(mesh.name());
        assertEquals(List.of("Body/left", "Body/right", "Lid/top"), names);
        ObjModel two = text("v 0 0 0\nv 1 0 0\nv 1 1 0\nusemtl #a\nf 1 2 3\nusemtl #b\nf 1 2 3\nusemtl #a\nf 1 2 3\n");
        assertEquals(3, two.file().meshes().size());
        ObjModel objects = text("v 0 0 0\nv 1 0 0\nv 1 1 0\no A\ng inner\nf 1 2 3\no B\nf 1 2 3\n");
        assertEquals("B", objects.file().meshes().get(1).name());
    }

    @Test
    void relativeAndHomogeneousVertices() {
        ObjModel model = text("v 2 4 6 2\nv 1 0 0\nv 0 1 0\nf -3 -2 -1\n");
        assertArrayEquals(new float[] {1, 2, 3}, model.file().positions().get(0));
        assertEquals(0, model.file().meshes().get(0).faces().get(0)[0][0]);
    }

    @Test
    void badIndexNamesFileAndLine() {
        ObjException e = assertThrows(ObjException.class, () -> text("v 0 0 0\nv 1 0 0\n\nf 1 2 7\n"));
        assertEquals("t:inline.obj line 4: v index 7 is out of range: there are 2 so far", e.getMessage());
        e = assertThrows(ObjException.class, () -> text("v 0 0 x\n"));
        assertEquals("t:inline.obj line 1: 'x' is not a number", e.getMessage());
        e = assertThrows(ObjException.class, () -> text("v 0 0 0\nf 1 1\n"));
        assertEquals("t:inline.obj line 2: a face needs at least 3 corners", e.getMessage());
    }

    @Test
    void missingObjIsAnError() {
        ObjException e = assertThrows(ObjException.class, () -> load("t:obj/nope.obj"));
        assertEquals("t:obj/nope.obj: no such OBJ file", e.getMessage());
    }

    @Test
    void unknownMaterialWarnsAndDrawsWithoutOne() {
        ObjModel model = text("mtllib gone.mtl\nv 0 0 0\nv 1 0 0\nv 1 1 0\nusemtl missing\nf 1 2 3\n");
        assertNull(model.material(model.file().meshes().get(0)));
        assertEquals(List.of(
                "t:inline.obj: usemtl 'missing' is not in [t:gone.mtl], so its faces draw the particle texture",
                "t:inline.obj: material library t:gone.mtl doesn't exist"), model.warnings());
    }

    @Test
    void unknownStatementsWarnOnceAndSkippedOnesDont() {
        ObjModel model = text("v 0 0 0\nv 1 0 0\nv 1 1 0\ns 1\nl 1 2\nfoo 1\nfoo 2\nf 1 2 3\n");
        assertEquals(List.of("t:inline.obj line 6: unknown statement 'foo' is ignored"), model.warnings());
        assertEquals(List.of("t:inline.obj: no faces, so the model draws nothing"), text("v 0 0 0\n").warnings());
    }

    @Test
    void materialLibraryReadsColoursMapsAndTint() {
        Map<String, ObjMaterial> lib = Mtl.parse("t:x.mtl", source("t:obj/cube.mtl"));
        assertEquals(1f, lib.get("glow").ambient());
        assertEquals("examplemod:block/lamp_glow", lib.get("glow").texture());
        assertEquals(0.8f, lib.get("stone").color()[0]);
        assertEquals(0f, lib.get("stone").ambient());
        Map<String, ObjMaterial> more = Mtl.parse("t:y.mtl",
                "newmtl a\nKd 0.5\nd 0.25\nbetafront_TintIndex 2\nmap_Kd -s 1 1 1 t:block/a\nnewmtl b\nTr 0.25\n");
        assertArrayEquals(new float[] {0.5f, 0.5f, 0.5f}, more.get("a").color());
        assertEquals(0.25f, more.get("a").alpha());
        assertEquals(2, more.get("a").tintIndex());
        assertEquals("t:block/a", more.get("a").texture());
        assertEquals(0.75f, more.get("b").alpha());
        assertEquals(-1, more.get("b").tintIndex());
    }

    @Test
    void linesJoinContinuationsAndKeepHashArguments() {
        List<ObjLines.Line> lines = ObjLines.split("# comment\n  v 1 2 \\\n 3\n\nmap_Kd #texture0\r\n");
        assertEquals(2, lines.size());
        assertArrayEquals(new String[] {"v", "1", "2", "3"}, lines.get(0).tokens());
        assertEquals(2, lines.get(0).number());
        assertArrayEquals(new String[] {"map_Kd", "#texture0"}, lines.get(1).tokens());
        assertEquals(5, lines.get(1).number());
    }

    @Test
    void rampCullsItsOuterFaces() {
        ObjModel ramp = load("t:obj/obj_ramp.obj");
        assertEquals(List.of("t:obj/obj_ramp.obj", "t:obj/obj_ramp.mtl"), read);
        assertEquals("#ramp", ramp.material(ramp.file().meshes().get(0)).texture());
        List<Side> sides = new ArrayList<>();
        for (ObjQuad quad : ramp.quads(false))
            sides.add(quad.cullSide());
        // bottom, back, slope (not on the boundary), west and east triangles
        assertEquals(Arrays.asList(Side.DOWN, Side.NORTH, null, Side.WEST, Side.EAST), sides);
    }

    @Test
    void blenderCrystalWithoutItsMtlUsesTheTextureVariable() {
        ObjModel crystal = load("t:obj/example_crystal.obj");
        assertEquals(1, crystal.file().meshes().size());
        assertEquals("#texture0", crystal.material(crystal.file().meshes().get(0)).texture());
        assertEquals(45, crystal.file().meshes().get(0).faces().size());
        assertEquals(45, crystal.quads(false).size());
        assertEquals(List.of(), crystal.warnings());
    }

    @Test
    void siblingFilesResolveNextToTheObj() {
        assertEquals("ns:models/block/x.mtl", ObjFile.sibling("ns:models/block/x.obj", "x.mtl"));
        assertEquals("ns:x.mtl", ObjFile.sibling("ns:x.obj", "x.mtl"));
        assertEquals("o:y.mtl", ObjFile.sibling("ns:models/x.obj", "o:y.mtl"));
    }
}
