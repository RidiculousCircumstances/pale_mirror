package io.farfrontier.palemirror.frontier.v3.api;

/** Stable identity of one non-replayable Minecraft effect or physical custody hand-off. */
public record PhysicalIntentId(String value) implements Comparable<PhysicalIntentId> {
    public PhysicalIntentId {
        value = Identifier.require(value, "physical intent id");
    }

    @Override public int compareTo(PhysicalIntentId other) {
        return value.compareTo(other.value);
    }
}
