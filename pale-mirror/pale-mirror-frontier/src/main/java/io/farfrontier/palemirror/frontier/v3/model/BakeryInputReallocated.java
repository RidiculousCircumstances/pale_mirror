package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt;

/** Same accepted bread job reacquires its retained input quantity after a witnessed loss. */
public record BakeryInputReallocated(SubjectId jobId, SubjectId sourceContainerId, ProductionInputHold replacement,
                                    ActorExecutionId executionId, Optional<PedestrianRouteReceipt> plannedRoute) implements FrontierPayload {
    public BakeryInputReallocated {
        Objects.requireNonNull(jobId, "bakery job");
        Objects.requireNonNull(sourceContainerId, "replacement source storage");
        Objects.requireNonNull(replacement, "replacement input");
        Objects.requireNonNull(executionId, "replacement execution");
        plannedRoute = Objects.requireNonNull(plannedRoute, "replacement route evidence");
        if (executionId.activityKind() != ActorActivityKind.PRODUCTION || !executionId.activityOwnerId().equals(jobId))
            throw new IllegalArgumentException("bakery replacement has a foreign execution owner");
        if (!(replacement instanceof ProductionInputHold.FungibleCold
                || replacement instanceof ProductionInputHold.FungibleBound
                || replacement instanceof ProductionInputHold.Materialized))
            throw new IllegalArgumentException("bakery replacement must be a declared depot input");
    }
    @Override public String type() { return "frontier.bakery_input_reallocated"; }
}
