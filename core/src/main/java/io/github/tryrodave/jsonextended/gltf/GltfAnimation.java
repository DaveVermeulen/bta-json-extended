package io.github.tryrodave.jsonextended.gltf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One glTF animation: keyframes for nodes' translation, rotation and scale. {@link #sample} gives each animated
 * node's TRS at a time, in the packing of {@link GltfModel#T}; rotations are spherically interpolated.
 * Times are in seconds; before the first key the first value holds, after the last the last.
 */
public final class GltfAnimation {
    /** How values between two keys are found. */
    public enum Interpolation { LINEAR, STEP, CUBICSPLINE }

    /**
     * One target: a node and one of its paths.
     *
     * @param node   place in {@link GltfModel#nodes()}
     * @param offset where the path sits in the TRS packing ({@link GltfModel#T}, {@link GltfModel#R} or {@link GltfModel#S})
     * @param values per key {@code size} floats, or {@code 3 * size} for cubic splines (in-tangent, value, out-tangent)
     */
    record Channel(int node, int offset, int size, Interpolation interpolation, float[] times, float[] values) {}

    private final String name;
    private final float length;
    private final List<Channel> channels;
    private final List<GltfModel.Node> nodes;

    private GltfAnimation(String name, float length, List<Channel> channels, List<GltfModel.Node> nodes) {
        this.name = name;
        this.length = length;
        this.channels = channels;
        this.nodes = nodes;
    }

    public String name() {
        return name;
    }

    /** Seconds from 0 to the last key of any channel. */
    public float length() {
        return length;
    }

    /** Places in {@link GltfModel#nodes()} this animation moves. */
    public List<Integer> nodes() {
        List<Integer> out = new ArrayList<>();
        for (Channel c : channels)
            if (!out.contains(c.node()))
                out.add(c.node());
        return out;
    }

    /** Each animated node's TRS at {@code time} seconds (not wrapped: loop it yourself). Paths not animated keep rest. */
    public Map<Integer, float[]> sample(float time) {
        Map<Integer, float[]> out = new HashMap<>();
        for (Channel c : channels) {
            float[] trs = out.computeIfAbsent(c.node(), n -> nodes.get(n).rest().clone());
            sample(c, time, trs);
        }
        return out;
    }

    private static void sample(Channel c, float time, float[] into) {
        float[] times = c.times();
        int n = c.size(), keys = times.length;
        int stride = c.interpolation() == Interpolation.CUBICSPLINE ? 3 * n : n;
        int valueAt = c.interpolation() == Interpolation.CUBICSPLINE ? n : 0;
        if (keys == 1 || time <= times[0]) {
            System.arraycopy(c.values(), valueAt, into, c.offset(), n);
            return;
        }
        if (time >= times[keys - 1]) {
            System.arraycopy(c.values(), (keys - 1) * stride + valueAt, into, c.offset(), n);
            return;
        }
        int k = 0;
        while (k + 2 < keys && times[k + 1] <= time)
            k++;
        float t0 = times[k], t1 = times[k + 1], span = t1 - t0;
        float u = span <= 0 ? 0 : (time - t0) / span;
        float[] v = c.values();
        int a = k * stride, b = (k + 1) * stride;
        switch (c.interpolation()) {
            case STEP -> System.arraycopy(v, a, into, c.offset(), n);
            case LINEAR -> {
                if (n == 4)
                    slerp(v, a, v, b, u, into, c.offset());
                else
                    for (int i = 0; i < n; i++)
                        into[c.offset() + i] = v[a + i] + (v[b + i] - v[a + i]) * u;
            }
            case CUBICSPLINE -> {
                float u2 = u * u, u3 = u2 * u;
                float h00 = 2 * u3 - 3 * u2 + 1, h10 = u3 - 2 * u2 + u, h01 = -2 * u3 + 3 * u2, h11 = u3 - u2;
                for (int i = 0; i < n; i++) {
                    float p0 = v[a + n + i], m0 = v[a + 2 * n + i] * span; // value and out-tangent of key k
                    float p1 = v[b + n + i], m1 = v[b + i] * span;         // value and in-tangent of key k + 1
                    into[c.offset() + i] = h00 * p0 + h10 * m0 + h01 * p1 + h11 * m1;
                }
                if (n == 4)
                    normalize(into, c.offset());
            }
        }
    }

    /** Spherical interpolation of unit quaternions (x, y, z, w), the short way round. */
    static void slerp(float[] q0, int a, float[] q1, int b, float u, float[] out, int o) {
        double x0 = q0[a], y0 = q0[a + 1], z0 = q0[a + 2], w0 = q0[a + 3];
        double x1 = q1[b], y1 = q1[b + 1], z1 = q1[b + 2], w1 = q1[b + 3];
        double dot = x0 * x1 + y0 * y1 + z0 * z1 + w0 * w1;
        if (dot < 0) {
            dot = -dot;
            x1 = -x1;
            y1 = -y1;
            z1 = -z1;
            w1 = -w1;
        }
        double s0, s1;
        if (dot > 0.9995) {
            s0 = 1 - u;
            s1 = u;
        } else {
            double theta = Math.acos(Math.min(dot, 1));
            double sin = Math.sin(theta);
            s0 = Math.sin((1 - u) * theta) / sin;
            s1 = Math.sin(u * theta) / sin;
        }
        out[o] = (float) (s0 * x0 + s1 * x1);
        out[o + 1] = (float) (s0 * y0 + s1 * y1);
        out[o + 2] = (float) (s0 * z0 + s1 * z1);
        out[o + 3] = (float) (s0 * w0 + s1 * w1);
        normalize(out, o);
    }

