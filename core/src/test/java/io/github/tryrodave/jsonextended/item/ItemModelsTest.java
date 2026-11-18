package io.github.tryrodave.jsonextended.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemModelsTest {
    private final Map<String, String> files = new HashMap<>();
    private final ItemModels models = new ItemModels(files::get);

    private ResolvedItemModel resolve(String id) {
        return models.resolve(id);
    }

    @Test
    void generatedLayers() {
        files.put("t:item/ruby", "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"t:item/ruby\", \"layer1\": \"t:item/ruby_glint\"}}");
        ResolvedItemModel m = resolve("t:item/ruby");
        assertEquals(ResolvedItemModel.Style.GENERATED, m.style());
        assertEquals(List.of("t:item/ruby", "t:item/ruby_glint"), m.layers());
        assertEquals("front", m.guiLight());
        assertNull(m.display("gui"), "no display entry: the renderer uses BTA's default");
    }

    @Test
    void handheldAndRod() {
        files.put("t:item/sword", "{\"parent\": \"minecraft:item/handheld\", \"textures\": {\"layer0\": \"t:item/sword\"}}");
        files.put("t:item/rod", "{\"parent\": \"item/handheld_rod\", \"textures\": {\"layer0\": \"t:item/rod\"}}");
        assertEquals(ResolvedItemModel.Style.HANDHELD, resolve("t:item/sword").style());
        assertEquals(ResolvedItemModel.Style.HANDHELD_ROD, resolve("t:item/rod").style());
    }

    @Test
    void childInheritsStyleTexturesAndDisplayFromParents() {
        files.put("t:item/base_tool", "{\"parent\": \"item/handheld\", \"textures\": {\"layer0\": \"#tool\"},"
                + "\"display\": {\"gui\": {\"rotation\": [0, 0, 45]}, \"ground\": {\"scale\": [0.5, 0.5, 0.5]}}}");
        files.put("t:item/pick", "{\"parent\": \"t:item/base_tool\", \"textures\": {\"tool\": \"pick\"},"
                + "\"display\": {\"gui\": {\"rotation\": [0, 0, 90]}}}");
        ResolvedItemModel m = resolve("t:item/pick");
        assertEquals(ResolvedItemModel.Style.HANDHELD, m.style());
        assertEquals(List.of("minecraft:pick"), m.layers(), "#tool followed, minecraft: added");
        assertEquals(90, m.display("gui").rotZ(), "child's gui wins");
        assertEquals(0.5, m.display("ground").scaleX(), "parent's ground kept");
    }

    @Test
    void elementsMakeA3dModel() {
        files.put("t:item/gem", "{\"credit\": \"Made with Blockbench\", \"texture_size\": [32, 32],"
                + "\"textures\": {\"0\": \"t:block/gem\", \"particle\": \"#0\"},"
                + "\"elements\": [{\"from\": [6, 0, 6], \"to\": [10, 8, 10], \"faces\": {}}],"
                + "\"display\": {\"thirdperson_righthand\": {\"rotation\": [75, 45, 0], \"translation\": [0, 2.5, 0], \"scale\": [0.375, 0.375, 0.375]}},"
                + "\"groups\": [0]}");
        files.put("t:item/gem_big", "{\"parent\": \"t:item/gem\", \"display\": {\"gui\": {\"scale\": [2, 2, 2]}}}");
        ResolvedItemModel m = resolve("t:item/gem_big");
        assertEquals(ResolvedItemModel.Style.MODEL, m.style());
        assertEquals("t:item/gem", m.elementsFrom());
        assertEquals("side", m.guiLight());
        assertEquals(List.of(), m.layers());
        assertEquals("t:block/gem", m.textures().get("particle"));
        assertTrue(m.warnings().isEmpty(), () -> "Blockbench keys are fine: " + m.warnings());
        DisplayTransform left = m.display("thirdperson_lefthand");
        assertEquals(m.display("thirdperson_righthand"), left, "left hand falls back to right hand");
    }

    @Test
    void guiLightFromNearestFile() {
        files.put("t:item/a", "{\"parent\": \"item/generated\", \"gui_light\": \"side\", \"textures\": {\"layer0\": \"t:item/a\"}}");
        files.put("t:item/b", "{\"parent\": \"t:item/a\"}");
        files.put("t:item/c", "{\"parent\": \"t:item/b\", \"gui_light\": \"front\"}");
        assertEquals("side", resolve("t:item/b").guiLight());
        assertEquals("front", resolve("t:item/c").guiLight());
    }

    @Test
    void packCanReplaceABuiltInParent() {
        files.put("minecraft:item/generated", "{\"parent\": \"builtin/generated\", \"display\": {\"head\": {\"translation\": [0, 13, 7]}}}");
        files.put("t:item/x", "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"t:item/x\"}}");
        ResolvedItemModel m = resolve("t:item/x");
        assertEquals(ResolvedItemModel.Style.GENERATED, m.style());
        assertEquals(13, m.display("head").transY());
    }

    @Test
    void warningsForIgnoredKeys() {
        files.put("t:item/x", "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"t:item/x\", \"layer2\": \"t:item/y\"},"
                + "\"overrides\": [], \"display\": {\"offhand\": {}}}");
        List<String> w = resolve("t:item/x").warnings();
        assertEquals(List.of("t:item/x: overrides: overrides are not supported yet and are ignored",
                "t:item/x: display.offhand: unknown display context 'offhand' is ignored",
                "t:item/x: textures: layer1 is missing, so the layers after it are ignored"), w);
    }

    @Test
    void errors() {
        assertError("t:item/none: no model file", "t:item/none");
        files.put("t:item/orphan", "{\"parent\": \"t:item/gone\", \"textures\": {\"layer0\": \"t:item/o\"}}");
        assertError("t:item/orphan: parent: parent t:item/gone doesn't exist", "t:item/orphan");
        files.put("t:item/a", "{\"parent\": \"t:item/b\"}");
        files.put("t:item/b", "{\"parent\": \"t:item/a\"}");
        assertError("t:item/a: parent loop through t:item/a", "t:item/a");
        files.put("t:item/nolayer", "{\"parent\": \"item/generated\", \"textures\": {\"layer1\": \"t:item/x\"}}");
        assertError("t:item/nolayer: textures.layer0: a generated item needs a layer0 texture", "t:item/nolayer");
        files.put("t:item/flat", "{\"textures\": {\"layer0\": \"t:item/x\"}}");
        assertError("t:item/flat: neither elements nor a generated parent (e.g. \"parent\": \"item/generated\")", "t:item/flat");
        files.put("t:item/ref", "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"#missing\"}}");
        assertError("t:item/ref: textures.layer0: #missing doesn't point to a texture", "t:item/ref");
        files.put("t:item/selfref", "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"#layer0\"}}");
        assertError("t:item/selfref: textures.layer0: #layer0 doesn't point to a texture", "t:item/selfref");
        files.put("t:item/light", "{\"parent\": \"item/generated\", \"gui_light\": \"top\", \"textures\": {\"layer0\": \"t:item/x\"}}");
        assertError("t:item/light: gui_light: gui_light is \"front\" or \"side\"", "t:item/light");
        files.put("t:item/scale", "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"t:item/x\"}, \"display\": {\"gui\": {\"scale\": [1, 1]}}}");
        assertError("t:item/scale: display.gui.scale: scale is a list of three numbers", "t:item/scale");
        files.put("t:item/json", "{\"parent\": }");
        assertTrue(assertThrows(ItemModelException.class, () -> resolve("t:item/json")).getMessage().startsWith("t:item/json: invalid JSON"));
    }

    @Test
    void clampsLikeVanilla() {
        DisplayTransform t = new DisplayTransform(0, 0, 0, 100, -100, 5, 9, 1, -9).clamped();
        assertEquals(new DisplayTransform(0, 0, 0, 80, -80, 5, 4, 1, -4), t);
    }

    private void assertError(String message, String id) {
        assertEquals(message, assertThrows(ItemModelException.class, () -> resolve(id)).getMessage());
    }
}
