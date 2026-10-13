package io.farfrontier.palemirror.frontier.v3.model.group;

import io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.List;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementPermission;

/** Transport policy is outside the generic roster, formation and movement algorithms. */
public final class TransportGroupMissionPort implements UnitGroupMissionPort {
    @Override public UnitGroup.MissionKind kind() { return UnitGroup.MissionKind.TRANSPORT; }
    @Override public GroupTravelPolicy travelPolicy(FrontierWorldState state, UnitGroup group) {
        mission(state, group);
        var rules = state.bootstrap().ruleset().expedition();
        return new GroupTravelPolicy(rules.formationSpacing(), rules.maxFormationStretch(), rules.ticksPerRouteEdge());
    }
    @Override public boolean permitsHomeFood(FrontierWorldState state, UnitGroup group) {
        var mission = mission(state, group);
        return mission.preparingSupplies() || mission.stage() == TransportMission.Stage.COMPLETE;
    }
    public static TransportMission mission(FrontierWorldState state, UnitGroup group) {
        var mission = state.shipments().missions().get(group.mission().id());
        if (group.mission().kind() != UnitGroup.MissionKind.TRANSPORT || mission == null || !mission.groupId().equals(group.id()))
            throw new IllegalArgumentException("group lacks its exact transport mission");
        return mission;
    }
    @Override public void validate(FrontierWorldState state, UnitGroup group) {
        var mission = mission(state, group);
        validateDeclaration(state, mission, group, state.shipments().shipments());
        validateExecutions(state.actorExecutions(), state.shipments(), group);
    }
    public static void validateDeclaration(FrontierWorldState state, TransportMission mission, UnitGroup group,
            java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, Shipment> shipments) {
        if (!mission.groupId().equals(group.id()) || !group.mission().equals(new UnitGroup.Mission(UnitGroup.MissionKind.TRANSPORT, mission.id())))
            throw new IllegalArgumentException("transport declaration has a foreign nominal group/mission tuple");
        mission.organization().require(state);
        var expectedHome = switch (mission.organization().itinerary()) { case SOURCE_DELIVERY -> mission.sender(); case DESTINATION_COLLECTION -> mission.receiver(); };
        if (!mission.organization().home().equals(expectedHome)) throw new IllegalArgumentException("transport home contradicts its declared itinerary");
        var carriers = new java.util.HashSet<io.farfrontier.palemirror.frontier.v3.api.SubjectId>();
        for (var member : group.members()) {
            var actor = state.actorLocations().get(member.actorId());
            if (actor == null || !(actor.kind() == ActorKind.RESIDENT && state.humanPopulation().resident(member.actorId()) != null
                    || actor.kind() == ActorKind.PACK_ANIMAL && mission.transportAssetId().equals(Optional.of(member.actorId()))
                        && member.role() == UnitGroup.Role.CARRIER))
                throw new IllegalArgumentException("transport group names an undeclared human or finite transport participant");
            if (member.role() == UnitGroup.Role.CARRIER) {
                if (member.activityKind() == ActorActivityKind.GROUP_MEMBER) {
                    if (!member.activityOwnerId().equals(group.id())) throw new IllegalArgumentException("manifest carrier has a foreign group owner");
                    var assigned = mission.shipmentIds().stream().map(shipments::get)
                            .filter(s -> s.execution().actorId().equals(member.actorId())).toList();
                    if (assigned.isEmpty() || assigned.size() > 1 && !mission.transportAssetId().equals(Optional.of(member.actorId())))
                        throw new IllegalArgumentException("manifest carrier exceeds its declared personal capacity");
                    for (var parcel : assigned) {
                        if (!TransportCargoExecution.shared(parcel) || !parcel.execution().activityOwnerId().equals(group.id())
                                || !parcel.transportMissionId().equals(Optional.of(mission.id()))
                                || !parcel.sender().equals(mission.sender()) || !parcel.receiver().equals(mission.receiver())
                                || !carriers.add(parcel.id())) throw new IllegalArgumentException("manifest cargo has a foreign responsibility");
                    }
                    continue;
                }
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
        mission.transportAssetId().ifPresent(actor -> {
            var asset = state.transportFleet().require(actor);
            if (!asset.homeSettlementId().equals(mission.organization().home().settlementId())
                    || group.members().stream().noneMatch(m -> m.actorId().equals(actor))
                    || shipments.values().stream().filter(s -> mission.shipmentIds().contains(s.id()))
                        .anyMatch(s -> !s.execution().actorId().equals(actor) || !s.mobileContainerId().equals(Optional.of(asset.containerId()))))
                throw new IllegalArgumentException("transport mission does not attach its declared goods to its finite asset");
        });
    }
    static void validateExecutions(ActorExecutionState executions, ShipmentState shipments, UnitGroup group) {
        for (var member : group.members()) {
            var retained = executions.actors().get(member.actorId());
            if (retained == null) continue;
            for (var execution : java.util.stream.Stream.concat(retained.current().stream(), retained.suspended().stream()).toList()) {
                if (execution.activityKind() != ActorActivityKind.GROUP_MEMBER) continue;
                // A closed roster is history, not an exclusive claim on its former members.
                // Still reject a retained execution of THIS closed group (including suspended work).
                if (group.phase() == UnitGroup.Phase.CLOSED && !execution.activityOwnerId().equals(group.id())) continue;
                if (!execution.activityOwnerId().equals(group.id()) || group.phase() == UnitGroup.Phase.CLOSED
                        || member.role() == UnitGroup.Role.CARRIER && member.activityKind() == ActorActivityKind.COURIER
                            && !shipments.shipments().get(member.activityOwnerId()).terminal())
                    throw new IllegalArgumentException("group execution has a foreign or prematurely released participant responsibility: group="
                            + group.id().value() + " phase=" + group.phase() + " actor=" + member.actorId().value()
                            + " executionOwner=" + execution.activityOwnerId().value());
            }
        }
    }
    @Override public KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, UnitGroup group) {
        validate(state, group); var mission = mission(state, group);
        return knowledgeForEndpoints(state, mission.sender(), mission.receiver()).forProfile(
                UnitNavigationProfile.enclosing(
                        group.members().stream().map(member -> state.actorLocations().get(member.actorId()).navigationProfile()).toList()));
    }
    public static KnownPedestrianRouteKnowledge knowledgeForEndpoints(FrontierWorldState state,
            ShipmentEndpoint sender, ShipmentEndpoint receiver) {
        return ShipmentEndpointComposition.knowledge(state, List.of(sender, receiver));
    }
    @Override public SurfaceAnchor destination(FrontierWorldState state, UnitGroup group, long ordinal) {
        var mission = mission(state, group);
        if (ordinal == 1) return mission.destinationRendezvous();
        else if (ordinal == 2) return mission.homeRendezvous();
        else throw new IllegalArgumentException("transport has no declared journey ordinal");
    }
    public static SurfaceAnchor rendezvous(FrontierWorldState state, ShipmentEndpoint endpoint, int members) {
        return io.farfrontier.palemirror.frontier.v3.model.group.GroupRendezvous.select(
                ShipmentEndpointComposition.knowledge(state, List.of(endpoint)), ShipmentEndpointComposition.exteriorApproach(state, endpoint),
                ServiceAccessCoordinator.boundary(state, endpoint.containerId()).occupiedSurfaces(), members);
    }
    public static SurfaceAnchor rendezvous(FrontierWorldState state, ShipmentEndpoint endpoint, int members,
            UnitNavigationProfile profile) {
        var knowledge = ShipmentEndpointComposition.knowledge(state, List.of(endpoint)).forProfile(profile);
        return GroupRendezvous.select(knowledge, endpoint.station(),
                ServiceAccessCoordinator.boundary(state, endpoint.containerId()).occupiedSurfaces(), members);
    }
    public static void validateRendezvous(FrontierWorldState state, ShipmentEndpoint endpoint, SurfaceAnchor target, int members) {
        var geometry = ShipmentEndpointComposition.knowledge(state, List.of(endpoint)).geometry();
        var boundary = ServiceAccessCoordinator.boundary(state, endpoint.containerId());
        if (!geometry.bounds().contains(target.support()) || !geometry.knowsSupport(target) || geometry.blocked(target)
                || boundary.occupiedSurfaces().stream().anyMatch(surface -> Math.abs((long) surface.x() - target.x())
                    + Math.abs((long) surface.z() - target.z()) < members * 2L))
            throw new IllegalArgumentException("transport admission lacks a known assembly point outside its service boundary");
    }
    @Override public List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> reconsider(FrontierWorldState state, UnitGroup group, long tick) {
        var mission = mission(state, group);
        var events = new java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent>();
        events.add(TransportMissionContinuation.wake(mission.id(), tick));
        for (var id : mission.shipmentIds()) if (!state.shipments().shipments().get(id).terminal())
            events.add(ShipmentContinuation.wake(id, tick));
        return List.copyOf(events);
    }
    @Override public Optional<ActorExecutionId> execution(FrontierWorldState state, UnitGroup group, UnitGroup.Member member) {
        validate(state, group); group.member(member.actorId());
        if (state.actorLocations().get(member.actorId()).condition().status() != ActorLifeStatus.ALIVE
                || state.humanPopulation().meals().containsKey(member.actorId())) return Optional.empty();
        if (member.role() == UnitGroup.Role.CARRIER && member.activityKind() == ActorActivityKind.COURIER
                && !state.shipments().shipments().get(member.activityOwnerId()).terminal()) {
            var shipment = state.shipments().shipments().get(member.activityOwnerId());
            if (shipment.pendingPhysicalStep().isPresent()) return Optional.empty();
            var current = state.actorExecutions().actors().get(member.actorId());
            return current != null && current.current().equals(Optional.of(shipment.execution()))
                    ? Optional.of(shipment.execution()) : Optional.empty();
        }
        var current = state.actorExecutions().actors().get(member.actorId());
        if (current != null && current.current().isPresent()) {
            var id = current.current().orElseThrow();
            if (id.activityKind() == ActorActivityKind.GROUP_MEMBER && id.activityOwnerId().equals(group.id()))
                return checkpoint(state, group, id).ready() ? Optional.of(id) : Optional.empty();
        }
        if (ActorExecutionCoordinator.groupWorkAdmission(state, member.actorId(), group.id()).permitted())
            return Optional.of(state.actorExecutions().next(member.actorId(), ActorActivityKind.GROUP_MEMBER, group.id()));
        return Optional.empty();
    }
    @Override public boolean mayTravel(FrontierWorldState state, UnitGroup group, long ordinal) {
        var mission = mission(state, group);
        return mission.replenishment().isEmpty() && (ordinal == 1 && mission.stage() == TransportMission.Stage.TO_SOURCE
                || ordinal == mission.deliveryOrdinal() && mission.stage() == TransportMission.Stage.OUTBOUND
                || ordinal == mission.returnOrdinal() && mission.stage() == TransportMission.Stage.RETURNING);
    }
    @Override public MovementPermission movementPermission(FrontierWorldState state, UnitGroup group) {
        var mission = mission(state, group);
        if (requestsJourneyStop(state, group)) return MovementPermission.hold(MovementPermission.Reason.MISSION_STOP_REQUESTED);
        if (mission.replenishment().isPresent()) return MovementPermission.hold(MovementPermission.Reason.REFILL_TRANSFER);
        if (io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.requiresLocalStock(state, mission))
            return MovementPermission.hold(MovementPermission.Reason.FOOD_STOCK_REQUIRED);
        return MovementPermission.allow();
    }
    @Override public boolean requestsJourneyStop(FrontierWorldState state, UnitGroup group) {
        var mission = mission(state, group);
        return (mission.stage() == TransportMission.Stage.OUTBOUND || mission.stage() == TransportMission.Stage.TO_SOURCE)
                && mission.shipmentIds().stream().allMatch(id -> state.shipments().shipments().get(id).terminal());
    }
    @Override public boolean mayClose(FrontierWorldState state, UnitGroup group) {
        return mission(state, group).stage() == TransportMission.Stage.RETURNING
                && (group.phase() == UnitGroup.Phase.AT_GOAL && group.goalOrdinal() == 2
                    || group.members().stream().allMatch(member -> state.actorLocations().get(member.actorId()).condition().status() == ActorLifeStatus.DEAD));
    }
    @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, UnitGroup group, ActorExecutionId execution) {
        var pending = TransportCargoExecution.parcels(state, group, execution).stream().filter(s -> s.pendingPhysicalStep().isPresent()).findFirst();
        return new ActorActivityCheckpoint(state, execution, pending.map(s ->
                new ActorActivityCheckpoint.Wait(ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION, s.id())));
    }
    @Override public void requireBodyRelease(FrontierWorldState state, UnitGroup group, ActorExecutionId execution) {
        for (var parcel : TransportCargoExecution.parcels(state, group, execution))
            if (parcel.pendingPhysicalStep().isPresent() || state.inventory().fungibleResources().bindings().values().stream()
                    .anyMatch(binding -> binding.accountId().equals(parcel.carriedAccountId())))
                throw new IllegalArgumentException("transport body release retains a bound cargo/effect");
    }
    @Override public FrontierWorldStateUpdate resumption(FrontierWorldState state, UnitGroup group, ActorExecutionId predecessor, ActorExecutionId successor) {
        return FrontierWorldStateUpdate.begin().shipments(state.shipments().resumeManifest(
                TransportCargoExecution.parcels(state, group, predecessor), predecessor, successor));
    }
    @Override public Optional<ActorCarriedResources.Presentation> workCargo(FrontierWorldState state, UnitGroup group, UnitGroup.Member member) {
        var mission = mission(state, group); group.member(member.actorId());
        var carried = mission.shipmentIds().stream().map(state.shipments().shipments()::get)
                .filter(s -> TransportCargoExecution.shared(s) && s.execution().actorId().equals(member.actorId())
                        && s.mobileContainerId().isEmpty() && !s.terminal() && s.status() == Shipment.Status.CARRYING).toList();
        if (carried.size() > 1) throw new IllegalArgumentException("resident manifest exceeds one work hand");
        return carried.isEmpty() ? Optional.empty() : Optional.of(new ActorCarriedResources.Presentation(member.actorId(),
                carried.getFirst().carriedAccountId(), new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.MAIN)));
    }
}
