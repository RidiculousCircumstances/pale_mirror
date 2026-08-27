package io.farfrontier.palemirror.frontier.v3.api;

/** Stable identity of one independent Frontier v3 world. */
public record WorldId(String value) implements Comparable<WorldId> {
    public WorldId {
        value = Identifier.require(value, "world id");
    }

    @Override
    public int compareTo(WorldId other) {
        return value.compareTo(other.value);
    }
}
