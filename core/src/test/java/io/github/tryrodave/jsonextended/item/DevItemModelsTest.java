package io.github.tryrodave.jsonextended.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Resolves the dev test items' models from {@code bta/} the way the mod does, and checks their textures exist. */
class DevItemModelsTest {
    private static final Path ASSETS = Path.of("../bta/src/main/resources/assets");
    private final ItemModels models = new ItemModels(DevItemModelsTest::read);

    private static String read(String id) {
        int colon = id.indexOf(':');
        Path file = ASSETS.resolve(id.substring(0, colon) + "/models/" + id.substring(colon + 1) + ".json");
        try {
            return Files.exists(file) ? Files.readString(file) : null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void devItemsResolve() {
        assumeTrue(Files.isDirectory(ASSETS), "bta/ is not next to core/");
        ResolvedItemModel ruby = models.resolve("jsonextended:item/test_ruby");
        assertEquals(List.of("jsonextended:item/test_ruby"), ruby.layers());
        assertEquals(ResolvedItemModel.Style.HANDHELD, models.resolve("jsonextended:item/test_sword").style());
        ResolvedItemModel display = models.resolve("jsonextended:item/test_display");
        assertEquals(45, display.display("gui").rotZ());
        assertEquals(1.5, display.display("head").scaleX(), "head pose comes from the parent file");
        assertEquals(List.of("jsonextended:item/test_ruby", "jsonextended:item/test_ring"),
                models.resolve("jsonextended:item/test_layered").layers());
        ResolvedItemModel gem = models.resolve("jsonextended:item/test_gem");
        assertEquals(ResolvedItemModel.Style.MODEL, gem.style());
        assertTrue(gem.warnings().isEmpty(), () -> gem.warnings().toString());
        assertThrows(ItemModelException.class, () -> models.resolve("jsonextended:item/test_broken"));
        for (String id : List.of("test_ruby", "test_sword", "test_display", "test_layered", "test_gem"))
            for (String texture : models.resolve("jsonextended:item/" + id).textures().values()) {
                String path = texture.substring(texture.indexOf(':') + 1);
                assertTrue(Files.exists(ASSETS.resolve("jsonextended/textures/" + path + ".png")), id + " names missing texture " + texture);
            }
    }
}
