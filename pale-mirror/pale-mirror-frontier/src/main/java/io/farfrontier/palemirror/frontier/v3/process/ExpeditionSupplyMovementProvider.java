package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;
import io.farfrontier.palemirror.frontier.v3.model.group.TransportGroupMissionPort;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Supply collection uses the common navigator and the participant's existing UAE authority. */
public final class ExpeditionSupplyMovementProvider implements ActorMovementProvider {
    @Override public ActorMovementContext.Provider key() { return ActorMovementContext.Provider.EXPEDITION_SUPPLY; }
    private TransportMission mission(FrontierWorldState state, ActorMovement movement) {
        if (movement.context() instanceof ActorMovementContext.ExpeditionReplenishment refill) {
            var mission = io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.require(state, refill.missionId());
            var transfer = mission.replenishment().orElseThrow();
            if (transfer.pending().isPresent() || !transfer.execution().equals(movement.executionId())
                    || !transfer.order(mission.id(), mission.revision()).movementOrder().equals(movement.order()))
                throw new IllegalArgumentException("replenishment movement lost its exact stock instruction or execution");
            state.actorExecutions().requireCurrent(transfer.execution());
            return mission;
        }
        if (movement.context() instanceof ActorMovementContext.ExpeditionAssembly assembly) {
            var mission = state.shipments().missions().get(assembly.missionId());
            if (mission == null || mission.stage() != TransportMission.Stage.LOADING || mission.supplies().isEmpty()
                    || !ExpeditionSupplyAuthority.assemblyOrder(mission, movement.order().actorId()).equals(movement.order())
                    || mission.supplies().orElseThrow().allocations().stream().anyMatch(a -> a.actorId().equals(movement.order().actorId()) && !a.loaded()))
                throw new IllegalArgumentException("assembly movement lacks a provisioned participant and its exact station");
            var group = state.unitGroups().groups().get(mission.groupId());
            if (!io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupMissionPorts.require(group)
                    .execution(state, group, group.member(movement.order().actorId())).equals(Optional.of(movement.executionId())))
                throw new IllegalArgumentException("assembly movement has foreign participant authority");
            return mission;
        }
        if (!(movement.context() instanceof ActorMovementContext.ExpeditionSupply context)) throw new IllegalArgumentException("foreign supply movement context");
        var mission = state.shipments().missions().get(context.missionId());
        if (mission == null || mission.stage() != TransportMission.Stage.LOADING || mission.supplies().isEmpty())
            throw new IllegalArgumentException("supply movement lost its loading mission");
        var load = mission.supplies().orElseThrow(); var allocation = load.allocations().stream()
                .filter(a -> a.claimId().equals(context.claimId()) && !a.loaded()).findFirst().orElseThrow();
        var order = load.order(mission.id(), mission.sender(), allocation).movementOrder();
        if (!order.equals(movement.order()) || allocation.pending().isPresent()
                || !ExpeditionSupplyAuthority.execution(state, mission, allocation).equals(Optional.of(movement.executionId())))
            throw new IllegalArgumentException("supply movement has a stale goal or foreign participant authority");
        return mission;
    }
    @Override public void validate(FrontierWorldState state, ActorMovement movement) { mission(state, movement); }
    @Override public FrontierWorldState start(FrontierWorldState state, ActorMovement movement, FrontierWorldStateUpdate update) {
        validate(state, movement); state.actorExecutions().requireCurrent(movement.executionId()); return state.withChanges(update);
    }
    @Override public List<SurfaceAnchor> route(FrontierWorldState state, ActorMovement movement, SurfaceAnchor start) {
        var m = mission(state, movement); return TransportGroupMissionPort.knowledgeForEndpoints(state, m.sender(), m.receiver()).plannedPath(start, movement.order());
    }
    @Override public void requireRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) {
        var m = mission(state, movement); TransportGroupMissionPort.knowledgeForEndpoints(state, m.sender(), m.receiver()).requireRoute(route);
        if (!movement.order().arrivedAt(route.getLast()) && route.size() != TimedKnownRoute.MAX_SURFACES)
            throw new IllegalArgumentException("supply route is neither a bounded prefix nor its declared station");
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
    @Override public List<ProposedEvent> arrived(FrontierWorldState state, ActorMovement movement, long tick) { return List.of(TransportMissionProcess.wake(mission(state, movement).id(), tick)); }
    @Override public List<ProposedEvent> interrupted(FrontierWorldState state, ActorMovement movement, long tick) { return arrived(state, movement, tick); }
}
