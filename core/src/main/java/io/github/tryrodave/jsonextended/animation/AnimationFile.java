package io.github.tryrodave.jsonextended.animation;

import io.github.tryrodave.jsonextended.json.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a Bedrock {@code .animation.json} (Blockbench's "Export Animations"). Supported: {@code loop},
 * {@code animation_length}, and per bone {@code rotation}, {@code position}, {@code scale} as a constant,
 * a scalar (all three axes), or keyframes ({@code "0.5": [x, y, z]} or {@code {"pre", "post", "lerp_mode"}}).
 * Values must be numbers or number strings: Molang expressions aren't evaluated; they read as 0 and give a warning.
 */
public final class AnimationFile {
    private final Map<String, Animation> animations;
    private final List<String> warnings;

    private AnimationFile(Map<String, Animation> animations, List<String> warnings) {
        this.animations = animations;
        this.warnings = warnings;
    }

    public Map<String, Animation> animations() {
        return animations;
    }

    /** Things in the file that were read but not as written (Molang, unknown lerp modes). */
    public List<String> warnings() {
        return warnings;
    }

    public Animation get(String name) {
        return animations.get(name);
    }

    public static AnimationFile parse(String id, String text) {
        Object root;
        try {
            root = Json.parse(text);
        } catch (RuntimeException e) {
            throw new AnimationException(id + ": invalid JSON: " + e.getMessage());
        }
        if (!(root instanceof Map<?, ?> file) || !(file.get("animations") instanceof Map<?, ?> all))
            throw new AnimationException(id + ": no \"animations\" object");
        List<String> warnings = new ArrayList<>();
        Map<String, Animation> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : all.entrySet()) {
            String name = String.valueOf(e.getKey());
            if (!(e.getValue() instanceof Map<?, ?> anim))
                throw new AnimationException(id + ": animation " + name + " isn't an object");
            out.put(name, animation(new Reader(id + " " + name, warnings), name, anim));
        }
        return new AnimationFile(Map.copyOf(out), List.copyOf(warnings));
    }

    private static Animation animation(Reader r, String name, Map<?, ?> anim) {
        Animation.Loop loop = switch (String.valueOf(anim.get("loop"))) {
            case "true" -> Animation.Loop.LOOP;
            case "hold_on_last_frame" -> Animation.Loop.HOLD;
            case "false", "null" -> Animation.Loop.ONCE;
            default -> {
                r.warn("loop " + anim.get("loop") + " isn't true, false or \"hold_on_last_frame\"; playing once");
                yield Animation.Loop.ONCE;
            }
        };
        Map<String, Animation.Bone> bones = new LinkedHashMap<>();
        float end = 0;
        if (anim.get("bones") instanceof Map<?, ?> boneMap) {
            for (Map.Entry<?, ?> b : boneMap.entrySet()) {
                String bone = String.valueOf(b.getKey());
                if (!(b.getValue() instanceof Map<?, ?> channels))
                    throw new AnimationException(r.where + ": bone " + bone + " isn't an object");
                Reader br = r.in("bone " + bone);
                Animation.Bone parsed = new Animation.Bone(
                        channel(br.in("rotation"), channels.get("rotation"), 0),
                        channel(br.in("position"), channels.get("position"), 0),
                        channel(br.in("scale"), channels.get("scale"), 1));
                for (Channel c : new Channel[] {parsed.rotation(), parsed.position(), parsed.scale()})
                    if (c != null)
                        end = Math.max(end, c.end());
                bones.put(bone, parsed);
            }
        }
        float length = anim.get("animation_length") instanceof Double d ? d.floatValue() : end;
        return new Animation(name, loop, length, Map.copyOf(bones));
    }

    private static Channel channel(Reader r, Object value, float neutral) {
        if (value == null)
            return null;
        if (value instanceof Map<?, ?> map && !map.containsKey("pre") && !map.containsKey("post")
                && !map.containsKey("vector")) {
            List<Keyframe> keys = new ArrayList<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                float time;
                try {
                    time = Float.parseFloat(String.valueOf(e.getKey()));
                } catch (NumberFormatException ex) {
                    throw new AnimationException(r.where + ": keyframe time \"" + e.getKey() + "\" isn't a number");
                }
                keys.add(keyframe(r.in("at " + e.getKey()), time, e.getValue(), neutral));
            }
            if (keys.isEmpty())
                return null;
            return new Channel(keys);
        }
        return new Channel(List.of(keyframe(r, 0, value, neutral)));
    }

    private static Keyframe keyframe(Reader r, float time, Object value, float neutral) {
        Keyframe.Lerp lerp = Keyframe.Lerp.LINEAR;
        float[] pre, post;
        if (value instanceof Map<?, ?> map) {
            Object postValue = map.containsKey("post") ? map.get("post") : map.get("vector");
            Object preValue = map.containsKey("pre") ? map.get("pre") : postValue;
            if (postValue == null)
                postValue = preValue;
            pre = vector(r, preValue, neutral);
            post = vector(r, postValue, neutral);
            Object mode = map.get("lerp_mode");
            if (mode != null) {
                lerp = switch (String.valueOf(mode)) {
                    case "linear" -> Keyframe.Lerp.LINEAR;
                    case "catmullrom", "catmull_rom" -> Keyframe.Lerp.CATMULL_ROM;
                    case "step" -> Keyframe.Lerp.STEP;
                    default -> {
                        r.warn("lerp_mode " + mode + " isn't linear, catmullrom or step; using linear");
                        yield Keyframe.Lerp.LINEAR;
                    }
                };
            }
        } else {
            pre = post = vector(r, value, neutral);
        }
        return new Keyframe(time, pre, post, lerp);
    }

    private static float[] vector(Reader r, Object value, float neutral) {
        if (value instanceof List<?> list) {
            if (list.size() != 3)
                throw new AnimationException(r.where + ": a vector needs 3 values, not " + list.size());
            return new float[] {number(r, list.get(0), neutral), number(r, list.get(1), neutral), number(r, list.get(2), neutral)};
        }
        float v = number(r, value, neutral);
        return new float[] {v, v, v};
    }

    private static float number(Reader r, Object value, float neutral) {
        if (value instanceof Double d)
            return d.floatValue();
        if (value instanceof String s) {
            try {
                return Float.parseFloat(s.trim());
            } catch (NumberFormatException e) {
                r.warn("Molang \"" + s + "\" isn't evaluated; using " + (neutral == 0 ? "0" : "1"));
                return neutral;
            }
        }
        throw new AnimationException(r.where + ": " + value + " isn't a number");
    }

    /** Where we are in the file, for messages. */
    private record Reader(String where, List<String> warnings) {
        Reader in(String part) {
            return new Reader(where + ", " + part, warnings);
        }

        void warn(String message) {
            warnings.add(where + ": " + message);
        }
    }
}
