package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.*;
import java.util.*;

/** Complete accepted trip length, never remaining ETA mistaken for the next replenishment cycle. */
final class LogisticsReserveSupply implements ReserveSupplyPort {
    @Override public Key key() { return Key.LOGISTICS; }
    @Override public Facts read(FrontierWorldState state, SubjectId home) {
        var timing = new TreeMap<String, Timing>(); var rules = state.bootstrap().ruleset().expedition();
        for (var shipment : state.shipments().shipments().values()) {
            var receiver = state.inventory().containers().get(shipment.receiver().containerId());
            if (shipment.terminal() || receiver == null || !receiver.ownerId().equals(home) || shipment.transportMissionId().isEmpty()) continue;
            var mission = state.shipments().missions().get(shipment.transportMissionId().orElseThrow());
            if (mission.supplies().isEmpty()) continue;
            var journey = mission.supplies().orElseThrow().journey();
            long travel = Math.multiplyExact(Math.addExact(journey.outward().edges(), journey.returning().edges()), rules.ticksPerRouteEdge());
            long lead = Math.addExact(travel, Math.multiplyExact(2, rules.destinationWorkTicks()));
            lead = Math.addExact(lead, state.bootstrap().ruleset().goodsTrade().reviewInterval());
            timing.merge(shipment.itemKind(), new Timing(Math.max(1, lead), TimingBasis.DELIVERY_ESTIMATE),
                    (a, b) -> a.leadTicks() <= b.leadTicks() ? a : b);
        }
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        if (participant != null && state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().isPresent()) {
            // Quotes extend the planning horizon before the first caravan. They
            // remain estimates, never dated incoming stock or promised delivery.
            for (var order : GoodsSaleQuotes.forBuyer(state.companies().goodsTrade(), participant.party())) {
                if (order.availableQuantity() == 0) continue;
                ProcurementTravelForecast.read(state, order.containerId(), order.itemKind(), participant,
                        TransportOrganization.Itinerary.DESTINATION_COLLECTION).ifPresent(estimate -> {
                    long lead = Math.addExact(estimate.journeyTicks(), state.bootstrap().ruleset().goodsTrade().reviewInterval());
                    timing.merge(order.itemKind(), new Timing(Math.max(1, lead), TimingBasis.DELIVERY_ESTIMATE),
                            (a, b) -> a.leadTicks() <= b.leadTicks() ? a : b);
                });
            }
        }
        return new Facts(Map.of(), List.of(), timing);
    }
}
