package io.farfrontier.palemirror.frontier.v3.model.group;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.List;
import java.util.Optional;

/** Transport policy is outside the generic roster, formation and movement algorithms. */
public final class TransportGroupMissionPort implements UnitGroupMissionPort {
    @Override public UnitGroup.MissionKind kind() { return UnitGroup.MissionKind.TRANSPORT; }
    public static TransportMission mission(FrontierWorldState state, UnitGroup group) {
        var mission = state.shipments().missions().get(group.mission().id());
        if (group.mission().kind() != UnitGroup.MissionKind.TRANSPORT || mission == null || !mission.groupId().equals(group.id()))
            throw new IllegalArgumentException("group lacks its exact transport mission");
        return mission;
    }
    @Override public void validate(FrontierWorldState state, UnitGroup group) {
        var mission = mission(state, group);
        validateDeclaration(state, mission, group, state.shipments().shipments());
        validateExecutions(state, group);
    }
    public static void validateDeclaration(FrontierWorldState state, TransportMission mission, UnitGroup group,
            java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, Shipment> shipments) {
        if (!mission.groupId().equals(group.id()) || !group.mission().equals(new UnitGroup.Mission(UnitGroup.MissionKind.TRANSPORT, mission.id())))
            throw new IllegalArgumentException("transport declaration has a foreign nominal group/mission tuple");
        var carriers = new java.util.HashSet<io.farfrontier.palemirror.frontier.v3.api.SubjectId>();
        for (var member : group.members()) {
            if (!state.actorLocations().containsKey(member.actorId()) || state.humanPopulation().resident(member.actorId()) == null)
                throw new IllegalArgumentException("transport group names an unknown resident actor");
            if (member.role() == UnitGroup.Role.CARRIER) {
                var shipment = shipments.get(member.activityOwnerId());
                if (member.activityKind() != ActorActivityKind.COURIER || shipment == null
                        || !mission.shipmentIds().contains(shipment.id()) || !shipment.execution().actorId().equals(member.actorId())
                        || !shipment.transportMissionId().equals(Optional.of(mission.id()))
                        || !shipment.sender().equals(mission.sender()) || !shipment.receiver().equals(mission.receiver())
                        || !carriers.add(shipment.id())) throw new IllegalArgumentException("transport carrier has a forged responsibility");
            } else if (member.role() != UnitGroup.Role.GUIDE || member.activityKind() != ActorActivityKind.GROUP_MEMBER || !member.activityOwnerId().equals(group.id()))
                throw new IllegalArgumentException("transport support member has a forged responsibility");
        }
        if (!carriers.equals(java.util.Set.copyOf(mission.shipmentIds())))
            throw new IllegalArgumentException("transport roster does not cover its exact shipments");
    }
    private static void validateExecutions(FrontierWorldState state, UnitGroup group) {
        for (var member : group.members()) {
            var retained = state.actorExecutions().actors().get(member.actorId());
            if (retained == null) continue;
            for (var execution : java.util.stream.Stream.concat(retained.current().stream(), retained.suspended().stream()).toList()) {
                if (execution.activityKind() != ActorActivityKind.GROUP_MEMBER) continue;
                if (!execution.activityOwnerId().equals(group.id()) || group.phase() == UnitGroup.Phase.CLOSED
                        || member.role() == UnitGroup.Role.CARRIER && !state.shipments().shipments().get(member.activityOwnerId()).terminal())
                    throw new IllegalArgumentException("group execution has a foreign or prematurely released participant responsibility");
            }
        }
    }
    @Override public KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, UnitGroup group) {
        validate(state, group); var mission = mission(state, group);
        return KnownPedestrianRouteKnowledge.forJourney(state, List.of(passage(state, mission.sender()), passage(state, mission.receiver())));
    }
    private static KnownPedestrianRouteKnowledge.SettlementPassage passage(FrontierWorldState state, ShipmentEndpoint endpoint) {
        ShipmentEndpointComposition.validate(state, endpoint);
        var facility = FrontierWorldStateSupport.settlement(state.bootstrap(), endpoint.settlementId()).structures().stream()
                .filter(value -> value.id().equals(endpoint.facilityId())).findFirst().orElseThrow();
        return new KnownPedestrianRouteKnowledge.SettlementPassage(endpoint.settlementId(),
                new KnownPedestrianRouteKnowledge.Passage(facility, KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS));
    }
    @Override public SurfaceAnchor destination(FrontierWorldState state, UnitGroup group, long ordinal) {
        var mission = mission(state, group);
        if (ordinal == 1) return mission.destinationRendezvous();
        else if (ordinal == 2) return mission.homeRendezvous();
        else throw new IllegalArgumentException("transport has no declared journey ordinal");
    }
    public static SurfaceAnchor rendezvous(FrontierWorldState state, ShipmentEndpoint endpoint, int members) {
        var passage = passage(state, endpoint);
        var service = SettlementDepotServicePort.forDepot(passage.passage().facility());
        return io.farfrontier.palemirror.frontier.v3.model.group.GroupRendezvous.select(
                KnownPedestrianRouteKnowledge.forJourney(state, List.of(passage)), service.exteriorApproach(),
                service.accessBoundary().occupiedSurfaces(), members);
    }
    public static void validateRendezvous(FrontierWorldState state, ShipmentEndpoint endpoint, SurfaceAnchor target, int members) {
        var passage = passage(state, endpoint); var service = SettlementDepotServicePort.forDepot(passage.passage().facility());
        var geometry = KnownPedestrianRouteKnowledge.forJourney(state, List.of(passage)).geometry();
        if (!geometry.bounds().contains(target.support()) || !target.equals(geometry.supportAt(target.x(), target.z())) || geometry.blocked(target)
                || service.accessBoundary().occupiedSurfaces().stream().anyMatch(surface -> Math.abs((long) surface.x() - target.x())
                    + Math.abs((long) surface.z() - target.z()) < members * 2L))
            throw new IllegalArgumentException("transport admission lacks a known assembly point outside its service boundary");
    }
    @Override public List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> reconsider(FrontierWorldState state, UnitGroup group, long tick) {
        return List.of(TransportMissionContinuation.wake(mission(state, group).id(), tick));
    }
    @Override public Optional<ActorExecutionId> execution(FrontierWorldState state, UnitGroup group, UnitGroup.Member member) {
        validate(state, group); group.member(member.actorId());
        if (state.actorLocations().get(member.actorId()).condition().status() != ActorLifeStatus.ALIVE
                || state.humanPopulation().meals().containsKey(member.actorId())) return Optional.empty();
        if (member.role() == UnitGroup.Role.CARRIER && !state.shipments().shipments().get(member.activityOwnerId()).terminal()) {
            var shipment = state.shipments().shipments().get(member.activityOwnerId());
            if (shipment.pendingPhysicalStep().isPresent()) return Optional.empty();
            var current = state.actorExecutions().actors().get(member.actorId());
            return current != null && current.current().equals(Optional.of(shipment.execution()))
                    ? Optional.of(shipment.execution()) : Optional.empty();
        }
        var current = state.actorExecutions().actors().get(member.actorId());
        if (current != null && current.current().isPresent()) {
            var id = current.current().orElseThrow();
            if (id.activityKind() == ActorActivityKind.GROUP_MEMBER && id.activityOwnerId().equals(group.id())) return Optional.of(id);
        }
        if (ActorExecutionCoordinator.groupWorkAdmission(state, member.actorId(), group.id()).permitted())
            return Optional.of(state.actorExecutions().next(member.actorId(), ActorActivityKind.GROUP_MEMBER, group.id()));
        return Optional.empty();
    }
    @Override public boolean mayTravel(FrontierWorldState state, UnitGroup group, long ordinal) {
        var mission = mission(state, group);
        return ordinal == 1 && mission.stage() == TransportMission.Stage.OUTBOUND
                || ordinal == 2 && mission.stage() == TransportMission.Stage.RETURNING;
    }
    @Override public boolean mayClose(FrontierWorldState state, UnitGroup group) {
        return mission(state, group).stage() == TransportMission.Stage.RETURNING && group.goalOrdinal() == 2;
    }
}
