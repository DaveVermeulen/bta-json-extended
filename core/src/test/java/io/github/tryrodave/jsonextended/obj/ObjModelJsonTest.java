package io.github.tryrodave.jsonextended.obj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ObjModelJsonTest {
    /** Betafront's crystal model JSON, as shipped. */
    private static final String CRYSTAL = """
            {
              "loader": "betafront:obj",
              "model": "betafront:models/block/example_crystal.obj",
              "flip_v": true,
              "textures": {
                "particle": "betafront:block/example_crystal",
                "texture0": "betafront:block/example_crystal"
              }
            }""";

    @Test
    void betafrontModelJsonReads() {
        ObjModelJson json = ObjModelJson.parse("t:block/crystal", CRYSTAL);
        assertEquals("betafront:models/block/example_crystal.obj", json.model());
        assertTrue(json.flipV());
        assertTrue(json.automaticCulling() && json.shadeQuads() && json.emissiveAmbient() && json.ambientOcclusion());
        assertNull(json.mtlOverride());
        assertEquals("betafront:block/example_crystal", json.texture("#texture0"));
        assertEquals("betafront:block/example_crystal", json.particle());
        assertEquals(List.of(), json.warnings());
        assertTrue(ObjModelJson.isObj(CRYSTAL));
    }

    @Test
    void loaderMayBeAnObjectWithId() {
        assertTrue(ObjModelJson.isObj("{\"loader\": {\"id\": \"neoforge:obj\"}, \"model\": \"a:b.obj\"}"));
        assertFalse(ObjModelJson.isObj("{\"loader\": \"other:thing\"}"));
        assertFalse(ObjModelJson.isObj("{broken"));
        assertFalse(ObjModelJson.isObj("{\"elements\": []}"));
    }

    @Test
    void texturesFollowVariablesAndStopAtLoops() {
        ObjModelJson json = ObjModelJson.parse("t:x", "{\"loader\": \"jsonextended:obj\", \"model\": \"t:x.obj\","
                + "\"textures\": {\"a\": \"#b\", \"b\": \"t:block/b\", \"c\": \"#d\", \"d\": \"#c\"},"
                + "\"render_type\": \"minecraft:cutout\", \"flip_v\": false}");
        assertEquals("t:block/b", json.texture("#a"));
        assertEquals("t:block/direct", json.texture("t:block/direct"));
        assertNull(json.texture("#c"));
        assertNull(json.texture("#missing"));
        assertNull(json.particle());
        assertEquals("cutout", json.renderType());
    }

    @Test
    void errorsNameModelAndKey() {
        assertEquals("t:x: model: needs the OBJ file, e.g. \"ns:models/block/name.obj\"",
                assertThrows(ObjException.class, () -> ObjModelJson.parse("t:x", "{\"loader\": \"forge:obj\"}")).getMessage());
        assertEquals("t:x: flip_v: expected true or false",
                assertThrows(ObjException.class, () -> ObjModelJson.parse("t:x",
                        "{\"loader\": \"forge:obj\", \"model\": \"t:x.obj\", \"flip_v\": 1}")).getMessage());
        assertTrue(assertThrows(ObjException.class, () -> ObjModelJson.parse("t:x", "{\"loader\": \"x\"}"))
                .getMessage().startsWith("t:x: loader: 'x' is not one of"));
        assertTrue(assertThrows(ObjException.class, () -> ObjModelJson.parse("t:x", "{"))
                .getMessage().startsWith("t:x: invalid JSON at line 1"));
    }

    @Test
    void unknownKeysAndParentWarn() {
        ObjModelJson json = ObjModelJson.parse("t:x", "{\"loader\": \"forge:obj\", \"model\": \"t:x.ob\","
                + "\"parent\": \"block/block\", \"elements\": []}");
        assertEquals(List.of("t:x: elements: unknown key 'elements' is ignored",
                "t:x: parent: OBJ models don't inherit from a parent; it is ignored",
                "t:x: model: 't:x.ob' doesn't end in .obj"), json.warnings());
    }
}
