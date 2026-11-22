package io.github.tryrodave.jsonextended.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ModelCheckTest {
    private static final Path ASSETS = Path.of("../bta/src/main/resources/assets");
    private final Map<String, String> files = new HashMap<>();
    private final ModelCheck check = new ModelCheck(files::get);

    private static final String CUBE = "{\"from\": [0, 0, 0], \"to\": [16, 16, 16], \"faces\": {\"north\": {\"uv\": [0, 0, 16, 16], \"texture\": \"#all\"}}}";

    private List<String> messages(String id) {
        return check.check(id).stream().map(ModelCheck.Problem::toString).toList();
    }

    @Test
    void cleanModelHasNoProblems() {
        files.put("t:block/base", "{\"textures\": {\"particle\": \"#all\"}, \"elements\": [" + CUBE + "]}");
        files.put("t:block/stone", "{\"parent\": \"t:block/base\", \"textures\": {\"all\": \"t:block/stone\"}}");
        assertEquals(List.of(), messages("t:block/stone"));
    }

    @Test
    void unsetTextureVariable() {
        files.put("t:block/x", "{\"elements\": [" + CUBE + "]}");
        assertEquals(List.of("error: t:block/x: elements[0].faces.north.texture: #all is never set in textures (here or in a parent), so BTA draws its default texture"),
                messages("t:block/x"));
    }

    @Test
    void missingParentAndLoop() {
        files.put("t:block/x", "{\"parent\": \"t:block/gone\", \"textures\": {\"all\": \"t:block/a\"}}");
        assertEquals(List.of("error: t:block/x: parent: parent t:block/gone doesn't exist (BTA ignores it silently)"), messages("t:block/x"));
        files.put("t:block/a", "{\"parent\": \"t:block/b\"}");
        files.put("t:block/b", "{\"parent\": \"t:block/a\"}");
        assertEquals(List.of("error: t:block/b: parent: parent loop through t:block/a"), messages("t:block/a"));
    }

    @Test
    void blockbenchKeysAndRenderType() {
        files.put("t:block/x", "{\"credit\": \"Made with Blockbench\", \"texture_size\": [32, 32], \"render_type\": \"minecraft:cutout\","
                + "\"textures\": {\"all\": \"t:block/a\"}, \"elements\": [" + CUBE.replace("\"faces\"", "\"name\": \"cube\", \"faces\"") + "],"
                + "\"groups\": []}");
        assertEquals(List.of(), messages("t:block/x"));
        files.put("t:block/y", "{\"render_type\": \"glowing\", \"textures\": {\"all\": \"t:block/a\"}, \"elements\": [" + CUBE + "]}");
        assertTrue(messages("t:block/y").get(0).startsWith("error: t:block/y: render_type: 'glowing' is not one of"));
    }

    @Test
    void elementMistakes() {
        files.put("t:block/x", "{\"textures\": {\"all\": \"t:block/a\"}, \"elements\": ["
                + "{\"from\": [8, 0, 0], \"to\": [4, 16, 16], \"rotation\": {\"angle\": 30, \"axis\": \"w\", \"origin\": [8, 8, 8]},"
                + " \"faces\": {\"front\": {\"texture\": \"#all\"}, \"up\": {\"texture\": \"#all\", \"rotation\": 45, \"cullface\": \"sky\"}}}]}");
        assertEquals(List.of(
                "error: t:block/x: elements[0]: from is larger than to on the x axis",
                "error: t:block/x: elements[0].rotation.axis: axis is \"x\", \"y\" or \"z\"",
                "error: t:block/x: elements[0].faces.front: a face is north, south, east, west, up or down",
                "error: t:block/x: elements[0].faces.up.rotation: face rotation is 0, 90, 180 or 270",
                "error: t:block/x: elements[0].faces.up.cullface: 'sky' is not a side"), messages("t:block/x"));
    }

    @Test
    void unknownKeysAndNothingToDraw() {
        files.put("t:block/x", "{\"textures\": {\"all\": \"t:block/a\"}, \"ambient_occlusion\": false}");
        assertEquals(List.of("warning: t:block/x: ambient_occlusion: unknown key 'ambient_occlusion' is ignored",
                "warning: t:block/x: no elements here or in any parent, so the model draws nothing"), messages("t:block/x"));
    }

    @Test
    void itemModelsWithBuiltInParents() {
        files.put("t:item/ruby", "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"t:item/ruby\"}}");
        assertEquals(List.of(), messages("t:item/ruby"));
    }

    @Test
    void renderTypeFromNearestFile() {
        files.put("t:block/glass_base", "{\"render_type\": \"minecraft:translucent\", \"textures\": {\"all\": \"t:block/a\"}, \"elements\": [" + CUBE + "]}");
        files.put("t:block/glass", "{\"parent\": \"t:block/glass_base\"}");
        files.put("t:block/leaf", "{\"parent\": \"t:block/glass_base\", \"render_type\": \"cutout_mipped\"}");
        assertEquals("translucent", check.renderType("t:block/glass"));
        assertEquals("cutout_mipped", check.renderType("t:block/leaf"));
        assertEquals(null, check.renderType("t:block/none"));
    }

    @Test
    void objModelsAreLeftToTheObjLoaderAndOtherLoadersWarn() {
        files.put("t:block/obj", "{\"loader\": \"jsonextended:obj\", \"model\": \"t:models/block/x.obj\"}");
        assertEquals(List.of(), messages("t:block/obj"));
        files.put("t:block/other", "{\"loader\": \"x:y\", \"textures\": {\"all\": \"t:block/a\"}, \"elements\": [" + CUBE + "]}");
        List<String> m = messages("t:block/other");
        assertTrue(m.get(0).startsWith("warning: t:block/other: loader: 'x:y' isn't a loader this mod knows"), m.toString());
    }

    @Test
    void unreadableOnlyForMissingOrBrokenFiles() {
        files.put("t:block/broken", "{\"elements\": [}");
        files.put("t:block/unset", "{\"elements\": [" + CUBE + "]}");
        assertTrue(ModelCheck.unreadable(check.check("t:block/broken"), "t:block/broken"));
        assertTrue(ModelCheck.unreadable(check.check("t:block/gone"), "t:block/gone"));
        List<ModelCheck.Problem> unset = check.check("t:block/unset");
        assertTrue(ModelCheck.hasErrors(unset));
        assertTrue(!ModelCheck.unreadable(unset, "t:block/unset"), "an unset #texture still draws, with BTA's default texture");
    }

    @Test
    void badJson() {
        files.put("t:block/x", "{\"elements\": [}");
        assertTrue(messages("t:block/x").get(0).startsWith("error: t:block/x: invalid JSON at line 1"));
    }

    /** Every model the dev client ships is clean: Blockbench exports, templates, blockstate and item test models. */
    @Test
    void devModelsAreClean() throws IOException {
        assumeTrue(Files.isDirectory(ASSETS), "bta/ is not next to core/");
        ModelCheck dev = new ModelCheck(ModelCheckTest::readDev);
        Path models = ASSETS.resolve("jsonextended/models");
        try (Stream<Path> walk = Files.walk(models)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".json")).toList()) {
                String rel = models.relativize(file).toString().replace('\\', '/');
                String id = "jsonextended:" + rel.substring(0, rel.length() - 5);
                // models/entity/ holds Bedrock geo files and their manifest, not model JSON.
                if (rel.startsWith("entity/"))
                    continue;
                if (id.equals("jsonextended:item/test_broken") || id.equals("jsonextended:block/test_broken_model"))
                    continue;
                List<ModelCheck.Problem> problems = dev.check(id).stream()
                        // Templates are only parents: their #variables are set by the models that use them.
                        .filter(p -> !(id.contains("/template/") && p.message().contains("is never set")))
                        .toList();
                assertEquals(List.of(), problems, id);
            }
        }
    }

    @Test
    void brokenDevModelIsUnreadable() {
        assumeTrue(Files.isDirectory(ASSETS), "bta/ is not next to core/");
        ModelCheck dev = new ModelCheck(ModelCheckTest::readDev);
        String id = "jsonextended:block/test_broken_model";
        assertTrue(ModelCheck.unreadable(dev.check(id), id), dev.check(id).toString());
        assertEquals("translucent", dev.renderType("jsonextended:block/test_glass"));
        assertEquals(null, dev.renderType("jsonextended:block/blockbench_test"));
    }

    private static String readDev(String id) {
        // In the game jar (knowledge/bta/vanilla-parents.md); vanilla's block/block only has display entries.
        if (id.equals("minecraft:block/block"))
            return "{\"display\": {}}";
        int colon = id.indexOf(':');
        Path file = ASSETS.resolve(id.substring(0, colon) + "/models/" + id.substring(colon + 1) + ".json");
        try {
            return Files.exists(file) ? Files.readString(file) : null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
