package io.github.tryrodave.jsonextended.obj;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tryrodave.jsonextended.obj.ObjQuad.Side;
import org.junit.jupiter.api.Test;

/** Expected values from knowledge/bta/static-model.md (BTA's StaticBlockModelMojang and Direction.rotateX/Y/Z). */
class QuarterTurnsTest {
    @Test
    void oneYTurnMovesEastToNorth() {
        assertArrayEquals(new float[] {0.5f, 0.5f, 0f}, QuarterTurns.point(new float[] {1, 0.5f, 0.5f}, 0, 1, 0));
        assertArrayEquals(new float[] {0, 0, -1}, QuarterTurns.vector(new float[] {1, 0, 0}, 0, 1, 0));
    }

    @Test
    void sidesTurnLikeBtaDirections() {
        // Direction.rotateY(1): N -> W -> S -> E -> N
        assertEquals(Side.WEST, QuarterTurns.side(Side.NORTH, 0, 1, 0));
        assertEquals(Side.SOUTH, QuarterTurns.side(Side.WEST, 0, 1, 0));
        assertEquals(Side.NORTH, QuarterTurns.side(Side.EAST, 0, 1, 0));
        // rotateX(1): DOWN -> NORTH -> UP -> SOUTH
        assertEquals(Side.NORTH, QuarterTurns.side(Side.DOWN, 1, 0, 0));
        assertEquals(Side.UP, QuarterTurns.side(Side.NORTH, 1, 0, 0));
        // rotateZ(1): DOWN -> EAST -> UP -> WEST
        assertEquals(Side.EAST, QuarterTurns.side(Side.DOWN, 0, 0, 1));
        assertEquals(Side.WEST, QuarterTurns.side(Side.UP, 0, 0, 1));
    }

    @Test
    void vanillaY90IsThreeTurns() {
        // Blockstate "y": 90 turns north to east; Part.btaRotY() gives 3.
        assertEquals(Side.EAST, QuarterTurns.side(Side.NORTH, 0, 3, 0));
        assertEquals(Side.EAST, QuarterTurns.side(Side.NORTH, 0, -1, 0));
    }

    @Test
    void xThenYThenZ() {
        // X first: up -> south (rotateX(1) UP -> SOUTH), then Y: south -> east.
        assertEquals(Side.EAST, QuarterTurns.side(Side.UP, 1, 1, 0));
        assertArrayEquals(new float[] {0.25f, 0.75f, 1f}, QuarterTurns.point(new float[] {0.25f, 0.75f, 1f}, 4, -4, 8));
    }
}
