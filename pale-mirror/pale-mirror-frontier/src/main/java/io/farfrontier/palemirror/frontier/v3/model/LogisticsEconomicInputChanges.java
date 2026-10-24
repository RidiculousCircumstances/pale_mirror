package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.model.SettlementEconomicPlanningState.Change.DELIVERY;

/** Logistics publishes changed arrival capabilities, never every pedestrian step. */
final class LogisticsEconomicInputChanges implements EconomicInputChangePort {
    @Override public void collect(FrontierWorldState before, FrontierWorldState after, Sink sink) {
        EconomicInputChangePort.difference(before.shipments().shipments(), after.shipments().shipments(), (old, next) -> {
            if (old != null && next != null && old.status() == next.status()
                    && old.quantity() == next.quantity() && old.reception().isPresent() == next.reception().isPresent()
                    && old.receiver().containerId().equals(next.receiver().containerId())) return;
            if (old != null) destination(old, sink);
            if (next != null) destination(next, sink);
        });
        EconomicInputChangePort.difference(before.shipments().missions(), after.shipments().missions(), (old, next) -> {
            if (old != null && next != null && old.stage() == next.stage()
                    && old.replenishment().isPresent() == next.replenishment().isPresent()) return;
            if (old != null) mission(before, old, sink);
            if (next != null) mission(after, next, sink);
        });
    }
    private static void destination(Shipment shipment, Sink sink) {
        sink.resource(shipment.receiver().settlementId(), DELIVERY, shipment.itemKind());
    }
    private static void mission(FrontierWorldState state, TransportMission mission, Sink sink) {
        for (var id : mission.shipmentIds()) {
            var shipment = state.shipments().shipments().get(id);
            if (shipment != null) destination(shipment, sink);
        }
    }
}
