package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.group.TransportGroupMissionPort;
import java.util.*;

/** Seller-arranged dispatch port: trade decisions never own motion or a second cargo inventory. */
final class GoodsShipmentPlanning {
    static List<ProposedEvent> dispatch(FrontierWorldState state, GoodsParticipant seller, long now) {
        for (var contract : state.companies().goodsTrade().contracts().values().stream()
                .filter(value -> !value.terminal() && value.seller().equals(seller.party())
                        && !value.sourceContainerId().equals(value.receiverContainerId()))
                .sorted(Comparator.comparing(GoodsTradeContract::id)).toList()) {
            var buyer = state.companies().goodsTrade().participants().participants().get(contract.buyer().id());
            if (buyer == null || !buyer.party().equals(contract.buyer())) throw new IllegalArgumentException("accepted goods contract lost its declared buyer");
            var source = FungibleResourceCustodySupport.accountAtContainer(state, contract.sourceContainerId()).orElse(null);
            if (source == null) continue;
            for (var claimId : contract.outstandingClaims().keySet().stream().sorted().toList()) {
                if (state.shipments().holds(claimId) || !source.claimQuantities().containsKey(claimId)) continue;
                var claim = state.inventory().fungibleResources().claims().get(claimId);
                if (claim.quantity() > 64) continue; // Current policy batches are bounded; larger manual contracts retain their own partition protocol.
                var courier = state.humanPopulation().residents().values().stream()
                        .filter(resident -> resident.settlementId().equals(seller.endpoint().settlementId())
                                && resident.capability(HumanCapability.LOGISTICS) > 0
                                && state.unitGroups().groups().values().stream().noneMatch(group -> group.phase() != io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup.Phase.CLOSED
                                    && group.members().stream().anyMatch(member -> member.actorId().equals(resident.id())))
                                && ActorExecutionCoordinator.ordinaryWorkAdmission(state, resident.id()).permitted()
                                && ResidentActivityCoordinator.mayStartOrdinaryWork(state, resident.id(), now))
                        .sorted(Comparator.comparingInt((ResidentProfile resident) -> resident.capability(HumanCapability.LOGISTICS)).reversed()
                                .thenComparing(ResidentProfile::id)).findFirst();
                if (courier.isEmpty()) return List.of();
                var id = new SubjectId("shipment:goods/" + claimId.value().replace(':', '-'));
                if (state.shipments().shipments().containsKey(id)) continue;
                var receiving = FungibleResourceCustodySupport.accountAtContainer(state, buyer.endpoint().containerId())
                        .map(CustodyAccount::id).orElseGet(() -> ReferenceContainerCustody.scopeId(buyer.endpoint().containerId()));
                var shipment = new Shipment(id, new ResourceClaimDelegation(ResourceClaimDelegation.Kind.GOODS_CONTRACT_SHIPMENT,
                        claimId, contract.id(), id, contract.revision()), state.actorExecutions().next(courier.orElseThrow().id(), ActorActivityKind.COURIER, id),
                        seller.endpoint(), buyer.endpoint(), source.id(), new SubjectId("custody:" + id.value().replace(':', '-')),
                        receiving, contract.itemKind(), claim.lotQuantities(), Shipment.Status.AWAITING_LOAD, 1);
                if (!reachable(state, seller.endpoint(), buyer.endpoint(), id)) continue;
                var missionId = new SubjectId("transport-mission:goods/" + claimId.value().replace(':', '-'));
                var groupId = new SubjectId("unit-group:transport/" + claimId.value().replace(':', '-'));
                shipment = shipment.withMission(missionId);
                var members = new java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup.Member>();
                members.add(new io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup.Member(shipment.execution().actorId(),
                        io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup.Role.CARRIER, ActorActivityKind.COURIER, shipment.id()));
                var carrierId = shipment.execution().actorId();
                // Initial non-combat accompaniment. Escort tactics are a separate future mission capability.
                state.humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(seller.endpoint().settlementId())
                        && !resident.id().equals(carrierId) && resident.capability(HumanCapability.LOGISTICS) > 0
                        && state.unitGroups().groups().values().stream().noneMatch(existing -> existing.phase() != io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup.Phase.CLOSED
                            && existing.members().stream().anyMatch(member -> member.actorId().equals(resident.id())))
                        && ActorExecutionCoordinator.ordinaryWorkAdmission(state, resident.id()).permitted()
                        && ResidentActivityCoordinator.mayStartOrdinaryWork(state, resident.id(), now))
                        .sorted(Comparator.comparingInt((ResidentProfile resident) -> resident.capability(HumanCapability.LOGISTICS)).reversed()
                                .thenComparing(ResidentProfile::id)).findFirst().ifPresent(resident -> members.add(
                            new io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup.Member(resident.id(),
                                    io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup.Role.GUIDE, ActorActivityKind.GROUP_MEMBER, groupId)));
                var group = new io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup(groupId,
                        new io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup.Mission(
                                io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup.MissionKind.TRANSPORT, missionId), members,
                        io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup.Formation.COLUMN,
                        io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup.Phase.READY, 1, 0, Optional.empty());
                SurfaceAnchor home, destination;
                try {
                    home = TransportGroupMissionPort.rendezvous(state, seller.endpoint(), members.size());
                    destination = TransportGroupMissionPort.rendezvous(state, buyer.endpoint(), members.size());
                } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { continue; }
                if (state.unitGroups().groups().size() >= io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState.MAX_GROUPS
                        || state.shipments().shipments().size() >= ShipmentState.MAX_SHIPMENTS) return List.of();
                var mission = new TransportMission(missionId, groupId, List.of(shipment.id()), seller.endpoint(), buyer.endpoint(),
                        home, destination, TransportMission.Stage.LOADING, 1);
                var admitted = new TransportMissionStarted(seller.party().id(), seller.party().kind(), mission, group, List.of(shipment));
                TransportMissionProcess.admit(state, mission.id(), admitted);
                return List.of(new ProposedEvent(mission.id(), admitted),
                        new ProposedEvent(shipment.id(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(ShipmentProcess.progress(shipment.id(), now + 1))),
                        new ProposedEvent(group.id(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(UnitGroupProcess.progress(group.id(), now + 1))),
                        new ProposedEvent(mission.id(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(TransportMissionProcess.progress(mission.id(), now + 1))));
            }
        }
        return List.of();
    }
    static boolean reachable(FrontierWorldState state, ShipmentEndpoint sender, ShipmentEndpoint receiver, SubjectId owner) {
        return routeStatus(state, sender, receiver, owner) == PedestrianRouteResult.Status.FOUND;
    }
    static PedestrianRouteResult.Status routeStatus(FrontierWorldState state, ShipmentEndpoint sender, ShipmentEndpoint receiver, SubjectId owner) {
        if (sender.containerId().equals(receiver.containerId())) return PedestrianRouteResult.Status.FOUND;
        var order = new MovementOrder(owner, owner, 0, 1, List.of(receiver.station()),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        try {
            KnownPedestrianRouteKnowledge.forJourney(state, List.of(passage(state, sender), passage(state, receiver)))
                    .plannedPath(sender.station(), order);
            return PedestrianRouteResult.Status.FOUND;
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { return unavailable.status(); }
    }
    private static KnownPedestrianRouteKnowledge.SettlementPassage passage(FrontierWorldState state, ShipmentEndpoint endpoint) {
        ShipmentEndpointComposition.validate(state, endpoint);
        var facility = FrontierWorldStateSupport.settlement(state.bootstrap(), endpoint.settlementId()).structures().stream()
                .filter(value -> value.id().equals(endpoint.facilityId())).findFirst().orElseThrow();
        return new KnownPedestrianRouteKnowledge.SettlementPassage(endpoint.settlementId(),
                new KnownPedestrianRouteKnowledge.Passage(facility, KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS));
    }
    private GoodsShipmentPlanning() { }
}
