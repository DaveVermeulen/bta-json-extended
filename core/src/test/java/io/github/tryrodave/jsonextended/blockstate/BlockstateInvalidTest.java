package io.github.tryrodave.jsonextended.blockstate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Every file in {@code blockstate/invalid/} fails to load with a message naming the file, the key and the reason. */
class BlockstateInvalidTest {
    private static final Path BLOCKSTATE = BlockstateCasesTest.RESOURCES.resolve("blockstate");

    @TestFactory
    Stream<DynamicTest> everyInvalidFile() throws IOException {
        Map<String, Object> table = BlockstateCasesTest.map(Json.parse(Files.readString(BLOCKSTATE.resolve("cases/invalid.json"))));
        List<DynamicTest> tests = new ArrayList<>();
        List<String> listed = new ArrayList<>();
        for (Object o : BlockstateCasesTest.list(table.get("cases"))) {
            Map<String, Object> c = BlockstateCasesTest.map(o);
            String file = (String) c.get("file"), reason = (String) c.get("error");
            listed.add(file);
            tests.add(DynamicTest.dynamicTest(file, () -> {
                BlockstateException e = assertThrows(BlockstateException.class,
                        () -> Blockstate.parse(file, Files.readString(BLOCKSTATE.resolve(file))));
                assertTrue(e.getMessage().startsWith(file + ": "), e.getMessage());
                assertTrue(e.getMessage().contains(reason), () -> "expected '" + reason + "' in: " + e.getMessage());
            }));
        }
        try (Stream<Path> files = Files.list(BLOCKSTATE.resolve("invalid"))) {
            for (Path f : files.toList())
                assertTrue(listed.contains("invalid/" + f.getFileName()), f + " has no entry in cases/invalid.json");
        }
        return tests.stream();
    }

    @Test
    void messagesNameTheKey() {
        assertMessage("{\"variants\": {\"\": {\"model\": \"a:b\", \"y\": 45}}}", "x.json: variants.\"\".y: y must be 0, 90, 180 or 270");
        assertMessage("{\"multipart\": [{\"when\": {\"open\": \"true\"}, \"apply\": {\"model\": \"a:b\"}}]}",
                "x.json: multipart[0].when.open: condition names property 'open', which the file doesn't declare");
        assertMessage("{\"variants\": {\"\": {\"model\": \"a:b\", \"modle\": \"a:c\"}}}",
                "x.json: variants.\"\".modle: unknown key 'modle'; expected one of [model, uvlock, weight, x, y]");
        assertMessage("{\"variants\": {\"\": {\"model\": \"a:b\"}},}", "x.json: invalid JSON at line 1, column 37: expected a key in quotes");
        assertMessage("{\"multipart\": [{\"when\": {}}]}", "x.json: multipart[0]: every multipart entry needs apply");
    }

    @Test
    void acceptsUnquotedBooleansAndNumbers() {
        Blockstate b = Blockstate.parse("x.json", "{\"properties\": {\"lit\": {\"bits\": 3, \"values\": [false, true]}},"
                + "\"multipart\": [{\"when\": {\"lit\": true}, \"apply\": {\"model\": \"a:b\"}}]}");
        assertEquals(1, b.select(8).choices().size());
        assertEquals(0, b.select(0).choices().size());
    }

    private static void assertMessage(String json, String message) {
        assertEquals(message, assertThrows(BlockstateException.class, () -> Blockstate.parse("x.json", json)).getMessage());
    }
}
