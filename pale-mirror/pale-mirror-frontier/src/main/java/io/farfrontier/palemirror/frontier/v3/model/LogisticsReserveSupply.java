package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.*;
import java.util.*;

/** Complete accepted trip length, never remaining ETA mistaken for the next replenishment cycle. */
final class LogisticsReserveSupply implements ReserveSupplyPort {
    @Override public Key key() { return Key.LOGISTICS; }
    @Override public Facts read(FrontierWorldState state, SubjectId home) {
        return project(state, home, OptionalLong.empty());
    }
    @Override public Facts readAt(FrontierWorldState state, SubjectId home, long atTick) {
        if (atTick < 0) throw new IllegalArgumentException("negative logistics forecast instant");
        return project(state, home, OptionalLong.of(atTick));
    }
    private static Facts project(FrontierWorldState state, SubjectId home, OptionalLong atTick) {
        var timing = new TreeMap<String, Timing>();
        for (var shipment : state.shipments().shipments().values()) {
            var receiver = state.inventory().containers().get(shipment.receiver().containerId());
            if (shipment.terminal() || receiver == null || !receiver.ownerId().equals(home) || shipment.transportMissionId().isEmpty()) continue;
            var mission = state.shipments().missions().get(shipment.transportMissionId().orElseThrow());
            if (mission.supplies().isEmpty()) continue;
            var journey = mission.supplies().orElseThrow().journey();
            // Admission, provisioning and reserves must value the same complete
            // journey, including its declared duration margin. Remaining ETA is
            // not the lead time of the next replenishment.
            long lead = Math.addExact(io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionTiming.assess(
                    state.bootstrap().ruleset().expedition(), journey.outward().edges(), journey.returning().edges(),
                    mission.organization().itinerary()).durationTicks(), state.bootstrap().ruleset().goodsTrade().reviewInterval());
            timing.merge(shipment.itemKind(), new Timing(Math.max(1, lead), TimingBasis.DELIVERY_ESTIMATE),
                    (a, b) -> a.leadTicks() <= b.leadTicks() ? a : b);
        }
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        if (participant != null && state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().isPresent()) {
            // Quotes extend the planning horizon before the first caravan. They
            // remain estimates, never dated incoming stock or promised delivery.
            for (var order : GoodsSaleQuotes.forBuyer(state.companies().goodsTrade(), participant.party())) {
                if (order.availableQuantity() == 0 || atTick.isPresent() && order.expiresAtTick() <= atTick.getAsLong()) continue;
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
