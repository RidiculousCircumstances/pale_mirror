package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** Stable semantic identity; it is never derived from coordinates or a Minecraft UUID. */
public record WorldObjectId(String value) implements Comparable<WorldObjectId> {
    public WorldObjectId {
        Objects.requireNonNull(value, "value");
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("World object id must be namespace:path: " + value);
        }
    }

    @Override
    public int compareTo(WorldObjectId other) {
        return value.compareTo(other.value);
    }
}
