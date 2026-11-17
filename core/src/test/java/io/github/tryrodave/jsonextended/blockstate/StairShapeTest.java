package io.github.tryrodave.jsonextended.blockstate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tryrodave.jsonextended.blockstate.StairShape.Stair;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Vanilla's stair corner rule, as tables: this stair's facing and half, its neighbours, the shape. */
class StairShapeTest {
    private static String shape(String facing, String half, Map<String, Stair> neighbours) {
        return StairShape.of(facing, half, neighbours::get);
    }

    private static Stair stair(String facing, String half) {
        return new Stair(facing, half);
    }

    @Test
    void aloneIsStraight() {
        assertEquals("straight", shape("east", "bottom", Map.of()));
    }

    @Test
    void stairBehindTurnedMakesAnOuterCorner() {
        // Facing east: the tall side and "behind" are east. Counter-clockwise of east is north.
        assertEquals("outer_left", shape("east", "bottom", Map.of("east", stair("north", "bottom"))));
        assertEquals("outer_right", shape("east", "bottom", Map.of("east", stair("south", "bottom"))));
        assertEquals("outer_left", shape("north", "top", Map.of("north", stair("west", "top"))));
    }

    @Test
    void stairInFrontTurnedMakesAnInnerCorner() {
        assertEquals("inner_left", shape("east", "bottom", Map.of("west", stair("north", "bottom"))));
        assertEquals("inner_right", shape("east", "bottom", Map.of("west", stair("south", "bottom"))));
        assertEquals("inner_right", shape("south", "bottom", Map.of("north", stair("west", "bottom"))));
    }

    @Test
    void otherHalfOrSameAxisStaysStraight() {
        assertEquals("straight", shape("east", "bottom", Map.of("east", stair("north", "top"))));
        assertEquals("straight", shape("east", "bottom", Map.of("east", stair("east", "bottom"))));
        assertEquals("straight", shape("east", "bottom", Map.of("east", stair("west", "bottom"))));
        assertEquals("straight", shape("east", "bottom", Map.of("west", stair("east", "bottom"))));
    }

    @Test
    void aLinedUpNeighbourKeepsTheRunStraight() {
        // The corner would point south, but the stair to the south continues this run.
        assertEquals("straight", shape("east", "bottom",
                Map.of("east", stair("north", "bottom"), "south", stair("east", "bottom"))));
        assertEquals("outer_left", shape("east", "bottom",
                Map.of("east", stair("north", "bottom"), "south", stair("east", "top"))));
        // Inner corner toward north is blocked by a lined-up stair to the north.
        assertEquals("straight", shape("east", "bottom",
                Map.of("west", stair("north", "bottom"), "north", stair("east", "bottom"))));
    }

    @Test
    void outerWinsOverInner() {
        assertEquals("outer_right", shape("east", "bottom",
                Map.of("east", stair("south", "bottom"), "west", stair("north", "bottom"))));
    }
}
