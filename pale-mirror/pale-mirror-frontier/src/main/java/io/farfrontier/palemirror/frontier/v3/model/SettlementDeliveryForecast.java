package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Joins exact incoming commercial/internal obligations with logistics advice, without counting orders twice. */
final class SettlementDeliveryForecast {
    private SettlementDeliveryForecast() { }
    static Map<String, List<SupplyPlanner.Arrival>> read(FrontierWorldState state, GoodsParticipant participant) {
        var owner = participant.party().id();
        // Every eligible warehouse is declared, but declaration alone grants no
        // use. Recheck current policy/custody for each local retained obligation.
        var containers = GoodsParticipantStorage.declared(state, participant);
        var contracts = containers.stream().flatMap(container -> state.companies().goodsTrade().receivables(container).stream());
        var claims = new HashMap<SubjectId, Integer>();
        for (var contract : contracts.toList())
            if (!contract.terminal() && contract.buyer().id().equals(owner)
                    && GoodsParticipantStorage.eligible(state, participant, contract.receiverContainerId(), contract.itemKind()))
                contract.outstandingClaims().forEach((id, quantity) -> {
                    if (claims.put(id, quantity) != null) throw new IllegalArgumentException("duplicate incoming trade claim");
                });
        var result = new TreeMap<String, List<SupplyPlanner.Arrival>>();
        var shipments = containers.stream().flatMap(container -> state.shipments().receiving(container).stream())
                .sorted(Comparator.comparing(Shipment::id)).toList();
        for (var shipment : shipments) {
            if (shipment.terminal() || shipment.reception().isPresent()
                    || !GoodsParticipantStorage.eligible(state, participant, shipment.receiver().containerId(), shipment.itemKind())) continue;
            int quantity = switch (shipment.authorization().kind()) {
                case GOODS_CONTRACT_SHIPMENT -> Math.min(shipment.quantity(), claims.getOrDefault(shipment.authorization().claimId(), 0));
                case INTERNAL_SHIPMENT, OWNED_GOODS_SHIPMENT -> shipment.authorization().claimantId().equals(owner) ? shipment.quantity() : 0;
            };
            if (quantity == 0) continue;
            var estimate = ShipmentDeliveryForecast.read(state, shipment);
            if (estimate.timing().isPresent()) result.computeIfAbsent(shipment.itemKind(), ignored -> new ArrayList<>())
                    .add(new SupplyPlanner.Arrival(quantity, estimate.timing().orElseThrow().planningTicks()));
        }
        result.replaceAll((item, arrivals) -> List.copyOf(arrivals));
        return Map.copyOf(result);
    }
}
