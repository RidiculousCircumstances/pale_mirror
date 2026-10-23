package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.group.TransportGroupMissionPort;
import java.util.*;

/** Seller-arranged dispatch port: trade decisions never own motion or a second cargo inventory. */
final class GoodsShipmentPlanning {
    static SettlementStaffingPort.Demand staffingDemand(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules, long atTick) {
        var retained = state.shipments().shipments().values().stream().filter(shipment -> !shipment.terminal())
                .map(shipment -> shipment.execution().actorId())
                .filter(id -> state.actorLocations().get(id).kind() == ActorKind.RESIDENT)
                .filter(id -> state.humanPopulation().resident(id).settlementId().equals(home))
                .collect(java.util.stream.Collectors.toSet());
        return new SettlementStaffingPort.Demand(ResidentWorkKind.LOGISTICS, HumanCapability.LOGISTICS,
                rules.targetWorkers(), rules.minimumLocalStaff(), rules.priority(), retained);
    }
    /** Read-only cargo opportunity for priority comparison; it never invokes selection or dispatch. */
    static boolean availableWork(FrontierWorldState state, ResidentProfile resident, long now) {
        if (!SettlementLabourAllocation.canCommitToMission(state, resident.settlementId(),
                ResidentWorkKind.LOGISTICS, List.of(resident.id()))) return false;
        for (var contract : state.companies().goodsTrade().contracts().values().stream()
                .filter(value -> !value.terminal() && value.fulfillment().mode() == GoodsTradeFulfillment.Mode.SELLER_DELIVERY
                        && !value.sourceContainerId().equals(value.receiverContainerId()))
                .sorted(Comparator.comparing(GoodsTradeContract::id)).toList()) {
            var seller = state.companies().goodsTrade().participants().participants().get(contract.seller().id());
            var buyer = state.companies().goodsTrade().participants().participants().get(contract.buyer().id());
            if (seller == null || buyer == null || !seller.settlementId().equals(resident.settlementId())) continue;
            for (var claimId : contract.outstandingClaims().keySet().stream().sorted().toList()) {
                if (state.shipments().holds(claimId) || parcel(state, contract, claimId).isEmpty()) continue;
                var shipmentId = new SubjectId("shipment:goods/" + claimId.value().replace(':', '-'));
                if (!state.shipments().shipments().containsKey(shipmentId)
                        && reachable(state, GoodsParticipantStorage.endpoint(state, contract.sourceContainerId()), GoodsParticipantStorage.endpoint(state, contract.receiverContainerId()), shipmentId)) return true;
            }
        }
        return false;
    }
    static List<ProposedEvent> dispatch(FrontierWorldState state, GoodsParticipant seller, long now) {
        try { return dispatchReady(state, seller, now); }
        catch (KnownPedestrianNavigation.RouteUnavailable pending) { return List.of(); }
    }
    private static List<ProposedEvent> dispatchReady(FrontierWorldState state, GoodsParticipant seller, long now) {
        for (var contract : state.companies().goodsTrade().contracts().values().stream()
                .filter(c -> !c.terminal() && c.fulfillment().mode() == GoodsTradeFulfillment.Mode.SELLER_DELIVERY
                        && c.seller().equals(seller.party()) && !c.sourceContainerId().equals(c.receiverContainerId()))
                .sorted(Comparator.comparing(GoodsTradeContract::id)).toList()) {
            var buyer = state.companies().goodsTrade().participants().participants().get(contract.buyer().id());
            if (buyer == null) throw new IllegalArgumentException("accepted goods contract lost its buyer");
            for (var claim : contract.outstandingClaims().keySet().stream().sorted().toList()) {
                if (state.shipments().holds(claim)) continue;
                var parcel = parcel(state, contract, claim);
                if (parcel.isEmpty()) continue;
                var declaration = GoodsTripAssembly.prepare(state, seller, buyer, List.of(parcel.orElseThrow()), contract.fulfillment().mode(), now);
                if (declaration.isPresent()) return GoodsTripAssembly.events(declaration.orElseThrow(), now);
            }
        }
        return List.of();
    }
    private static Optional<GoodsTripAssembly.Parcel> parcel(FrontierWorldState state, GoodsTradeContract contract, SubjectId claimId) {
        var individual = contract.individualAllocations().get(claimId);
        if (individual != null) {
            var item = state.inventory().items().get(individual.itemId());
            return individual.matches(item) && item.custody() instanceof InventoryCustody.ContainerSlot slot
                    && slot.containerId().equals(contract.sourceContainerId())
                    ? Optional.of(new GoodsTripAssembly.Parcel(contract, new GoodsTripAssembly.Stock.Individual(individual))) : Optional.empty();
        }
        var source = FungibleResourceCustodySupport.accountAtContainer(state, contract.sourceContainerId()).orElse(null);
        var claim = state.inventory().fungibleResources().claims().get(claimId);
        return source != null && claim != null && source.claimQuantities().getOrDefault(claimId, 0) == claim.quantity()
                && claim.quantity() <= 64 ? Optional.of(new GoodsTripAssembly.Parcel(contract, claim, source.id())) : Optional.empty();
    }
    static boolean reachable(FrontierWorldState state, ShipmentEndpoint sender, ShipmentEndpoint receiver, SubjectId owner) {
        return routeStatus(state, sender, receiver, owner) == PedestrianRouteResult.Status.FOUND;
    }
    static PedestrianRouteResult.Status routeStatus(FrontierWorldState state, ShipmentEndpoint sender, ShipmentEndpoint receiver, SubjectId owner) {
        if (sender.containerId().equals(receiver.containerId())) return PedestrianRouteResult.Status.FOUND;
        var order = new MovementOrder(owner, owner, 0, 1, List.of(receiver.station()),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        try {
            ShipmentEndpointComposition.knowledge(state, List.of(sender, receiver))
                    .plannedPath(sender.station(), order);
            return PedestrianRouteResult.Status.FOUND;
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { return unavailable.status(); }
    }
    private GoodsShipmentPlanning() { }
}
