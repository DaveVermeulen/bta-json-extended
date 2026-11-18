package io.github.tryrodave.jsonextended.pose;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
}
