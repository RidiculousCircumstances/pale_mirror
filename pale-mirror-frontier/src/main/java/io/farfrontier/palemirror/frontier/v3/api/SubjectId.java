package io.farfrontier.palemirror.frontier.v3.api;

/** Stable identity of the canonical subject affected by a command, event or schedule. */
public record SubjectId(String value) implements Comparable<SubjectId> {
    public SubjectId {
        value = Identifier.require(value, "subject id");
    }

    @Override
    public int compareTo(SubjectId other) {
        return value.compareTo(other.value);
    }
}
