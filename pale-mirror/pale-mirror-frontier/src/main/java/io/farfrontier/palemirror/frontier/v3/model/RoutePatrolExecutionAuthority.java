package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Map;
import java.util.Optional;

/** The patrol owns its exact crew purpose; no body, route or progress is copied into UAE. */
public final class RoutePatrolExecutionAuthority {
    private RoutePatrolExecutionAuthority() { }
    public static ActorExecutionGroup admission(FrontierWorldState state, RoutePatrol patrol) {
        return new ActorExecutionGroup(patrol.memberIds().stream().map(actor ->
                state.actorExecutions().next(actor, ActorActivityKind.ROUTE_PATROL, patrol.taskId())).toList());
    }
    public static ActorExecutionGroup current(FrontierWorldState state, RoutePatrol patrol) {
        return new ActorExecutionGroup(patrol.memberIds().stream().map(actor -> {
            var id = state.actorExecutions().current(ActorActivityKind.ROUTE_PATROL).get(actor);
            if (id == null || !id.activityOwnerId().equals(patrol.taskId()))
                throw new IllegalArgumentException("patrol lost an exact current crew execution");
            state.actorExecutions().requireCurrent(id);
            return id;
        }).toList());
    }
    public static void requireCurrent(FrontierWorldState state, RoutePatrol patrol, ActorExecutionGroup group) {
        group.requireDeclaration(ActorActivityKind.ROUTE_PATROL, patrol.taskId(), patrol.memberIds());
        group.requireCurrent(state.actorExecutions());
    }
    public static ActorExecutionState retired(FrontierWorldState state, RoutePatrol patrol) {
        return ActorExecutionComposition.LIFECYCLE.retireCurrentGroup(state.actorExecutions(), current(state, patrol));
    }
    static void validateReferences(Map<SubjectId, RoutePatrol> patrols, ActorExecutionState executions) {
        for (var id : executions.current(ActorActivityKind.ROUTE_PATROL).values()) require(patrols, id);
        for (var patrol : patrols.values()) if (patrol.active()) for (var actor : patrol.memberIds()) {
            var id = executions.current(ActorActivityKind.ROUTE_PATROL).get(actor);
            if (id == null || !id.activityOwnerId().equals(patrol.taskId()))
                throw new IllegalArgumentException("active patrol lacks its declared participant execution");
        }
    }
    private static RoutePatrol require(Map<SubjectId, RoutePatrol> patrols, ActorExecutionId id) {
        var patrol = patrols.get(id.activityOwnerId());
        if (id.activityKind() != ActorActivityKind.ROUTE_PATROL || patrol == null || !patrol.active()
                || !patrol.memberIds().contains(id.actorId()))
            throw new IllegalArgumentException("patrol execution has a foreign or terminal owner");
        return patrol;
    }
    static ActorActivityCapability capability() { return new ActorActivityCapability() {
        @Override public ActorActivityKind kind() { return ActorActivityKind.ROUTE_PATROL; }
        @Override public Interruption interruption() { return Interruption.TERMINAL_ONLY; }
        @Override public void validateReference(FrontierWorldState state, ActorExecutionId id) {
            require(state.strategicPlans().routePatrols(), id);
        }
        @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId id) {
            var patrol = require(state.strategicPlans().routePatrols(), id);
            return new ActorActivityCheckpoint(state, id, Optional.of(new ActorActivityCheckpoint.Wait(
                    ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY, patrol.taskId())));
        }
        @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId id, long atTick) {
            throw new IllegalArgumentException("patrol crew must settle its coordinated inspection");
        }
        @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId id, long atTick) {
            throw new IllegalArgumentException("patrol has no suspended continuation");
        }
        @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId id) {
            throw new IllegalArgumentException("patrol retires through its exact terminal owner outcome");
        }
    }; }
}
