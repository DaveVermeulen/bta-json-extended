package io.github.tryrodave.jsonextended.animation;

import java.util.Comparator;
import java.util.List;

/**
 * One bone's rotation, position or scale over time: keyframes sorted by time. Sampling follows Blockbench's
 * preview: before the first keyframe its {@code pre}, after the last its {@code post}; between two keyframes
 * step if the first is step, Catmull-Rom if either is, else linear.
 */
public final class Channel {
    private final List<Keyframe> keyframes;

    public Channel(List<Keyframe> keyframes) {
        if (keyframes.isEmpty())
            throw new IllegalArgumentException("a channel needs at least one keyframe");
        this.keyframes = keyframes.stream().sorted(Comparator.comparingDouble(Keyframe::time)).toList();
    }

    public List<Keyframe> keyframes() {
        return keyframes;
    }

    /** Time of the last keyframe. */
    public float end() {
        return keyframes.get(keyframes.size() - 1).time();
    }

    public float[] sample(float time) {
        Keyframe first = keyframes.get(0);
        if (time <= first.time())
            return time < first.time() ? first.pre().clone() : first.post().clone();
        int n = keyframes.size();
        Keyframe last = keyframes.get(n - 1);
        if (time >= last.time())
            return last.post().clone();
        int i = 0;
        while (keyframes.get(i + 1).time() <= time)
            i++;
        Keyframe a = keyframes.get(i), b = keyframes.get(i + 1);
        float t = (time - a.time()) / (b.time() - a.time());
        if (a.lerp() == Keyframe.Lerp.STEP)
            return a.post().clone();
        float[] out = new float[3];
        if (a.lerp() == Keyframe.Lerp.CATMULL_ROM || b.lerp() == Keyframe.Lerp.CATMULL_ROM) {
            float[] p0 = i > 0 ? keyframes.get(i - 1).post() : a.post();
            float[] p3 = i + 2 < n ? keyframes.get(i + 2).pre() : b.pre();
            for (int c = 0; c < 3; c++)
                out[c] = catmullRom(p0[c], a.post()[c], b.pre()[c], p3[c], t);
        } else {
            for (int c = 0; c < 3; c++)
                out[c] = a.post()[c] + (b.pre()[c] - a.post()[c]) * t;
        }
        return out;
    }

    /** Uniform Catmull-Rom through p1 (t = 0) and p2 (t = 1). */
    static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t, t3 = t2 * t;
        return 0.5f * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }
}
