package io.github.tryrodave.jsonextended.animation;

import io.github.tryrodave.jsonextended.pose.PartTransform;
import io.github.tryrodave.jsonextended.pose.Pose;
import java.util.Map;

/**
 * One animation from a Bedrock {@code .animation.json}: channels per bone, sampled to a {@link Pose}.
 * Times are in seconds. Values are converted from Bedrock's axes to the Java model's (see {@link #toPart}).
 */
public record Animation(String name, Loop loop, float length, Map<String, Bone> bones) {
    /** {@code loop}: false (play once, then no effect), true, or "hold_on_last_frame". */
    public enum Loop { ONCE, LOOP, HOLD }

    /** A bone's channels; any of them may be null. */
    public record Bone(Channel rotation, Channel position, Channel scale) {}

    /** Animation time for {@code seconds} since it started: wrapped when looping, clamped otherwise. */
    public float localTime(float seconds) {
        if (length <= 0)
            return 0;
        if (loop == Loop.LOOP)
            return (float) (seconds - Math.floor(seconds / length) * length);
        return Math.min(Math.max(seconds, 0), length);
    }

    /** True once a play-once animation has run its length; it then has no effect. */
    public boolean finished(float seconds) {
        return loop == Loop.ONCE && seconds >= length;
    }

    /** The pose {@code seconds} after the animation started, on top of {@code base}. */
    public Pose sample(float seconds, Pose base) {
        if (finished(seconds))
            return base;
        float t = localTime(seconds);
        Pose pose = base;
        for (Map.Entry<String, Bone> e : bones.entrySet())
            pose = pose.with(e.getKey(), pose.get(e.getKey()).plus(toPart(e.getValue(), t)));
        return pose;
    }

    public Pose sample(float seconds) {
        return sample(seconds, Pose.REST);
    }

    /**
     * A bone's values as the file writes them (Bedrock axes): rotation in degrees, position in pixels, scale;
     * null if the animation doesn't move that bone. For geo entity models, which use Bedrock's axes themselves.
     */
    public float[] raw(String bone, float seconds) {
        Bone b = bones.get(bone);
        if (b == null)
            return null;
        float t = localTime(seconds);
        float[] r = b.rotation() == null ? new float[3] : b.rotation().sample(t);
        float[] p = b.position() == null ? new float[3] : b.position().sample(t);
        float[] s = b.scale() == null ? new float[] {1, 1, 1} : b.scale().sample(t);
        return new float[] {r[0], r[1], r[2], p[0], p[1], p[2], s[0], s[1], s[2]};
    }

    /**
     * Bedrock bone values to our part transform. Blockbench writes Bedrock rotations with X and Y negated and
     * positions with X negated compared with the Java model it shows (the same flip GeckoLib undoes), so undo it.
     * Rotation order matches: X first, then Y, then Z.
     */
    static PartTransform toPart(Bone bone, float t) {
        float[] r = bone.rotation() == null ? new float[3] : bone.rotation().sample(t);
        float[] p = bone.position() == null ? new float[3] : bone.position().sample(t);
        float[] s = bone.scale() == null ? new float[] {1, 1, 1} : bone.scale().sample(t);
        return new PartTransform(-r[0], -r[1], r[2], -p[0], p[1], p[2], s[0], s[1], s[2]);
    }
}
