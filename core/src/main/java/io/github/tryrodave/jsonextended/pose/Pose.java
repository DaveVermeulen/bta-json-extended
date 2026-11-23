package io.github.tryrodave.jsonextended.pose;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** A transform per part name; parts without one stay as modelled. Immutable: {@code with} returns a new pose. */
public final class Pose {
    public static final Pose REST = new Pose(Map.of());

    private final Map<String, PartTransform> parts;

    private Pose(Map<String, PartTransform> parts) {
        this.parts = parts;
    }

    public Pose with(String part, PartTransform transform) {
        Map<String, PartTransform> copy = new LinkedHashMap<>(parts);
        copy.put(part, transform);
        return new Pose(Collections.unmodifiableMap(copy));
    }

    public PartTransform get(String part) {
        return parts.getOrDefault(part, PartTransform.NONE);
    }

    public Map<String, PartTransform> parts() {
        return parts;
    }

    /** Equal when every part sits the same, so renderers can keep last frame's faces while nothing moves. */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Pose other))
            return false;
        Set<String> names = new HashSet<>(parts.keySet());
        names.addAll(other.parts.keySet());
        for (String name : names)
            if (!get(name).equals(other.get(name)))
                return false;
        return true;
    }

    @Override
    public int hashCode() {
        int h = 0;
        for (Map.Entry<String, PartTransform> e : parts.entrySet())
            if (!e.getValue().equals(PartTransform.NONE))
                h += e.getKey().hashCode() ^ e.getValue().hashCode();
        return h;
    }
}
