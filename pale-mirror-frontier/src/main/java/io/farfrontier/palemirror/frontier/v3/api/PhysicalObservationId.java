package io.farfrontier.palemirror.frontier.v3.api;

/** Stable deduplication identity of one observed Minecraft physical result. */
public record PhysicalObservationId(String value) implements Comparable<PhysicalObservationId> {
    public PhysicalObservationId { value = Identifier.require(value, "physical observation id"); }
    @Override public int compareTo(PhysicalObservationId other) { return value.compareTo(other.value); }
}
