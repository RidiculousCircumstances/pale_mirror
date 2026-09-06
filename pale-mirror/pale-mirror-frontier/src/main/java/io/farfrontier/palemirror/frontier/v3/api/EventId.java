package io.farfrontier.palemirror.frontier.v3.api;

/** Globally stable identity of one accepted canonical event. */
public record EventId(String value) implements Comparable<EventId> {
    public EventId {
        value = Identifier.require(value, "event id");
    }

    @Override
    public int compareTo(EventId other) {
        return value.compareTo(other.value);
    }
}
