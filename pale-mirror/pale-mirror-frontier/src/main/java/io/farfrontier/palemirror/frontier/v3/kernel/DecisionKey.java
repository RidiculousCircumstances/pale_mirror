package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Immutable key for a random decision; unrelated decisions never share a stream. */
public record DecisionKey(long worldSeed, String subsystem, SubjectId subject, String decisionKind, long ordinal) {
    public DecisionKey {
        subsystem = requireToken(subsystem, "subsystem");
        Objects.requireNonNull(subject, "subject");
        decisionKind = requireToken(decisionKind, "decision kind");
        if (ordinal < 0L) {
            throw new IllegalArgumentException("decision ordinal cannot be negative");
        }
    }

    private static String requireToken(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("[a-z][a-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException(label + " is invalid: " + value);
        }
        return value;
    }
}
