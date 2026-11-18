package io.github.tryrodave.jsonextended.blockstate;

import io.github.tryrodave.jsonextended.json.Json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs every expected-results table in {@code blockstate/cases/}: meta and resolver values in, decoded properties
 * and the parts drawn (with BTA quarter turns) out.
 */
class BlockstateCasesTest {
    static final Path RESOURCES = Path.of("src/test/resources");
    static final Path CASES = RESOURCES.resolve("blockstate/cases");

    @TestFactory
    Stream<DynamicTest> everyCaseTable() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        try (Stream<Path> files = Files.list(CASES)) {
            for (Path file : files.sorted().toList()) {
                if (file.getFileName().toString().equals("invalid.json"))
                    continue;
                Map<String, Object> table = map(Json.parse(Files.readString(file)));
                String name = (String) table.get("blockstate");
                Blockstate blockstate = load(name);
                List<Object> cases = list(table.get("cases"));
                for (int i = 0; i < cases.size(); i++) {
                    Map<String, Object> c = map(cases.get(i));
                    tests.add(DynamicTest.dynamicTest(file.getFileName() + " case " + i + " meta " + intOf(c.get("meta")),
                            () -> check(blockstate, c)));
                }
            }
        }
        assertTrue(tests.size() > 50, "expected the fixture tables to have many cases, got " + tests.size());
        return tests.stream();
    }

    private static void check(Blockstate blockstate, Map<String, Object> c) {
        int meta = intOf(c.get("meta"));
        Map<String, String> resolved = strings(c.getOrDefault("resolved", Map.of()));
        Selection selection = blockstate.select(meta, resolved);

        if ("missing".equals(c.get("parts"))) {
            assertTrue(selection.missing(), "expected the missing model, got " + selection);
            assertEquals(c.get("error"), selection.error());
            return;
        }
        assertTrue(!selection.missing(), () -> "unexpected missing model: " + selection.error());
        assertEquals(strings(c.get("properties")), selection.properties());

        if (c.containsKey("candidates")) {
            assertEquals(1, selection.choices().size(), "a weighted variant is one choice");
            List<Object> expected = list(c.get("candidates"));
            List<Part> options = selection.choices().get(0).options();
            assertEquals(expected.size(), options.size());
            for (int i = 0; i < expected.size(); i++) {
                Map<String, Object> e = map(expected.get(i));
                assertPart(e, options.get(i));
                assertEquals(intOf(e.get("weight")), options.get(i).weight());
            }
            return;
        }
        List<Object> expected = list(c.get("parts"));
        List<Choice> choices = selection.choices();
        assertEquals(expected.size(), choices.size(), () -> "parts drawn: " + choices);
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(1, choices.get(i).options().size());
            assertPart(map(expected.get(i)), choices.get(i).first());
        }
    }

    private static void assertPart(Map<String, Object> e, Part part) {
        assertEquals(e.get("model"), part.model());
        assertEquals(intOf(e.get("x")), part.x(), "x");
        assertEquals(intOf(e.get("y")), part.y(), "y");
        assertEquals(e.get("uvlock"), part.uvlock(), "uvlock");
        List<Object> bta = list(e.get("bta"));
        assertEquals(List.of(intOf(bta.get(0)), intOf(bta.get(1)), intOf(bta.get(2))),
                List.of(part.btaRotX(), part.btaRotY(), part.btaRotZ()), "BTA quarter turns of " + part);
    }

    @Test
    void selectionsAreCachedPerState() throws IOException {
        Blockstate stairs = load("jsonextended:test_stairs");
        Selection first = stairs.select(5, Map.of("shape", "outer_left"));
        assertSame(first, stairs.select(5, Map.of("shape", "outer_left")));
        assertSame(first, stairs.select(5 | 16, Map.of("shape", "outer_left")), "only 4 meta bits count");
        assertTrue(first != stairs.select(5, Map.of("shape", "outer_right")));
        Blockstate slab = load("jsonextended:test_slab");
        assertSame(slab.select(1), slab.select(1));
    }

    @Test
    void missingStatesWarnOnce() throws IOException {
        Blockstate slab = load("jsonextended:test_slab");
        List<String> lines = new ArrayList<>();
        slab.onWarning(lines::add);
        slab.select(3);
        slab.select(3);
        slab.select(7);
        assertEquals(List.of("jsonextended:test_slab: type index 3 has no value"), lines);
    }

    @Test
    void badResolverValuesDrawTheMissingModel() throws IOException {
        Blockstate stairs = load("jsonextended:test_stairs");
        List<String> lines = new ArrayList<>();
        stairs.onWarning(lines::add);
        assertTrue(stairs.select(0, Map.of()).missing());
        assertTrue(stairs.select(0, Map.of("shape", "spiral")).missing());
        assertEquals(List.of("jsonextended:test_stairs: property 'shape' has no bits and no StateResolver value",
                "jsonextended:test_stairs: 'spiral' is not one of shape's values"), lines);
    }

    @Test
    void listsModelsAndResolverProperties() throws IOException {
        Blockstate stairs = load("jsonextended:test_stairs");
        assertEquals(List.of("jsonextended:block/test_stairs", "jsonextended:block/test_stairs_inner",
                "jsonextended:block/test_stairs_outer"), stairs.models());
        assertEquals(List.of("shape"), stairs.resolverProperties().stream().map(Property::name).toList());
        assertTrue(!load("jsonextended:test_slab").needsResolver());
        assertEquals(4, load("jsonextended:test_fence").resolverProperties().size());
    }

    /** {@code ns:name} is a dev block's blockstate; anything else a path under {@code blockstate/}. */
    static Blockstate load(String name) throws IOException {
        Path path;
        int colon = name.indexOf(':');
        if (colon >= 0)
            path = RESOURCES.resolve("assets/" + name.substring(0, colon) + "/blockstates/" + name.substring(colon + 1) + ".json");
        else
            path = RESOURCES.resolve("blockstate/" + name);
        return Blockstate.parse(name, Files.readString(path));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object o) {
        return (List<Object>) o;
    }

    static int intOf(Object o) {
        return ((Double) o).intValue();
    }

    static Map<String, String> strings(Object o) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map(o).entrySet())
            out.put(e.getKey(), (String) e.getValue());
        return out;
    }
}
