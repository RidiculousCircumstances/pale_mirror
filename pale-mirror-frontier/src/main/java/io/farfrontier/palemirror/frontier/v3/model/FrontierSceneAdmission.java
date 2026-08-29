package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Collection;
import java.util.Objects;

/** Pure admission predicates for exclusive scene and ambient execution. */
public final class FrontierSceneAdmission {
    private FrontierSceneAdmission() { }

    /** A scene must wait for every existing ambient executor to finish its own durable hand-off. */
    public static boolean available(FrontierWorldState state, Collection<SubjectId> actorIds) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(actorIds, "actor ids");
        return actorIds.stream().allMatch(actorId -> {
            AmbientActorLease lease = state.ambientLeases().get(Objects.requireNonNull(actorId, "actor id"));
            return lease == null || lease.status() == AmbientLeaseStatus.CLOSED;
        });
    }

    /**
     * A non-terminal HOT/COLD hand-off exclusively owns its route operation.  A COLD planner
     * must not advance or resolve that operation until the lease is closed: otherwise one
     * operation would have two simultaneous execution authorities.
     */
    public static boolean hasActiveSceneLease(FrontierWorldState state, SubjectId operationId) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(operationId, "operation id");
        return state.sceneLeases().values().stream().anyMatch(lease -> lease.operationId().equals(operationId)
                && lease.status() != SceneLeaseStatus.CLOSED);
    }

    /** A generic route scene must yield while the hive already owns an unresolved interception. */
    public static boolean hasUnresolvedRouteEngagement(FrontierWorldState state, SubjectId operationId) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(operationId, "operation id");
        return state.strategicPlans().routeEngagements().values().stream().anyMatch(engagement -> engagement.operationId().equals(operationId)
                && engagement.status() != RouteEngagementStatus.RESOLVED);
    }

    /** Admission for beginning a new COLD interception, not for progressing its own engagement. */
    public static boolean coldInterceptionAvailable(FrontierWorldState state, SubjectId operationId) {
        return !hasActiveSceneLease(state, operationId) && !hasUnresolvedRouteEngagement(state, operationId);
    }

    /** An active operation or pending engagement reserves exact actors before a player loads them. */
    public static boolean reserved(FrontierWorldState state, SubjectId actorId) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(actorId, "actor id");
        return state.operations().values().stream().anyMatch(operation -> operation.stage() == OperationStage.EN_ROUTE
                && operation.participantIds().contains(actorId))
                || state.coldEngagementSceneCandidates().stream().anyMatch(candidate -> candidate.actorIds().contains(actorId));
    }
}
