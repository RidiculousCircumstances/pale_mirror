package io.farfrontier.palemirror.frontier.v3.api;

/** Stable identity of one persisted future action. */
public record ScheduleId(String value) implements Comparable<ScheduleId> {
    public ScheduleId {
        value = Identifier.require(value, "schedule id");
    }

    @Override
    public int compareTo(ScheduleId other) {
        return value.compareTo(other.value);
    }
}
