package io.github.tryrodave.jsonextended.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tryrodave.jsonextended.pose.ModelQuad;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ElementQuadsTest {
    private final Map<String, String> files = new HashMap<>();

    private List<ModelQuad> bake(String id) {
        return ElementQuads.bake(ResolvedModel.load(id, files::get));
    }

    @Test
    void fullCubeHasVanillaCornersUvsAndNormals() {
        files.put("t:block/cube", "{\"textures\": {\"all\": \"t:block/stone\"}, \"elements\": [{\"from\": [0, 0, 0], \"to\": [16, 16, 16],"
                + "\"faces\": {\"down\": {\"texture\": \"#all\", \"cullface\": \"bottom\"}, \"up\": {\"texture\": \"#all\"},"
                + "\"north\": {\"texture\": \"#all\"}, \"south\": {\"texture\": \"#all\"}, \"west\": {\"texture\": \"#all\"},"
                + "\"east\": {\"texture\": \"#all\", \"tintindex\": 0}}}]}");
        List<ModelQuad> quads = bake("t:block/cube");
        assertEquals(6, quads.size());
        ModelQuad north = quads.get(2);
        assertArrayEquals(new float[] {1, 1, 0}, north.positions()[0]);
        assertArrayEquals(new float[] {0, 0, -1}, north.normal());
        // Default UVs cover the whole texture; vertex 0 takes the top-left corner.
        assertArrayEquals(new float[] {0, 0}, north.uvs()[0]);
        assertArrayEquals(new float[] {1, 1}, north.uvs()[2]);
        assertEquals("t:block/stone", north.texture());
        assertEquals("down", quads.get(0).cullFace());
        assertEquals(0, quads.get(5).tintIndex());
        assertEquals(-1, north.tintIndex());
    }

    @Test
    void defaultUvsFollowTheElement() {
        assertArrayEquals(new float[] {2, 6, 4, 16}, ElementQuads.defaultUv("north", new float[] {12, 0, 0}, new float[] {14, 10, 2}));
        assertArrayEquals(new float[] {12, 0, 14, 2}, ElementQuads.defaultUv("up", new float[] {12, 0, 0}, new float[] {14, 10, 2}));
    }

    @Test
    void faceRotationShiftsTheUvCorners() {
        files.put("t:block/r", "{\"elements\": [{\"from\": [0, 0, 0], \"to\": [16, 16, 16],"
                + "\"faces\": {\"up\": {\"texture\": \"#a\", \"uv\": [0, 0, 8, 8], \"rotation\": 90}}}]}");
        ModelQuad up = bake("t:block/r").get(0);
        assertArrayEquals(new float[] {0, 0.5f}, up.uvs()[0]);
        assertArrayEquals(new float[] {0, 0}, up.uvs()[3]);
        assertNull(up.texture());
    }

    @Test
    void elementRotationTurnsAboutTheOriginAndRescales() {
        files.put("t:block/x", "{\"elements\": [{\"from\": [0, 0, 8], \"to\": [16, 16, 8],"
                + "\"rotation\": {\"origin\": [8, 8, 8], \"axis\": \"y\", \"angle\": 45, \"rescale\": true},"
                + "\"faces\": {\"north\": {\"texture\": \"#a\"}}}]}");
        ModelQuad north = bake("t:block/x").get(0);
        // (16, y, 8) relative to the origin is (+0.5, 0, 0); 45 about Y and rescaled by sqrt 2: (+0.5, 0, -0.5).
        assertArrayEquals(new float[] {1, 1, 0}, north.positions()[0], 1e-6f);
        float s = (float) Math.sqrt(0.5);
        assertArrayEquals(new float[] {-s, 0, -s}, north.normal(), 1e-6f);
    }

    @Test
    void parentsGiveTexturesAndElements() {
        files.put("t:block/parent", "{\"elements\": [{\"from\": [0, 0, 0], \"to\": [16, 8, 16], \"faces\": {\"up\": {\"texture\": \"#top\"}}}],"
                + "\"textures\": {\"top\": \"#side\", \"side\": \"t:block/old\"}}");
        files.put("t:block/child", "{\"parent\": \"t:block/parent\", \"textures\": {\"side\": \"t:block/new\"}}");
        ResolvedModel child = ResolvedModel.load("t:block/child", files::get);
        assertEquals(1, child.elements().size());
        assertEquals("t:block/new", ElementQuads.bake(child).get(0).texture());
        assertThrows(IllegalArgumentException.class, () -> ResolvedModel.load("t:block/none", files::get));
    }
}
