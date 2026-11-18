package io.github.tryrodave.jsonextended.blockstate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChoiceTest {
    private final Part a = new Part("a", 0, 0, false, 4);
    private final Part a90 = new Part("a", 0, 90, false, 1);
    private final Part b = new Part("b", 0, 0, false, 2);
    private final Part c = new Part("c", 0, 0, false, 1);
    private final Choice weighted = new Choice(List.of(a, a90, b, c));

    @Test
    void sameSeedSamePick() {
        for (long seed = -50; seed < 50; seed++)
            assertSame(weighted.pick(seed), weighted.pick(seed));
    }

    @Test
    void picksFollowTheWeights() {
        Map<Part, Integer> counts = new HashMap<>();
        int n = 80_000;
        // Seeds like BTA's positional seed: neighbouring positions give neighbouring numbers.
        for (int i = 0; i < n; i++)
            counts.merge(weighted.pick(i * 31L), 1, Integer::sum);
        assertEquals(8, weighted.totalWeight());
        assertShare(counts.get(a), n * 4 / 8);
        assertShare(counts.get(a90), n / 8);
        assertShare(counts.get(b), n * 2 / 8);
        assertShare(counts.get(c), n / 8);
    }

    @Test
    void singlePartIsAlwaysPicked() {
        Choice one = new Choice(List.of(b));
        assertSame(b, one.pick(123));
        assertSame(b, one.first());
    }

    private static void assertShare(int actual, int expected) {
        assertTrue(Math.abs(actual - expected) < expected * 0.05, actual + " picks, expected about " + expected);
    }
}
