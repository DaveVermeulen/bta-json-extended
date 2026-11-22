package io.github.tryrodave.jsonextended.animation;

import io.github.tryrodave.jsonextended.pose.PartTransform;
import io.github.tryrodave.jsonextended.pose.Pose;
import java.util.HashSet;
import java.util.Set;

/**
 * Plays one animation at a time for one object (a tile entity, an entity), with an optional crossfade from the
 * previous one. Time is whatever clock the caller uses, in seconds (e.g. {@code (ticks + partialTick) / 20}).
 * Not thread-safe; keep one per object on the client.
 */
public final class AnimationPlayer {
    private Animation current;
    private float startedAt;
    private Animation previous;
    private float previousStartedAt;
    private float fadeStart;
    private float fadeLength;

    /** Starts {@code animation} at {@code now}; with {@code fadeSeconds > 0} it blends in from the last one. */
    public void play(Animation animation, float now, float fadeSeconds) {
        if (current != null && fadeSeconds > 0) {
            previous = current;
            previousStartedAt = startedAt;
            fadeStart = now;
            fadeLength = fadeSeconds;
        } else {
            previous = null;
        }
        current = animation;
        startedAt = now;
    }

    public void play(Animation animation, float now) {
        play(animation, now, 0);
    }

    /**
     * Shows {@code animation} as already played to its end, with no movement and no fade: for state loaded with
     * the world, so a chest that was open loads open. A hold animation then shows its last frame, a loop goes on
     * from its start, and a play-once one has no effect.
     */
    public void skipToEnd(Animation animation, float now) {
        play(animation, now - animation.length(), 0);
    }

    /** Plays {@code animation} unless it's already the current one (so calling it every tick is fine). */
    public void ensure(Animation animation, float now, float fadeSeconds) {
        if (current != animation)
            play(animation, now, fadeSeconds);
    }

    public void stop() {
        current = null;
        previous = null;
    }

    public Animation current() {
        return current;
    }

    /** Seconds since the current animation started (0 with none). */
    public float elapsed(float now) {
        return current == null ? 0 : now - startedAt;
    }

    /** True while an animation is running: looping, holding, or a play-once one before its end. */
    public boolean playing(float now) {
        return current != null && !current.finished(now - startedAt);
    }

    /** The pose at {@code now}, on top of {@code base} (the pose set from code, or {@link Pose#REST}). */
    public Pose pose(float now, Pose base) {
        Pose to = current == null ? base : current.sample(now - startedAt, base);
        if (previous == null)
            return to;
        float t = (now - fadeStart) / fadeLength;
        if (t >= 1) {
            previous = null;
            return to;
        }
        Pose from = previous.sample(now - previousStartedAt, base);
        Set<String> parts = new HashSet<>(from.parts().keySet());
        parts.addAll(to.parts().keySet());
        Pose out = base;
        for (String part : parts)
            out = out.with(part, PartTransform.lerp(from.get(part), to.get(part), Math.max(t, 0)));
        return out;
    }
}