    private static void normalize(float[] q, int o) {
        double n = Math.sqrt(q[o] * q[o] + q[o + 1] * q[o + 1] + q[o + 2] * q[o + 2] + q[o + 3] * q[o + 3]);
        if (n < 1e-12) {
            q[o] = q[o + 1] = q[o + 2] = 0;
            q[o + 3] = 1;
            return;
        }
        for (int i = 0; i < 4; i++)
            q[o + i] /= (float) n;
    }

    /** Every animation in the file, by name; channels on nodes outside the scene or on weights are skipped with a warning. */
    static Map<String, GltfAnimation> read(GltfFile file, Map<Integer, Integer> nodeByFileIndex, List<GltfModel.Node> nodes,
                                           List<String> warnings) {
        String id = file.id();
        Map<String, GltfAnimation> out = new LinkedHashMap<>();
        List<Object> animations = file.array("animations");
        for (int a = 0; a < animations.size(); a++) {
            String path = "animations[" + a + "]";
            Map<?, ?> animation = GltfFile.object(id, animations.get(a), path);
            String name = animation.get("name") instanceof String s && !s.isEmpty() ? s : "animation_" + a;
            List<Object> samplers = GltfFile.list(animation, "samplers");
            List<Channel> channels = new ArrayList<>();
            float length = 0;
            List<Object> channelList = GltfFile.list(animation, "channels");
            for (int c = 0; c < channelList.size(); c++) {
                String cp = path + ".channels[" + c + "]";
                Map<?, ?> channel = GltfFile.object(id, channelList.get(c), cp);
                Map<?, ?> target = GltfFile.object(id, channel.get("target"), cp + ".target");
                String targetPath = target.get("path") instanceof String s ? s : "";
                int offset = switch (targetPath) {
                    case "translation" -> GltfModel.T;
                    case "rotation" -> GltfModel.R;
                    case "scale" -> GltfModel.S;
                    default -> -1;
                };
                if (offset < 0) {
                    warn(warnings, id + ": " + cp + ".target.path: '" + targetPath + "' isn't played (only translation, rotation and scale)");
                    continue;
                }
                if (!target.containsKey("node"))
                    continue; // allowed by the spec: an extension supplies the target
                Integer node = nodeByFileIndex.get(GltfFile.integer(id, target, "node", cp + ".target", -1));
                if (node == null) {
                    warn(warnings, id + ": " + cp + ".target.node: not in the scene, so it isn't played");
                    continue;
                }
                if (nodes.get(node).matrix() != null) {
                    warn(warnings, id + ": " + cp + ".target.node: a node with a matrix can't be animated");
                    continue;
                }
                int si = GltfFile.integer(id, channel, "sampler", cp, -1);
                if (si >= samplers.size())
                    throw new GltfException(id + ": " + cp + ".sampler: " + si + " doesn't exist");
                String sp = path + ".samplers[" + si + "]";
                Map<?, ?> sampler = GltfFile.object(id, samplers.get(si), sp);
                Interpolation interpolation;
                try {
                    interpolation = Interpolation.valueOf(sampler.get("interpolation") instanceof String s ? s : "LINEAR");
                } catch (IllegalArgumentException e) {
                    throw new GltfException(id + ": " + sp + ".interpolation: expected LINEAR, STEP or CUBICSPLINE");
                }
                int size = offset == GltfModel.R ? 4 : 3;
                float[] times = file.floats(GltfFile.integer(id, sampler, "input", sp, -1), sp + ".input");
                int outputAccessor = GltfFile.integer(id, sampler, "output", sp, -1);
                if (file.components(outputAccessor, sp + ".output") != size)
                    throw new GltfException(id + ": " + sp + ".output: expected VEC" + size + " for " + targetPath);
                float[] values = file.floats(outputAccessor, sp + ".output");
                int perKey = interpolation == Interpolation.CUBICSPLINE ? 3 * size : size;
                if (times.length == 0 || values.length != times.length * perKey)
                    throw new GltfException(id + ": " + sp + ": " + times.length + " times but "
                            + values.length / Math.max(perKey, 1) + " values");
                for (int k = 1; k < times.length; k++)
                    if (times[k] < times[k - 1])
                        throw new GltfException(id + ": " + sp + ".input: times must not go down");
                length = Math.max(length, times[times.length - 1]);
                channels.add(new Channel(node, offset, size, interpolation, times, values));
            }
            out.putIfAbsent(name, new GltfAnimation(name, length, List.copyOf(channels), nodes));
        }
        return Collections.unmodifiableMap(out);
    }

    private static void warn(List<String> warnings, String line) {
        if (!warnings.contains(line))
            warnings.add(line);
    }
}
