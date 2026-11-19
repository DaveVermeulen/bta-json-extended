package io.github.tryrodave.jsonextended.animation;

/**
 * One keyframe of a channel. {@code pre} is the value arriving at this time, {@code post} the value leaving it
 * (they differ only for a jump). {@code lerp} is how the segment from here to the next keyframe is filled.
 */
public record Keyframe(float time, float[] pre, float[] post, Lerp lerp) {
    public enum Lerp { LINEAR, CATMULL_ROM, STEP }
}
