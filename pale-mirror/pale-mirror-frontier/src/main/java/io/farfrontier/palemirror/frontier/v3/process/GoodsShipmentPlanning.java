package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
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
                return ShipmentProcess.dispatch(state, seller.party().id(), shipment, now);
            }
        }
        return List.of();
    }
    static boolean reachable(FrontierWorldState state, ShipmentEndpoint sender, ShipmentEndpoint receiver, SubjectId owner) {
        if (sender.containerId().equals(receiver.containerId())) return true;
        var order = new MovementOrder(owner, owner, 0, 1, List.of(receiver.station()),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        try {
            KnownPedestrianRouteKnowledge.forJourney(state, List.of(passage(state, sender), passage(state, receiver)))
                    .path(sender.station(), order);
            return true;
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { return false; }
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
