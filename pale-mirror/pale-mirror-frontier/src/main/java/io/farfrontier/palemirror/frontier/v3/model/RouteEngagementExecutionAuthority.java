package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Map;
import java.util.Optional;

/** Interception owns its attacker cohort, not the transport crew or physical bodies. */
public final class RouteEngagementExecutionAuthority {
    private RouteEngagementExecutionAuthority() { }
    public static void requireOwner(ActorExecutionGroup group, SubjectId owner) {
        for (var id : group.members()) if (id.activityKind() != ActorActivityKind.ROUTE_INTERCEPTION
                || !id.activityOwnerId().equals(owner))
            throw new IllegalArgumentException("interception cohort has a foreign activity kind or owner");
    }
    public static ActorExecutionGroup admission(FrontierWorldState state, RouteEngagement engagement) {
        return new ActorExecutionGroup(engagement.attackerIds().stream().map(actor ->
                state.actorExecutions().next(actor, ActorActivityKind.ROUTE_INTERCEPTION, engagement.id())).toList());
    }
    public static ActorExecutionGroup current(FrontierWorldState state, RouteEngagement engagement) {
        return new ActorExecutionGroup(engagement.attackerIds().stream().map(actor -> {
            var id = state.actorExecutions().current(ActorActivityKind.ROUTE_INTERCEPTION).get(actor);
            if (id == null || !id.activityOwnerId().equals(engagement.id()))
                throw new IllegalArgumentException("interception lost its exact attacker execution");
            state.actorExecutions().requireCurrent(id);
            return id;
        }).toList());
    }
    public static void requireCurrent(FrontierWorldState state, RouteEngagement engagement, ActorExecutionGroup group) {
        group.requireDeclaration(ActorActivityKind.ROUTE_INTERCEPTION, engagement.id(), engagement.attackerIds());
        group.requireCurrent(state.actorExecutions());
    }
    /** The two sides keep different declared owners even while one engagement coordinates them. */
    public static ActorExecutionGroup combatCurrent(FrontierWorldState state, RouteEngagement engagement) {
        var members = new java.util.ArrayList<>(current(state, engagement).members());
        var operation = state.operations().get(engagement.operationId());
        if (operation == null) throw new IllegalArgumentException("interception lost its declared transport operation");
        members.addAll(OperationExecutionAuthority.logisticsCurrent(state, operation).members());
        return new ActorExecutionGroup(members);
    }
    public static ActorExecutionState retire(ActorExecutionState executions, ActorExecutionGroup group) {
        group.requireCurrent(executions);
        for (var id : group.members()) executions = ActorExecutionComposition.LIFECYCLE.retire(
                executions, id.actorId(), id.activityKind(), id.activityOwnerId());
        return executions;
    }
    /** The transport owner supplies the exact aborted engagements in its atomic interruption. */
    public static ActorExecutionState retireInterrupted(FrontierWorldState state, SubjectId operationId,
                                                       ActorExecutionState executions) {
        for (var engagement : state.strategicPlans().routeEngagements().values())
            if (engagement.operationId().equals(operationId) && engagement.status() != RouteEngagementStatus.RESOLVED)
                executions = retire(executions, current(state, engagement));
        return executions;
    }
    static void validateReferences(Map<SubjectId, RouteEngagement> engagements, ActorExecutionState executions) {
        for (var id : executions.current(ActorActivityKind.ROUTE_INTERCEPTION).values()) require(engagements, id);
        for (var engagement : engagements.values()) if (engagement.status() != RouteEngagementStatus.RESOLVED)
            for (var actor : engagement.attackerIds()) {
                var id = executions.current(ActorActivityKind.ROUTE_INTERCEPTION).get(actor);
                if (id == null || !id.activityOwnerId().equals(engagement.id()))
                    throw new IllegalArgumentException("active interception lacks its declared attacker execution");
            }
    }
    private static RouteEngagement require(Map<SubjectId, RouteEngagement> engagements, ActorExecutionId id) {
        var engagement = engagements.get(id.activityOwnerId());
        if (id.activityKind() != ActorActivityKind.ROUTE_INTERCEPTION || engagement == null
                || engagement.status() == RouteEngagementStatus.RESOLVED || !engagement.attackerIds().contains(id.actorId()))
            throw new IllegalArgumentException("interception execution has a foreign or terminal owner");
        return engagement;
    }
    static ActorActivityCapability capability() { return new ActorActivityCapability() {
        @Override public ActorActivityKind kind() { return ActorActivityKind.ROUTE_INTERCEPTION; }
        @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) { }
        @Override public ActorActivityBodyCheckpoint bodyCheckpoint() { return ActorActivityBodyCheckpoint.usesActorLocation(); }
        @Override public Interruption interruption() { return Interruption.TERMINAL_ONLY; }
        @Override public void validateReference(FrontierWorldState state, ActorExecutionId id) {
            require(state.strategicPlans().routeEngagements(), id);
        }
        @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId id, AmbientActorLease lease) {
            return false; // Approach/combat belong to the interception, never ambient fallback.
        }
        @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId id) {
            var engagement = require(state.strategicPlans().routeEngagements(), id);
            return new ActorActivityCheckpoint(state, id, Optional.of(new ActorActivityCheckpoint.Wait(
                    ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY, engagement.id())));
        }
        @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId id, long tick) {
            throw new IllegalArgumentException("interception must settle its coordinated operation");
        }
        @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId id, long tick) {
            throw new IllegalArgumentException("interception has no suspended continuation");
        }
        @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId id) {
            throw new IllegalArgumentException("interception outcome retires its exact attacker cohort");
        }
    }; }
}
