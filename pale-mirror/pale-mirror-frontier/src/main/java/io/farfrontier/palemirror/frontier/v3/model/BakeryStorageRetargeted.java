package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt;

/** Owner-local replacement of an unbegun output handoff, never a transfer of goods. */
public record BakeryStorageRetargeted(SubjectId jobId, StationWorkState.Phase phase,
                                      SubjectId previousContainer, SubjectId destinationContainer,
                                      ActorExecutionId executionId, Optional<PedestrianRouteReceipt> plannedRoute) implements FrontierPayload {
    public BakeryStorageRetargeted {
        Objects.requireNonNull(jobId); Objects.requireNonNull(phase);
        Objects.requireNonNull(previousContainer); Objects.requireNonNull(destinationContainer);
        Objects.requireNonNull(executionId); plannedRoute = Objects.requireNonNull(plannedRoute);
        if (executionId.activityKind() != ActorActivityKind.PRODUCTION || !executionId.activityOwnerId().equals(jobId))
            throw new IllegalArgumentException("bakery storage replacement has a foreign execution owner");
        if (phase == StationWorkState.Phase.DELIVERED || previousContainer.equals(destinationContainer))
            throw new IllegalArgumentException("bakery storage replacement needs a different uncompleted destination");
    }
    @Override public String type() { return "frontier.bakery_storage_retargeted"; }
}
