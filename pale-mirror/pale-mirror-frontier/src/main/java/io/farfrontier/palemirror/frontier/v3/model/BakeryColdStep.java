package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt;

import java.util.Objects;
import java.util.Optional;

/** One COLD-only movement, transfer or station-work fact for the currently retained bread job. */
public record BakeryColdStep(SubjectId jobId, BakeryWorkState.Phase expectedPhase, Action action,
                             SurfaceAnchor nextSurface, ActorExecutionId executionId,
                             Optional<PedestrianRouteReceipt> plannedRoute, Optional<BakeryLabourChange> labourChange) implements FrontierPayload {
    public enum Action {
        MOVE(1), PICKUP(2), LOAD(3), LABOUR(9), RECIPE(5), UNLOAD(6), DELIVER(7), FINALIZE(8);
        private final int wireTag;
        Action(int wireTag) { this.wireTag = wireTag; }
        public int wireTag() { return wireTag; }
        public static Action fromWireTag(int tag) {
            for (Action action : values()) if (action.wireTag == tag) return action;
            throw new IllegalArgumentException("unknown bakery step action tag: " + tag);
        }
    }

    public BakeryColdStep {
        Objects.requireNonNull(jobId, "bakery cold job");
        Objects.requireNonNull(expectedPhase, "bakery expected phase");
        Objects.requireNonNull(action, "bakery cold action");
        Objects.requireNonNull(nextSurface, "bakery next or current surface");
        Objects.requireNonNull(executionId, "bakery execution authority");
        plannedRoute = Objects.requireNonNull(plannedRoute, "bakery accepted route");
        labourChange = Objects.requireNonNull(labourChange, "bakery labour change");
        if (executionId.activityKind() != ActorActivityKind.PRODUCTION || !executionId.activityOwnerId().equals(jobId)
                || (action == Action.LABOUR) != labourChange.isPresent()
                || (action == Action.MOVE) != plannedRoute.isPresent()
                || plannedRoute.isPresent() && !plannedRoute.orElseThrow().route().get(1).equals(nextSurface))
            throw new IllegalArgumentException("bakery step needs its exact production owner and movement evidence");
    }

    public BakeryColdStep(SubjectId jobId, BakeryWorkState.Phase expectedPhase, Action action,
            SurfaceAnchor nextSurface, ActorExecutionId executionId, Optional<PedestrianRouteReceipt> route) {
        this(jobId, expectedPhase, action, nextSurface, executionId, route, Optional.empty());
    }

    @Override public String type() { return "frontier.bakery_cold_step"; }
}
