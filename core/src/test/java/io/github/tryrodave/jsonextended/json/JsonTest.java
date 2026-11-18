package io.github.tryrodave.jsonextended.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {
    @Test
    void readsEveryKindOfValue() {
        Object v = Json.parse(" {\"a\": [1, -2.5e1, true, false, null], \"b\": \"q\\\"\\u00e9\\n\", \"c\": {}} ");
        assertEquals(Map.of("a", Arrays.asList(1.0, -25.0, true, false, null), "b", "q\"é\n", "c", Map.of()), v);
    }

    @Test
    void keepsKeyOrder() {
        assertEquals(List.of("z", "a", "m"), List.copyOf(((Map<?, ?>) Json.parse("{\"z\":1,\"a\":2,\"m\":3}")).keySet()));
    }

    @Test
    void rejectsBrokenJson() {
        for (String bad : List.of("", "{", "{\"a\" 1}", "[1,]", "{\"a\":1}x", "{\"a\":1,\"a\":2}", "tru", "\"abc"))
            assertThrows(IllegalArgumentException.class, () -> Json.parse(bad), bad);
    }
}
