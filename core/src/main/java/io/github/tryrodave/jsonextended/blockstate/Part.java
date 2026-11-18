package io.github.tryrodave.jsonextended.blockstate;

/**
 * One model to draw: a variant object ({@code model}, {@code x}, {@code y}, {@code uvlock}, {@code weight}).
 *
 * @param x vanilla X rotation in degrees: 0, 90, 180 or 270
 * @param y vanilla Y rotation in degrees: 0, 90, 180 or 270
 */
public record Part(String model, int x, int y, boolean uvlock, int weight) {
    /** X rotation as BTA {@code renderAttached} quarter turns. */
    public int btaRotX() {
        return quarterTurns(x);
    }

    /** Y rotation as BTA {@code renderAttached} quarter turns. */
    public int btaRotY() {
        return quarterTurns(y);
    }

    /** Z rotation as BTA {@code renderAttached} quarter turns; vanilla blockstates have none. */
    public int btaRotZ() {
        return 0;
    }

    /**
     * Vanilla turns clockwise looking down the axis toward the origin (y=90 turns north to east), BTA's
     * quarter turns go the other way: {@code -degrees/90 mod 4}. Derived from BTA's maths; still to be
     * confirmed in game with the test blocks.
     */
    static int quarterTurns(int degrees) {
        return Math.floorMod(-degrees / 90, 4);
    }
}
