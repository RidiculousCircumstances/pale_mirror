package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** One exact resident's current activity, derived from the canonical record that owns it. */
public record HumanAssignment(SubjectId residentId, HumanAssignmentKind kind, Optional<SubjectId> ownerId) {
    public HumanAssignment {
        Objects.requireNonNull(residentId, "assignment resident"); Objects.requireNonNull(kind, "assignment kind");
        ownerId = Optional.ofNullable(ownerId).orElse(Optional.empty());
        if (!residentId.value().startsWith("resident:")) throw new IllegalArgumentException("assignment resident must be canonical");
        if ((kind == HumanAssignmentKind.IDLE) != ownerId.isEmpty()) throw new IllegalArgumentException("only idle assignment has no owner");
    }

    public static HumanAssignment idle(SubjectId residentId) { return new HumanAssignment(residentId, HumanAssignmentKind.IDLE, Optional.empty()); }
    public boolean active() { return kind != HumanAssignmentKind.IDLE; }
}
