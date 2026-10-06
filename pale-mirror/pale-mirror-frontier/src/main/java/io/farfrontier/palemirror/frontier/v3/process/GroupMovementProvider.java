package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupMissionPorts;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.List;
import java.util.Optional;

/** Formation supplies an exact goal. The shared navigator alone chooses and executes each member's path. */
public final class GroupMovementProvider implements ActorMovementProvider {
    @Override public ActorMovementContext.Provider key() { return ActorMovementContext.Provider.GROUP; }
    private UnitGroup group(FrontierWorldState state, ActorMovement movement) {
        if (!(movement.context() instanceof ActorMovementContext.GroupLeg leg)) throw new IllegalArgumentException("foreign group movement context");
        var group = state.unitGroups().groups().get(leg.groupId());
        if (group == null || group.phase() != UnitGroup.Phase.TRAVELLING || group.revision() != leg.groupRevision()
                || !movement.order().ownerId().equals(group.id()) || movement.order().goalOrdinal() != group.goalOrdinal()
                || movement.order().goalRevision() != group.revision()
                || !movement.order().legalStations().equals(List.of(group.journey().orElseThrow().stations().get(movement.order().actorId())))
                || movement.order().capability() != TraversalCapability.PEDESTRIAN)
            throw new IllegalArgumentException("group movement has a stale or foreign formation goal");
        var port = UnitGroupMissionPorts.require(group); port.validate(state, group);
        if (!port.execution(state, group, group.member(movement.order().actorId())).equals(Optional.of(movement.executionId())))
            throw new IllegalArgumentException("group movement has a foreign participant execution");
        return group;
    }
    @Override public void validate(FrontierWorldState state, ActorMovement movement) { group(state, movement); }
    @Override public FrontierWorldState start(FrontierWorldState state, ActorMovement movement, FrontierWorldStateUpdate update) {
        group(state, movement);
        var current = state.actorExecutions().actors().get(movement.order().actorId());
        return current != null && current.current().equals(Optional.of(movement.executionId())) ? state.withChanges(update)
                : ActorExecutionComposition.LIFECYCLE.prepareVacant(state, movement.executionId()).commit(state, update);
    }
    @Override public List<SurfaceAnchor> route(FrontierWorldState state, ActorMovement movement, SurfaceAnchor start) {
        var group = group(state, movement); return UnitGroupMissionPorts.require(group).knowledge(state, group).plannedPath(start, movement.order());
    }
    @Override public void requireRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) {
        var group = group(state, movement); UnitGroupMissionPorts.require(group).knowledge(state, group).requireRoute(route);
        if (!movement.order().arrivedAt(route.getLast()) && route.size() != TimedKnownRoute.MAX_SURFACES)
            throw new IllegalArgumentException("group route is neither a bounded prefix nor its formation goal");
    }
    @Override public List<SurfaceAnchor> coldSegment(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) {
        validate(state, movement); return List.copyOf(route.subList(0, Math.min(route.size(), TimedKnownRoute.MAX_SURFACES)));
    }
    @Override public ActorExecutionState arrivalAuthority(FrontierWorldState state, ActorMovement movement) { validate(state, movement); return state.actorExecutions(); }
    @Override public Optional<BodyPosition> interruptionCheckpoint(FrontierWorldState state, ActorMovement movement, long tick) {
        validate(state, movement); return Optional.of(ActorMovementProcess.bodyAt(state, movement.order().actorId(), tick));
    }
    @Override public ActorExecutionState interruptionAuthority(FrontierWorldState state, ActorMovement movement) { validate(state, movement); return state.actorExecutions(); }
    @Override public boolean permitsReplacement(ResidentActivityChoice.Kind next) { return next == ResidentActivityChoice.Kind.EAT; }
    @Override public List<ProposedEvent> arrived(FrontierWorldState state, ActorMovement movement, long tick) {
        return List.of(UnitGroupProcess.wake(movement.order().ownerId(), tick));
    }
    @Override public List<ProposedEvent> interrupted(FrontierWorldState state, ActorMovement movement, long tick) {
        return List.of(UnitGroupProcess.wake(movement.order().ownerId(), tick));
    }
}
