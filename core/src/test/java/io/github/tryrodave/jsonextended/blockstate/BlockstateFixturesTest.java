package io.github.tryrodave.jsonextended.blockstate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Keeps the blockstate fixtures consistent until the phase 1 parser can load them for real:
 * the copies in core match the dev blocks' assets, and every model, parent and texture they name exists.
 * Plain text checks, so core needs no JSON library yet.
 */
class BlockstateFixturesTest {
    private static final Path FIXTURES = Path.of("src/test/resources/assets/jsonextended");
    private static final Path BLOCKSTATE_FIXTURES = Path.of("src/test/resources/blockstate");
    private static final Path DEV_ASSETS = Path.of("../bta/src/main/resources/assets/jsonextended");

    private static final Pattern MODEL_REF = Pattern.compile("\"model\"\\s*:\\s*\"jsonextended:block/([^\"]+)\"");
    private static final Pattern PARENT_REF = Pattern.compile("\"parent\"\\s*:\\s*\"jsonextended:block/([^\"]+)\"");
    private static final Pattern TEXTURE_REF = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"jsonextended:block/([^\"]+)\"");

    @Test
    void fixturesAreCopiesOfTheDevBlockAssets() throws IOException {
        assumeTrue(Files.isDirectory(DEV_ASSETS), "bta/ is not next to core/");
        for (Path fixture : files(FIXTURES, ".json")) {
            Path asset = DEV_ASSETS.resolve(FIXTURES.relativize(fixture).toString());
            assertTrue(Files.exists(asset), "no dev asset for fixture " + fixture);
            assertArrayEquals(Files.readAllBytes(asset), Files.readAllBytes(fixture),
                    fixture + " differs from " + asset + "; copy the dev asset over it");
        }
        for (Path asset : files(DEV_ASSETS.resolve("blockstates"), ".json")) {
            Path fixture = FIXTURES.resolve(DEV_ASSETS.relativize(asset).toString());
            assertTrue(Files.exists(fixture), "no core fixture for " + asset);
        }
    }

    @Test
    void everyModelNamedByABlockstateExists() throws IOException {
        List<Path> sources = new ArrayList<>(files(FIXTURES.resolve("blockstates"), ".json"));
        sources.addAll(files(BLOCKSTATE_FIXTURES.resolve("cases"), ".json"));
        sources.addAll(files(BLOCKSTATE_FIXTURES.resolve("conditions"), ".json"));
        for (Path source : sources)
            for (String model : matches(MODEL_REF, source, 1))
                assertTrue(Files.exists(model(model)), source + " names missing model jsonextended:block/" + model);
    }

    @Test
    void everyParentExists() throws IOException {
        for (Path model : files(FIXTURES.resolve("models"), ".json"))
            for (String parent : matches(PARENT_REF, model, 1))
                assertTrue(Files.exists(model(parent)), model + " names missing parent jsonextended:block/" + parent);
    }

    @Test
    void everyTextureExists() throws IOException {
        assumeTrue(Files.isDirectory(DEV_ASSETS), "bta/ is not next to core/");
        for (Path model : files(FIXTURES.resolve("models"), ".json")) {
            Matcher m = TEXTURE_REF.matcher(Files.readString(model));
            while (m.find()) {
                if (m.group(1).equals("parent") || m.group(1).equals("model"))
                    continue;
                Path png = DEV_ASSETS.resolve("textures/block/" + m.group(2) + ".png");
                assertTrue(Files.exists(png), model + " names missing texture jsonextended:block/" + m.group(2));
            }
        }
    }

    @Test
    void everyBlockstateHasACasesTable() throws IOException {
        for (Path blockstate : files(FIXTURES.resolve("blockstates"), ".json")) {
            Path cases = BLOCKSTATE_FIXTURES.resolve("cases").resolve(blockstate.getFileName().toString());
            assertTrue(Files.exists(cases), "no expected-results table for " + blockstate);
        }
    }

    private static Path model(String path) {
        return FIXTURES.resolve("models/block/" + path + ".json");
    }

    private static List<String> matches(Pattern pattern, Path file, int group) throws IOException {
        List<String> found = new ArrayList<>();
        Matcher m = pattern.matcher(Files.readString(file));
        while (m.find())
            found.add(m.group(group));
        return found;
    }

    private static List<Path> files(Path dir, String suffix) throws IOException {
        assertTrue(Files.isDirectory(dir), "missing fixture folder " + dir.toAbsolutePath());
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.toString().endsWith(suffix)).sorted().toList();
        }
    }
}
