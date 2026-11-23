package io.github.tryrodave.jsonextended.gltf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.tryrodave.jsonextended.model.ModelCheck;
import io.github.tryrodave.jsonextended.obj.ObjQuad;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GltfModelJsonTest {
    private static final Path ASSETS = Path.of("../bta/src/main/resources/assets");

    @Test
    void parsesKeysAndDefaults() {
        GltfModelJson json = GltfModelJson.parse("t:block/x", """
                {"loader": {"id": "jsonextended:gltf"}, "model": "t:models/block/x.glb",
                 "offset": [0.5, 0, 0.5], "render_type": "minecraft:cutout",
                 "textures": {"particle": "#stone", "stone": "t:block/stone"}, "colour": 1}""");
        assertEquals("t:models/block/x.glb", json.model());
        assertArrayEquals(new float[] {0.5f, 0, 0.5f}, json.offset());
        assertEquals("cutout", json.renderType());
        assertEquals("t:block/stone", json.particle());
        assertFalse(json.flipV());
        assertTrue(json.automaticCulling() && json.shadeQuads() && json.emissiveAmbient() && json.ambientOcclusion());
        assertEquals(List.of("t:block/x: colour: unknown key 'colour' is ignored"), json.warnings());
        assertTrue(GltfModelJson.isGltf("{\"loader\": \"jsonextended:gltf\"}"));
        assertFalse(GltfModelJson.isGltf("{\"loader\": \"jsonextended:obj\"}"));
        assertFalse(GltfModelJson.isGltf("{broken"));
    }

    @Test
    void errorsNameTheKey() {
        assertEquals("t:x: model: needs the glTF file, e.g. \"ns:models/block/name.glb\"",
                assertThrows(GltfException.class, () -> GltfModelJson.parse("t:x", "{\"loader\": \"jsonextended:gltf\"}")).getMessage());
        assertEquals("t:x: offset: expected [x, y, z] in blocks",
                assertThrows(GltfException.class, () -> GltfModelJson.parse("t:x",
                        "{\"loader\": \"jsonextended:gltf\", \"model\": \"t:x.glb\", \"offset\": [1, 2]}")).getMessage());
        assertEquals("t:x: flip_v: expected true or false",
                assertThrows(GltfException.class, () -> GltfModelJson.parse("t:x",
                        "{\"loader\": \"jsonextended:gltf\", \"model\": \"t:x.glb\", \"flip_v\": 1}")).getMessage());
    }

    @Test
    void placeAddsOffsetAndFlipsV() {
        Gltf g = new Gltf();
        int pos = g.floats("VEC3", 0, 0, 0, 1, 0, 0, 0, 1, 0);
        int uv = g.floats("VEC2", 0, 0.25f, 1, 0, 0, 1);
        byte[] glb = g.glb("\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":" + pos + ",\"TEXCOORD_0\":" + uv
                + "}}]}],\"nodes\":[{\"mesh\":0}]");
        GltfModel model = GltfModel.load("t:m.glb", id -> glb);
        GltfModelJson json = GltfModelJson.parse("t:x", "{\"loader\": \"jsonextended:gltf\", \"model\": \"t:m.glb\","
                + " \"offset\": [0.5, 0, 0.5], \"flip_v\": true}");
        ObjQuad q = json.place(model.quads()).get(0);
        assertArrayEquals(new float[] {0.5f, 0, 0.5f}, q.positions()[0], 1e-6f);
        assertArrayEquals(new float[] {0, 0.75f}, q.uvs()[0], 1e-6f);
    }

    @Test
    void checkLeavesGltfModelsToTheirLoader() {
        String file = "{\"loader\": \"jsonextended:gltf\", \"model\": \"t:m.glb\"}";
        assertEquals(List.of(), new ModelCheck(id -> id.equals("t:block/x") ? file : null).check("t:block/x"));
    }

    @Test
    void devLanternLoadsWithoutWarnings() {
        assumeTrue(Files.isDirectory(ASSETS), "bta/ is not next to core/");
        String id = "jsonextended:block/test_glb_lantern";
        GltfModelJson json = GltfModelJson.parse(id, new String(dev("jsonextended:models/block/test_glb_lantern.json"),
                StandardCharsets.UTF_8));
        assertEquals(List.of(), json.warnings());
        GltfModel model = GltfModel.load(json.model(), GltfModelJsonTest::dev);
        assertEquals(List.of(), model.warnings());
        List<ObjQuad> quads = json.place(model.quads());
        assertEquals(List.of(), json.unsetMaterials(quads));
        // The base box merges to 6 quads; the gem's 8 triangles share no plane and stay triangles.
        assertEquals(14, quads.size());
        for (ObjQuad q : quads) {
            assertNotNull(json.texture(q.material().texture()), id + " has a face with no texture");
            for (float[] p : q.positions())
                for (float c : p)
                    assertTrue(c >= -1e-5f && c <= 1 + 1e-5f, id + " stays inside its block");
        }
        assertEquals(6, quads.stream().filter(q -> q.mesh().equals("base")).count());
        assertEquals(8, quads.stream().filter(q -> q.mesh().equals("gem")).count());
        assertEquals(1f, quads.stream().filter(q -> q.mesh().equals("gem")).findFirst().orElseThrow().material().ambient(), 1e-6f);
    }

    @Test
    void devAnimatedLanternPlaysInsideItsBlock() {
        assumeTrue(Files.isDirectory(ASSETS), "bta/ is not next to core/");
        String id = "jsonextended:block/test_glb_animated_lantern";
        GltfModelJson json = GltfModelJson.parse(id, new String(dev("jsonextended:models/block/test_glb_animated_lantern.json"),
                StandardCharsets.UTF_8));
        assertEquals(List.of(), json.warnings());
        GltfModel model = GltfModel.load(json.model(), GltfModelJsonTest::dev);
        assertEquals(List.of(), model.warnings());
        assertEquals(List.of("idle", "spin"), List.copyOf(model.animations().keySet()));
        assertEquals(4f, model.animations().get("idle").length(), 1e-6f);
        assertEquals(0.5f, model.animations().get("spin").length(), 1e-6f);
        for (GltfAnimation animation : model.animations().values())
            for (float t = 0; t <= animation.length(); t += animation.length() / 16)
                for (ObjQuad q : json.place(model.pose(animation.sample(t))))
                    for (float[] p : q.positions())
                        for (float c : p)
                            assertTrue(c >= -1e-4f && c <= 1 + 1e-4f, animation.name() + " at " + t + " leaves the block");
    }

    private static byte[] dev(String id) {
        Path file = ASSETS.resolve(id.substring(0, id.indexOf(':'))).resolve(id.substring(id.indexOf(':') + 1));
        try {
            return Files.exists(file) ? Files.readAllBytes(file) : null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
