package io.farfrontier.palemirror.frontier.v3.api;

/** Stable identity for one exclusive HOT/COLD execution-location hand-off. */
public record SceneLeaseId(String value) implements Comparable<SceneLeaseId> {
    public SceneLeaseId { value = Identifier.require(value, "scene lease id"); }
    @Override public int compareTo(SceneLeaseId other) { return value.compareTo(other.value); }
}
