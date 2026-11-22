package io.github.tryrodave.jsonextended.gltf;

import io.github.tryrodave.jsonextended.animation.Animation.Loop;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plays one glTF animation at a time for one object (a tile entity, an entity), with an optional crossfade from
 * the previous one, like {@link io.github.tryrodave.jsonextended.animation.AnimationPlayer} does for Bedrock files.
 * glTF animations have no loop setting, so the caller picks one when playing. Time is whatever clock the caller
 * uses, in seconds. Not thread-safe; keep one per object on the client.
 */
public final class GltfAnimationPlayer {
    private final List<GltfModel.Node> nodes;
    private GltfAnimation current;
    private Loop loop = Loop.ONCE;
    private float startedAt;
    private GltfAnimation previous;
    private Loop previousLoop;
    private float previousStartedAt;
    private float fadeStart;
    private float fadeLength;

    /** @param model the model whose nodes the animations move */
    public GltfAnimationPlayer(GltfModel model) {
        this.nodes = model.nodes();
    }

    /** Starts {@code animation} at {@code now}; with {@code fadeSeconds > 0} it blends in from the last one. */
    public void play(GltfAnimation animation, Loop loop, float now, float fadeSeconds) {
        if (current != null && fadeSeconds > 0) {
            previous = current;
            previousLoop = this.loop;
            previousStartedAt = startedAt;
            fadeStart = now;
            fadeLength = fadeSeconds;
        } else {
            previous = null;
        }
        current = animation;
        this.loop = loop;
        startedAt = now;
    }

    /** Plays {@code animation} unless it's already the current one (so calling it every frame is fine). */
    public void ensure(GltfAnimation animation, Loop loop, float now, float fadeSeconds) {
        if (current != animation || this.loop != loop)
            play(animation, loop, now, fadeSeconds);
    }

    /** Shows {@code animation} as already played to its end, with no fade: for state loaded with the world. */
    public void skipToEnd(GltfAnimation animation, Loop loop, float now) {
        play(animation, loop, now - animation.length(), 0);
    }

    public void stop() {
        current = null;
        previous = null;
    }

    public GltfAnimation current() {
        return current;
    }

    /** Seconds since the current animation started (0 with none). */
    public float elapsed(float now) {
        return current == null ? 0 : now - startedAt;
    }

    /** True while an animation is running: looping, holding, or a play-once one before its end. */
    public boolean playing(float now) {
        return current != null && !(loop == Loop.ONCE && now - startedAt >= current.length());
    }

    /** Node TRS for {@link GltfModel#pose} at {@code now}; nodes not in it stay at rest. */
    public Map<Integer, float[]> pose(float now) {
        Map<Integer, float[]> to = sample(current, loop, now - startedAt);
        if (previous == null)
            return to;
        float t = (now - fadeStart) / fadeLength;
        if (t >= 1) {
            previous = null;
            return to;
        }
        Map<Integer, float[]> from = sample(previous, previousLoop, now - previousStartedAt);
        Set<Integer> moved = new HashSet<>(from.keySet());
        moved.addAll(to.keySet());
        Map<Integer, float[]> out = new HashMap<>();
        for (int node : moved)
            out.put(node, blend(from.getOrDefault(node, nodes.get(node).rest()), to.getOrDefault(node, nodes.get(node).rest()),
                    Math.max(t, 0)));
        return out;
    }

    /** {@code animation} {@code seconds} after it started: wrapped when looping, clamped otherwise, nothing after a play-once end. */
    public static Map<Integer, float[]> sample(GltfAnimation animation, Loop loop, float seconds) {
        if (animation == null)
            return Map.of();
        float length = animation.length();
        if (loop == Loop.ONCE && seconds >= length)
            return Map.of();
        float t;
        if (length <= 0)
            t = 0;
        else if (loop == Loop.LOOP)
            t = (float) (seconds - Math.floor(seconds / length) * length);
        else
            t = Math.min(Math.max(seconds, 0), length);
        return animation.sample(t);
    }

    /** Two TRS blended: translation and scale linearly, rotation spherically. */
    static float[] blend(float[] a, float[] b, float t) {
        float[] out = new float[GltfModel.TRS];
        for (int i = 0; i < 3; i++) {
            out[GltfModel.T + i] = a[GltfModel.T + i] + (b[GltfModel.T + i] - a[GltfModel.T + i]) * t;
            out[GltfModel.S + i] = a[GltfModel.S + i] + (b[GltfModel.S + i] - a[GltfModel.S + i]) * t;
        }
        GltfAnimation.slerp(a, GltfModel.R, b, GltfModel.R, t, out, GltfModel.R);
        return out;
    }
}
