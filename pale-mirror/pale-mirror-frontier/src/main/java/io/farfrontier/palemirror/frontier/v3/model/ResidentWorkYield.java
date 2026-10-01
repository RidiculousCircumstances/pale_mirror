package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Read-only safe-point assessment. The owning process still performs the actual pause. */
public record ResidentWorkYield(SubjectId residentId, HumanAssignment assignment, Status status) {
    public enum Status {
        READY, SCENE_OR_AMBIENT_AUTHORITY, CARRYING_RESOURCE, PENDING_PHYSICAL_EFFECT,
        OWNER_SAFETY_HOLD
    }

    public ResidentWorkYield {
        Objects.requireNonNull(residentId, "yield resident");
        Objects.requireNonNull(assignment, "yield assignment");
        Objects.requireNonNull(status, "yield status");
        if (!residentId.equals(assignment.residentId()))
            throw new IllegalArgumentException("yield assessment must name its exact resident");
    }

    public boolean ready() { return status == Status.READY; }

    /** Every current work kind is deliberate; an unadapted family cannot accidentally yield. */
    public static ResidentWorkYield assess(FrontierWorldState state, HumanAssignment assignment) {
        return ActorExecutionCoordinator.workYield(state, assignment);
    }
}
